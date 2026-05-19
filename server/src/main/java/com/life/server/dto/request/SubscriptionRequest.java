package com.life.server.dto.request;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubscriptionRequest {

    @NotNull(message = "订阅状态不能为空")
    private Boolean accepted;
}
