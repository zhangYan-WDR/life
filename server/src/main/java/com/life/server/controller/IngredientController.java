package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.CreateFamilyIngredientRequest;
import com.life.server.dto.response.IngredientCatalogResponse;
import com.life.server.dto.response.IngredientView;
import com.life.server.security.AuthContext;
import com.life.server.service.IngredientService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @GetMapping("/catalog")
    public ApiResponse<IngredientCatalogResponse> getCatalog() {
        return ApiResponse.success(ingredientService.getCatalog(AuthContext.getUserId()));
    }

    @PostMapping("/family")
    public ApiResponse<IngredientView> createFamilyIngredient(@Valid @RequestBody CreateFamilyIngredientRequest request) {
        return ApiResponse.success(ingredientService.createFamilyIngredient(AuthContext.getUserId(), request));
    }
}
