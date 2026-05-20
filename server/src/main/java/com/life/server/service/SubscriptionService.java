package com.life.server.service;

public interface SubscriptionService {

    void updateExpiryReminder(Long userId, Boolean accepted);

    void updateMealRequestReminder(Long userId, Boolean accepted);
}
