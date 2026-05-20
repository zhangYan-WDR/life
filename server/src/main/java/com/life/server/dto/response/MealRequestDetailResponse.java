package com.life.server.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MealRequestDetailResponse {

    private Long id;
    private String title;
    private String note;
    private String status;
    private Long requesterUserId;
    private String requesterName;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private Boolean currentUserPending;
    private List<MealRequestRecipeView> recipes;
    private List<MealRequestResponseView> responses;
}
