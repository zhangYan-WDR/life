package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recipe_ingredients")
public class RecipeIngredientEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long recipeId;
    private String sourceType;
    private Long sourceId;
    private String nameSnapshot;
    private String categorySnapshot;
    private BigDecimal quantity;
    private String unit;
    private Integer sortOrder;
}
