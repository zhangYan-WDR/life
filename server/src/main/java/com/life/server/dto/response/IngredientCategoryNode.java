package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientCategoryNode {

    private String category;
    private Integer totalCount;
    private List<IngredientSubCategoryNode> subCategories;
}
