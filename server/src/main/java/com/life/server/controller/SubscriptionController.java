package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.SubscriptionRequest;
import com.life.server.security.AuthContext;
import com.life.server.service.SubscriptionService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/expiry-reminder")
    public ApiResponse<Void> updateExpiryReminder(@Valid @RequestBody SubscriptionRequest request) {
        subscriptionService.updateExpiryReminder(AuthContext.getUserId(), request.getAccepted());
        return ApiResponse.success("设置成功", null);
    }

    @PostMapping("/meal-request-reminder")
    public ApiResponse<Void> updateMealRequestReminder(@Valid @RequestBody SubscriptionRequest request) {
        subscriptionService.updateMealRequestReminder(AuthContext.getUserId(), request.getAccepted());
        return ApiResponse.success("设置成功", null);
    }
}
