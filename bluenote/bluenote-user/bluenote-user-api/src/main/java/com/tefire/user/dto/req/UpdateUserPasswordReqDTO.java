package com.tefire.user.dto.req;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-27 23:48:40
 * @Description: 密码更新
 */
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserPasswordReqDTO {

    @NotBlank(message = "密码不能为空")
    private String encodePassword;

}
