package com.finrpa.audit.dto.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 截图上传结果视图对象（M7.2）
 *
 * <p>Python 上传截图后，Java 返回对象路径与预签名 URL。
 * Python 在后续上报审计日志（POST /internal/audit/logs）时将 presignUrl 填入
 * beforeScreenshotUrl / afterScreenshotUrl 字段。</p>
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
@Data
public class ScreenshotUploadVO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 对象路径（{date}/{task_id}/{step_index}_{phase}.png） */
    private String objectPath;

    /** 预签名 URL（有效期 1 小时） */
    private String presignUrl;
}
