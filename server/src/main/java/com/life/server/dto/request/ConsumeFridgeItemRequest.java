package com.life.server.dto.request;

import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConsumeFridgeItemRequest {

    @NotNull(message = "消耗数量不能为空")
    private BigDecimal quantity;
    private String note;
}
