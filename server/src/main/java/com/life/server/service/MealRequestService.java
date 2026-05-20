package com.life.server.service;

import com.life.server.dto.request.CreateMealRequestRequest;
import com.life.server.dto.request.RespondMealRequestRequest;
import com.life.server.dto.response.MealIngredientGapResponse;
import com.life.server.dto.response.MealRequestDetailResponse;
import com.life.server.dto.response.MealRequestListItemView;
import java.util.List;

public interface MealRequestService {

    MealRequestDetailResponse createMealRequest(Long userId, CreateMealRequestRequest request);

    List<MealRequestListItemView> listMealRequests(Long userId, String view);

    MealRequestDetailResponse getMealRequest(Long userId, Long mealRequestId);

    MealRequestDetailResponse respondMealRequest(Long userId, Long mealRequestId, RespondMealRequestRequest request);

    MealIngredientGapResponse getIngredientGap(Long userId, Long mealRequestId);
}
