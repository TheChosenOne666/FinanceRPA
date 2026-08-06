package com.finrpa.system.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 系统配置视图对象（设置页展示用）
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class SystemConfigVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型 */
    private String configType;

    /** 描述说明 */
    private String description;

    /** 启用状态（0-禁用 1-启用） */
    private Integer status;

    /** 更新时间 */
    private Timestamp updateTime;
}
