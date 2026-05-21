package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recipes")
public class RecipeEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long familyId;
    private String name;
    private BigDecimal baseServings;
    private String instructions;
    private String note;
    private String coverUrl;
    private String coverObjectKey;
    private String referenceUrl;
    private String status;
    private Long createdBy;
}
