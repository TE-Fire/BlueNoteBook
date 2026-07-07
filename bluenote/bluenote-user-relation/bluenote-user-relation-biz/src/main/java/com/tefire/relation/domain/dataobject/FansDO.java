package com.tefire.relation.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FansDO {
    private Long id;

    private Long userId;

    private Long fansUserId;

    private LocalDateTime createTime;
}