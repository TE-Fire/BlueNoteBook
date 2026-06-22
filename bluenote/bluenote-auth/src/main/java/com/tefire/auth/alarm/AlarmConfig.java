
package com.tefire.auth.alarm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tefire.auth.alarm.impl.MailAlarmHelper;
import com.tefire.auth.alarm.impl.SmsAlarmHelper;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-22 17:26:13
 * @Description: 警告配置类
 */
@RefreshScope // 实现配置动态刷新功能
@Configuration
public class AlarmConfig {

    @Value("${alarm.type}")
    private String alarmType;

    @Bean
    public AlarmInterface alarmHelper() {
        // 根据配置文件中的告警类型，初始化选择不同的告警实现类
        if (StringUtils.equals("sms", alarmType)) {
            return new SmsAlarmHelper();
        } else if (StringUtils.equals("mail", alarmType)) {
            return new MailAlarmHelper();
        } else {
            throw new IllegalArgumentException("错误的告警类型...");
        }
    }
}