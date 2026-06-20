package com.tefire.auth.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 14:40:57
 * @Description: 角色实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoleDO {
    private Long id;

    private String roleName;

    private String roleKey;

    private Integer status;

    private Integer sort;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}