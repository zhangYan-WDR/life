package com.life.server.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("game_rps_gestures")
public class GameRpsGestureEntity extends BaseEntity {

    @TableId
    private Long id;
    private Long roomId;
    private Long userId;
    private String gestureHash;
    private String gesture;
    private String salt;
    private LocalDateTime submittedAt;
}
