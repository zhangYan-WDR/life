package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientView {

    private Long id;
    private String sourceType;
    private String name;
    private String category;
    private String defaultUnit;
}
