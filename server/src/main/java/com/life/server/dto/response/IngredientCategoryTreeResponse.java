package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientCategoryTreeResponse {

    private List<IngredientCategoryNode> systemCategories;
    private List<IngredientView> familyIngredients;
}
