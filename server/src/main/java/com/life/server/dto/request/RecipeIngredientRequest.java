package com.life.server.dto.request;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecipeIngredientRequest {

    @NotBlank(message = "食材来源不能为空")
    private String sourceType;
    private Long sourceId;

    @DecimalMin(value = "0.01", message = "食材数量必须大于0")
    private BigDecimal quantity;

    @NotBlank(message = "食材单位不能为空")
    private String unit;
}
