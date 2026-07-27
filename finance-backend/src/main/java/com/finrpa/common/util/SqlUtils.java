package com.finrpa.common.util;

import org.apache.commons.lang3.StringUtils;

/**
 * SQL 工具
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */
public class SqlUtils {

    /**
     * 校验排序字段是否合法（防止 SQL 注入）
     *
     * @param sortField 待校验的排序字段
     * @return 合法返回 true，非法或为空返回 false
     */
    public static boolean validSortField(String sortField) {
        // 1. 空字段直接判定为非法
        if (StringUtils.isBlank(sortField)) {
            return false;
        }
        // 2. 包含危险字符判定为非法
        return !StringUtils.containsAny(sortField, "=", "(", ")", " ");
    }
}
