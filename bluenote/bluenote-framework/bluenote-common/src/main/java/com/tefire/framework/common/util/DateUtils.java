package com.tefire.framework.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 15:23:35
 * @Description: 日期工具类
 */
public class DateUtils {
    
     /**
     * LocalDateTime 转时间戳
     *
     * @param localDateTime
     * @return
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
