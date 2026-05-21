package com.life.server.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecipeCoverUploadPolicyRequest {

    @NotBlank(message = "文件名不能为空")
    private String originalFileName;
}
