package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.dto.request.RecipeIngredientRequest;
import com.life.server.dto.request.UpsertRecipeRequest;
import com.life.server.dto.response.RecipeDetailResponse;
import com.life.server.dto.response.RecipeIngredientView;
import com.life.server.dto.response.RecipeListItemView;
import com.life.server.entity.FamilyIngredientEntity;
import com.life.server.entity.RecipeEntity;
import com.life.server.entity.RecipeIngredientEntity;
import com.life.server.entity.SystemIngredientEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyIngredientMapper;
import com.life.server.mapper.RecipeIngredientMapper;
import com.life.server.mapper.RecipeMapper;
import com.life.server.mapper.SystemIngredientMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.RecipeService;
import com.life.server.util.IdGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper recipeIngredientMapper;
    private final SystemIngredientMapper systemIngredientMapper;
    private final FamilyIngredientMapper familyIngredientMapper;
    private final UserMapper userMapper;

    public RecipeServiceImpl(RecipeMapper recipeMapper,
                             RecipeIngredientMapper recipeIngredientMapper,
                             SystemIngredientMapper systemIngredientMapper,
                             FamilyIngredientMapper familyIngredientMapper,
                             UserMapper userMapper) {
        this.recipeMapper = recipeMapper;
        this.recipeIngredientMapper = recipeIngredientMapper;
        this.systemIngredientMapper = systemIngredientMapper;
        this.familyIngredientMapper = familyIngredientMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<RecipeListItemView> listRecipes(Long userId) {
        UserEntity user = requireFamilyUser(userId);
        List<RecipeEntity> recipes = recipeMapper.selectList(new LambdaQueryWrapper<RecipeEntity>()
            .eq(RecipeEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(RecipeEntity::getStatus, "ACTIVE")
            .orderByDesc(RecipeEntity::getUpdatedAt));
        List<RecipeListItemView> views = new ArrayList<RecipeListItemView>();
        for (RecipeEntity recipe : recipes) {
            Long ingredientCount = recipeIngredientMapper.selectCount(new LambdaQueryWrapper<RecipeIngredientEntity>()
                .eq(RecipeIngredientEntity::getRecipeId, recipe.getId()));
            views.add(RecipeListItemView.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .baseServings(recipe.getBaseServings())
                .ingredientCount(ingredientCount == null ? 0 : ingredientCount.intValue())
                .note(recipe.getNote())
                .updatedAt(recipe.getUpdatedAt())
                .build());
        }
        return views;
    }

    @Override
    public RecipeDetailResponse getRecipe(Long userId, Long recipeId) {
        UserEntity user = requireFamilyUser(userId);
        RecipeEntity recipe = requireRecipe(user, recipeId);
        return toDetail(recipe);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecipeDetailResponse createRecipe(Long userId, UpsertRecipeRequest request) {
        UserEntity user = requireFamilyUser(userId);
        RecipeEntity entity = new RecipeEntity();
        entity.setId(IdGenerator.nextId());
        entity.setFamilyId(user.getCurrentFamilyId());
        entity.setName(request.getName().trim());
        entity.setBaseServings(request.getBaseServings());
        entity.setInstructions(request.getInstructions());
        entity.setNote(request.getNote());
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(userId);
        recipeMapper.insert(entity);
        replaceIngredients(entity.getId(), user, request.getIngredients());
        return getRecipe(userId, entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecipeDetailResponse updateRecipe(Long userId, Long recipeId, UpsertRecipeRequest request) {
        UserEntity user = requireFamilyUser(userId);
        RecipeEntity entity = requireRecipe(user, recipeId);
        entity.setName(request.getName().trim());
        entity.setBaseServings(request.getBaseServings());
        entity.setInstructions(request.getInstructions());
        entity.setNote(request.getNote());
        recipeMapper.updateById(entity);
        replaceIngredients(entity.getId(), user, request.getIngredients());
        return getRecipe(userId, entity.getId());
    }

    @Override
    public RecipeDetailResponse randomRecipe(Long userId) {
        List<RecipeListItemView> recipes = listRecipes(userId);
        if (recipes.isEmpty()) {
            throw new BizException("请先创建至少一个菜谱");
        }
        int index = ThreadLocalRandom.current().nextInt(recipes.size());
        return getRecipe(userId, recipes.get(index).getId());
    }

    private void replaceIngredients(Long recipeId, UserEntity user, List<RecipeIngredientRequest> requests) {
        List<RecipeIngredientEntity> existing = recipeIngredientMapper.selectList(new LambdaQueryWrapper<RecipeIngredientEntity>()
            .eq(RecipeIngredientEntity::getRecipeId, recipeId));
        for (RecipeIngredientEntity item : existing) {
            recipeIngredientMapper.deleteById(item.getId());
        }
        for (int i = 0; i < requests.size(); i++) {
            RecipeIngredientRequest request = requests.get(i);
            IngredientSnapshot snapshot = resolveIngredientSnapshot(user, request);
            RecipeIngredientEntity entity = new RecipeIngredientEntity();
            entity.setId(IdGenerator.nextId());
            entity.setRecipeId(recipeId);
            entity.setSourceType(request.getSourceType());
            entity.setSourceId(request.getSourceId());
            entity.setNameSnapshot(snapshot.name);
            entity.setCategorySnapshot(snapshot.category);
            entity.setQuantity(request.getQuantity());
            entity.setUnit(request.getUnit());
            entity.setSortOrder(i + 1);
            recipeIngredientMapper.insert(entity);
        }
    }

    private IngredientSnapshot resolveIngredientSnapshot(UserEntity user, RecipeIngredientRequest request) {
        if (!StringUtils.hasText(request.getSourceType())) {
            throw new BizException("食材来源不能为空");
        }
        if ("SYSTEM".equalsIgnoreCase(request.getSourceType())) {
            if (request.getSourceId() == null) {
                throw new BizException("请选择系统食材");
            }
            SystemIngredientEntity entity = systemIngredientMapper.selectById(request.getSourceId());
            if (entity == null) {
                throw new BizException("系统食材不存在");
            }
            return new IngredientSnapshot(entity.getName(), entity.getCategory());
        }
        if ("FAMILY".equalsIgnoreCase(request.getSourceType())) {
            if (request.getSourceId() == null) {
                throw new BizException("请选择家庭食材");
            }
            FamilyIngredientEntity entity = familyIngredientMapper.selectById(request.getSourceId());
            if (entity == null || !user.getCurrentFamilyId().equals(entity.getFamilyId())) {
                throw new BizException("家庭食材不存在");
            }
            return new IngredientSnapshot(entity.getName(), entity.getCategory());
        }
        throw new BizException("菜谱食材请先从目录中选择");
    }

    private RecipeDetailResponse toDetail(RecipeEntity recipe) {
        List<RecipeIngredientEntity> ingredients = recipeIngredientMapper.selectList(new LambdaQueryWrapper<RecipeIngredientEntity>()
            .eq(RecipeIngredientEntity::getRecipeId, recipe.getId())
            .orderByAsc(RecipeIngredientEntity::getSortOrder));
        List<RecipeIngredientView> views = new ArrayList<RecipeIngredientView>();
        for (RecipeIngredientEntity ingredient : ingredients) {
            views.add(RecipeIngredientView.builder()
                .sourceId(ingredient.getSourceId())
                .sourceType(ingredient.getSourceType())
                .name(ingredient.getNameSnapshot())
                .category(ingredient.getCategorySnapshot())
                .quantity(ingredient.getQuantity())
                .unit(ingredient.getUnit())
                .sortOrder(ingredient.getSortOrder())
                .build());
        }
        return RecipeDetailResponse.builder()
            .id(recipe.getId())
            .name(recipe.getName())
            .baseServings(recipe.getBaseServings())
            .instructions(recipe.getInstructions())
            .note(recipe.getNote())
            .status(recipe.getStatus())
            .updatedAt(recipe.getUpdatedAt())
            .ingredients(views)
            .build();
    }

    private RecipeEntity requireRecipe(UserEntity user, Long recipeId) {
        RecipeEntity recipe = recipeMapper.selectById(recipeId);
        if (recipe == null || !user.getCurrentFamilyId().equals(recipe.getFamilyId()) || !"ACTIVE".equals(recipe.getStatus())) {
            throw new BizException("菜谱不存在");
        }
        return recipe;
    }

    private UserEntity requireFamilyUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        return user;
    }

    private static class IngredientSnapshot {
        private final String name;
        private final String category;

        private IngredientSnapshot(String name, String category) {
            this.name = name;
            this.category = category;
        }
    }
}
