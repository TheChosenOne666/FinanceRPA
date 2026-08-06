/**
 * E2E 测试环境变量读取（M9.1）。
 *
 * 所有配置走环境变量，默认值适配本地 docker-compose 全栈。
 */

/** Java 后端地址（E2E 调用入口）。 */
export const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

/**
 * mock 银行页基址：供 Skyvern 容器内访问。
 * - docker-compose 全栈：用容器服务名 http://mock-bank
 * - 本机直跑：用 http://localhost:8090
 */
export const MOCK_BANK_BASE = process.env.MOCK_BANK_BASE || 'http://mock-bank';

/** 演示账号。admin 为 org_admin 角色，关联演示组织，可触发工作流 + 审批。 */
export const E2E_USERNAME = process.env.E2E_USERNAME || 'admin';
/** admin 密码（V5 迁移脚本明文 admin123）。 */
export const E2E_PASSWORD = process.env.E2E_PASSWORD || 'admin123';

/** 等待单个任务终态的最大毫秒数（Skyvern 视觉决策 + 浏览器执行较慢，实测单场景约 4-8 分钟）。 */
export const TASK_WAIT_TIMEOUT = Number(process.env.TASK_WAIT_TIMEOUT || 600_000);

/** 任务终态轮询间隔（毫秒）。 */
export const TASK_POLL_INTERVAL = Number(process.env.TASK_POLL_INTERVAL || 3_000);

/** 内部回调 token（模拟 Python 回调时用，默认值与 ai.internal-token 一致）。 */
export const INTERNAL_TOKEN = process.env.AI_INTERNAL_TOKEN || 'finrpa-internal-secret';
