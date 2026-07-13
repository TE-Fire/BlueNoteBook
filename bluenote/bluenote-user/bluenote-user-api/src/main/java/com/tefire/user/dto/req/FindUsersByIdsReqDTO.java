package com.tefire.user.dto.req;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-13 11:32:17
 * @Description: 批量查询用户关注列表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FindUsersByIdsReqDTO {
    
    @NotNull(message = "用户 ID 集合不能为空")
    @Size(min = 1, max = 10, message = "用户 ID 集合大小必须大于等于 1, 小于等于 10")
    private List<Long> ids;
}
