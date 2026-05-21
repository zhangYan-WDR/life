package com.life.server.controller;

import com.life.server.common.ApiResponse;
import com.life.server.dto.request.RecipeCoverUploadPolicyRequest;
import com.life.server.dto.request.UpsertRecipeRequest;
import com.life.server.dto.response.RecipeCoverUploadPolicyResponse;
import com.life.server.dto.response.RecipeDetailResponse;
import com.life.server.dto.response.RecipeListItemView;
import com.life.server.security.AuthContext;
import com.life.server.service.RecipeService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public ApiResponse<List<RecipeListItemView>> listRecipes() {
        return ApiResponse.success(recipeService.listRecipes(AuthContext.getUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecipeDetailResponse> getRecipe(@PathVariable("id") Long id) {
        return ApiResponse.success(recipeService.getRecipe(AuthContext.getUserId(), id));
    }

    @PostMapping
    public ApiResponse<RecipeDetailResponse> createRecipe(@Valid @RequestBody UpsertRecipeRequest request) {
        return ApiResponse.success(recipeService.createRecipe(AuthContext.getUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecipeDetailResponse> updateRecipe(@PathVariable("id") Long id,
                                                          @Valid @RequestBody UpsertRecipeRequest request) {
        return ApiResponse.success(recipeService.updateRecipe(AuthContext.getUserId(), id, request));
    }

    @PostMapping("/{id}/cover-upload-policy")
    public ApiResponse<RecipeCoverUploadPolicyResponse> createCoverUploadPolicy(
        @PathVariable("id") Long id,
        @Valid @RequestBody RecipeCoverUploadPolicyRequest request
    ) {
        return ApiResponse.success(recipeService.createCoverUploadPolicy(AuthContext.getUserId(), id, request.getOriginalFileName()));
    }

    @GetMapping("/random")
    public ApiResponse<RecipeDetailResponse> randomRecipe() {
        return ApiResponse.success(recipeService.randomRecipe(AuthContext.getUserId()));
    }
}
