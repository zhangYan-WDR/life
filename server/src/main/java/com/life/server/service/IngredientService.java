package com.life.server.service;

import com.life.server.dto.request.CreateFamilyIngredientRequest;
import com.life.server.dto.response.IngredientCatalogResponse;
import com.life.server.dto.response.IngredientView;

public interface IngredientService {

    IngredientCatalogResponse getCatalog(Long userId);

    IngredientView createFamilyIngredient(Long userId, CreateFamilyIngredientRequest request);
}
