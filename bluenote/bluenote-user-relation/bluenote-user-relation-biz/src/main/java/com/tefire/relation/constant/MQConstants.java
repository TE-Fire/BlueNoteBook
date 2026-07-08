package com.tefire.relation.constant;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 22:17:08
 * @Description: MQ 常量
 */
public interface MQConstants {
    
    
    /**
     * Topic: 关注、取关共用一个
     */
    String TOPIC_FOLLOW_OR_UNFOLLOW = "FollowUnfollowTopic";

    /**
     * 关注标签
     */
    String TAG_FOLLOW = "Follow";

    /**
     * 取关标签
     */
    String TAG_UNFOLLOW = "Unfollow";
}
