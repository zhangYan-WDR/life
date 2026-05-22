package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("game_rps_participants")
public class GameRpsParticipantEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long roomId;
    private Long userId;
}
