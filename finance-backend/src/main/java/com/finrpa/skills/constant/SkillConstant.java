package com.finrpa.skills.constant;

/**
 * Skill 元数据常量
 *
 * <p>定义 7 个内置 Skill 的 name、分类常量，以及参数 JSON Schema（硬编码）。
 * SkillMetaInitializer 启动时引用这些常量构建 SkillMetaEO 并 upsert。</p>
 *
 * <p>param_schema 由 Python {@code LoginParams.model_json_schema()} 等导出，
 * 保证 Java 元数据与 Python params_model 一致。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface SkillConstant {

    // region 分类常量

    /** 认证类 */
    String CATEGORY_AUTH = "auth";

    /** 交互类 */
    String CATEGORY_INTERACTION = "interaction";

    /** 提取类 */
    String CATEGORY_EXTRACTION = "extraction";

    // endregion

    // region 内置 Skill name 常量

    /** 登录 */
    String SKILL_LOGIN = "login";

    /** 会话保活 */
    String SKILL_SESSION_KEEP_ALIVE = "session_keep_alive";

    /** 表单填充 */
    String SKILL_FORM_FILL = "form_fill";

    /** 搜索选择 */
    String SKILL_SEARCH_AND_SELECT = "search_and_select";

    /** 分页遍历 */
    String SKILL_PAGINATION = "pagination";

    /** 表格提取 */
    String SKILL_TABLE_EXTRACT = "table_extract";

    /** 文件下载 */
    String SKILL_FILE_DOWNLOAD = "file_download";

    // endregion

    // region 参数 JSON Schema（由 Python model_json_schema() 导出，M3.3 硬编码）

    /** LoginParams 的 JSON Schema */
    String SCHEMA_LOGIN = """
            {"description":"LoginSkill 的输入参数。","properties":{"url":{"description":"登录页 URL","title":"Url","type":"string"},"username":{"description":"登录用户名","title":"Username","type":"string"},"password":{"description":"登录密码","title":"Password","type":"string"},"captcha_strategy":{"default":"skip","description":"验证码处理策略：skip | manual | ocr","title":"Captcha Strategy","type":"string"},"submit_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"登录按钮 CSS 选择器（省略时自动检测）","title":"Submit Selector"},"success_indicator":{"default":"","description":"登录成功标志：URL 片段或页面文本","title":"Success Indicator","type":"string"}},"required":["url","username","password"],"title":"LoginParams","type":"object"}""";

    /** SessionKeepAliveParams 的 JSON Schema */
    String SCHEMA_SESSION_KEEP_ALIVE = """
            {"$defs":{"LoginParams":{"description":"LoginSkill 的输入参数。","properties":{"url":{"description":"登录页 URL","title":"Url","type":"string"},"username":{"description":"登录用户名","title":"Username","type":"string"},"password":{"description":"登录密码","title":"Password","type":"string"},"captcha_strategy":{"default":"skip","description":"验证码处理策略：skip | manual | ocr","title":"Captcha Strategy","type":"string"},"submit_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"登录按钮 CSS 选择器（省略时自动检测）","title":"Submit Selector"},"success_indicator":{"default":"","description":"登录成功标志：URL 片段或页面文本","title":"Success Indicator","type":"string"}},"required":["url","username","password"],"title":"LoginParams","type":"object"}},"description":"SessionKeepAliveSkill 的输入参数。","properties":{"check_interval_seconds":{"default":300,"description":"保活检查间隔秒数","title":"Check Interval Seconds","type":"integer"},"heartbeat_url":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"心跳 URL（如有）","title":"Heartbeat Url"},"session_timeout_indicator":{"default":"","description":"会话超时页面文本（如 'session expired'）","title":"Session Timeout Indicator","type":"string"},"relogin_on_expire":{"default":true,"description":"会话过期时是否自动重新登录","title":"Relogin On Expire","type":"boolean"},"login_params":{"anyOf":[{"$ref":"#/$defs/LoginParams"},{"type":"null"}],"default":null,"description":"自动重新登录所需的 LoginParams"}},"title":"SessionKeepAliveParams","type":"object"}""";

    /** FormFillParams 的 JSON Schema */
    String SCHEMA_FORM_FILL = """
            {"description":"FormFillSkill 的输入参数。","properties":{"field_mapping":{"additionalProperties":{"type":"string"},"description":"字段标签/名称 → 填充值 的映射","title":"Field Mapping","type":"object"},"submit_after_fill":{"default":true,"description":"填充完成后是否点击提交按钮","title":"Submit After Fill","type":"boolean"},"submit_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"提交按钮 CSS 选择器（省略时自动检测）","title":"Submit Selector"},"date_format":{"default":"YYYY-MM-DD","description":"日期选择器字段格式","title":"Date Format","type":"string"}},"required":["field_mapping"],"title":"FormFillParams","type":"object"}""";

    /** SearchAndSelectParams 的 JSON Schema */
    String SCHEMA_SEARCH_AND_SELECT = """
            {"description":"SearchAndSelectParams 的输入参数。","properties":{"search_text":{"description":"搜索框输入的文本","title":"Search Text","type":"string"},"target_text":{"description":"要点击的目标结果项文本","title":"Target Text","type":"string"},"search_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"搜索输入框 CSS 选择器（省略时自动检测）","title":"SearchSelector"},"result_container_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"结果容器 CSS 选择器","title":"Result Container Selector"},"wait_for_results_ms":{"default":3000,"description":"等待搜索结果出现的毫秒数","title":"Wait For Results Ms","type":"integer"}},"required":["search_text","target_text"],"title":"SearchAndSelectParams","type":"object"}""";

    /** PaginationParams 的 JSON Schema */
    String SCHEMA_PAGINATION = """
            {"description":"PaginationSkill 的输入参数。","properties":{"max_pages":{"default":10,"description":"最大遍历页数","title":"Max Pages","type":"integer"},"next_button_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"「下一页」按钮 CSS 选择器","title":"Next Button Selector"},"next_button_text":{"default":"下一页","description":"下一页按钮文本（未指定 selector 时用）","title":"Next Button Text","type":"string"},"page_data_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"每页数据容器的 CSS 选择器","title":"Page Data Selector"},"wait_between_pages_ms":{"default":2000,"description":"翻页之间的等待毫秒数","title":"Wait Between Pages Ms","type":"integer"},"stop_on_empty":{"default":true,"description":"当前页无数据时是否停止翻页","title":"Stop On Empty","type":"boolean"}},"title":"PaginationParams","type":"object"}""";

    /** TableExtractParams 的 JSON Schema */
    String SCHEMA_TABLE_EXTRACT = """
            {"description":"TableExtractSkill 的输入参数。","properties":{"table_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"目标表格 CSS 选择器（省略时自动检测）","title":"Table Selector"},"headers":{"anyOf":[{"items":{"type":"string"},"type":"array"},{"type":"null"}],"default":null,"description":"期望列头列表（用于校验）","title":"Headers"},"output_format":{"default":"json","description":"输出格式：json | csv","title":"Output Format","type":"string"},"max_rows":{"default":1000,"description":"最大提取行数（安全限制）","title":"Max Rows","type":"integer"},"include_pagination":{"default":false,"description":"是否跨多页提取","title":"Include Pagination","type":"boolean"},"skip_empty_rows":{"default":true,"description":"是否跳过空行","title":"Skip Empty Rows","type":"boolean"}},"title":"TableExtractParams","type":"object"}""";

    /** FileDownloadParams 的 JSON Schema */
    String SCHEMA_FILE_DOWNLOAD = """
            {"description":"FileDownloadSkill 的输入参数。","properties":{"trigger_selector":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"下载触发元素的 CSS 选择器","title":"Trigger Selector"},"trigger_text":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"下载按钮/链接的文本（未指定 selector 时用）","title":"Trigger Text"},"download_path":{"default":"/tmp/finrpa/downloads/","description":"下载文件保存目录（容器内临时路径）","title":"Download Path","type":"string"},"expected_extension":{"anyOf":[{"type":"string"},{"type":"null"}],"default":null,"description":"期望文件扩展名（如 '.csv', '.pdf'）","title":"Expected Extension"},"wait_timeout_ms":{"default":30000,"description":"等待下载完成的最大毫秒数","title":"Wait Timeout Ms","type":"integer"}},"title":"FileDownloadParams","type":"object"}""";

    // endregion
}
