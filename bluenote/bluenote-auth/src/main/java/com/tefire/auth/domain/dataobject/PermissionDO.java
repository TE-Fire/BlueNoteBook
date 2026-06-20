package com.tefire.auth.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-20 14:40:57
 * @Description: 权限表实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PermissionDO {
    private Long id;

    private Long parentId;

    private String name;

    private Integer type;

    private String menuUrl;

    private String menuIcon;

    private Integer sort;

    private String permissionKey;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}