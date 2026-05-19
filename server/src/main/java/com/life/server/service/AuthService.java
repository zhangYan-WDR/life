package com.life.server.service;

import com.life.server.dto.request.WxLoginRequest;
import com.life.server.dto.response.AuthLoginResponse;

public interface AuthService {

    AuthLoginResponse login(WxLoginRequest request);
}
