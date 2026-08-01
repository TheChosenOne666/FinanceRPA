package com.finrpa.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finrpa.approval.constant.ApprovalConstant;
import com.finrpa.approval.constant.RiskKeywordConstant;
import com.finrpa.approval.dto.request.RiskKeywordAddRequest;
import com.finrpa.approval.dto.request.RiskKeywordQueryRequest;
import com.finrpa.approval.dto.response.RiskKeywordVO;
import com.finrpa.approval.entity.RiskKeywordEO;
import com.finrpa.approval.mapper.RiskKeywordMapper;
import com.finrpa.approval.service.RiskKeywordService;
import com.finrpa.common.exception.ThrowUtils;
import com.finrpa.common.response.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 风险关键词管理服务实现
 *
 * <p>实现关键词库的 CRUD、查询与内置关键词初始化。
 * 内置关键词（builtin=1）不可删除，仅可禁用或更新描述。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Slf4j
@Service
public class RiskKeywordServiceImpl implements RiskKeywordService {

    /** 风险关键词 Mapper */
    @Resource
    private RiskKeywordMapper riskKeywordMapper;

    // region 查询

    /**
     * 分页查询关键词库
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    @Override
    public IPage<RiskKeywordVO> listKeywords(RiskKeywordQueryRequest queryRequest) {
        // 1. 构建查询条件
        QueryWrapper<RiskKeywordEO> wrapper = new QueryWrapper<>();
        if (queryRequest != null) {
            if (queryRequest.getKeyword() != null && !queryRequest.getKeyword().isBlank()) {
                wrapper.like("keyword", queryRequest.getKeyword());
            }
            if (queryRequest.getIndustry() != null && !queryRequest.getIndustry().isBlank()) {
                wrapper.eq("industry", queryRequest.getIndustry());
            }
            if (queryRequest.getCategory() != null && !queryRequest.getCategory().isBlank()) {
                wrapper.eq("category", queryRequest.getCategory());
            }
            if (queryRequest.getRiskType() != null && !queryRequest.getRiskType().isBlank()) {
                wrapper.eq("risk_type", queryRequest.getRiskType());
            }
            if (queryRequest.getEnabled() != null) {
                wrapper.eq("enabled", queryRequest.getEnabled());
            }
        }
        wrapper.orderByAsc("industry", "category", "keyword");

        // 2. 分页查询
        long current = queryRequest != null ? queryRequest.getCurrent() : 1;
        long size = queryRequest != null ? queryRequest.getPageSize() : 10;
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR, "每页数量不能超过 200");
        Page<RiskKeywordEO> page = new Page<>(current, size);
        IPage<RiskKeywordEO> keywordPage = riskKeywordMapper.selectPage(page, wrapper);

        // 3. 转换为 VO
        return keywordPage.convert(eo -> {
            RiskKeywordVO vo = new RiskKeywordVO();
            BeanUtils.copyProperties(eo, vo);
            return vo;
        });
    }

    /**
     * 查询全部启用的关键词（用于预筛加载到内存）
     *
     * @param industry 行业（可空）
     * @return 启用状态的关键词列表
     */
    @Override
    public List<RiskKeywordEO> listEnabledKeywords(String industry) {
        QueryWrapper<RiskKeywordEO> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        if (industry != null && !industry.isBlank()) {
            wrapper.eq("industry", industry);
        }
        return riskKeywordMapper.selectList(wrapper);
    }

    /**
     * 查询关键词详情
     *
     * @param keywordId 关键词业务 ID
     * @return 关键词 VO
     */
    @Override
    public RiskKeywordVO getKeywordDetail(Long keywordId) {
        ThrowUtils.throwIf(keywordId == null, ErrorCode.PARAMS_ERROR, "关键词 ID 不能为空");

        QueryWrapper<RiskKeywordEO> wrapper = new QueryWrapper<>();
        wrapper.eq("keyword_id", keywordId);
        RiskKeywordEO eo = riskKeywordMapper.selectOne(wrapper);
        ThrowUtils.throwIf(eo == null, ErrorCode.NOT_FOUND_ERROR, "关键词不存在");

        RiskKeywordVO vo = new RiskKeywordVO();
        BeanUtils.copyProperties(eo, vo);
        return vo;
    }

    // endregion

    // region 新增

    /**
     * 新增自定义关键词
     *
     * @param request 新增请求
     * @return 新增的关键词业务 ID
     */
    @Override
    public Long addKeyword(RiskKeywordAddRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "关键词请求不能为空");
        ThrowUtils.throwIf(request.getKeyword() == null || request.getKeyword().isBlank(),
                ErrorCode.PARAMS_ERROR, "关键词文本不能为空");
        ThrowUtils.throwIf(request.getIndustry() == null || request.getIndustry().isBlank(),
                ErrorCode.PARAMS_ERROR, "行业不能为空");
        ThrowUtils.throwIf(request.getCategory() == null || request.getCategory().isBlank(),
                ErrorCode.PARAMS_ERROR, "分类不能为空");

        // 2. 校验行业 / 分类 / 风险类型合法
        validateIndustry(request.getIndustry());
        validateCategory(request.getCategory());
        String riskType = request.getRiskType() != null && !request.getRiskType().isBlank()
                ? request.getRiskType() : ApprovalConstant.RISK_TYPE_MEDIUM;
        validateRiskType(riskType);

        // 3. 构建实体
        RiskKeywordEO eo = new RiskKeywordEO();
        eo.setKeyword(request.getKeyword().trim());
        eo.setIndustry(request.getIndustry());
        eo.setCategory(request.getCategory());
        eo.setRiskType(riskType);
        eo.setDescription(request.getDescription());
        eo.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        eo.setBuiltin(0);

        // 4. 保存
        int rows = riskKeywordMapper.insert(eo);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "关键词保存失败");

        log.info("新增自定义关键词成功: keywordId={}, keyword={}, industry={}, category={}",
                eo.getKeywordId(), eo.getKeyword(), eo.getIndustry(), eo.getCategory());
        return eo.getKeywordId();
    }

    // endregion

    // region 更新

    /**
     * 更新关键词（内置关键词仅可更新 enabled / description 字段）
     *
     * @param keywordId 关键词业务 ID
     * @param request   更新请求
     * @return 是否更新成功
     */
    @Override
    public boolean updateKeyword(Long keywordId, RiskKeywordAddRequest request) {
        ThrowUtils.throwIf(keywordId == null, ErrorCode.PARAMS_ERROR, "关键词 ID 不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "关键词请求不能为空");

        // 1. 查询原记录
        QueryWrapper<RiskKeywordEO> wrapper = new QueryWrapper<>();
        wrapper.eq("keyword_id", keywordId);
        RiskKeywordEO existing = riskKeywordMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "关键词不存在");

        // 2. 校验行业 / 分类 / 风险类型合法（如传入）
        if (request.getIndustry() != null && !request.getIndustry().isBlank()) {
            validateIndustry(request.getIndustry());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            validateCategory(request.getCategory());
        }
        if (request.getRiskType() != null && !request.getRiskType().isBlank()) {
            validateRiskType(request.getRiskType());
        }

        // 3. 构建更新字段
        UpdateWrapper<RiskKeywordEO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("keyword_id", keywordId);

        if (existing.getBuiltin() != null && existing.getBuiltin() == 1) {
            // 内置关键词：仅可更新 enabled / description
            if (request.getEnabled() != null) {
                updateWrapper.set("enabled", request.getEnabled());
            }
            if (request.getDescription() != null) {
                updateWrapper.set("description", request.getDescription());
            }
        } else {
            // 自定义关键词：全字段可更新
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                updateWrapper.set("keyword", request.getKeyword().trim());
            }
            if (request.getIndustry() != null && !request.getIndustry().isBlank()) {
                updateWrapper.set("industry", request.getIndustry());
            }
            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                updateWrapper.set("category", request.getCategory());
            }
            if (request.getRiskType() != null && !request.getRiskType().isBlank()) {
                updateWrapper.set("risk_type", request.getRiskType());
            }
            if (request.getDescription() != null) {
                updateWrapper.set("description", request.getDescription());
            }
            if (request.getEnabled() != null) {
                updateWrapper.set("enabled", request.getEnabled());
            }
        }

        // 4. 执行更新
        int rows = riskKeywordMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "关键词更新失败");

        log.info("更新关键词成功: keywordId={}, builtin={}", keywordId, existing.getBuiltin());
        return true;
    }

    // endregion

    // region 删除

    /**
     * 删除关键词（内置关键词不可删除，仅可禁用）
     *
     * @param keywordId 关键词业务 ID
     * @return 是否删除成功
     */
    @Override
    public boolean deleteKeyword(Long keywordId) {
        ThrowUtils.throwIf(keywordId == null, ErrorCode.PARAMS_ERROR, "关键词 ID 不能为空");

        // 1. 查询原记录
        QueryWrapper<RiskKeywordEO> wrapper = new QueryWrapper<>();
        wrapper.eq("keyword_id", keywordId);
        RiskKeywordEO existing = riskKeywordMapper.selectOne(wrapper);
        ThrowUtils.throwIf(existing == null, ErrorCode.NOT_FOUND_ERROR, "关键词不存在");

        // 2. 内置关键词不可删除
        ThrowUtils.throwIf(existing.getBuiltin() != null && existing.getBuiltin() == 1,
                ErrorCode.OPERATION_ERROR, "内置关键词不可删除，可禁用");

        // 3. 逻辑删除
        int rows = riskKeywordMapper.deleteById(existing.getId());
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "关键词删除失败");

        log.info("删除关键词成功: keywordId={}, keyword={}", keywordId, existing.getKeyword());
        return true;
    }

    // endregion

    // region 内置关键词初始化

    /**
     * 注册内置关键词（启动时调用，upsert 语义）
     *
     * <p>遍历 {@link RiskKeywordConstant#BUILTIN_KEYWORDS}，对每个关键词：
     * 已存在则更新元数据字段（不动 builtin / enabled），不存在则插入。</p>
     *
     * @return 注册的关键词数量
     */
    @Override
    public int registerBuiltinKeywords() {
        int count = 0;
        for (String[] entry : RiskKeywordConstant.BUILTIN_KEYWORDS) {
            // entry = {keyword, industry, category, riskType, description}
            String keyword = entry[0];
            String industry = entry[1];
            String category = entry[2];
            String riskType = entry[3];
            String description = entry.length > 4 ? entry[4] : null;

            // 1. 查询是否已存在（按 keyword + industry 唯一）
            QueryWrapper<RiskKeywordEO> wrapper = new QueryWrapper<>();
            wrapper.eq("keyword", keyword).eq("industry", industry);
            RiskKeywordEO existing = riskKeywordMapper.selectOne(wrapper);

            if (existing != null) {
                // 已存在：更新元数据字段（不动 enabled，保持用户自定义的启用状态）
                UpdateWrapper<RiskKeywordEO> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("keyword_id", existing.getKeywordId())
                        .set("category", category)
                        .set("risk_type", riskType)
                        .set("description", description)
                        .set("builtin", 1);
                riskKeywordMapper.update(null, updateWrapper);
            } else {
                // 不存在：插入新记录
                RiskKeywordEO eo = new RiskKeywordEO();
                eo.setKeyword(keyword);
                eo.setIndustry(industry);
                eo.setCategory(category);
                eo.setRiskType(riskType);
                eo.setDescription(description);
                eo.setEnabled(1);
                eo.setBuiltin(1);
                riskKeywordMapper.insert(eo);
            }
            count++;
        }
        log.info("内置关键词注册完成: 共 {} 个", count);
        return count;
    }

    // endregion

    // region 私有方法：参数校验

    /**
     * 校验行业合法
     *
     * @param industry 行业值
     */
    private void validateIndustry(String industry) {
        boolean valid = "banking".equals(industry)
                || "insurance".equals(industry)
                || "securities".equals(industry);
        ThrowUtils.throwIf(!valid, ErrorCode.PARAMS_ERROR, "无效的行业: " + industry);
    }

    /**
     * 校验分类合法
     *
     * @param category 分类值
     */
    private void validateCategory(String category) {
        boolean valid = ApprovalConstant.CATEGORY_HIGH_RISK_OPERATION.equals(category)
                || ApprovalConstant.CATEGORY_SENSITIVE_DATA.equals(category)
                || ApprovalConstant.CATEGORY_LARGE_AMOUNT.equals(category);
        ThrowUtils.throwIf(!valid, ErrorCode.PARAMS_ERROR, "无效的分类: " + category);
    }

    /**
     * 校验风险类型合法
     *
     * @param riskType 风险类型值
     */
    private void validateRiskType(String riskType) {
        boolean valid = ApprovalConstant.RISK_TYPE_HIGH.equals(riskType)
                || ApprovalConstant.RISK_TYPE_MEDIUM.equals(riskType)
                || ApprovalConstant.RISK_TYPE_LOW.equals(riskType);
        ThrowUtils.throwIf(!valid, ErrorCode.PARAMS_ERROR, "无效的风险类型: " + riskType);
    }

    // endregion
}
