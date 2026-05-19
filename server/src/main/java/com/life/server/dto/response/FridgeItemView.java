package com.life.server.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FridgeItemView {

    private Long id;
    private String sourceType;
    private Long sourceId;
    private String name;
    private String category;
    private BigDecimal quantity;
    private String unit;
    private LocalDate producedAt;
    private LocalDate expiresAt;
    private String location;
    private String note;
    private String status;
    private String reminderState;
}
