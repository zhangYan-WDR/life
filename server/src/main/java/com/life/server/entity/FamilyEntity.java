package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("families")
public class FamilyEntity extends BaseEntity {

    @TableId
    private Long id;
    private String name;
    private String inviteCode;
    private Long ownerUserId;
    private String status;
}
