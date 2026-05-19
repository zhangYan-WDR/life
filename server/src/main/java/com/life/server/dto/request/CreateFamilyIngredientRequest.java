package com.life.server.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateFamilyIngredientRequest {

    @NotBlank(message = "食材名称不能为空")
    private String name;
    @NotBlank(message = "分类不能为空")
    private String category;
    @NotBlank(message = "单位不能为空")
    private String defaultUnit;
}
