package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.CreateFamilyRequest;
import com.life.server.dto.request.JoinFamilyRequest;
import com.life.server.dto.response.FamilyCurrentResponse;
import com.life.server.security.AuthContext;
import com.life.server.service.FamilyService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping
    public ApiResponse<FamilyCurrentResponse> createFamily(@Valid @RequestBody CreateFamilyRequest request) {
        return ApiResponse.success(familyService.createFamily(AuthContext.getUserId(), request));
    }

    @PostMapping("/join")
    public ApiResponse<FamilyCurrentResponse> joinFamily(@Valid @RequestBody JoinFamilyRequest request) {
        return ApiResponse.success(familyService.joinFamily(AuthContext.getUserId(), request));
    }

    @GetMapping("/current")
    public ApiResponse<FamilyCurrentResponse> getCurrentFamily() {
        return ApiResponse.success(familyService.getCurrentFamily(AuthContext.getUserId()));
    }
}
