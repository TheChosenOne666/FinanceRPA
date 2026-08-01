package com.finrpa.approval.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.approval.dto.request.RiskKeywordAddRequest;
import com.finrpa.approval.dto.request.RiskKeywordQueryRequest;
import com.finrpa.approval.dto.response.RiskKeywordVO;
import com.finrpa.approval.entity.RiskKeywordEO;

import java.util.List;

/**
 * 风险关键词管理服务接口
 *
 * <p>负责关键词库的 CRUD、查询与内置关键词初始化。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface RiskKeywordService {

    /**
     * 分页查询关键词库
     *
     * @param queryRequest 查询请求（含分页 + 筛选参数）
     * @return 分页结果
     */
    IPage<RiskKeywordVO> listKeywords(RiskKeywordQueryRequest queryRequest);

    /**
     * 查询全部启用的关键词（用于预筛加载到内存）
     *
     * @param industry 行业（可空，为空时返回全部行业）
     * @return 启用状态的关键词列表
     */
    List<RiskKeywordEO> listEnabledKeywords(String industry);

    /**
     * 查询关键词详情
     *
     * @param keywordId 关键词业务 ID
     * @return 关键词 VO
     */
    RiskKeywordVO getKeywordDetail(Long keywordId);

    /**
     * 新增自定义关键词
     *
     * @param request 新增请求
     * @return 新增的关键词业务 ID
     */
    Long addKeyword(RiskKeywordAddRequest request);

    /**
     * 更新关键词（内置关键词仅可更新 enabled / description 字段）
     *
     * @param keywordId 关键词业务 ID
     * @param request   更新请求
     * @return 是否更新成功
     */
    boolean updateKeyword(Long keywordId, RiskKeywordAddRequest request);

    /**
     * 删除关键词（内置关键词不可删除，仅可禁用）
     *
     * @param keywordId 关键词业务 ID
     * @return 是否删除成功
     */
    boolean deleteKeyword(Long keywordId);

    /**
     * 注册内置关键词（启动时调用，upsert 语义）
     *
     * @return 注册的关键词数量
     */
    int registerBuiltinKeywords();
}
