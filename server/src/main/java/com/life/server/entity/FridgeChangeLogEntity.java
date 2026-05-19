package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fridge_change_logs")
public class FridgeChangeLogEntity {

    @TableId
    private Long id;
    private Long fridgeItemId;
    private Long familyId;
    private String actionType;
    private BigDecimal quantityChange;
    private Long operatorUserId;
    private String note;
    private LocalDateTime createdAt;
}
