package com.life.server.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinFamilyRequest {

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
