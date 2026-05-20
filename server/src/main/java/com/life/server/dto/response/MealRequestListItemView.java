package com.life.server.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MealRequestListItemView {

    private Long id;
    private String title;
    private String status;
    private String requesterName;
    private LocalDateTime requestedAt;
    private Integer pendingCount;
    private List<String> recipeNames;
    private Boolean currentUserPending;
}
