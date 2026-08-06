package com.finrpa.batch.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量任务创建结果视图
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class BatchTaskResultVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 本次批量批次 ID（用于前端追踪） */
    private String batchId;

    /** 总数据行数 */
    private int total;

    /** 成功创建的任务数 */
    private int successCount;

    /** 失败行数 */
    private int failedCount;

    /** 每条数据的处理结果 */
    private List<ItemResult> results = new ArrayList<>();

    /** 单条处理结果 */
    @Data
    public static class ItemResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 行号（从 1 开始） */
        private int rowIndex;

        /** 是否成功 */
        private boolean success;

        /** 成功时返回的任务 ID */
        private Long taskId;

        /** 成功时的任务状态 */
        private String state;

        /** 失败原因 */
        private String error;
    }
}
