package com.life.server.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MealRequestResponseView {

    private Long userId;
    private String nickname;
    private String role;
    private String decision;
    private String comment;
    private LocalDateTime decidedAt;
    private Boolean currentUser;
}
