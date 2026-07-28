"""内存演示数据生成器。

在启动时填充内存 store（任务/审批/审计/模型调用），
供 Dashboard 和 Approval 等路由使用。

M2.1 适配版：移除了对 approval/audit/dashboard routes 和 action_cache 的依赖，
数据存储在模块级变量中，通过 getter 函数访问。

@from enterprise/demo_seed.py
@author FinanceRPA
"""

import random
from datetime import datetime, timedelta

import structlog

LOG = structlog.get_logger()

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------
ORG_ID = "o_demo_cmb"

DEPARTMENTS = {
    "dept_corp_credit": "对公信贷部",
    "dept_personal_fin": "个人金融部",
    "dept_asset_mgmt": "资产管理部",
    "dept_risk_mgmt": "风险管理部",
    "dept_compliance": "合规审计部",
    "dept_it": "信息技术部",
}

BUSINESS_LINES = {
    "bl_corp_loan": "对公贷款",
    "bl_retail_credit": "零售信贷",
    "bl_wealth_mgmt": "财富管理",
    "bl_intl_settle": "国际结算",
}

DEPT_BL_MAP = {
    "dept_corp_credit": ["bl_corp_loan", "bl_intl_settle"],
    "dept_personal_fin": ["bl_retail_credit"],
    "dept_asset_mgmt": ["bl_wealth_mgmt"],
    "dept_risk_mgmt": ["bl_corp_loan", "bl_retail_credit", "bl_wealth_mgmt", "bl_intl_settle"],
    "dept_compliance": ["bl_corp_loan", "bl_retail_credit", "bl_wealth_mgmt", "bl_intl_settle"],
    "dept_it": ["bl_corp_loan"],
}

DEPT_OPERATORS = {
    "dept_corp_credit": ["eu_cc_op1", "eu_cc_op2", "eu_cc_cross"],
    "dept_personal_fin": ["eu_pf_op"],
    "dept_asset_mgmt": ["eu_am_op"],
    "dept_risk_mgmt": ["eu_risk_viewer1", "eu_risk_viewer2"],
    "dept_compliance": ["eu_comp_approver", "eu_comp_viewer"],
    "dept_it": ["eu_it_op"],
}

DEPT_APPROVERS = {
    "dept_corp_credit": "eu_cc_approver",
    "dept_personal_fin": "eu_pf_approver",
    "dept_asset_mgmt": "eu_am_approver",
    "dept_compliance": "eu_comp_approver",
}

TASK_TEMPLATES = {
    "bl_corp_loan": [
        "企业贷款申请材料审核",
        "贷款额度计算与风险评估",
        "企业信用报告查询",
        "贷后监控数据采集",
        "抵押物价值评估录入",
        "贷款合同条款自动化审查",
        "企业财务报表数据提取",
    ],
    "bl_retail_credit": [
        "个人征信报告查询",
        "信用卡申请资料核验",
        "零售贷款利率计算",
        "个人还款能力评估",
        "消费贷款自动化审批",
        "客户KYC信息更新",
    ],
    "bl_wealth_mgmt": [
        "基金产品净值更新",
        "客户资产配置方案生成",
        "理财产品到期提醒处理",
        "投资组合风险分析",
        "高净值客户画像更新",
    ],
    "bl_intl_settle": [
        "跨境汇款合规审查",
        "国际结算单据核验",
        "外汇交易数据录入",
        "贸易融资申请处理",
        "进出口报关信息采集",
    ],
}

ERROR_TYPES = [
    ("ELEMENT_NOT_FOUND", 30),
    ("TIMEOUT", 25),
    ("LLM_FAILURE", 20),
    ("PAGE_LOAD_ERROR", 10),
    ("NAVIGATION_ERROR", 8),
    ("CAPTCHA_BLOCKED", 5),
    ("SESSION_EXPIRED", 2),
]

ACTION_TYPES = [
    "NAVIGATE", "CLICK", "INPUT_TEXT", "SELECT_OPTION",
    "WAIT", "SCREENSHOT", "SCROLL", "EXTRACT_DATA",
]

RISK_REASONS = {
    "high": [
        "大额交易操作，金额超过100万元",
        "敏感客户信息批量导出",
        "贷款额度调整超过审批权限",
        "跨境交易金额异常",
        "关联交易检测触发",
    ],
    "critical": [
        "系统权限变更操作",
        "核心数据库批量修改",
        "超大额资金划转（超过1000万）",
        "监管报送数据修改",
        "客户隐私数据大规模访问",
    ],
}

PAGE_URLS = [
    "https://core-banking.demo.bank/loans/application",
    "https://core-banking.demo.bank/credit/assessment",
    "https://core-banking.demo.bank/customer/kyc",
    "https://core-banking.demo.bank/settlement/international",
    "https://core-banking.demo.bank/wealth/portfolio",
    "https://core-banking.demo.bank/risk/monitoring",
    "https://core-banking.demo.bank/compliance/reports",
    "https://core-banking.demo.bank/forex/transactions",
]

DECISION_NOTES_APPROVE = [
    "审核通过", "已核实，同意执行", "风险可控，批准",
    "材料完整，通过", "合规检查无异常",
]
DECISION_NOTES_REJECT = [
    "材料不完整，请补充", "风险评估未通过", "超出审批权限",
    "需要额外审查", "操作目标存疑，拒绝",
]

# ---------------------------------------------------------------------------
# 模块级 store（内存）
# ---------------------------------------------------------------------------
_tasks: list[dict] = []
_approvals: list[dict] = []
_audit_logs: list[dict] = []
_model_calls: list[dict] = []


# ---------------------------------------------------------------------------
# 数据生成器
# ---------------------------------------------------------------------------

def _generate_tasks(rng: random.Random, now: datetime, count: int = 250) -> list[dict]:
    """生成分布于最近 30 天的真实任务记录。"""
    tasks = []
    operational_depts = ["dept_corp_credit", "dept_personal_fin", "dept_asset_mgmt", "dept_it"]
    dept_weights = [0.40, 0.25, 0.20, 0.15]

    error_names = [e[0] for e in ERROR_TYPES]
    error_weights = [e[1] for e in ERROR_TYPES]

    for i in range(count):
        dept_id = rng.choices(operational_depts, weights=dept_weights, k=1)[0]
        bl_id = rng.choice(DEPT_BL_MAP[dept_id])
        creator = rng.choice(DEPT_OPERATORS.get(dept_id, ["eu_it_op"]))
        task_name = rng.choice(TASK_TEMPLATES.get(bl_id, TASK_TEMPLATES["bl_corp_loan"]))

        days_ago = min(int(rng.expovariate(0.15)), 30)
        hours = rng.randint(8, 18)
        minutes = rng.randint(0, 59)
        created_at = now - timedelta(days=days_ago, hours=rng.randint(0, 6))
        created_at = created_at.replace(hour=hours, minute=minutes, second=rng.randint(0, 59))

        roll = rng.random()
        if roll < 0.72:
            status = "completed"
        elif roll < 0.87:
            status = "failed"
        elif roll < 0.92:
            status = "running"
        elif roll < 0.97:
            status = "needs_human"
        else:
            status = "pending_approval"

        if status == "running":
            created_at = now - timedelta(hours=rng.randint(0, 3), minutes=rng.randint(0, 59))

        if status == "completed":
            duration_ms = rng.randint(30000, 900000)
        elif status == "failed":
            duration_ms = rng.randint(10000, 300000)
        elif status == "running":
            duration_ms = None
        else:
            duration_ms = rng.randint(20000, 600000)

        error_type = None
        if status == "failed":
            error_type = rng.choices(error_names, weights=error_weights, k=1)[0]

        task_id = f"tsk_demo_{i + 1:04d}"
        tasks.append({
            "task_id": task_id,
            "org_id": ORG_ID,
            "organization_id": ORG_ID,
            "department_id": dept_id,
            "business_line_id": bl_id,
            "status": status,
            "created_at": created_at.isoformat(),
            "duration_ms": duration_ms,
            "error_type": error_type,
            "created_by": creator,
            "task_name": task_name,
        })

    tasks.sort(key=lambda t: t["created_at"])
    return tasks


def _generate_approvals(rng: random.Random, tasks: list[dict]) -> list[dict]:
    """为风险触发的任务生成审批记录。"""
    approvals: list[dict] = []
    idx = 0

    def _add_approval(task: dict, risk_level: str, final_status: str, response_min: int | None = None):
        nonlocal idx
        idx += 1
        approval_id = f"apr_demo_{idx:04d}"
        dept_id = task["department_id"]
        bl_id = task["business_line_id"]
        reason = rng.choice(RISK_REASONS[risk_level])

        approver_dept = "dept_compliance" if (risk_level == "critical" or rng.random() < 0.2) else dept_id
        requested_at = datetime.fromisoformat(task["created_at"])

        decided_at = None
        approver_user = None
        note = None
        if final_status != "pending" and response_min is not None:
            decided_at = (requested_at + timedelta(minutes=response_min)).isoformat()
            approver_user = DEPT_APPROVERS.get(approver_dept, "eu_comp_approver")
            if final_status == "approved":
                note = rng.choice(DECISION_NOTES_APPROVE)
            else:
                note = rng.choice(DECISION_NOTES_REJECT)

        approvals.append({
            "approval_id": approval_id,
            "task_id": task["task_id"],
            "organization_id": ORG_ID,
            "department_id": dept_id,
            "business_line_id": bl_id,
            "risk_level": risk_level,
            "risk_reason": reason,
            "operation_description": task["task_name"],
            "screenshot_path": None,
            "approver_department_id": approver_dept,
            "status": final_status,
            "requested_at": requested_at.isoformat(),
            "timeout_seconds": 3600 if risk_level == "high" else 1800,
            "approver_user_id": approver_user,
            "decided_at": decided_at,
            "decision_note": note,
        })

    # 待审批
    for task in (t for t in tasks if t["status"] == "pending_approval"):
        _add_approval(task, rng.choice(["high", "critical"]), "pending")

    # 历史批准
    completed = [t for t in tasks if t["status"] == "completed"]
    for task in rng.sample(completed, min(40, len(completed))):
        _add_approval(task, rng.choice(["high", "high", "critical"]), "approved", response_min=rng.randint(5, 90))

    # 历史拒绝
    failed = [t for t in tasks if t["status"] == "failed"]
    for task in rng.sample(failed, min(8, len(failed))):
        _add_approval(task, rng.choice(["high", "critical"]), "rejected", response_min=rng.randint(3, 60))

    return approvals


def _generate_audit_logs(
    rng: random.Random,
    tasks: list[dict],
    approval_store: list[dict],
) -> list[dict]:
    """为已完成/失败任务生成审计日志。"""
    logs = []
    log_idx = 0

    task_approvals: dict[str, dict] = {}
    for apr in approval_store:
        tid = apr["task_id"]
        if tid not in task_approvals:
            task_approvals[tid] = apr

    loggable = [
        t for t in tasks
        if t["status"] in ("completed", "failed", "needs_human", "pending_approval")
    ]
    loggable = sorted(loggable, key=lambda t: t["created_at"], reverse=True)[:120]

    for task in loggable:
        task_id = task["task_id"]
        dept_id = task["department_id"]
        bl_id = task["business_line_id"]
        num_actions = rng.randint(3, 12)
        task_created = datetime.fromisoformat(task["created_at"])

        approval = task_approvals.get(task_id)
        has_approval = approval is not None
        approval_action_idx = rng.randint(2, max(2, num_actions - 1)) if has_approval else -1

        for action_idx in range(num_actions):
            log_idx += 1
            offset_s = sum(rng.randint(2, 30) for _ in range(action_idx))
            action_time = task_created + timedelta(seconds=offset_s)

            if action_idx == 0:
                action_type = "NAVIGATE"
            elif action_idx == num_actions - 1 and task["status"] == "completed":
                action_type = "EXTRACT_DATA"
            else:
                action_type = rng.choice(ACTION_TYPES)

            if task["status"] == "failed" and action_idx == num_actions - 1:
                exec_result = "failed"
                error_msg = task.get("error_type", "UNKNOWN")
            elif rng.random() < 0.03:
                exec_result = "failed"
                error_msg = rng.choice([
                    "Element not interactable",
                    "Timeout waiting for element",
                    "Navigation failed",
                ])
            else:
                exec_result = "success"
                error_msg = None

            target = None
            input_val = None
            if action_type == "CLICK":
                target = rng.choice([
                    "button#submit", "a.nav-link", "input[type=submit]",
                    "div.menu-item", "span.action-btn", "button.confirm",
                ])
            elif action_type == "INPUT_TEXT":
                target = rng.choice([
                    "input#loan-amount", "input#customer-id", "textarea#remarks",
                    "input#search", "input#account-number",
                ])
                input_val = "***"
            elif action_type == "SELECT_OPTION":
                target = rng.choice([
                    "select#risk-level", "select#department", "select#currency",
                ])

            is_approval_action = has_approval and action_idx == approval_action_idx
            logs.append({
                "audit_log_id": f"aud_demo_{log_idx:06d}",
                "task_id": task_id,
                "organization_id": ORG_ID,
                "department_id": dept_id,
                "business_line_id": bl_id,
                "action_index": action_idx,
                "action_type": action_type,
                "target_element": target,
                "input_value": input_val,
                "page_url": rng.choice(PAGE_URLS),
                "screenshot_before_url": None,
                "screenshot_after_url": None,
                "duration_ms": rng.randint(100, 15000),
                "executor": "agent",
                "execution_result": exec_result,
                "error_message": error_msg,
                "has_approval": is_approval_action,
                "approval_id": approval["approval_id"] if is_approval_action else None,
                "approver_user_id": approval.get("approver_user_id") if is_approval_action else None,
                "created_at": action_time.isoformat(),
            })

    return logs


def _generate_model_calls(
    rng: random.Random,
    tasks: list[dict],
    count: int = 1200,
) -> list[dict]:
    """生成三档模型的 LLM 调用记录。"""
    calls = []
    tiers = ["light", "standard", "heavy"]
    tier_weights = [0.50, 0.35, 0.15]
    token_ranges = {"light": (200, 2000), "standard": (1000, 8000), "heavy": (5000, 32000)}
    cache_rates = {"light": 0.45, "standard": 0.30, "heavy": 0.15}

    for _ in range(count):
        tier = rng.choices(tiers, weights=tier_weights, k=1)[0]
        lo, hi = token_ranges[tier]
        task = rng.choice(tasks)
        calls.append({
            "org_id": ORG_ID,
            "model_tier": tier,
            "tokens": rng.randint(lo, hi),
            "cache_hit": rng.random() < cache_rates[tier],
            "task_id": task["task_id"],
            "created_at": task["created_at"],
        })

    return calls


# ---------------------------------------------------------------------------
# Getter 函数（供其他模块访问 demo 数据）
# ---------------------------------------------------------------------------

def get_tasks() -> list[dict]:
    """获取演示任务列表。"""
    return _tasks


def get_approvals() -> list[dict]:
    """获取演示审批列表。"""
    return _approvals


def get_audit_logs() -> list[dict]:
    """获取演示审计日志列表。"""
    return _audit_logs


def get_model_calls() -> list[dict]:
    """获取演示模型调用列表。"""
    return _model_calls


# ---------------------------------------------------------------------------
# 公共入口
# ---------------------------------------------------------------------------

def populate_all_stores() -> None:
    """生成所有演示数据并填充内存 store。

    幂等：多次调用安全（每次替换数据）。
    """
    global _tasks, _approvals, _audit_logs, _model_calls

    LOG.info("正在填充演示数据...")

    rng = random.Random(42)
    now = datetime.utcnow()

    # 1. 任务
    _tasks = _generate_tasks(rng, now, count=250)
    LOG.info("演示数据: 已生成任务", count=len(_tasks))

    # 2. 审批
    _approvals = _generate_approvals(rng, _tasks)
    pending_count = sum(1 for a in _approvals if a["status"] == "pending")
    LOG.info("演示数据: 已生成审批", total=len(_approvals), pending=pending_count)

    # 3. 审计日志
    _audit_logs = _generate_audit_logs(rng, _tasks, _approvals)
    LOG.info("演示数据: 已生成审计日志", count=len(_audit_logs))

    # 4. 模型调用
    _model_calls = _generate_model_calls(rng, _tasks, count=1200)
    LOG.info("演示数据: 已生成模型调用", count=len(_model_calls))

    LOG.info("演示数据填充完成")
