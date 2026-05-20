package com.life.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.life.server.entity.RecipeEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecipeMapper extends BaseMapper<RecipeEntity> {
}
