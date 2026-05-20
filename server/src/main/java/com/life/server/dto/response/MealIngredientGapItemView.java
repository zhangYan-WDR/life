package com.life.server.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MealIngredientGapItemView {

    private String name;
    private String category;
    private BigDecimal requiredQuantity;
    private String requiredUnit;
    private BigDecimal availableQuantity;
    private String availableUnit;
    private BigDecimal missingQuantity;
    private String status;
    private String note;
}
