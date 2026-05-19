package com.life.server.service;

public interface ReminderService {

    void dispatchExpiryReminders();

    boolean triggerTestReminder(Long userId);
}
