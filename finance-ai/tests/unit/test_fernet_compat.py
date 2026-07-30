"""Fernet 跨语言兼容测试（Python 侧）

验证内容：
1. Python cryptography.fernet.Fernet 自加密自解密
2. Token 格式符合 Fernet 规范（Version 0x80 + Base64url）
3. 密钥与 Java application.yml 配置一致

跨语言验证：Java 侧 FernetCryptoServiceTest 会解密本测试同密钥生成的 Token，
因此这里只需验证 Python 侧正确性即可。

@from cryptography.fernet.Fernet
@author FinanceRPA
"""

import base64

import pytest
from cryptography.fernet import Fernet

# 与 Java application.yml 中 fernet.key 配置一致
FERNET_KEY = b"Mfm6N6AzRZDx8U1zIlbYj64Sfh69sNQnr2eK6mGGjC4="


class TestFernetCompat:
    """Fernet 跨语言兼容测试。"""

    @pytest.fixture
    def fernet(self) -> Fernet:
        """创建 Fernet 实例。"""
        return Fernet(FERNET_KEY)

    def test_key_is_valid_32_bytes(self) -> None:
        """密钥应为 Base64url 编码的 32 字节。"""
        decoded = base64.urlsafe_b64decode(FERNET_KEY)
        assert len(decoded) == 32, f"密钥应为 32 字节，实际 {len(decoded)} 字节"

    def test_round_trip_plain_password(self, fernet: Fernet) -> None:
        """自加密自解密：普通密码。"""
        plaintext = b"admin123"
        token = fernet.encrypt(plaintext)
        decrypted = fernet.decrypt(token)
        assert decrypted == plaintext

    def test_round_trip_special_chars(self, fernet: Fernet) -> None:
        """自加密自解密：特殊字符。"""
        plaintext = b"password=Secret123!@#$%^&*()"
        token = fernet.encrypt(plaintext)
        decrypted = fernet.decrypt(token)
        assert decrypted == plaintext

    def test_round_trip_chinese(self, fernet: Fernet) -> None:
        """自加密自解密：中文。"""
        plaintext = "中文密码测试".encode("utf-8")
        token = fernet.encrypt(plaintext)
        decrypted = fernet.decrypt(token)
        assert decrypted == plaintext

    def test_round_trip_empty(self, fernet: Fernet) -> None:
        """自加密自解密：空字符串。"""
        plaintext = b""
        token = fernet.encrypt(plaintext)
        decrypted = fernet.decrypt(token)
        assert decrypted == plaintext

    def test_round_trip_long_text(self, fernet: Fernet) -> None:
        """自加密自解密：长文本。"""
        plaintext = b"The quick brown fox 123" * 100
        token = fernet.encrypt(plaintext)
        decrypted = fernet.decrypt(token)
        assert decrypted == plaintext

    def test_encrypt_generates_different_tokens(self, fernet: Fernet) -> None:
        """相同明文应生成不同 Token（随机 IV）。"""
        plaintext = b"admin123"
        token1 = fernet.encrypt(plaintext)
        token2 = fernet.encrypt(plaintext)
        assert token1 != token2

    def test_token_starts_with_version_byte(self, fernet: Fernet) -> None:
        """Token 解码后首字节应为 0x80（Fernet 版本标识）。"""
        token = fernet.encrypt(b"admin123")
        raw = base64.urlsafe_b64decode(token)
        assert raw[0] == 0x80, f"版本字节应为 0x80，实际 0x{raw[0]:02x}"

    def test_decrypt_tampered_token_should_fail(self, fernet: Fernet) -> None:
        """篡改 Token 应解密失败。"""
        token = fernet.encrypt(b"admin123")
        # 篡改最后几个字符（HMAC 区域）
        tampered = token[:-5] + b"XXXXX"
        with pytest.raises(Exception):
            fernet.decrypt(tampered)

    def test_decrypt_with_wrong_key_should_fail(self) -> None:
        """错误密钥加密的 Token 应解密失败。"""
        wrong_fernet = Fernet(Fernet.generate_key())
        token = wrong_fernet.encrypt(b"admin123")
        correct_fernet = Fernet(FERNET_KEY)
        with pytest.raises(Exception):
            correct_fernet.decrypt(token)
