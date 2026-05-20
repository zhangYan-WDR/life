package com.life.server.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeIngredientView {

    private Long sourceId;
    private String sourceType;
    private String name;
    private String category;
    private BigDecimal quantity;
    private String unit;
    private Integer sortOrder;
}
