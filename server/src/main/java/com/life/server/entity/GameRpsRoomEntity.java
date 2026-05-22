package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("game_rps_rooms")
public class GameRpsRoomEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long familyId;
    private Long creatorId;
    private String status;
    private LocalDateTime revealedAt;
}
