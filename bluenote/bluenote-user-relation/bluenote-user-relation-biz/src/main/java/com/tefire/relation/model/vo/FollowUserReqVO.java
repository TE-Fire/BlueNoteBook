package com.tefire.relation.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-07 14:46:08
 * @Description: 关注用户
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowUserReqVO {
    
    @NotNull(message = "被关注用户 ID 不能为空")
    private Long followUserId;
}
