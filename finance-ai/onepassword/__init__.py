"""1Password SDK stub 模块。

M3.7：Skyvern 在 skyvern/forge/sdk/workflow/context_manager.py 中导入
``from onepassword import ItemFieldType`` 与 ``from onepassword.client import Client``，
但官方 1Password Python SDK 不在 PyPI 上（仅通过 GitHub Packages 分发）。
本项目使用本地凭据存储（SKYVERN_STORAGE_TYPE=local），不使用 1Password 凭据 Vault，
因此提供 stub 让导入通过；调用相关方法时会抛 RuntimeError 提示未启用。

@author FinanceRPA
@from https://github.com/1Password/onepassword-sdk-python
"""

from enum import Enum


class ItemFieldType(str, Enum):
    """1Password ItemFieldType stub 枚举（覆盖 Skyvern 使用的字段类型）。"""

    TEXT = "text"
    PASSWORD = "password"
    EMAIL = "email"
    URL = "url"
    OTP = "otp"
    DATE = "date"
    MONTH_YEAR = "monthYear"
    CONCEALED = "concealed"


__all__ = ["ItemFieldType"]
