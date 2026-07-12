/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 15:07:03
 * @Description: 
 */
package com.tefire.relation.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.framework.biz.operationlog.aspect.ApiOperationLog;
import com.tefire.framework.common.response.Response;
import com.tefire.relation.model.vo.FollowUserReqVO;
import com.tefire.relation.model.vo.UnfollowUserReqVO;
import com.tefire.relation.service.RelationService;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 15:07:03
 * @Description: 用户关系
 */
@RestController
@RequestMapping("/relation")
@Slf4j
public class RelationController {

    @Resource
    private RelationService relationService;

    @PostMapping("/follow")
    @ApiOperationLog(description = "关注用户")
    public Response<?> follow(@Validated @RequestBody FollowUserReqVO followUserReqVO) {
        return relationService.follow(followUserReqVO);
    }

    @PostMapping("/unfollow")
    @ApiOperationLog(description = "取关用户")
    public Response<?> unfollow(@Validated @RequestBody UnfollowUserReqVO unfollowUserReqVO) {
        return relationService.unfollow(unfollowUserReqVO);
    }

}