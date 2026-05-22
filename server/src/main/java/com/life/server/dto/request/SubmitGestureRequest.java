package com.life.server.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SubmitGestureRequest {

    @NotBlank
    @Pattern(regexp = "ROCK|SCISSORS|PAPER", message = "手势必须是 ROCK、SCISSORS 或 PAPER")
    private String gesture;
}
