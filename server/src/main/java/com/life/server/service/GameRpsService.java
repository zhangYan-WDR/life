package com.life.server.service;

import com.life.server.dto.request.CreateRpsRoomRequest;
import com.life.server.dto.request.SubmitGestureRequest;
import com.life.server.dto.response.RpsRoomStatusResponse;

public interface GameRpsService {

    RpsRoomStatusResponse createRoom(Long userId, CreateRpsRoomRequest request);

    RpsRoomStatusResponse getStatus(Long userId, Long roomId);

    void submitGesture(Long userId, Long roomId, SubmitGestureRequest request);
}
