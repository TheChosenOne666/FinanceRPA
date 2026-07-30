package com.finrpa.common.crypto;

import com.finrpa.common.exception.BusinessException;
import com.finrpa.common.response.ErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FernetCryptoService 单元测试
 *
 * <p>验证内容：
 * <ul>
 *   <li>Java 自加密自解密（round-trip）</li>
 *   <li>跨语言兼容：解密 Python cryptography.fernet.Fernet 生成的 Token</li>
 *   <li>错误场景：篡改 Token / 错误密钥 / 无效密钥长度</li>
 * </ul>
 *
 * <p>Python Token 由 {@code cryptography.fernet.Fernet} 用相同密钥生成，
 * 确保字节级兼容。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@DisplayName("FernetCryptoService 加解密测试")
class FernetCryptoServiceTest {

    /** 测试密钥（与 application.yml 一致，Base64url 编码 32 字节） */
    private static final String FERNET_KEY = "Mfm6N6AzRZDx8U1zIlbYj64Sfh69sNQnr2eK6mGGjC4=";

    /** 另一个有效密钥（用于错误密钥测试） */
    private static final String ANOTHER_KEY = "ZmDfcTF7_60GrrY167zsiPd67pEvs0aGOv2oasOM1Pg=";

    private FernetCryptoService fernet;

    @BeforeEach
    void setUp() {
        fernet = new FernetCryptoService(FERNET_KEY);
    }

    // region 自加密自解密

    @Test
    @DisplayName("自加密自解密：普通密码")
    void testRoundTripPlainPassword() {
        String plaintext = "admin123";
        String token = fernet.encrypt(plaintext);
        String decrypted = fernet.decrypt(token);
        Assertions.assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("自加密自解密：含特殊字符")
    void testRoundTripSpecialChars() {
        String plaintext = "password=Secret123!@#$%^&*()";
        String token = fernet.encrypt(plaintext);
        String decrypted = fernet.decrypt(token);
        Assertions.assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("自加密自解密：中文")
    void testRoundTripChinese() {
        String plaintext = "中文密码测试";
        String token = fernet.encrypt(plaintext);
        String decrypted = fernet.decrypt(token);
        Assertions.assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("自加密自解密：空字符串")
    void testRoundTripEmpty() {
        String plaintext = "";
        String token = fernet.encrypt(plaintext);
        String decrypted = fernet.decrypt(token);
        Assertions.assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("自加密自解密：单字符")
    void testRoundTripSingleChar() {
        String plaintext = "a";
        String token = fernet.encrypt(plaintext);
        String decrypted = fernet.decrypt(token);
        Assertions.assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("自加密自解密：长文本")
    void testRoundTripLongText() {
        String plaintext = "The quick brown fox jumps over the lazy dog 1234567890 !@#$%^&*()".repeat(10);
        String token = fernet.encrypt(plaintext);
        String decrypted = fernet.decrypt(token);
        Assertions.assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("每次加密生成不同 Token（随机 IV）")
    void testEncryptGeneratesDifferentTokens() {
        String plaintext = "admin123";
        String token1 = fernet.encrypt(plaintext);
        String token2 = fernet.encrypt(plaintext);
        Assertions.assertNotEquals(token1, token2, "相同明文应生成不同 Token（随机 IV）");
    }

    // endregion

    // region 跨语言兼容：解密 Python 生成的 Token

    @Test
    @DisplayName("跨语言兼容：解密 Python 生成的 Token（admin123）")
    void testDecryptPythonTokenAdminPassword() {
        // 由 Python cryptography.fernet.Fernet 用 FERNET_KEY 加密 "admin123" 生成
        String pythonToken = "gAAAAABqa2IqiOmsJ2Z5mtw269VH3eJRclZ4Dh2fqGHVhxRmIWGnG7YcHek4k51GfXI85J0bl_xD6U6ZtNbBcQAQDhz89_MEZA==";
        String decrypted = fernet.decrypt(pythonToken);
        Assertions.assertEquals("admin123", decrypted);
    }

    @Test
    @DisplayName("跨语言兼容：解密 Python 生成的 Token（特殊字符）")
    void testDecryptPythonTokenSpecialChars() {
        String pythonToken = "gAAAAABqa2Iqh_C5RTHyPk0fozuLsyC7Bt5rc-Y9U82m5qQ15hQp2Ul8PqhLu6KFNEYkx6kWh5HRqvPKalXHhAyku4oZT7MzD78y1ev3eCtvrcDdU4ksZSU=";
        String decrypted = fernet.decrypt(pythonToken);
        Assertions.assertEquals("password=Secret123!", decrypted);
    }

    @Test
    @DisplayName("跨语言兼容：解密 Python 生成的 Token（中文）")
    void testDecryptPythonTokenChinese() {
        String pythonToken = "gAAAAABqa2Iqks1bl1KurlvyX5C8EWEhPgQmvziXvfDwB8Ce3ngKFNyg7-e-bTg8VomyXzUfsdMzQ7Sd0pw1iJO4mt0z3bClS8IicRZo-iY2ZGs_pLlQPPQ=";
        String decrypted = fernet.decrypt(pythonToken);
        Assertions.assertEquals("中文密码测试", decrypted);
    }

    @Test
    @DisplayName("跨语言兼容：解密 Python 生成的 Token（空字符串）")
    void testDecryptPythonTokenEmpty() {
        String pythonToken = "gAAAAABqa2IqDibSLxXOP2UzQIEJg2SF2Rm7K9i-eZUrt8QvoGkJOBSMi-yG31i3YRjzaKFm1OMRCPgbqIf4Dx-y8hBVuQJfvA==";
        String decrypted = fernet.decrypt(pythonToken);
        Assertions.assertEquals("", decrypted);
    }

    @Test
    @DisplayName("跨语言兼容：解密 Python 生成的 Token（单字符）")
    void testDecryptPythonTokenSingleChar() {
        String pythonToken = "gAAAAABqa2IqiZWykBIjNvw1IkCgKI0XDcI4Iyu4WdglQQjLNhs3fJ8mcmWCqRTU7_pKhWdPWC8dMZBKm5xwGbdrhnj2SG5FZg==";
        String decrypted = fernet.decrypt(pythonToken);
        Assertions.assertEquals("a", decrypted);
    }

    @Test
    @DisplayName("跨语言兼容：解密 Python 生成的 Token（混合文本）")
    void testDecryptPythonTokenMixedText() {
        String pythonToken = "gAAAAABqa2IqmWfodUwPjPYdr8IvBNdl6hYfQO9HR34C9K2O92T7oWGQ4bKEZnJQHbVbZfhonDd8T8VbuiM7tMNV_De14Zvb2WHR1b8doz9leib-EXa3YHY=";
        String decrypted = fernet.decrypt(pythonToken);
        Assertions.assertEquals("The quick brown fox 123 !@#", decrypted);
    }

    // endregion

    // region 错误场景

    @Test
    @DisplayName("篡改 Token 应解密失败")
    void testDecryptTamperedTokenShouldFail() {
        String token = fernet.encrypt("admin123");
        // 篡改最后几个字符（HMAC 区域）
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> fernet.decrypt(tampered)
        );
        Assertions.assertEquals(ErrorCode.FERNET_DECRYPT_FAILED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("错误密钥加密的 Token 应解密失败")
    void testDecryptWithWrongKeyShouldFail() {
        // 用另一个密钥加密
        FernetCryptoService wrongKeyService = new FernetCryptoService(ANOTHER_KEY);
        String token = wrongKeyService.encrypt("admin123");
        // 用正确密钥解密应失败（HMAC 校验不通过）
        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> fernet.decrypt(token)
        );
        Assertions.assertEquals(ErrorCode.FERNET_DECRYPT_FAILED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("无效密钥长度应抛异常")
    void testInvalidKeyLengthShouldFail() {
        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> new FernetCryptoService("AAAA")
        );
        Assertions.assertEquals(ErrorCode.FERNET_KEY_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("空 Token 应抛异常")
    void testDecryptEmptyTokenShouldFail() {
        BusinessException ex = Assertions.assertThrows(
                BusinessException.class,
                () -> fernet.decrypt("")
        );
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    // endregion
}
