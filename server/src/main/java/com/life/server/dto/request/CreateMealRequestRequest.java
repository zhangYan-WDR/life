package com.life.server.dto.request;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class CreateMealRequestRequest {

    private String title;
    private String note;

    @Valid
    @NotEmpty(message = "至少选择一道菜")
    private List<MealRequestRecipeSelectionRequest> recipes;
}
