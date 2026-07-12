package com.tefire.relation.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnfollowUserReqVO {
    @NotNull(message = "被取关用户 ID 不能为空")
    private Long unfollowUserId;
}
