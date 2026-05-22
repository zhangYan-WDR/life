package com.life.server.service;

import com.life.server.dto.request.CreateFamilyIngredientRequest;
import com.life.server.dto.response.IngredientCatalogResponse;
import com.life.server.dto.response.IngredientCategoryTreeResponse;
import com.life.server.dto.response.IngredientGroupView;
import com.life.server.dto.response.IngredientSearchResponse;
import com.life.server.dto.response.IngredientView;
import java.util.List;

public interface IngredientService {

    IngredientCatalogResponse getCatalog(Long userId, boolean includeSystem);

    List<IngredientGroupView> getSystemGroups(int previewSize);

    IngredientSearchResponse searchCatalog(Long userId, String keyword, int limit);

    IngredientCategoryTreeResponse getCategoryTree(Long userId);

    List<IngredientView> listByCategory(Long userId, String category, String secondaryCategory, int offset, int limit);

    IngredientView createFamilyIngredient(Long userId, CreateFamilyIngredientRequest request);
}
