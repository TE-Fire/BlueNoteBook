package com.tefire.user.biz.domain.dataobject;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private Date createTime;

    private Date updateTime;

    private Boolean isDeleted;

}