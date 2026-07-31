"""1Password Client stub 模块。

M3.7：Skyvern 调用 ``OnePasswordClient`` 时实际不会创建实例（凭据 Vault 未启用时跳过），
stub 提供类签名占位，实例化时抛 RuntimeError 避免误用。

@author FinanceRPA
@from https://github.com/1Password/onepassword-sdk-python
"""


class Client:
    """1Password SDK Client stub。

    真实 SDK 通过 ``Client.new_client(...)`` 异步工厂方法创建，
    本 stub 不实现任何方法，实例化或调用方法时抛 RuntimeError。
    """

    def __init__(self, *args: object, **kwargs: object) -> None:
        """禁止实例化：本项目未启用 1Password 凭据 Vault。"""
        raise RuntimeError(
            "1Password SDK 未安装。本项目使用 SKYVERN_STORAGE_TYPE=local，"
            "不依赖 1Password 凭据 Vault。如需启用请安装官方 SDK："
            "https://github.com/1Password/onepassword-sdk-python"
        )


__all__ = ["Client"]
