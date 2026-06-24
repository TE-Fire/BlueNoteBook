package com.tefire.auth.model.vo.user;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-24 13:57:53
 * @Description: 修改用户密码
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdatePasswordReqVO {
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
