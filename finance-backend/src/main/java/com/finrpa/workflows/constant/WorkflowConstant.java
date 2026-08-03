package com.finrpa.workflows.constant;

import com.finrpa.workflows.entity.WorkflowTemplateEO;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流模板常量
 *
 * <p>定义 6 个内置金融场景工作流模板的元数据（name / description / industry / risk_level /
 * params / steps）。WorkflowTemplateInitializer 启动时引用这些常量构建 WorkflowTemplateEO 并 upsert。</p>
 *
 * <p>模板清单（按系统设计 6.8.2 节）：
 * <ol>
 *   <li>银行流水下载（banking / medium）：Login → FormFill → FileDownload</li>
 *   <li>跨行转账核对（banking / high）：Login → TableExtract → Pagination</li>
 *   <li>对公贷款放款（banking / critical）：Login → FormFill → SearchAndSelect</li>
 *   <li>保单申请填写（insurance / high）：Login → FormFill</li>
 *   <li>理赔审核提交（insurance / high）：Login → FileDownload → FormFill</li>
 *   <li>委托下单（securities / high）：Login → FormFill</li>
 * </ol>
 * </p>
 *
 * <p>params / steps 字段为 JSON 字符串，与 rpa_workflow_template 表的 JSONB 字段对应。
 * 步骤中的 {@code {{param}}} 占位符由 WorkflowTriggerServiceImpl.resolveParams 替换为用户传入的实际值。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface WorkflowConstant {

    // region 模板名称常量

    /** 银行流水下载 */
    String TEMPLATE_BANK_STATEMENT = "银行流水下载";

    /** 跨行转账核对 */
    String TEMPLATE_CROSS_BANK_RECONCILE = "跨行转账核对";

    /** 对公贷款放款 */
    String TEMPLATE_CORPORATE_LOAN = "对公贷款放款";

    /** 保单申请填写 */
    String TEMPLATE_POLICY_APPLICATION = "保单申请填写";

    /** 理赔审核提交 */
    String TEMPLATE_CLAIM_REVIEW = "理赔审核提交";

    /** 委托下单 */
    String TEMPLATE_SECURITIES_ORDER = "委托下单";

    // endregion

    // region 通用登录参数 JSON 片段（6 个模板共享）

    /**
     * 通用登录参数定义：login_url / login_username / login_password（加密）
     *
     * <p>所有金融场景都需要先登录业务系统，统一抽取这 3 个参数避免重复。
     * 注意：以 {@code [} 开头但不含结尾 {@code ]}，便于各模板追加业务参数后闭合数组。</p>
     */
    String COMMON_LOGIN_PARAMS = """
            [
              {"name":"login_url","type":"string","required":true,"encrypted":false,"description":"业务系统登录页 URL"},
              {"name":"login_username","type":"string","required":true,"encrypted":false,"description":"登录用户名"},
              {"name":"login_password","type":"string","required":true,"encrypted":true,"description":"登录密码（Fernet 加密存储）"}
            """;

    /**
     * 通用登录步骤：引用 {{login_url}} / {{login_username}} / {{login_password}}
     */
    String COMMON_LOGIN_STEP = """
            {"skill":"login","params_mapping":{
              "url":"{{login_url}}",
              "username":"{{login_username}}",
              "password":"{{login_password}}"
            }}""";

    // endregion

    // region 6 个金融场景模板定义

    /** 银行流水下载（banking / medium）：Login → FormFill → FileDownload */
    WorkflowTemplateEO BANK_STATEMENT_TEMPLATE = buildTemplate(
            TEMPLATE_BANK_STATEMENT,
            "登录企业网银，按日期下载对公账户流水 PDF",
            "banking",
            "medium",
            COMMON_LOGIN_PARAMS + """
                     ,
                      {"name":"account_number","type":"string","required":true,"encrypted":false,"description":"对公账户号"},
                      {"name":"date_start","type":"date","required":true,"encrypted":false,"description":"流水开始日期 YYYY-MM-DD"},
                      {"name":"date_end","type":"date","required":true,"encrypted":false,"description":"流水结束日期 YYYY-MM-DD"}
                    ]""",
            "[" + COMMON_LOGIN_STEP + "," + """
                  {"skill":"form_fill","params_mapping":{
                    "field_mapping":{"账号":"{{account_number}}","开始日期":"{{date_start}}","结束日期":"{{date_end}}"}
                  }},
                  {"skill":"file_download","params_mapping":{
                    "trigger_text":"下载流水",
                    "expected_extension":".pdf"
                  }}
                ]"""
    );

    /** 跨行转账核对（banking / high）：Login → TableExtract → Pagination */
    WorkflowTemplateEO CROSS_BANK_RECONCILE_TEMPLATE = buildTemplate(
            TEMPLATE_CROSS_BANK_RECONCILE,
            "自动比对跨行流水与内部账务系统记录，跨页提取并核对",
            "banking",
            "high",
            COMMON_LOGIN_PARAMS + """
                     ,
                      {"name":"query_date","type":"date","required":true,"encrypted":false,"description":"核对日期 YYYY-MM-DD"}
                    ]""",
            "[" + COMMON_LOGIN_STEP + "," + """
                  {"skill":"table_extract","params_mapping":{
                    "output_format":"json",
                    "include_pagination":true,
                    "skip_empty_rows":true
                  }},
                  {"skill":"pagination","params_mapping":{
                    "max_pages":10,
                    "next_button_text":"下一页"
                  }}
                ]"""
    );

    /** 对公贷款放款（banking / critical）：Login → FormFill → SearchAndSelect */
    WorkflowTemplateEO CORPORATE_LOAN_TEMPLATE = buildTemplate(
            TEMPLATE_CORPORATE_LOAN,
            "填写放款申请表单并提交核验，搜索借款人并选择目标记录",
            "banking",
            "critical",
            COMMON_LOGIN_PARAMS + """
                     ,
                      {"name":"loan_amount","type":"string","required":true,"encrypted":false,"description":"放款金额（元）"},
                      {"name":"loan_account","type":"string","required":true,"encrypted":false,"description":"贷款账号"},
                      {"name":"borrower_name","type":"string","required":true,"encrypted":false,"description":"借款人名称"},
                      {"name":"search_target","type":"string","required":true,"encrypted":false,"description":"搜索结果目标项文本"}
                    ]""",
            "[" + COMMON_LOGIN_STEP + "," + """
                  {"skill":"form_fill","params_mapping":{
                    "field_mapping":{"放款金额":"{{loan_amount}}","贷款账号":"{{loan_account}}","借款人":"{{borrower_name}}"}
                  }},
                  {"skill":"search_and_select","params_mapping":{
                    "search_text":"{{borrower_name}}",
                    "target_text":"{{search_target}}"
                  }}
                ]"""
    );

    /** 保单申请填写（insurance / high）：Login → FormFill */
    WorkflowTemplateEO POLICY_APPLICATION_TEMPLATE = buildTemplate(
            TEMPLATE_POLICY_APPLICATION,
            "客户信息录入、保额计算并提交保单申请",
            "insurance",
            "high",
            COMMON_LOGIN_PARAMS + """
                     ,
                      {"name":"applicant_name","type":"string","required":true,"encrypted":false,"description":"投保人姓名"},
                      {"name":"applicant_id","type":"string","required":true,"encrypted":false,"description":"投保人身份证号"},
                      {"name":"insured_name","type":"string","required":true,"encrypted":false,"description":"被保人姓名"},
                      {"name":"product_name","type":"string","required":true,"encrypted":false,"description":"保险产品名称"},
                      {"name":"insured_amount","type":"string","required":true,"encrypted":false,"description":"保额（元）"}
                    ]""",
            "[" + COMMON_LOGIN_STEP + "," + """
                  {"skill":"form_fill","params_mapping":{
                    "field_mapping":{
                      "投保人":"{{applicant_name}}",
                      "身份证号":"{{applicant_id}}",
                      "被保人":"{{insured_name}}",
                      "产品名称":"{{product_name}}",
                      "保额":"{{insured_amount}}"
                    }
                  }}
                ]"""
    );

    /** 理赔审核提交（insurance / high）：Login → FileDownload → FormFill */
    WorkflowTemplateEO CLAIM_REVIEW_TEMPLATE = buildTemplate(
            TEMPLATE_CLAIM_REVIEW,
            "下载理赔材料并填写审核意见后提交",
            "insurance",
            "high",
            COMMON_LOGIN_PARAMS + """
                     ,
                      {"name":"claim_number","type":"string","required":true,"encrypted":false,"description":"理赔编号"},
                      {"name":"claim_amount","type":"string","required":true,"encrypted":false,"description":"理赔金额（元）"},
                      {"name":"reviewer_comment","type":"string","required":true,"encrypted":false,"description":"审核意见"}
                    ]""",
            "[" + COMMON_LOGIN_STEP + "," + """
                  {"skill":"file_download","params_mapping":{
                    "trigger_text":"下载理赔材料"
                  }},
                  {"skill":"form_fill","params_mapping":{
                    "field_mapping":{
                      "理赔编号":"{{claim_number}}",
                      "理赔金额":"{{claim_amount}}",
                      "审核意见":"{{reviewer_comment}}"
                    }
                  }}
                ]"""
    );

    /** 委托下单（securities / high）：Login → FormFill */
    WorkflowTemplateEO SECURITIES_ORDER_TEMPLATE = buildTemplate(
            TEMPLATE_SECURITIES_ORDER,
            "委托单录入并完成风险揭示确认",
            "securities",
            "high",
            COMMON_LOGIN_PARAMS + """
                     ,
                      {"name":"stock_code","type":"string","required":true,"encrypted":false,"description":"股票代码"},
                      {"name":"trade_type","type":"string","required":true,"encrypted":false,"description":"交易类型（买/卖）"},
                      {"name":"quantity","type":"string","required":true,"encrypted":false,"description":"委托数量（股）"},
                      {"name":"price","type":"string","required":true,"encrypted":false,"description":"委托价格（元）"}
                    ]""",
            "[" + COMMON_LOGIN_STEP + "," + """
                  {"skill":"form_fill","params_mapping":{
                    "field_mapping":{
                      "股票代码":"{{stock_code}}",
                      "交易类型":"{{trade_type}}",
                      "委托数量":"{{quantity}}",
                      "委托价格":"{{price}}"
                    }
                  }}
                ]"""
    );

    // endregion

    // region 内置模板列表

    /** 内置工作流模板列表（6 个金融场景） */
    List<WorkflowTemplateEO> BUILTIN_TEMPLATES = List.of(
            BANK_STATEMENT_TEMPLATE,
            CROSS_BANK_RECONCILE_TEMPLATE,
            CORPORATE_LOAN_TEMPLATE,
            POLICY_APPLICATION_TEMPLATE,
            CLAIM_REVIEW_TEMPLATE,
            SECURITIES_ORDER_TEMPLATE
    );

    // endregion

    // region 私有构建方法

    /**
     * 构建工作流模板实体（仅赋值元数据字段，workflowId 由数据库雪花算法生成）
     *
     * @param name        模板名称（唯一）
     * @param description 模板描述
     * @param industry    行业：banking / insurance / securities
     * @param riskLevel   风险等级：low / medium / high / critical
     * @param params      参数定义 JSON 数组
     * @param steps       步骤 JSON 数组
     * @return 模板实体（未持久化，workflowId 为 null）
     */
    static WorkflowTemplateEO buildTemplate(String name, String description, String industry,
                                             String riskLevel, String params, String steps) {
        WorkflowTemplateEO entity = new WorkflowTemplateEO();
        entity.setName(name);
        entity.setDescription(description);
        entity.setIndustry(industry);
        entity.setRiskLevel(riskLevel);
        entity.setParams(params);
        entity.setSteps(steps);
        entity.setVersion("1.0.0");
        entity.setEnabled(1);
        return entity;
    }

    // endregion
}
