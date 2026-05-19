package com.life.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.life.server.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
