package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_request_recipes")
public class MealRequestRecipeEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long mealRequestId;
    private Long recipeId;
    private String recipeNameSnapshot;
    private BigDecimal baseServings;
    private BigDecimal targetServings;
    private BigDecimal servingsMultiplier;
}
