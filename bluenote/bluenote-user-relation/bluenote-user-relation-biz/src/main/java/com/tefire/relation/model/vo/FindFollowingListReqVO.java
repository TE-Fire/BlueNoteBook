package com.tefire.relation.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-13 14:53:45
 * @Description: 查询关注列表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FindFollowingListReqVO {
    
    @NotNull(message = "查询用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "页码不能为空")
    private Integer pageNo = 1; // 默认值为第一页
}
