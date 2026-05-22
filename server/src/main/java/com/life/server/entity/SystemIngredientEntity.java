package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ingredient_catalog_system")
public class SystemIngredientEntity extends BaseEntity {

    @TableId
    private Long id;
    private String name;
    private String category;
    private String secondaryCategory;
    private String defaultUnit;
    private Boolean enabled;
    private Integer sortOrder;
}
