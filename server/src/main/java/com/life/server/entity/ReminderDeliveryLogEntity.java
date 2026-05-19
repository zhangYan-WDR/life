package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("reminder_delivery_logs")
public class ReminderDeliveryLogEntity {

    @TableId
    private Long id;
    private Long userId;
    private Long fridgeItemId;
    private String reminderType;
    private LocalDate deliveredOn;
    private LocalDateTime createdAt;
}
