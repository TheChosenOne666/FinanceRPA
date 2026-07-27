package com.finrpa.common.model;

import java.io.Serializable;
import lombok.Data;

/**
 * 删除请求基类
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * 待删除记录主键 ID
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
