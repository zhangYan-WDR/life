package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecognizedIngredientResponse {

    private String name;
    private String quantity;
    private String unit;
}
