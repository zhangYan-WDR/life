package com.life.server.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RespondMealRequestRequest {

    @NotBlank(message = "表态不能为空")
    private String decision;
    private String comment;
}
