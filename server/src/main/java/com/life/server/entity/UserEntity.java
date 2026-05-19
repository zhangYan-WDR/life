package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class UserEntity extends BaseEntity {

    @TableId
    private Long id;
    private String openid;
    private String nickname;
    private String avatar;
    private Long currentFamilyId;
    private String status;
}
