package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.CreateMealRequestRequest;
import com.life.server.dto.request.RespondMealRequestRequest;
import com.life.server.dto.response.MealIngredientGapResponse;
import com.life.server.dto.response.MealRequestDetailResponse;
import com.life.server.dto.response.MealRequestListItemView;
import com.life.server.security.AuthContext;
import com.life.server.service.MealRequestService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meal-requests")
public class MealRequestController {

    private final MealRequestService mealRequestService;

    public MealRequestController(MealRequestService mealRequestService) {
        this.mealRequestService = mealRequestService;
    }

    @PostMapping
    public ApiResponse<MealRequestDetailResponse> createMealRequest(@Valid @RequestBody CreateMealRequestRequest request) {
        return ApiResponse.success(mealRequestService.createMealRequest(AuthContext.getUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<MealRequestListItemView>> listMealRequests(@RequestParam(value = "view", required = false) String view) {
        return ApiResponse.success(mealRequestService.listMealRequests(AuthContext.getUserId(), view));
    }

    @GetMapping("/{id}")
    public ApiResponse<MealRequestDetailResponse> getMealRequest(@PathVariable("id") Long id) {
        return ApiResponse.success(mealRequestService.getMealRequest(AuthContext.getUserId(), id));
    }

    @PostMapping("/{id}/respond")
    public ApiResponse<MealRequestDetailResponse> respondMealRequest(@PathVariable("id") Long id,
                                                                     @Valid @RequestBody RespondMealRequestRequest request) {
        return ApiResponse.success(mealRequestService.respondMealRequest(AuthContext.getUserId(), id, request));
    }

    @GetMapping("/{id}/ingredient-gap")
    public ApiResponse<MealIngredientGapResponse> getIngredientGap(@PathVariable("id") Long id) {
        return ApiResponse.success(mealRequestService.getIngredientGap(AuthContext.getUserId(), id));
    }
}
