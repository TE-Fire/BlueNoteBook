package com.tefire.relation.service;

import com.tefire.framework.common.response.Response;
import com.tefire.relation.model.vo.FollowUserReqVO;
import com.tefire.relation.model.vo.UnfollowUserReqVO;

public interface RelationService {
    
     /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    Response<?> follow(FollowUserReqVO followUserReqVO);

     /**
     * 取关用户
     * @param unfollowUserReqVO
     * @return
     */
    Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO);
}
