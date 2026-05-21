package com.life.server.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeDetailResponse {

    private Long id;
    private String name;
    private BigDecimal baseServings;
    private String instructions;
    private String note;
    private String coverUrl;
    private String coverObjectKey;
    private String referenceUrl;
    private String status;
    private LocalDateTime updatedAt;
    private List<RecipeIngredientView> ingredients;
}
