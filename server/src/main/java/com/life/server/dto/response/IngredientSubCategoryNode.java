package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientSubCategoryNode {

    private String secondaryCategory;
    private Integer count;
}
