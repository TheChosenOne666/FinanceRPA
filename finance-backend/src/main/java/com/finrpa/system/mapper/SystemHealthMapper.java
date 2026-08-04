package com.finrpa.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统健康检查 Mapper（P2 OPS-1）
 *
 * <p>仅用于 DB 连通性检查（SELECT 1），不走租户隔离插件，
 * 因为系统级健康检查不绑定具体 org_id。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Mapper
public interface SystemHealthMapper {

    /**
     * 执行 SELECT 1，验证数据库连通性
     *
     * <p>PostgreSQL {@code SELECT 1} 等价于 MySQL 的 ping，毫秒级响应。</p>
     *
     * @return 1（仅当 DB 可达）
     */
    @Select("SELECT 1")
    Integer ping();
}
