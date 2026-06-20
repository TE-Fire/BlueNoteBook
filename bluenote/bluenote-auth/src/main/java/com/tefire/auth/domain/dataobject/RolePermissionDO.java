package com.tefire.auth.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 14:40:57
 * @Description: 角色-权限实体类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RolePermissionDO {
    private Long id;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}