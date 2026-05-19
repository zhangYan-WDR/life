package com.life.server.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertFridgeItemRequest {

    private Long id;
    @NotBlank(message = "来源类型不能为空")
    private String sourceType;
    private Long sourceId;
    private String customName;
    @NotNull(message = "数量不能为空")
    private BigDecimal quantity;
    @NotBlank(message = "单位不能为空")
    private String unit;
    private LocalDate producedAt;
    private LocalDate expiresAt;
    private String location;
    private String note;
}
