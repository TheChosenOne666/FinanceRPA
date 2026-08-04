-- P3 ai-monitoring 页面原型对齐：业务线维度扩展
-- 1. rpa_llm_call_log 新增 business_line_id 字段
--    用于 LLM 监控页按业务线筛选调用记录 + 调用记录列表展示业务线
-- 2. rpa_needs_human_queue 新增 business_line_id 字段
--    用于人工接管队列卡片展示业务线 + 按业务线筛选

-- ===== 1. rpa_llm_call_log 表新增业务线字段 =====
ALTER TABLE finrpa.rpa_llm_call_log
    ADD COLUMN IF NOT EXISTS business_line_id BIGINT;

COMMENT ON COLUMN finrpa.rpa_llm_call_log.business_line_id IS '业务线业务 ID（关联 enterprise_business_line.business_line_id，NULL 表示未归属业务线）';

-- 1.1 索引：按业务线筛选调用记录
CREATE INDEX IF NOT EXISTS idx_rpa_llm_call_log_biz_line
    ON finrpa.rpa_llm_call_log(business_line_id);

-- ===== 2. rpa_needs_human_queue 表新增业务线字段 =====
ALTER TABLE finrpa.rpa_needs_human_queue
    ADD COLUMN IF NOT EXISTS business_line_id BIGINT;

COMMENT ON COLUMN finrpa.rpa_needs_human_queue.business_line_id IS '业务线业务 ID（关联 enterprise_business_line.business_line_id，NULL 表示未归属业务线）';

-- 2.1 索引：按业务线筛选接管事件
CREATE INDEX IF NOT EXISTS idx_rpa_needs_human_queue_biz_line
    ON finrpa.rpa_needs_human_queue(business_line_id);
