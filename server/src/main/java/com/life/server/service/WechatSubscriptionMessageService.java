package com.life.server.service;

import com.life.server.dto.response.ExpiryReminderMessage;

public interface WechatSubscriptionMessageService {

    boolean sendExpiryReminder(ExpiryReminderMessage message);
}
