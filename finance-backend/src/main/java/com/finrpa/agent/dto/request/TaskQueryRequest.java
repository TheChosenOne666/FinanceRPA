package com.finrpa.agent.dto.request;

import com.finrpa.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务分页查询请求 DTO
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskQueryRequest extends PageRequest {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务状态筛选（可选） */
    private String status;

    /** 关键词搜索（匹配 goal 字段，可选） */
    private String searchText;

    /** 工作流模板 ID 筛选（可选，用于查询某个工作流的执行历史） */
    private Long workflowId;

    /** 业务线 ID 筛选（可选；M7.6 三维度 RBAC，普通用户自动限制为本人关联业务线，org_admin 全局可筛） */
    private Long businessLineId;

    /** 部门 ID 筛选（可选；M7.6 三维度 RBAC） */
    private Long departmentId;
}
