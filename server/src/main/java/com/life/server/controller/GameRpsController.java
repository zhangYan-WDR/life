package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.CreateRpsRoomRequest;
import com.life.server.dto.request.SubmitGestureRequest;
import com.life.server.dto.response.RpsRoomStatusResponse;
import com.life.server.security.AuthContext;
import com.life.server.service.GameRpsService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/games/rps")
public class GameRpsController {

    private final GameRpsService gameRpsService;

    public GameRpsController(GameRpsService gameRpsService) {
        this.gameRpsService = gameRpsService;
    }

    @PostMapping
    public ApiResponse<RpsRoomStatusResponse> createRoom(@Valid @RequestBody CreateRpsRoomRequest request) {
        return ApiResponse.success(gameRpsService.createRoom(AuthContext.getUserId(), request));
    }

    @GetMapping("/{roomId}/status")
    public ApiResponse<RpsRoomStatusResponse> getStatus(@PathVariable Long roomId) {
        return ApiResponse.success(gameRpsService.getStatus(AuthContext.getUserId(), roomId));
    }

    @PostMapping("/{roomId}/gesture")
    public ApiResponse<Void> submitGesture(@PathVariable Long roomId,
                                           @Valid @RequestBody SubmitGestureRequest request) {
        gameRpsService.submitGesture(AuthContext.getUserId(), roomId, request);
        return ApiResponse.success(null);
    }
}
