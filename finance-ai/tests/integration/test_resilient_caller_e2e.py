"""M5.1 三层容错集成测试：端到端真实场景验证。

启动 Mock Java 后端 HTTP 服务捕获 LLM 调用记录 + NEEDS_HUMAN 事件，
使用可配置 Mock LLM 模拟 5 种真实场景，验证三层容错全链路。

运行方式：
    uv run python tests/integration/test_resilient_caller_e2e.py

@author FinanceRPA
"""

import asyncio
import json
import logging
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from typing import Any
from urllib.parse import urlparse

# 确保项目根目录在 sys.path
sys.path.insert(0, ".")

from app.agent.coordinator import AgentCoordinator
from app.agent.planner import PlannerAgent
from app.clients.java_backend import JavaBackendClient
from app.llm.resilient_caller import LlmCallRecord, NeedsHumanError, ResilientCaller

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(name)s] %(levelname)s %(message)s")
logger = logging.getLogger("e2e_test")

# ============================================================
# Mock Java 后端 HTTP 服务
# ============================================================

class MockJavaState:
    """捕获 Java 后端接收到的所有请求。"""
    def __init__(self):
        self.llm_calls: list[dict] = []
        self.task_states: list[dict] = []
        self.subtasks: list[dict] = []
        self.coordination_states: list[dict] = []

    def reset(self):
        self.llm_calls.clear()
        self.task_states.clear()
        self.subtasks.clear()
        self.coordination_states.clear()


_mock_state = MockJavaState()


class MockJavaHandler(BaseHTTPRequestHandler):
    """Mock Java 后端请求处理器。"""

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length else "{}"
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            payload = {"raw": body}

        path = urlparse(self.path).path

        if "/llm/calls" in path:
            _mock_state.llm_calls.append(payload)
            self._respond(200, {"code": 0, "message": "ok"})
        elif "/state" in path and "coordination" not in path:
            _mock_state.task_states.append({"path": path, **payload})
            self._respond(200, {"code": 0, "message": "ok"})
        elif "/subtasks" in path:
            _mock_state.subtasks.append({"path": path, **payload})
            self._respond(200, {"code": 0, "message": "ok"})
        elif "/coordination-state" in path:
            _mock_state.coordination_states.append({"path": path, **payload})
            self._respond(200, {"code": 0, "message": "ok"})
        else:
            self._respond(200, {"code": 0, "message": "ok"})

    def _respond(self, status: int, data: dict):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, format, *args):
        pass  # 静默


def start_mock_java_server(port: int = 18099) -> HTTPServer:
    """启动 Mock Java 后端 HTTP 服务。"""
    server = HTTPServer(("127.0.0.1", port), MockJavaHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    logger.info("Mock Java 后端已启动: http://127.0.0.1:%d", port)
    return server


# ============================================================
# 可配置 Mock LLM
# ============================================================

class MockLLM:
    """可配置的 Mock LLM，按预设响应序列返回。

    支持场景：
    - 正常返回合法 JSON
    - 返回非法 JSON（触发层 2 重试）
    - 返回缺字段 JSON（触发 Pydantic 校验失败）
    - 返回 ```json 代码块包裹
    - 网络异常
    """

    def __init__(self, responses: list[str], fail_with: Exception | None = None):
        """
        @param responses: 按顺序返回的响应列表（每次调用取下一个）
        @param fail_with: 如果设置，调用时抛此异常（模拟网络错误）
        """
        self.responses = list(responses)
        self.fail_with = fail_with
        self._idx = 0
        self.call_count = 0
        self.prompts_received: list[str] = []

    async def __call__(self, prompt: str) -> str:
        self.call_count += 1
        self.prompts_received.append(prompt)

        if self.fail_with:
            raise self.fail_with

        if self._idx < len(self.responses):
            resp = self.responses[self._idx]
        else:
            resp = self.responses[-1] if self.responses else "{}"
        self._idx += 1

        # 模拟 LLM 延迟
        await asyncio.sleep(0.05)
        return resp


# ============================================================
# 测试场景数据
# ============================================================

VALID_PLAN_JSON = json.dumps({
    "steps": [
        {"goal": "登录网银系统", "completion_condition": "URL 包含 /dashboard", "failure_strategy": "abort", "max_retries": 3},
        {"goal": "导航到流水下载页", "completion_condition": "页面标题包含 流水", "failure_strategy": "replan", "max_retries": 2},
        {"goal": "选择日期范围并下载", "completion_condition": "文件下载触发", "failure_strategy": "retry", "max_retries": 2},
    ]
}, ensure_ascii=False)

INVALID_JSON = "抱歉，我无法完成这个任务。"
# 缺少 steps 字段 → PlannerOutput Pydantic 校验失败
WRONG_SCHEMA_JSON = json.dumps({"description": "这是一个计划", "items": []})
CODE_BLOCK_JSON = '```json\n{"steps": [{"goal": "登录", "completion_condition": "成功", "failure_strategy": "abort", "max_retries": 2}]}\n```'


# ============================================================
# 测试场景
# ============================================================

class TestResult:
    """单个测试场景结果。"""
    def __init__(self, name: str):
        self.name = name
        self.passed = False
        self.duration_ms = 0
        self.details: list[str] = []
        self.llm_call_count = 0
        self.java_llm_reports = 0
        self.java_needs_human = False

    def mark_pass(self, msg: str = ""):
        self.passed = True
        if msg:
            self.details.append(f"  ✅ {msg}")

    def mark_fail(self, msg: str):
        self.passed = False
        self.details.append(f"  ❌ {msg}")

    def info(self, msg: str):
        self.details.append(f"  ℹ️  {msg}")


async def scenario_1_normal_success(java_client: JavaBackendClient) -> TestResult:
    """场景 1：正常流程 — LLM 首次返回合法 JSON → 直接成功。"""
    result = TestResult("场景 1: 正常成功（首次即合法）")
    _mock_state.reset()

    llm = MockLLM([VALID_PLAN_JSON])
    caller = ResilientCaller(llm, max_retries=2, java_client=java_client, model_name="gpt-4o-mini")
    planner = PlannerAgent(resilient_caller=caller)

    start = time.monotonic()
    try:
        plan = await planner.create_plan("下载 Q1 银行流水", task_id="e2e-001")
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.llm_call_count = llm.call_count
        result.java_llm_reports = len(_mock_state.llm_calls)

        if len(plan.subtasks) == 3:
            result.mark_pass(f"生成 3 个子任务: {[s.goal for s in plan.subtasks]}")
        else:
            result.mark_fail(f"期望 3 个子任务，实际 {len(plan.subtasks)}")

        if llm.call_count == 1:
            result.mark_pass("LLM 仅调用 1 次（未重试）")
        else:
            result.mark_fail(f"期望 LLM 调用 1 次，实际 {llm.call_count} 次")

        if len(_mock_state.llm_calls) == 1:
            result.mark_pass("Java 收到 1 条 LLM 调用记录（success=True）")
        else:
            result.mark_fail(f"期望 Java 收到 1 条记录，实际 {len(_mock_state.llm_calls)}")

        if _mock_state.llm_calls and _mock_state.llm_calls[0].get("success"):
            result.mark_pass("调用记录 success=True")
        else:
            result.mark_fail("调用记录 success 应为 True")

    except Exception as e:
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.mark_fail(f"异常: {e}")

    return result


async def scenario_2_retry_then_success(java_client: JavaBackendClient) -> TestResult:
    """场景 2：首次返回非法 JSON → 重试 → 第二次返回合法 JSON → 成功。"""
    result = TestResult("场景 2: 重试后成功（首次非法 JSON）")
    _mock_state.reset()

    llm = MockLLM([INVALID_JSON, VALID_PLAN_JSON])
    caller = ResilientCaller(llm, max_retries=2, java_client=java_client)
    planner = PlannerAgent(resilient_caller=caller)

    start = time.monotonic()
    try:
        plan = await planner.create_plan("下载 Q2 银行流水", task_id="e2e-002")
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.llm_call_count = llm.call_count
        result.java_llm_reports = len(_mock_state.llm_calls)

        if len(plan.subtasks) == 3:
            result.mark_pass("重试后生成 3 个子任务")
        else:
            result.mark_fail(f"期望 3 个子任务，实际 {len(plan.subtasks)}")

        if llm.call_count == 2:
            result.mark_pass("LLM 调用 2 次（首次失败 + 重试成功）")
        else:
            result.mark_fail(f"期望 LLM 调用 2 次，实际 {llm.call_count}")

        # 验证第二次 prompt 包含错误反馈
        if len(llm.prompts_received) >= 2:
            if "PREVIOUS ATTEMPT FAILED" in llm.prompts_received[1]:
                result.mark_pass("第二次 prompt 包含错误反馈")
            else:
                result.mark_fail("第二次 prompt 应包含错误反馈")

        if len(_mock_state.llm_calls) == 2:
            result.mark_pass("Java 收到 2 条调用记录（1 失败 + 1 成功）")
        else:
            result.mark_fail(f"期望 Java 收到 2 条记录，实际 {len(_mock_state.llm_calls)}")

        # 验证第一条记录 success=False，第二条 success=True
        if len(_mock_state.llm_calls) == 2:
            if not _mock_state.llm_calls[0]["success"] and _mock_state.llm_calls[1]["success"]:
                result.mark_pass("调用记录顺序正确: 失败 → 成功")
            else:
                result.mark_fail("调用记录顺序应: 失败 → 成功")

    except Exception as e:
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.mark_fail(f"异常: {e}")

    return result


async def scenario_3_needs_human(java_client: JavaBackendClient) -> TestResult:
    """场景 3：所有重试均返回非法 JSON → NEEDS_HUMAN。"""
    result = TestResult("场景 3: 重试耗尽转 NEEDS_HUMAN")
    _mock_state.reset()

    llm = MockLLM([INVALID_JSON, INVALID_JSON, INVALID_JSON])
    caller = ResilientCaller(llm, max_retries=2, java_client=java_client)
    planner = PlannerAgent(resilient_caller=caller)

    start = time.monotonic()
    try:
        await planner.create_plan("下载 Q3 银行流水", task_id="e2e-003")
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.mark_fail("应抛出 NeedsHumanError")
    except NeedsHumanError as e:
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.llm_call_count = llm.call_count
        result.java_llm_reports = len(_mock_state.llm_calls)
        result.java_needs_human = any(s.get("state") == "NEEDS_HUMAN" for s in _mock_state.task_states)

        result.mark_pass(f"NeedsHumanError 抛出，attempts={e.attempts}")

        if e.attempts == 3:
            result.mark_pass("尝试次数 = 3（首次 + 2 次重试）")
        else:
            result.mark_fail(f"期望 attempts=3，实际 {e.attempts}")

        if llm.call_count == 3:
            result.mark_pass("LLM 调用 3 次（全部失败）")
        else:
            result.mark_fail(f"期望 LLM 调用 3 次，实际 {llm.call_count}")

        if len(_mock_state.llm_calls) == 3:
            result.mark_pass("Java 收到 3 条失败调用记录")
        else:
            result.mark_fail(f"期望 Java 收到 3 条记录，实际 {len(_mock_state.llm_calls)}")

        if result.java_needs_human:
            result.mark_pass("Java 收到 NEEDS_HUMAN 状态上报")
        else:
            result.mark_fail("Java 未收到 NEEDS_HUMAN 状态")

        # 验证所有调用记录 success=False
        all_failed = all(not c.get("success") for c in _mock_state.llm_calls)
        if all_failed:
            result.mark_pass("所有调用记录 success=False")
        else:
            result.mark_fail("所有调用记录应 success=False")

    except Exception as e:
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.mark_fail(f"应抛出 NeedsHumanError，实际抛出: {type(e).__name__}: {e}")

    return result


async def scenario_4_code_block_and_validation(java_client: JavaBackendClient) -> TestResult:
    """场景 4：代码块包裹 + Pydantic 校验失败 → 重试 → 成功。"""
    result = TestResult("场景 4: 代码块兼容 + 校验失败重试")
    _mock_state.reset()

    # 第一次返回缺字段（Pydantic 校验失败），第二次返回代码块包裹（应兼容）
    llm = MockLLM([WRONG_SCHEMA_JSON, CODE_BLOCK_JSON])
    caller = ResilientCaller(llm, max_retries=2, java_client=java_client)
    planner = PlannerAgent(resilient_caller=caller)

    start = time.monotonic()
    try:
        plan = await planner.create_plan("下载 Q4 银行流水", task_id="e2e-004")
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.llm_call_count = llm.call_count
        result.java_llm_reports = len(_mock_state.llm_calls)

        if len(plan.subtasks) == 1:
            result.mark_pass("重试后生成 1 个子任务（代码块包裹兼容）")
        else:
            result.mark_fail(f"期望 1 个子任务，实际 {len(plan.subtasks)}")

        if llm.call_count == 2:
            result.mark_pass("LLM 调用 2 次（首次校验失败 + 重试成功）")
        else:
            result.mark_fail(f"期望 LLM 调用 2 次，实际 {llm.call_count}")

        result.info(f"首次返回: {WRONG_SCHEMA_JSON[:60]}... (缺 steps 字段，Pydantic 校验失败)")
        result.info(f"第二次返回: ```json 代码块包裹（ResilientCaller 自动清理）")

    except Exception as e:
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.mark_fail(f"异常: {e}")

    return result


async def scenario_5_coordinator_e2e(java_client: JavaBackendClient) -> TestResult:
    """场景 5：Coordinator 端到端 — Planner NEEDS_HUMAN → Coordinator 捕获 → 上报 Java。"""
    result = TestResult("场景 5: Coordinator 端到端 NEEDS_HUMAN")
    _mock_state.reset()

    llm = MockLLM([INVALID_JSON, INVALID_JSON, INVALID_JSON])
    caller = ResilientCaller(llm, max_retries=2, java_client=java_client)
    planner = PlannerAgent(resilient_caller=caller)

    # Mock Executor（不会被调用到，因为 Planner 会先失败）
    class MockExecutor:
        async def execute_subtask(self, subtask, context=None):
            return type("R", (), {"success": True, "result_data": {}, "error_message": None, "screenshot_key": None, "page_url": None, "duration_ms": 100})()

    coordinator = AgentCoordinator(
        planner=planner,
        executor=MockExecutor(),
        java_client=java_client,
    )

    start = time.monotonic()
    try:
        state = await coordinator.run(
            task_id="e2e-005",
            org_id="org-001",
            navigation_goal="下载年度报告",
        )
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.llm_call_count = llm.call_count
        result.java_llm_reports = len(_mock_state.llm_calls)
        result.java_needs_human = any(s.get("state") == "NEEDS_HUMAN" for s in _mock_state.task_states)

        if state.status == "needs_human":
            result.mark_pass(f"Coordinator 状态 = needs_human")
        else:
            result.mark_fail(f"期望状态 needs_human，实际 {state.status}")

        if "LLM 规划重试耗尽" in (state.error_message or ""):
            result.mark_pass("错误信息包含 'LLM 规划重试耗尽'")
        else:
            result.mark_fail(f"错误信息应包含 'LLM 规划重试耗尽'，实际: {state.error_message}")

        if result.java_needs_human:
            result.mark_pass("Java 收到 NEEDS_HUMAN 终态上报")
        else:
            result.mark_fail("Java 未收到 NEEDS_HUMAN 终态")

        # 验证 Java 收到了 coordination_state 上报
        coord_count = len(_mock_state.coordination_states)
        result.info(f"Java 收到 {coord_count} 条 coordination_state 上报")

    except Exception as e:
        result.duration_ms = int((time.monotonic() - start) * 1000)
        result.mark_fail(f"异常: {type(e).__name__}: {e}")

    return result


# ============================================================
# 主入口
# ============================================================

async def main():
    """运行全部集成测试场景。"""
    print("=" * 80)
    print("M5.1 三层容错集成测试 — 端到端真实场景验证")
    print("=" * 80)
    print()

    # 启动 Mock Java 后端
    server = start_mock_java_server(port=18099)

    try:
        # 创建 JavaBackendClient 指向 Mock 服务
        java_client = JavaBackendClient(
            base_url="http://127.0.0.1:18099",
            internal_token="test-token",
        )

        # 运行所有场景
        scenarios = [
            scenario_1_normal_success,
            scenario_2_retry_then_success,
            scenario_3_needs_human,
            scenario_4_code_block_and_validation,
            scenario_5_coordinator_e2e,
        ]

        results: list[TestResult] = []
        for scenario_fn in scenarios:
            print(f"\n{'─' * 60}")
            result = await scenario_fn(java_client)
            results.append(result)

            # 打印结果
            print(f"\n{'='*60}")
            status = "✅ PASS" if result.passed else "❌ FAIL"
            print(f"{status} | {result.name}")
            print(f"  耗时: {result.duration_ms}ms | LLM 调用: {result.llm_call_count}次 | Java 记录: {result.java_llm_reports}条")
            if result.java_needs_human:
                print(f"  Java NEEDS_HUMAN: ✅ 已上报")
            for detail in result.details:
                print(detail)
            print()

        await java_client.close()

    finally:
        server.shutdown()

    # 汇总
    print("\n" + "=" * 80)
    passed = sum(1 for r in results if r.passed)
    failed = sum(1 for r in results if not r.passed)
    total = len(results)
    print(f"集成测试汇总: {passed}/{total} 通过, {failed} 失败")
    print("=" * 80)

    # 打印表格
    print(f"\n{'场景':<45} {'结果':<8} {'耗时':<8} {'LLM调用':<8} {'Java记录':<8}")
    print("─" * 80)
    for r in results:
        status = "✅ PASS" if r.passed else "❌ FAIL"
        print(f"{r.name:<45} {status:<8} {r.duration_ms}ms{'':<4} {r.llm_call_count}次{'':<5} {r.java_llm_reports}条")

    print()
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    sys.exit(exit_code)
