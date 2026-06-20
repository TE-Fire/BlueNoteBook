package com.tefire.auth.domain.dataobject;

import java.time.LocalDateTime;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 13:40:05
 * @Description: 用户表实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDO {
    private Long id;

    private String xiaohashuId;

    private String password;

    private String nickname;

    private String avatar;

    private Date birthday;

    private String backgroundImg;

    private String phone;

    private Byte sex;

    private Byte status;

    private String introduction;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}