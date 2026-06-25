package com.tefire.user.biz.domain.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.tefire.user.biz.domain.dataobject.UserDO;

@Mapper
public interface UserDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserDO record);

    int insertSelective(UserDO record);

    UserDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserDO record);

    int updateByPrimaryKey(UserDO record);
}