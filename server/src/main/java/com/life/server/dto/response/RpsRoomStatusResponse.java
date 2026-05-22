package com.life.server.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RpsRoomStatusResponse {

    private Long roomId;
    private String status;
    private List<ParticipantView> participants;

    @Data
    @Builder
    public static class ParticipantView {
        private Long userId;
        private String nickname;
        private boolean submitted;
        private String gesture;
        private String outcome;
    }
}
