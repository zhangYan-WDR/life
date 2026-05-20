package com.life.server.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MealRequestRecipeView {

    private Long recipeId;
    private String recipeName;
    private BigDecimal baseServings;
    private BigDecimal targetServings;
    private BigDecimal servingsMultiplier;
}
