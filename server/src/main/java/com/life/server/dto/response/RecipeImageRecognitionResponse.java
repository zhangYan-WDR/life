package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeImageRecognitionResponse {

    private String name;
    private String instructions;
    private String note;
    private List<String> ingredientNames;
    private String rawText;
    private List<String> lines;
}
