package com.life.server.service;

import com.life.server.dto.request.UpsertRecipeRequest;
import com.life.server.dto.response.RecipeCoverUploadPolicyResponse;
import com.life.server.dto.response.RecipeDetailResponse;
import com.life.server.dto.response.RecipeListItemView;
import java.util.List;

public interface RecipeService {

    List<RecipeListItemView> listRecipes(Long userId);

    RecipeDetailResponse getRecipe(Long userId, Long recipeId);

    RecipeDetailResponse createRecipe(Long userId, UpsertRecipeRequest request);

    RecipeDetailResponse updateRecipe(Long userId, Long recipeId, UpsertRecipeRequest request);

    RecipeCoverUploadPolicyResponse createCoverUploadPolicy(Long userId, Long recipeId, String originalFileName);

    RecipeDetailResponse randomRecipe(Long userId);
}
