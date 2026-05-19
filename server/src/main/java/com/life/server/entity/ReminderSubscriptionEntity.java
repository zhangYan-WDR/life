package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reminder_subscriptions")
public class ReminderSubscriptionEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long userId;
    private String templateType;
    private Boolean accepted;
    private LocalDateTime lastAcceptedAt;
}
