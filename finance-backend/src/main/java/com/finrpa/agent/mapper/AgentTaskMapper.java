package com.finrpa.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finrpa.agent.entity.AgentTaskEO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent 任务 Mapper 接口
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEO> {

    /**
     * 按工作流模板 ID 批量统计任务执行次数（用于工作流列表页显示"已执行 X 次"）
     *
     * @param workflowIds 工作流模板 ID 列表
     * @return Map 列表，每项包含 workflowId 和 runCount
     */
    @Select("<script>" +
            "SELECT workflow_id AS workflowId, COUNT(*) AS runCount " +
            "FROM finrpa.rpa_agent_task " +
            "WHERE deleted = 0 AND workflow_id IN " +
            "<foreach item='id' collection='workflowIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY workflow_id" +
            "</script>")
    List<Map<String, Object>> countByWorkflowIds(@Param("workflowIds") List<Long> workflowIds);
}
