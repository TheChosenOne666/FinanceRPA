/**
 * SIT 测试环境变量读取（M9.5）。
 *
 * 所有配置走环境变量，默认值适配本地 docker-compose 全栈。
 */

/** Java 后端地址（SIT 调用入口）。 */
export const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

/**
 * mock 银行页基址：供 Skyvern 容器内访问（场景1 全链路契约用）。
 * - docker-compose 全栈：用容器服务名 http://mock-bank
 * - 本机直跑：用 http://localhost:8090
 */
export const MOCK_BANK_BASE = process.env.MOCK_BANK_BASE || 'http://mock-bank';

/** 内部回调 token（模拟 Python 回调时用，默认值与 ai.internal-token 一致）。 */
export const INTERNAL_TOKEN = process.env.AI_INTERNAL_TOKEN || 'finrpa-internal-secret';

/**
 * 主组织账号（场景1-4 使用）。
 * admin 是 V5 迁移脚本创建的默认账号（org_admin 角色），
 * DemoDataGenerator.updateDefaultAdminUser 会将其 org_id 更新为第一个演示组织（银河证券）的雪花 id，
 * 因此 admin 能正常触发工作流、创建任务、审批（E2E 已验证）。密码明文 admin123（V5 迁移脚本）。
 */
export const SIT_USERNAME_PRIMARY = process.env.SIT_USERNAME_PRIMARY || 'admin';
export const SIT_PASSWORD_PRIMARY = process.env.SIT_PASSWORD_PRIMARY || 'admin123';

/**
 * 跨组织账号（场景5 隔离测试使用）。
 * admin_demo_xcba 是 DemoDataGenerator 创建的星辰银行 org_admin（orgCode=demo_XCBA），
 * 与 admin（银河证券）属于不同组织，互不可见任务/审批/审计。密码明文 123456（DemoDataGenerator 确认）。
 */
export const SIT_USERNAME_CROSS = process.env.SIT_USERNAME_CROSS || 'admin_demo_xcba';
export const SIT_PASSWORD_CROSS = process.env.SIT_PASSWORD_CROSS || '123456';

/** 等待单个任务终态的最大毫秒数（仅场景1 真实 Skyvern 执行用，默认 10 分钟）。 */
export const TASK_WAIT_TIMEOUT = Number(process.env.TASK_WAIT_TIMEOUT || 600_000);

/** 任务终态轮询间隔（毫秒）。 */
export const TASK_POLL_INTERVAL = Number(process.env.TASK_POLL_INTERVAL || 3_000);

/** 审批超时轮询间隔（毫秒，场景4 用）。 */
export const APPROVAL_TIMEOUT_POLL_INTERVAL = Number(process.env.APPROVAL_TIMEOUT_POLL_INTERVAL || 5_000);

/** 审批超时等待最大毫秒数（场景4：动态改阈值 1 分钟，留 3 分钟余量）。 */
export const APPROVAL_TIMEOUT_WAIT = Number(process.env.APPROVAL_TIMEOUT_WAIT || 180_000);
