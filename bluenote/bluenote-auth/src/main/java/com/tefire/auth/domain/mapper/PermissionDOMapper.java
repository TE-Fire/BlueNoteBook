package com.tefire.auth.domain.mapper;

import java.util.List;

import com.tefire.auth.domain.dataobject.PermissionDO;

public interface PermissionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(PermissionDO record);

    int insertSelective(PermissionDO record);

    PermissionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(PermissionDO record);

    int updateByPrimaryKey(PermissionDO record);

    /**
     * 查询 APP 端所有被启用的权限
     *
     * @return
     */
    List<PermissionDO> selectAppEnabledList();
}