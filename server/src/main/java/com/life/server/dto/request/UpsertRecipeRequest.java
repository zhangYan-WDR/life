package com.life.server.dto.request;

import java.math.BigDecimal;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertRecipeRequest {

    @NotBlank(message = "菜谱名称不能为空")
    private String name;

    @NotNull(message = "基准份数不能为空")
    @DecimalMin(value = "0.01", message = "基准份数必须大于0")
    private BigDecimal baseServings;

    private String instructions;
    private String note;

    @Valid
    @NotEmpty(message = "至少需要一个食材")
    private List<RecipeIngredientRequest> ingredients;
}
