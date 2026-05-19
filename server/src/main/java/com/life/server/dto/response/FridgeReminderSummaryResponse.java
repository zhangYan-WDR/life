package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FridgeReminderSummaryResponse {

    private long total;
    private long expiringSoon;
    private long expired;
}
