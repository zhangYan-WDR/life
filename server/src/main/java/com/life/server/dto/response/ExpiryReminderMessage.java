package com.life.server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExpiryReminderMessage {

    private Long userId;
    private String openid;
    private String productName;
    private String expiryDate;
    private String remainingDays;
    private String inventoryQuantity;
    private String remark;
    private String reminderType;
}
