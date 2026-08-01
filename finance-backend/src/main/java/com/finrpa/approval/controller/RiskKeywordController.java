package com.finrpa.approval.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.approval.dto.request.RiskKeywordAddRequest;
import com.finrpa.approval.dto.request.RiskKeywordQueryRequest;
import com.finrpa.approval.dto.response.RiskKeywordVO;
import com.finrpa.approval.service.RiskKeywordService;
import com.finrpa.common.response.BaseResponse;
import com.finrpa.common.response.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风险关键词库管理控制器（对外 API）
 *
 * <p>对外端点（实际访问路径前缀 {@code /api/risk-keywords}）：
 * <ul>
 *   <li>GET /risk-keywords —— 分页查询关键词库（支持行业/分类/启用状态筛选）</li>
 *   <li>GET /risk-keywords/{keywordId} —— 查询关键词详情</li>
 *   <li>POST /risk-keywords —— 新增自定义关键词</li>
 *   <li>PUT /risk-keywords/{keywordId} —— 更新关键词（内置关键词仅可改 enabled/description）</li>
 *   <li>DELETE /risk-keywords/{keywordId} —— 删除关键词（内置不可删除）</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@RestController
@RequestMapping("/risk-keywords")
@Tag(name = "风险关键词库管理", description = "银行 / 保险 / 证券 三大行业风险关键词库 CRUD")
public class RiskKeywordController {

    /** 风险关键词管理服务 */
    @Resource
    private RiskKeywordService riskKeywordService;

    // region 查询

    /**
     * 分页查询关键词库
     *
     * @param queryRequest 查询请求（含分页 + 筛选参数）
     * @return 关键词分页列表
     */
    @GetMapping
    @Operation(summary = "关键词列表", description = "分页查询关键词库，支持行业/分类/启用状态筛选")
    public BaseResponse<IPage<RiskKeywordVO>> listKeywords(RiskKeywordQueryRequest queryRequest) {
        IPage<RiskKeywordVO> page = riskKeywordService.listKeywords(queryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 查询关键词详情
     *
     * @param keywordId 关键词业务 ID
     * @return 关键词详情
     */
    @GetMapping("/{keywordId}")
    @Operation(summary = "关键词详情", description = "按 ID 查询关键词详情")
    public BaseResponse<RiskKeywordVO> getKeywordDetail(@PathVariable Long keywordId) {
        RiskKeywordVO vo = riskKeywordService.getKeywordDetail(keywordId);
        return ResultUtils.success(vo);
    }

    // endregion

    // region 写操作

    /**
     * 新增自定义关键词
     *
     * @param request 新增请求
     * @return 新增的关键词业务 ID
     */
    @PostMapping
    @Operation(summary = "新增关键词", description = "新增自定义风险关键词")
    public BaseResponse<Long> addKeyword(@RequestBody RiskKeywordAddRequest request) {
        Long keywordId = riskKeywordService.addKeyword(request);
        return ResultUtils.success(keywordId);
    }

    /**
     * 更新关键词
     *
     * <p>内置关键词仅可更新 enabled / description 字段，自定义关键词全字段可更新。</p>
     *
     * @param keywordId 关键词业务 ID
     * @param request   更新请求
     * @return 操作结果
     */
    @PutMapping("/{keywordId}")
    @Operation(summary = "更新关键词", description = "更新关键词，内置关键词仅可改 enabled/description")
    public BaseResponse<Boolean> updateKeyword(@PathVariable Long keywordId,
                                                @RequestBody RiskKeywordAddRequest request) {
        boolean success = riskKeywordService.updateKeyword(keywordId, request);
        return ResultUtils.success(success);
    }

    /**
     * 删除关键词
     *
     * <p>内置关键词不可删除（仅可禁用），自定义关键词可逻辑删除。</p>
     *
     * @param keywordId 关键词业务 ID
     * @return 操作结果
     */
    @DeleteMapping("/{keywordId}")
    @Operation(summary = "删除关键词", description = "删除自定义关键词，内置关键词不可删除")
    public BaseResponse<Boolean> deleteKeyword(@PathVariable Long keywordId) {
        boolean success = riskKeywordService.deleteKeyword(keywordId);
        return ResultUtils.success(success);
    }

    // endregion
}
