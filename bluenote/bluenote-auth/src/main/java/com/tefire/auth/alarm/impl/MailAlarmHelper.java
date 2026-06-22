package com.tefire.auth.alarm.impl;

import com.tefire.auth.alarm.AlarmInterface;

import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 17:22:03
 * @Description: 邮件通知
 */
@Slf4j
public class MailAlarmHelper implements AlarmInterface {

    /**
     * 发送告警信息
     *
     * @param message
     * @return
     */
    @Override
    public boolean send(String message) {
        log.info("==> 【邮件告警】：{}", message);
        
        // 业务逻辑...
        
        return true;
    }
}
