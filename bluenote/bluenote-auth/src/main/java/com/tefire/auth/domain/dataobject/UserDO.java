package com.tefire.auth.domain.dataobject;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDO {
    private Long id;

    @NotBlank(message = "昵称不能为空")
    private String username;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}