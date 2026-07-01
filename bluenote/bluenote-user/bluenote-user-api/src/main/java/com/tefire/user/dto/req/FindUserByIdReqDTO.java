package com.tefire.user.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-01 15:08:05
 * @Description: 查询用户信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindUserByIdReqDTO {
    /**
     * 用户 id
     */
    @NotNull(message = "用户 ID 不能为空")
    private Long id;
}
