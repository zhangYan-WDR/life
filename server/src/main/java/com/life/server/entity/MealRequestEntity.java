package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_requests")
public class MealRequestEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long familyId;
    private Long requesterUserId;
    private String title;
    private String note;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
}
