package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ingredient_catalog_family")
public class FamilyIngredientEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long familyId;
    private String name;
    private String category;
    private String defaultUnit;
    private Boolean enabled;
}
