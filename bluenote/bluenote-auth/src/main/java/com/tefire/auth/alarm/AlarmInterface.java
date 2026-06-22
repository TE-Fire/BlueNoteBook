package com.tefire.auth.alarm;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 17:20:56
 * @Description: 异常警告通知接口
 */
public interface AlarmInterface {
    /**
     * 发送告警信息
     *
     * @param message
     * @return
     */
    boolean send(String message);
}
