package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_request_responses")
public class MealRequestResponseEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long mealRequestId;
    private Long userId;
    private String decision;
    private String comment;
    private LocalDateTime decidedAt;
}
