package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FamilyMemberView {

    private Long userId;
    private String nickname;
    private String avatar;
    private String role;
}
