package com.tefire.relation.service;

import com.tefire.framework.common.response.PageResponse;
import com.tefire.framework.common.response.Response;
import com.tefire.relation.model.vo.FindFansListReqVO;
import com.tefire.relation.model.vo.FindFansUserRspVO;
import com.tefire.relation.model.vo.FindFollowingListReqVO;
import com.tefire.relation.model.vo.FindFollowingUserRspVO;
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

     /**
     * 查询关注列表
     * @param findFollowingListReqVO
     * @return
     */
    PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO);

     /**
     * 查询粉丝列表
     * @param findFansListReqVO
     * @return
     */
    PageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO findFansListReqVO);
}
