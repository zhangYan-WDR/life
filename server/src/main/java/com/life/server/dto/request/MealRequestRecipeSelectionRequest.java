package com.life.server.dto.request;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MealRequestRecipeSelectionRequest {

    @NotNull(message = "菜谱不能为空")
    private Long recipeId;

    @NotNull(message = "目标份数不能为空")
    @DecimalMin(value = "0.01", message = "目标份数必须大于0")
    private BigDecimal targetServings;
}
