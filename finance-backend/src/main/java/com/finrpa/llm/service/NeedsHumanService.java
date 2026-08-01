package com.finrpa.llm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finrpa.llm.dto.request.NeedsHumanQueryRequest;
import com.finrpa.llm.dto.request.NeedsHumanReportRequest;
import com.finrpa.llm.dto.request.NeedsHumanResolveRequest;
import com.finrpa.llm.dto.response.NeedsHumanQueueVO;

/**
 * NEEDS_HUMAN 队列服务接口
 *
 * <p>负责 NEEDS_HUMAN 事件的入队、查询与处置。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface NeedsHumanService {

    /**
     * 上报 NEEDS_HUMAN 事件入队（Python 回调）
     *
     * @param request 上报请求
     * @return 是否入队成功
     */
    boolean reportNeedsHuman(NeedsHumanReportRequest request);

    /**
     * 分页查询 NEEDS_HUMAN 队列
     *
     * @param queryRequest 查询请求（含分页参数）
     * @param orgId        组织 ID（租户隔离）
     * @return 分页结果
     */
    IPage<NeedsHumanQueueVO> listNeedsHuman(NeedsHumanQueryRequest queryRequest, Long orgId);

    /**
     * 查询 NEEDS_HUMAN 事件详情
     *
     * @param queueId 队列业务 ID
     * @param orgId   组织 ID（租户隔离）
     * @return 队列详情 VO
     */
    NeedsHumanQueueVO getNeedsHumanDetail(Long queueId, Long orgId);

    /**
     * 处置 NEEDS_HUMAN 事件
     *
     * <p>操作员选择处置动作后，更新队列状态并触发后续操作：
     * <ul>
     *   <li>skip / manual —— 调 {@code TaskService.resumeTask} 续跑任务</li>
     *   <li>abort —— 调 {@code TaskService.abortTask} 终止任务</li>
     * </ul>
     * </p>
     *
     * @param queueId      队列业务 ID
     * @param resolveRequest 处置请求（含 action）
     * @param userId       处置人用户 ID
     * @param orgId        组织 ID（租户隔离）
     * @return 是否处置成功
     */
    boolean resolveNeedsHuman(Long queueId, NeedsHumanResolveRequest resolveRequest, Long userId, Long orgId);
}
