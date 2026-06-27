/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 21:59:01
 * @Description: 
 */
package com.tefire.user.biz.domain.mapper;

import java.util.List;

import com.tefire.user.biz.domain.dataobject.PermissionDO;

public interface PermissionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(PermissionDO record);

    int insertSelective(PermissionDO record);

    PermissionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(PermissionDO record);

    int updateByPrimaryKey(PermissionDO record);

    /**
     * 查询所有被启用的权限
     *
     * @return
     */
    List<PermissionDO> selectAppEnabledList();
}