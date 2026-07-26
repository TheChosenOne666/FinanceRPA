"""健康检查端点。"""

from fastapi import APIRouter
from pydantic import BaseModel

from app import __version__

router = APIRouter(prefix="/api/v1/ai", tags=["health"])


class HealthResponse(BaseModel):
    """健康检查响应。"""

    status: str
    service: str
    version: str


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """健康检查端点。

    返回服务状态，供 Kubernetes liveness/readiness 探针或负载均衡健康检查使用。
    """
    return HealthResponse(status="up", service="finance-ai", version=__version__)
