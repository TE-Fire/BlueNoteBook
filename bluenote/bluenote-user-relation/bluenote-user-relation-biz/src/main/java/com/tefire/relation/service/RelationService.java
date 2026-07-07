package com.tefire.relation.service;

import com.tefire.framework.common.response.Response;
import com.tefire.relation.model.vo.FollowUserReqVO;

public interface RelationService {
    
     /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    Response<?> follow(FollowUserReqVO followUserReqVO);
}
