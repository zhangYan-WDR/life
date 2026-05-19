package com.life.server.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {

    @NotBlank(message = "code不能为空")
    private String code;
    private String nickname;
    private String avatar;
    private String debugUserKey;
}
