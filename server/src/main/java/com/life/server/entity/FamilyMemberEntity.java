package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("family_members")
public class FamilyMemberEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long familyId;
    private Long userId;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
}
