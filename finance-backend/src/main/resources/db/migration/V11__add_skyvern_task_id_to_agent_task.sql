-- M3.8：rpa_agent_task 表新增 skyvern_task_id 字段，用于关联 Skyvern 任务
-- 存储 Skyvern 返回的 task_id（如 tsk_557478467325455186），用于状态查询映射

SET search_path = finrpa;

ALTER TABLE rpa_agent_task
    ADD COLUMN IF NOT EXISTS skyvern_task_id VARCHAR(64);

COMMENT ON COLUMN rpa_agent_task.skyvern_task_id IS 'Skyvern 任务 ID（M3.8 引入，用于关联 Skyvern 原生任务）';
