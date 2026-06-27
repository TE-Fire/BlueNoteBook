/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 21:59:01
 * @Description: 
 */
package com.tefire.user.biz.domain.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.tefire.user.biz.domain.dataobject.RolePermissionDO;

public interface RolePermissionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(RolePermissionDO record);

    int insertSelective(RolePermissionDO record);

    RolePermissionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(RolePermissionDO record);

    int updateByPrimaryKey(RolePermissionDO record);

     /**
     * 根据角色 ID 集合批量查询
     *
     * @param roleIds
     * @return
     */
    List<RolePermissionDO> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
}