package com.life.server.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeListItemView {

    private Long id;
    private String name;
    private BigDecimal baseServings;
    private Integer ingredientCount;
    private String note;
    private String coverUrl;
    private LocalDateTime updatedAt;
}
