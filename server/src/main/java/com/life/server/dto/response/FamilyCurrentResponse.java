package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FamilyCurrentResponse {

    private Long familyId;
    private String familyName;
    private String inviteCode;
    private List<FamilyMemberView> members;
}
