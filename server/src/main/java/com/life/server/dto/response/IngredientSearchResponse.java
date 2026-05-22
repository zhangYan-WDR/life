package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientSearchResponse {

    private List<IngredientView> items;
}
