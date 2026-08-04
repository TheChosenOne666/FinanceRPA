package com.finrpa.system.service;

import com.finrpa.system.dto.response.SystemHealthVO;

/**
 * 系统健康检查服务（P2 OPS-1）
 *
 * <p>聚合 DB / Redis / Python AI / MinIO 四类组件连通性检查，
 * 供设置页「安全策略 · 系统健康」一键检测调用。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>每个组件独立 try-catch，单组件 DOWN 不影响其他组件检查</li>
 *   <li>检查带超时（DB/Redis 1s，HTTP 3s），避免页面长时间等待</li>
 *   <li>仅做轻量 ping，不执行业务 SQL / 不上传对象</li>
 * </ul>
 * </p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public interface SystemHealthService {

    /**
     * 执行全量健康检查（DB + Redis + Python AI + MinIO）
     *
     * <p>每个组件独立检查，单组件失败不抛异常，仅在返回的 VO 中标记 DOWN。
     * 整体状态规则：全部 UP → UP；部分 DOWN → DEGRADED；全部 DOWN → DOWN。</p>
     *
     * @return 健康检查结果
     */
    SystemHealthVO check();
}
