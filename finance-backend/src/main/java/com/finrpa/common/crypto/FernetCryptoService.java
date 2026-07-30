package com.finrpa.common.crypto;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Fernet 对称加密服务（与 Python cryptography 库字节级兼容）
 *
 * <p>Fernet 算法规格（参考 Python cryptography 库）：
 * <ul>
 *   <li>密钥：32 字节，Base64url 编码。前 16 字节为 HMAC-SHA256 签名密钥，后 16 字节为 AES-128-CBC 加密密钥</li>
 *   <li>Token 格式：Version(0x80) || Timestamp(8B) || IV(16B) || Ciphertext || HMAC(32B)</li>
 *   <li>加密：AES-128-CBC + PKCS7Padding</li>
 *   <li>签名：HMAC-SHA256(signing_key, Version || Timestamp || IV || Ciphertext)</li>
 *   <li>编码：Base64url（无 padding）</li>
 * </ul>
 *
 * <p>密钥通过环境变量 {@code FERNET_KEY} 注入（Base64url 编码的 32 字节）。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Component
public class FernetCryptoService {

    /** Fernet 版本标识 */
    private static final int VERSION = 0x80;

    /** 时间戳字节数 */
    private static final int TIMESTAMP_BYTES = 8;

    /** IV 字节数（AES 块大小） */
    private static final int IV_BYTES = 16;

    /** HMAC-SHA256 输出字节数 */
    private static final int HMAC_BYTES = 32;

    /** 最小密文字节数（至少一个 AES 块） */
    private static final int MIN_CIPHERTEXT_BYTES = 16;

    /** Token 最小总长度：1 + 8 + 16 + 16 + 32 = 73 */
    private static final int MIN_TOKEN_LENGTH = 1 + TIMESTAMP_BYTES + IV_BYTES + MIN_CIPHERTEXT_BYTES + HMAC_BYTES;

    /** 签名数据起始偏移（Version + Timestamp + IV + Ciphertext） */
    private static final int SIGNED_PART_HEADER = 1 + TIMESTAMP_BYTES + IV_BYTES;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

    /** HMAC-SHA256 签名密钥（前 16 字节） */
    private final byte[] signingKey;

    /** AES-128-CBC 加密密钥（后 16 字节） */
    private final byte[] encryptionKey;

    /**
     * 构造 Fernet 加密服务。
     *
     * @param fernetKey Fernet 密钥（Base64url 编码的 32 字节），从配置 {@code fernet.key} 注入
     */
    public FernetCryptoService(@Value("${fernet.key}") String fernetKey) {
        byte[] key = urlDecoder.decode(fernetKey);
        // 校验密钥长度必须为 32 字节
        ThrowUtils.throwIf(key.length != 32, ErrorCode.FERNET_KEY_INVALID,
                "Fernet 密钥必须为 32 字节，实际: " + key.length);
        this.signingKey = new byte[16];
        this.encryptionKey = new byte[16];
        // 前 16 字节为签名密钥，后 16 字节为加密密钥
        System.arraycopy(key, 0, signingKey, 0, 16);
        System.arraycopy(key, 16, encryptionKey, 0, 16);
        log.info("FernetCryptoService: 初始化完成，密钥长度=32 字节");
    }

    /**
     * 加密明文为 Fernet Token。
     *
     * @param plaintext 明文字符串（UTF-8）
     * @return Fernet Token（Base64url 字符串）
     */
    public String encrypt(String plaintext) {
        ThrowUtils.throwIf(plaintext == null, ErrorCode.PARAMS_ERROR, "明文不能为空");
        try {
            // 1. 生成随机 IV
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            // 2. AES-128-CBC 加密
            byte[] ciphertext = aesEncrypt(plaintext.getBytes(StandardCharsets.UTF_8), iv);

            // 3. 构建签名部分：Version || Timestamp || IV || Ciphertext
            long timestamp = Instant.now().getEpochSecond();
            byte[] signedPart = buildSignedPart(timestamp, iv, ciphertext);

            // 4. 计算 HMAC-SHA256
            byte[] hmac = hmacSha256(signingKey, signedPart);

            // 5. 拼接完整 Token 并 Base64url 编码
            byte[] token = new byte[signedPart.length + HMAC_BYTES];
            System.arraycopy(signedPart, 0, token, 0, signedPart.length);
            System.arraycopy(hmac, 0, token, signedPart.length, HMAC_BYTES);

            return urlEncoder.encodeToString(token);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fernet 加密失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FERNET_ENCRYPT_FAILED, "Fernet 加密失败: " + e.getMessage());
        }
    }

    /**
     * 解密 Fernet Token（不校验 TTL）。
     *
     * @param token Fernet Token（Base64url 字符串）
     * @return 明文字符串
     */
    public String decrypt(String token) {
        return decrypt(token, 0);
    }

    /**
     * 解密 Fernet Token（带 TTL 校验）。
     *
     * @param token       Fernet Token（Base64url 字符串）
     * @param ttlSeconds  生存时间（秒），0 表示不校验
     * @return 明文字符串
     */
    public String decrypt(String token, long ttlSeconds) {
        ThrowUtils.throwIf(token == null || token.isEmpty(), ErrorCode.PARAMS_ERROR, "Token 不能为空");
        try {
            // 1. Base64url 解码
            byte[] raw = urlDecoder.decode(token);
            ThrowUtils.throwIf(raw.length < MIN_TOKEN_LENGTH, ErrorCode.FERNET_DECRYPT_FAILED,
                    "Token 长度不合法: " + raw.length);

            // 2. 校验版本
            int version = raw[0] & 0xFF;
            ThrowUtils.throwIf(version != VERSION, ErrorCode.FERNET_DECRYPT_FAILED,
                    "Fernet 版本不匹配: 0x" + Integer.toHexString(version));

            // 3. 解析时间戳并校验 TTL
            long timestamp = readBigEndianLong(raw, 1);
            if (ttlSeconds > 0) {
                long age = Instant.now().getEpochSecond() - timestamp;
                ThrowUtils.throwIf(age > ttlSeconds, ErrorCode.FERNET_DECRYPT_FAILED,
                        "Token 已过期，年龄=" + age + "s，TTL=" + ttlSeconds + "s");
            }

            // 4. 解析 IV、Ciphertext、HMAC
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(raw, 1 + TIMESTAMP_BYTES, iv, 0, IV_BYTES);

            int ciphertextLen = raw.length - MIN_TOKEN_LENGTH + MIN_CIPHERTEXT_BYTES;
            byte[] ciphertext = new byte[ciphertextLen];
            System.arraycopy(raw, SIGNED_PART_HEADER, ciphertext, 0, ciphertextLen);

            byte[] hmac = new byte[HMAC_BYTES];
            System.arraycopy(raw, raw.length - HMAC_BYTES, hmac, 0, HMAC_BYTES);

            // 5. HMAC 校验（使用常量时间比较防止时序攻击）
            byte[] signedPart = new byte[raw.length - HMAC_BYTES];
            System.arraycopy(raw, 0, signedPart, 0, signedPart.length);
            byte[] expectedHmac = hmacSha256(signingKey, signedPart);
            ThrowUtils.throwIf(!MessageDigest.isEqual(hmac, expectedHmac),
                    ErrorCode.FERNET_DECRYPT_FAILED, "HMAC 校验失败");

            // 6. AES-128-CBC 解密
            byte[] plaintext = aesDecrypt(ciphertext, iv);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fernet 解密失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FERNET_DECRYPT_FAILED, "Fernet 解密失败: " + e.getMessage());
        }
    }

    /**
     * 构建签名部分：Version || Timestamp || IV || Ciphertext。
     */
    private byte[] buildSignedPart(long timestamp, byte[] iv, byte[] ciphertext) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + TIMESTAMP_BYTES + IV_BYTES + ciphertext.length);
        buffer.put((byte) VERSION);
        buffer.putLong(timestamp);
        buffer.put(iv);
        buffer.put(ciphertext);
        return buffer.array();
    }

    /**
     * AES-128-CBC 加密（PKCS5Padding 等同于 PKCS7Padding）。
     */
    private byte[] aesEncrypt(byte[] plaintext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(plaintext);
    }

    /**
     * AES-128-CBC 解密。
     */
    private byte[] aesDecrypt(byte[] ciphertext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }

    /**
     * 计算 HMAC-SHA256。
     */
    private byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /**
     * 读取大端序 8 字节无符号整数。
     */
    private long readBigEndianLong(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < TIMESTAMP_BYTES; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }
}
