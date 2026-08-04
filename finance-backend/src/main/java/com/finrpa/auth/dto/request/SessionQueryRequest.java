package com.finrpa.auth.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 在线会话查询请求（P2 SEC-3）
 *
 * <p>用于设置页「安全策略 · 在线会话」按用户 ID / 用户名筛选与分页。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionQueryRequest extends PageRequest {

    /** 用户业务 ID 筛选（可选，精确匹配） */
    private String userId;

    /** 用户名筛选（可选，模糊匹配） */
    private String username;
}
