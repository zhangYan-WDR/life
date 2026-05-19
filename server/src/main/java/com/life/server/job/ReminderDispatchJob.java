package com.life.server.job;

import com.life.server.service.ReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderDispatchJob {

    private final ReminderService reminderService;

    public ReminderDispatchJob(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "0 0 8 * * ?")
    public void run() {
        reminderService.dispatchExpiryReminders();
    }
}
