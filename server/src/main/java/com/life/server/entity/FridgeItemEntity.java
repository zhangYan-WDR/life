package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fridge_items")
public class FridgeItemEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long familyId;
    private String sourceType;
    private Long sourceId;
    private String nameSnapshot;
    private String categorySnapshot;
    private BigDecimal quantity;
    private String unit;
    private LocalDate producedAt;
    private LocalDate expiresAt;
    private String location;
    private String note;
    private String status;
    private Long createdBy;
}
