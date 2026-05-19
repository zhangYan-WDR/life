package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.WxLoginRequest;
import com.life.server.dto.response.AuthLoginResponse;
import com.life.server.service.AuthService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wx-login")
    public ApiResponse<AuthLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
