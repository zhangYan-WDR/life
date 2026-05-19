package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientCatalogResponse {

    private List<IngredientView> systemIngredients;
    private List<IngredientView> familyIngredients;
}
