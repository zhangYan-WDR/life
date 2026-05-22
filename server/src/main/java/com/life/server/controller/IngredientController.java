package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.CreateFamilyIngredientRequest;
import com.life.server.dto.response.IngredientCatalogResponse;
import com.life.server.dto.response.IngredientCategoryTreeResponse;
import com.life.server.dto.response.IngredientGroupView;
import com.life.server.dto.response.IngredientSearchResponse;
import com.life.server.dto.response.IngredientView;
import com.life.server.security.AuthContext;
import com.life.server.service.IngredientService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @GetMapping("/catalog")
    public ApiResponse<IngredientCatalogResponse> getCatalog(
            @RequestParam(defaultValue = "true") boolean includeSystem) {
        return ApiResponse.success(ingredientService.getCatalog(AuthContext.getUserId(), includeSystem));
    }

    @GetMapping("/system-groups")
    public ApiResponse<List<IngredientGroupView>> getSystemGroups(
            @RequestParam(defaultValue = "12") int previewSize) {
        return ApiResponse.success(ingredientService.getSystemGroups(previewSize));
    }

    @GetMapping("/search")
    public ApiResponse<IngredientSearchResponse> searchCatalog(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.success(ingredientService.searchCatalog(AuthContext.getUserId(), keyword, limit));
    }

    @GetMapping("/category-tree")
    public ApiResponse<IngredientCategoryTreeResponse> getCategoryTree() {
        return ApiResponse.success(ingredientService.getCategoryTree(AuthContext.getUserId()));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<IngredientView>> listByCategory(
            @RequestParam String category,
            @RequestParam(required = false, defaultValue = "") String secondaryCategory,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(ingredientService.listByCategory(AuthContext.getUserId(), category, secondaryCategory, offset, limit));
    }

    @PostMapping("/family")
    public ApiResponse<IngredientView> createFamilyIngredient(@Valid @RequestBody CreateFamilyIngredientRequest request) {
        return ApiResponse.success(ingredientService.createFamilyIngredient(AuthContext.getUserId(), request));
    }
}
