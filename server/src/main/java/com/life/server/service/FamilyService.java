package com.life.server.service;

import com.life.server.dto.request.CreateFamilyRequest;
import com.life.server.dto.request.JoinFamilyRequest;
import com.life.server.dto.response.FamilyCurrentResponse;

public interface FamilyService {

    FamilyCurrentResponse createFamily(Long userId, CreateFamilyRequest request);

    FamilyCurrentResponse joinFamily(Long userId, JoinFamilyRequest request);

    FamilyCurrentResponse getCurrentFamily(Long userId);
}
