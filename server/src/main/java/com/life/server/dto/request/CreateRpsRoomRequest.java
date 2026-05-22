package com.life.server.dto.request;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRpsRoomRequest {

    @NotEmpty
    @Size(min = 2, message = "至少选择 2 名参与者")
    private List<Long> participantUserIds;
}
