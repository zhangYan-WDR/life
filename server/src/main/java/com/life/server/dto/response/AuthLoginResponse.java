package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthLoginResponse {

    private String token;
    private Long userId;
    private boolean joinedFamily;
}
