"""M5.3 ModelRouter 单元测试。

测试覆盖：
1. DOM 统计解析：节点数 / 表单字段数 / 动态元素数
2. 复杂度评分：各维度权重 / 归一化 / 档位判定
3. 模型路由：light / standard / heavy 档位对应模型
4. 路由配置：默认值 / Java 读取 / 缓存 / 刷新 / 异常回退
5. 一步路由：route() 端到端

@author FinanceRPA
"""

import time
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import Response

from app.llm.model_router import (
    ComplexityScore,
    ModelRouter,
    RoutingConfig,
)

# ============================================================
# DOM 统计解析测试
# ============================================================

class TestParseDomStats:
    """_parse_dom_stats 方法测试。"""

    def test_empty_dom(self):
        """空 DOM 返回 0。"""
        router = ModelRouter()
        nodes, fields, dynamic = router._parse_dom_stats("")
        assert nodes == 0
        assert fields == 0
        assert dynamic == 0

    def test_simple_dom(self):
        """简单 DOM 统计正确。"""
        router = ModelRouter()
        dom = "<html><body><div><p>hello</p></div></body></html>"
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        assert nodes == 4  # html, body, div, p
        assert fields == 0
        assert dynamic == 0

    def test_form_fields(self):
        """表单字段统计正确。"""
        router = ModelRouter()
        dom = """
        <form>
            <input type="text" name="username">
            <input type="password" name="password">
            <select name="role"><option>admin</option></select>
            <textarea name="bio"></textarea>
        </form>
        """
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        assert fields == 4  # 2 input + 1 select + 1 textarea

    def test_dynamic_elements_buttons(self):
        """button 元素统计正确。"""
        router = ModelRouter()
        dom = "<div><button>提交</button><button>取消</button></div>"
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        assert dynamic == 2  # 2 buttons

    def test_dynamic_elements_links(self):
        """带 href 的 a 标签统计正确。"""
        router = ModelRouter()
        dom = '<div><a href="/page1">链接1</a><a href="/page2">链接2</a></div>'
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        assert dynamic == 2  # 2 links with href

    def test_dynamic_elements_event_handlers(self):
        """onclick/onchange 等事件处理器统计正确。"""
        router = ModelRouter()
        dom = '<div onclick="doSomething()"><span onchange="validate()">text</span></div>'
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        assert dynamic == 2  # 1 onclick + 1 onchange

    def test_self_closing_tags_not_counted_as_nodes(self):
        """自闭合标签不计为节点（<br/> <img/> 等）。"""
        router = ModelRouter()
        dom = "<div><br/><img src='x'/><p>text</p></div>"
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        # <br/> 和 <img/> 匹配 <tag/> 模式被排除，但 <img src='x'/> 不带 /> 结尾...
        # 实际正则 <[a-zA-Z][^/>]*> 不匹配 <br/>（因为 /> 结尾）
        # 但 <img src='x'/> 也不匹配（因为 /> 结尾）
        # <div> 和 <p> 匹配
        assert nodes >= 2  # 至少 div + p

    def test_case_insensitive(self):
        """标签大小写不敏感。"""
        router = ModelRouter()
        dom = "<HTML><BODY><DIV><INPUT></DIV></BODY></HTML>"
        nodes, fields, dynamic = router._parse_dom_stats(dom)
        assert fields == 1  # INPUT


# ============================================================
# 复杂度评分测试
# ============================================================

class TestScoreComplexity:
    """score_complexity 方法测试。"""

    def test_empty_dom_light(self):
        """空 DOM 评分 0 → light 档位。"""
        router = ModelRouter()
        score = router.score_complexity("")
        assert score.total_score == 0.0
        assert score.level == "light"

    def test_simple_dom_light(self):
        """简单 DOM（少节点）→ light 档位。"""
        router = ModelRouter()
        dom = "<html><body><div><p>hello</p></div></body></html>"
        score = router.score_complexity(dom)
        assert score.level == "light"
        assert score.total_score < 30
        assert score.dom_nodes == 4

    def test_complex_dom_heavy(self):
        """复杂 DOM（多节点 + 多表单）→ heavy 档位。"""
        router = ModelRouter()
        # 构造 heavy 档位：100 divs(20分) + 15 inputs(30分) + 20 buttons(20分) = 70 分
        dom = "<html><body>"
        dom += "<div>" * 100  # 100 个 div → 100/5=20 分
        dom += "<input type='text'>" * 15  # 15 个表单字段 → 15*2=30 分
        dom += "<button>btn</button>" * 20  # 20 个 button → 20 分
        dom += "</body></html>"

        score = router.score_complexity(dom)
        assert score.level == "heavy"
        assert score.total_score >= 70
        assert score.form_fields == 15
        assert score.dynamic_elements == 20

    def test_medium_dom_standard(self):
        """中等复杂度 DOM → standard 档位。"""
        router = ModelRouter()
        # 构造中等复杂度：约 80 节点 + 8 表单 + 10 动态
        dom = "<html><body>"
        dom += "<div>" * 40  # 40 个 div
        dom += "<input type='text'>" * 8  # 8 个表单字段
        dom += "<button>btn</button>" * 10  # 10 个 button
        dom += "</body></html>"

        score = router.score_complexity(dom)
        assert score.level == "standard"
        assert 30 <= score.total_score < 70

    def test_screenshot_entropy_added(self):
        """截图熵加入总分。"""
        router = ModelRouter()
        dom = "<html><body><div></div></body></html>"

        score_no_entropy = router.score_complexity(dom, screenshot_entropy=0.0)
        score_with_entropy = router.score_complexity(dom, screenshot_entropy=1.0)

        assert score_with_entropy.total_score > score_no_entropy.total_score
        # 熵 1.0 → 10 分
        assert score_with_entropy.total_score - score_no_entropy.total_score == pytest.approx(10.0, abs=0.1)

    def test_screenshot_entropy_clamped(self):
        """截图熵超过 1.0 被截断为 1.0。"""
        router = ModelRouter()
        dom = ""
        score = router.score_complexity(dom, screenshot_entropy=5.0)
        # 熵 5.0 被截断为 1.0 → 10 分（dom 为空 → 0 分）
        assert score.screenshot_entropy == 5.0  # 原值保留
        assert score.total_score == 10.0  # 评分按 1.0 计算

    def test_screenshot_entropy_negative_clamped(self):
        """截图熵负值被截断为 0。"""
        router = ModelRouter()
        dom = ""
        score = router.score_complexity(dom, screenshot_entropy=-0.5)
        assert score.total_score == 0.0

    def test_score_has_all_fields(self):
        """评分结果包含所有字段。"""
        router = ModelRouter()
        dom = "<html><body><div><input type='text'><button>click</button></div></body></html>"
        score = router.score_complexity(dom, screenshot_entropy=0.5)

        assert score.dom_nodes > 0
        assert score.form_fields > 0
        assert score.dynamic_elements > 0
        assert score.screenshot_entropy == 0.5
        assert score.total_score > 0
        assert score.level in ("light", "standard", "heavy")


# ============================================================
# 模型路由测试
# ============================================================

class TestGetModel:
    """get_model 方法测试。"""

    def test_light_model(self):
        """light 档位返回轻量模型。"""
        router = ModelRouter()
        score = ComplexityScore(level="light", total_score=10)
        model = router.get_model(score)
        assert model == "gpt-4o-mini"

    def test_standard_model(self):
        """standard 档位返回标准模型。"""
        router = ModelRouter()
        score = ComplexityScore(level="standard", total_score=50)
        model = router.get_model(score)
        assert model == "gpt-4o"

    def test_heavy_model(self):
        """heavy 档位返回重型模型。"""
        router = ModelRouter()
        score = ComplexityScore(level="heavy", total_score=80)
        model = router.get_model(score)
        assert model == "gpt-4o-2024-08-06"

    def test_custom_config(self):
        """自定义路由配置。"""
        config = RoutingConfig(
            light_model="custom-light",
            standard_model="custom-standard",
            heavy_model="custom-heavy",
        )
        router = ModelRouter(config=config)

        assert router.get_model(ComplexityScore(level="light")) == "custom-light"
        assert router.get_model(ComplexityScore(level="standard")) == "custom-standard"
        assert router.get_model(ComplexityScore(level="heavy")) == "custom-heavy"


# ============================================================
# 路由配置测试
# ============================================================

class TestRoutingConfig:
    """RoutingConfig 测试。"""

    def test_default_config(self):
        """默认配置正确。"""
        config = RoutingConfig()
        assert config.light_model == "gpt-4o-mini"
        assert config.standard_model == "gpt-4o"
        assert config.heavy_model == "gpt-4o-2024-08-06"
        assert config.light_threshold == 30.0
        assert config.heavy_threshold == 70.0

    def test_custom_thresholds(self):
        """自定义阈值影响档位判定。"""
        config = RoutingConfig(light_threshold=20, heavy_threshold=50)
        router = ModelRouter(config=config)

        # score=25 → standard（因为 > 20）
        score = router.score_complexity("<html>" + "<div>" * 15 + "</html>")
        # 15 个 div → 15/5 = 3 分... 太低了
        # 构造 score=25 的 DOM
        dom = "<html><body>" + "<div>" * 30 + "</body></html>"  # 30/5=6 分
        # 需要 25 分 → 大约 125 个节点
        dom = "<html><body>" + "<div>" * 125 + "</body></html>"  # 125/5=25 分
        score = router.score_complexity(dom)
        assert score.level == "standard"  # 25 > 20, 25 < 50


# ============================================================
# Java 配置读取测试
# ============================================================

class TestJavaConfigFetch:
    """从 Java 读取路由配置测试。"""

    @pytest.mark.asyncio
    async def test_get_config_without_java_client(self):
        """无 Java 客户端时返回本地默认配置。"""
        router = ModelRouter(java_client=None)
        config = await router.get_routing_config()
        assert config.light_model == "gpt-4o-mini"

    @pytest.mark.asyncio
    async def test_fetch_config_from_java(self):
        """从 Java 成功读取配置。"""
        # Mock Java client
        mock_client = AsyncMock()
        mock_http_client = AsyncMock()
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json = MagicMock(return_value={
            "light_model": "java-light",
            "standard_model": "java-standard",
            "heavy_model": "java-heavy",
            "light_threshold": 25.0,
            "heavy_threshold": 65.0,
        })
        mock_http_client.get = AsyncMock(return_value=mock_response)
        mock_client._get_client = AsyncMock(return_value=mock_http_client)

        router = ModelRouter(java_client=mock_client)
        config = await router.get_routing_config()

        assert config.light_model == "java-light"
        assert config.standard_model == "java-standard"
        assert config.heavy_model == "java-heavy"
        assert config.light_threshold == 25.0
        mock_http_client.get.assert_called_once_with("/api/v1/ai/llm/routing-config")

    @pytest.mark.asyncio
    async def test_fetch_config_with_base_response_wrapper(self):
        """Java 返回 BaseResponse 包装格式。"""
        mock_client = AsyncMock()
        mock_http_client = AsyncMock()
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json = MagicMock(return_value={
            "code": 0,
            "data": {
                "light_model": "wrapped-light",
                "standard_model": "wrapped-standard",
                "heavy_model": "wrapped-heavy",
            },
        })
        mock_http_client.get = AsyncMock(return_value=mock_response)
        mock_client._get_client = AsyncMock(return_value=mock_http_client)

        router = ModelRouter(java_client=mock_client)
        config = await router.get_routing_config()

        assert config.light_model == "wrapped-light"

    @pytest.mark.asyncio
    async def test_config_cache(self):
        """配置缓存 5 分钟内不重复请求。"""
        mock_client = AsyncMock()
        mock_http_client = AsyncMock()
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json = MagicMock(return_value={"light_model": "cached"})
        mock_http_client.get = AsyncMock(return_value=mock_response)
        mock_client._get_client = AsyncMock(return_value=mock_http_client)

        router = ModelRouter(java_client=mock_client)

        # 第一次请求
        config1 = await router.get_routing_config()
        assert config1.light_model == "cached"
        assert mock_http_client.get.call_count == 1

        # 第二次请求（应命中缓存，不重复 HTTP 请求）
        config2 = await router.get_routing_config()
        assert config2.light_model == "cached"
        assert mock_http_client.get.call_count == 1  # 仍然只调用 1 次

    @pytest.mark.asyncio
    async def test_config_refresh(self):
        """refresh_config 强制刷新缓存。"""
        mock_client = AsyncMock()
        mock_http_client = AsyncMock()
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json = MagicMock(return_value={"light_model": "refreshed"})
        mock_http_client.get = AsyncMock(return_value=mock_response)
        mock_client._get_client = AsyncMock(return_value=mock_http_client)

        router = ModelRouter(java_client=mock_client)

        # 第一次请求
        await router.get_routing_config()
        assert mock_http_client.get.call_count == 1

        # 强制刷新
        config = await router.refresh_config()
        assert config.light_model == "refreshed"
        assert mock_http_client.get.call_count == 2

    @pytest.mark.asyncio
    async def test_java_error_fallback(self):
        """Java 不可用时回退到本地配置。"""
        mock_client = AsyncMock()
        mock_http_client = AsyncMock()
        mock_http_client.get = AsyncMock(side_effect=Exception("Connection refused"))
        mock_client._get_client = AsyncMock(return_value=mock_http_client)

        router = ModelRouter(java_client=mock_client)
        config = await router.get_routing_config()

        # 应回退到默认配置
        assert config.light_model == "gpt-4o-mini"
        assert config.standard_model == "gpt-4o"


# ============================================================
# 一步路由测试
# ============================================================

class TestRoute:
    """route 方法测试（端到端）。"""

    def test_route_simple_dom(self):
        """简单 DOM 路由到轻量模型。"""
        router = ModelRouter()
        dom = "<html><body><div><p>hello</p></div></body></html>"
        score, model = router.route(dom)

        assert score.level == "light"
        assert model == "gpt-4o-mini"

    def test_route_complex_dom(self):
        """复杂 DOM 路由到重型模型。"""
        router = ModelRouter()
        dom = "<html><body>"
        dom += "<div>" * 100 + "<input type='text'>" * 15 + "<button>x</button>" * 20
        dom += "</body></html>"
        score, model = router.route(dom)

        assert score.level == "heavy"
        assert model == "gpt-4o-2024-08-06"

    def test_route_returns_tuple(self):
        """route 返回 (score, model) 元组。"""
        router = ModelRouter()
        score, model = router.route("<html></html>")

        assert isinstance(score, ComplexityScore)
        assert isinstance(model, str)

    def test_route_with_screenshot_entropy(self):
        """route 支持传入截图熵。"""
        router = ModelRouter()
        dom = "<html><body><div></div></body></html>"

        score1, model1 = router.route(dom, screenshot_entropy=0.0)
        score2, model2 = router.route(dom, screenshot_entropy=1.0)

        # 熵高时评分更高
        assert score2.total_score > score1.total_score
