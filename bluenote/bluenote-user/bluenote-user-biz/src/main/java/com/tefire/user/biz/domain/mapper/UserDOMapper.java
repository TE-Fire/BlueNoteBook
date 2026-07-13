/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 21:04:14
 * @Description: 
 */
package com.tefire.user.biz.domain.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.tefire.user.biz.domain.dataobject.UserDO;

public interface UserDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserDO record);

    int insertSelective(UserDO record);

    UserDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserDO record);

    int updateByPrimaryKey(UserDO record);

     /**
     * 根据手机号查询记录
     * @param phone
     * @return
     */
    UserDO selectByPhone(String phone);

     /**
     * 批量查询用户信息
     * 
     * @param ids
     * @return
     */
    List<UserDO> selectByIds(@Param("ids") List<Long> ids);
}