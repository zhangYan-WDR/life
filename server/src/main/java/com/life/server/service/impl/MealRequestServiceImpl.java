package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.dto.request.CreateMealRequestRequest;
import com.life.server.dto.request.MealRequestRecipeSelectionRequest;
import com.life.server.dto.request.RespondMealRequestRequest;
import com.life.server.dto.response.MealIngredientGapItemView;
import com.life.server.dto.response.MealIngredientGapResponse;
import com.life.server.dto.response.MealRequestDetailResponse;
import com.life.server.dto.response.MealRequestListItemView;
import com.life.server.dto.response.MealRequestRecipeView;
import com.life.server.dto.response.MealRequestResponseView;
import com.life.server.entity.FamilyMemberEntity;
import com.life.server.entity.FridgeItemEntity;
import com.life.server.entity.MealRequestEntity;
import com.life.server.entity.MealRequestRecipeEntity;
import com.life.server.entity.MealRequestResponseEntity;
import com.life.server.entity.RecipeEntity;
import com.life.server.entity.RecipeIngredientEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyMemberMapper;
import com.life.server.mapper.FridgeItemMapper;
import com.life.server.mapper.MealRequestMapper;
import com.life.server.mapper.MealRequestRecipeMapper;
import com.life.server.mapper.MealRequestResponseMapper;
import com.life.server.mapper.RecipeIngredientMapper;
import com.life.server.mapper.RecipeMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.MealRequestService;
import com.life.server.util.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MealRequestServiceImpl implements MealRequestService {

    private static final BigDecimal WEIGHT_JIN_IN_GRAMS = new BigDecimal("500");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private final MealRequestMapper mealRequestMapper;
    private final MealRequestRecipeMapper mealRequestRecipeMapper;
    private final MealRequestResponseMapper mealRequestResponseMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper recipeIngredientMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FridgeItemMapper fridgeItemMapper;
    private final UserMapper userMapper;

    public MealRequestServiceImpl(MealRequestMapper mealRequestMapper,
                                  MealRequestRecipeMapper mealRequestRecipeMapper,
                                  MealRequestResponseMapper mealRequestResponseMapper,
                                  RecipeMapper recipeMapper,
                                  RecipeIngredientMapper recipeIngredientMapper,
                                  FamilyMemberMapper familyMemberMapper,
                                  FridgeItemMapper fridgeItemMapper,
                                  UserMapper userMapper) {
        this.mealRequestMapper = mealRequestMapper;
        this.mealRequestRecipeMapper = mealRequestRecipeMapper;
        this.mealRequestResponseMapper = mealRequestResponseMapper;
        this.recipeMapper = recipeMapper;
        this.recipeIngredientMapper = recipeIngredientMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.fridgeItemMapper = fridgeItemMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealRequestDetailResponse createMealRequest(Long userId, CreateMealRequestRequest request) {
        UserEntity user = requireFamilyUser(userId);
        List<FamilyMemberEntity> members = listActiveMembers(user.getCurrentFamilyId());
        if (members.isEmpty()) {
            throw new BizException("家庭成员不存在");
        }
        MealRequestEntity entity = new MealRequestEntity();
        entity.setId(IdGenerator.nextId());
        entity.setFamilyId(user.getCurrentFamilyId());
        entity.setRequesterUserId(userId);
        entity.setTitle(buildTitle(request));
        entity.setNote(request.getNote());
        entity.setStatus(members.size() <= 1 ? "APPROVED" : "PENDING");
        entity.setRequestedAt(LocalDateTime.now());
        entity.setDecidedAt(members.size() <= 1 ? LocalDateTime.now() : null);
        mealRequestMapper.insert(entity);

        for (MealRequestRecipeSelectionRequest selection : request.getRecipes()) {
            RecipeEntity recipe = requireRecipe(user.getCurrentFamilyId(), selection.getRecipeId());
            MealRequestRecipeEntity relation = new MealRequestRecipeEntity();
            relation.setId(IdGenerator.nextId());
            relation.setMealRequestId(entity.getId());
            relation.setRecipeId(recipe.getId());
            relation.setRecipeNameSnapshot(recipe.getName());
            relation.setBaseServings(recipe.getBaseServings());
            relation.setTargetServings(selection.getTargetServings());
            relation.setServingsMultiplier(selection.getTargetServings().divide(recipe.getBaseServings(), 4, RoundingMode.HALF_UP));
            mealRequestRecipeMapper.insert(relation);
        }

        if (members.size() > 1) {
            for (FamilyMemberEntity member : members) {
                if (userId.equals(member.getUserId())) {
                    continue;
                }
                MealRequestResponseEntity response = new MealRequestResponseEntity();
                response.setId(IdGenerator.nextId());
                response.setMealRequestId(entity.getId());
                response.setUserId(member.getUserId());
                response.setDecision("PENDING");
                mealRequestResponseMapper.insert(response);
            }
        }
        return getMealRequest(userId, entity.getId());
    }

    @Override
    public List<MealRequestListItemView> listMealRequests(Long userId, String view) {
        UserEntity user = requireFamilyUser(userId);
        String normalizedView = normalizeView(view);
        List<MealRequestEntity> requests = mealRequestMapper.selectList(new LambdaQueryWrapper<MealRequestEntity>()
            .eq(MealRequestEntity::getFamilyId, user.getCurrentFamilyId())
            .orderByDesc(MealRequestEntity::getRequestedAt));
        List<MealRequestListItemView> views = new ArrayList<MealRequestListItemView>();
        for (MealRequestEntity requestEntity : requests) {
            List<MealRequestResponseEntity> responses = mealRequestResponseMapper.selectList(new LambdaQueryWrapper<MealRequestResponseEntity>()
                .eq(MealRequestResponseEntity::getMealRequestId, requestEntity.getId()));
            MealRequestResponseEntity currentUserResponse = findUserResponse(responses, userId);
            if ("PENDING".equals(normalizedView) && (currentUserResponse == null || !"PENDING".equals(currentUserResponse.getDecision()))) {
                continue;
            }
            if ("MINE".equals(normalizedView) && !userId.equals(requestEntity.getRequesterUserId())) {
                continue;
            }

            List<MealRequestRecipeEntity> recipeRelations = mealRequestRecipeMapper.selectList(new LambdaQueryWrapper<MealRequestRecipeEntity>()
                .eq(MealRequestRecipeEntity::getMealRequestId, requestEntity.getId()));
            List<String> recipeNames = new ArrayList<String>();
            for (MealRequestRecipeEntity relation : recipeRelations) {
                recipeNames.add(relation.getRecipeNameSnapshot());
            }
            UserEntity requester = userMapper.selectById(requestEntity.getRequesterUserId());
            views.add(MealRequestListItemView.builder()
                .id(requestEntity.getId())
                .title(requestEntity.getTitle())
                .status(requestEntity.getStatus())
                .requesterName(requester == null ? "家人" : requester.getNickname())
                .requestedAt(requestEntity.getRequestedAt())
                .pendingCount(countPendingResponses(responses))
                .recipeNames(recipeNames)
                .currentUserPending(currentUserResponse != null && "PENDING".equals(currentUserResponse.getDecision()))
                .build());
        }
        return views;
    }

    @Override
    public MealRequestDetailResponse getMealRequest(Long userId, Long mealRequestId) {
        UserEntity user = requireFamilyUser(userId);
        MealRequestEntity entity = requireMealRequest(user, mealRequestId);
        return buildDetail(entity, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MealRequestDetailResponse respondMealRequest(Long userId, Long mealRequestId, RespondMealRequestRequest request) {
        UserEntity user = requireFamilyUser(userId);
        MealRequestEntity mealRequest = requireMealRequest(user, mealRequestId);
        if (!"PENDING".equals(mealRequest.getStatus())) {
            throw new BizException("当前点餐单已结束");
        }
        MealRequestResponseEntity response = mealRequestResponseMapper.selectOne(new LambdaQueryWrapper<MealRequestResponseEntity>()
            .eq(MealRequestResponseEntity::getMealRequestId, mealRequestId)
            .eq(MealRequestResponseEntity::getUserId, userId)
            .last("limit 1"));
        if (response == null) {
            throw new BizException("当前点餐无需你处理");
        }
        if (!"PENDING".equals(response.getDecision())) {
            throw new BizException("你已经表态过了");
        }
        String decision = normalizeDecision(request.getDecision());
        response.setDecision(decision);
        response.setComment(request.getComment());
        response.setDecidedAt(LocalDateTime.now());
        mealRequestResponseMapper.updateById(response);

        List<MealRequestResponseEntity> responses = mealRequestResponseMapper.selectList(new LambdaQueryWrapper<MealRequestResponseEntity>()
            .eq(MealRequestResponseEntity::getMealRequestId, mealRequestId));
        if ("REJECTED".equals(decision)) {
            mealRequest.setStatus("REJECTED");
            mealRequest.setDecidedAt(LocalDateTime.now());
            mealRequestMapper.updateById(mealRequest);
        } else if (!hasPendingResponse(responses) && !hasRejectedResponse(responses)) {
            mealRequest.setStatus("APPROVED");
            mealRequest.setDecidedAt(LocalDateTime.now());
            mealRequestMapper.updateById(mealRequest);
        }
        return getMealRequest(userId, mealRequestId);
    }

    @Override
    public MealIngredientGapResponse getIngredientGap(Long userId, Long mealRequestId) {
        UserEntity user = requireFamilyUser(userId);
        MealRequestEntity mealRequest = requireMealRequest(user, mealRequestId);
        List<MealRequestRecipeEntity> requestRecipes = mealRequestRecipeMapper.selectList(new LambdaQueryWrapper<MealRequestRecipeEntity>()
            .eq(MealRequestRecipeEntity::getMealRequestId, mealRequestId));

        Map<String, GapBucket> buckets = new LinkedHashMap<String, GapBucket>();
        for (MealRequestRecipeEntity requestRecipe : requestRecipes) {
            List<RecipeIngredientEntity> ingredients = recipeIngredientMapper.selectList(new LambdaQueryWrapper<RecipeIngredientEntity>()
                .eq(RecipeIngredientEntity::getRecipeId, requestRecipe.getRecipeId())
                .orderByAsc(RecipeIngredientEntity::getSortOrder));
            for (RecipeIngredientEntity ingredient : ingredients) {
                accumulateRequiredIngredient(buckets, ingredient, requestRecipe.getServingsMultiplier());
            }
        }

        List<FridgeItemEntity> fridgeItems = fridgeItemMapper.selectList(new LambdaQueryWrapper<FridgeItemEntity>()
            .eq(FridgeItemEntity::getFamilyId, mealRequest.getFamilyId())
            .eq(FridgeItemEntity::getStatus, "ACTIVE"));
        for (GapBucket bucket : buckets.values()) {
            applyFridgeAvailability(bucket, fridgeItems);
        }

        List<MealIngredientGapItemView> items = new ArrayList<MealIngredientGapItemView>();
        for (GapBucket bucket : buckets.values()) {
            BigDecimal availableForDisplay = bucket.dimension == UnitDimension.COUNT
                ? bucket.availableCanonical
                : fromCanonical(bucket.availableCanonical, bucket.displayUnit, bucket.dimension);
            BigDecimal requiredForDisplay = bucket.dimension == UnitDimension.COUNT
                ? bucket.requiredCanonical
                : fromCanonical(bucket.requiredCanonical, bucket.displayUnit, bucket.dimension);
            BigDecimal missingForDisplay = bucket.dimension == UnitDimension.COUNT
                ? maxZero(bucket.requiredCanonical.subtract(bucket.availableCanonical))
                : fromCanonical(maxZero(bucket.requiredCanonical.subtract(bucket.availableCanonical)), bucket.displayUnit, bucket.dimension);
            items.add(MealIngredientGapItemView.builder()
                .name(bucket.name)
                .category(bucket.category)
                .requiredQuantity(scale(requiredForDisplay))
                .requiredUnit(bucket.displayUnit)
                .availableQuantity(scale(availableForDisplay))
                .availableUnit(bucket.displayUnit)
                .missingQuantity(scale(missingForDisplay))
                .status(bucket.resolveStatus())
                .note(bucket.note)
                .build());
        }
        return MealIngredientGapResponse.builder()
            .items(items)
            .build();
    }

    private MealRequestDetailResponse buildDetail(MealRequestEntity entity, Long currentUserId) {
        List<MealRequestRecipeEntity> relations = mealRequestRecipeMapper.selectList(new LambdaQueryWrapper<MealRequestRecipeEntity>()
            .eq(MealRequestRecipeEntity::getMealRequestId, entity.getId()));
        List<MealRequestRecipeView> recipes = new ArrayList<MealRequestRecipeView>();
        for (MealRequestRecipeEntity relation : relations) {
            recipes.add(MealRequestRecipeView.builder()
                .recipeId(relation.getRecipeId())
                .recipeName(relation.getRecipeNameSnapshot())
                .baseServings(scale(relation.getBaseServings()))
                .targetServings(scale(relation.getTargetServings()))
                .servingsMultiplier(scale(relation.getServingsMultiplier()))
                .build());
        }

        List<MealRequestResponseEntity> responseEntities = mealRequestResponseMapper.selectList(new LambdaQueryWrapper<MealRequestResponseEntity>()
            .eq(MealRequestResponseEntity::getMealRequestId, entity.getId())
            .orderByAsc(MealRequestResponseEntity::getCreatedAt));
        List<MealRequestResponseView> responses = new ArrayList<MealRequestResponseView>();
        boolean currentUserPending = false;
        for (MealRequestResponseEntity response : responseEntities) {
            UserEntity member = userMapper.selectById(response.getUserId());
            FamilyMemberEntity familyMember = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMemberEntity>()
                .eq(FamilyMemberEntity::getFamilyId, entity.getFamilyId())
                .eq(FamilyMemberEntity::getUserId, response.getUserId())
                .last("limit 1"));
            if (currentUserId.equals(response.getUserId()) && "PENDING".equals(response.getDecision())) {
                currentUserPending = true;
            }
            responses.add(MealRequestResponseView.builder()
                .userId(response.getUserId())
                .nickname(member == null ? "家人" : member.getNickname())
                .role(familyMember == null ? "MEMBER" : familyMember.getRole())
                .decision(response.getDecision())
                .comment(response.getComment())
                .decidedAt(response.getDecidedAt())
                .currentUser(currentUserId.equals(response.getUserId()))
                .build());
        }
        UserEntity requester = userMapper.selectById(entity.getRequesterUserId());
        return MealRequestDetailResponse.builder()
            .id(entity.getId())
            .title(entity.getTitle())
            .note(entity.getNote())
            .status(entity.getStatus())
            .requesterUserId(entity.getRequesterUserId())
            .requesterName(requester == null ? "家人" : requester.getNickname())
            .requestedAt(entity.getRequestedAt())
            .decidedAt(entity.getDecidedAt())
            .currentUserPending(currentUserPending)
            .recipes(recipes)
            .responses(responses)
            .build();
    }

    private void accumulateRequiredIngredient(Map<String, GapBucket> buckets,
                                              RecipeIngredientEntity ingredient,
                                              BigDecimal multiplier) {
        UnitDimension dimension = detectDimension(ingredient.getUnit());
        String normalizedUnit = normalizeUnit(ingredient.getUnit());
        String key = ingredient.getNameSnapshot() + "||" + (dimension == UnitDimension.COUNT ? normalizedUnit : dimension.name());
        GapBucket bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new GapBucket();
            bucket.name = ingredient.getNameSnapshot();
            bucket.category = ingredient.getCategorySnapshot();
            bucket.displayUnit = ingredient.getUnit();
            bucket.dimension = dimension;
            buckets.put(key, bucket);
        }
        BigDecimal scaledQuantity = ingredient.getQuantity().multiply(multiplier);
        if (dimension == UnitDimension.COUNT) {
            bucket.requiredCanonical = bucket.requiredCanonical.add(scaledQuantity);
        } else {
            bucket.requiredCanonical = bucket.requiredCanonical.add(toCanonical(scaledQuantity, ingredient.getUnit(), dimension));
        }
    }

    private void applyFridgeAvailability(GapBucket bucket, List<FridgeItemEntity> fridgeItems) {
        for (FridgeItemEntity item : fridgeItems) {
            if (!bucket.name.equals(item.getNameSnapshot())) {
                continue;
            }
            UnitDimension itemDimension = detectDimension(item.getUnit());
            if (bucket.dimension != itemDimension) {
                bucket.note = "库存里存在不同量纲的同名食材，请人工确认";
                continue;
            }
            if (bucket.dimension == UnitDimension.COUNT) {
                if (normalizeUnit(bucket.displayUnit).equals(normalizeUnit(item.getUnit()))) {
                    bucket.availableCanonical = bucket.availableCanonical.add(item.getQuantity());
                } else {
                    bucket.note = "库存里存在其他包装单位，暂未自动换算";
                }
            } else {
                bucket.availableCanonical = bucket.availableCanonical.add(toCanonical(item.getQuantity(), item.getUnit(), bucket.dimension));
            }
        }
    }

    private RecipeEntity requireRecipe(Long familyId, Long recipeId) {
        RecipeEntity recipe = recipeMapper.selectById(recipeId);
        if (recipe == null || !familyId.equals(recipe.getFamilyId()) || !"ACTIVE".equals(recipe.getStatus())) {
            throw new BizException("菜谱不存在");
        }
        return recipe;
    }

    private MealRequestEntity requireMealRequest(UserEntity user, Long mealRequestId) {
        MealRequestEntity entity = mealRequestMapper.selectById(mealRequestId);
        if (entity == null || !user.getCurrentFamilyId().equals(entity.getFamilyId())) {
            throw new BizException("点餐单不存在");
        }
        return entity;
    }

    private UserEntity requireFamilyUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        return user;
    }

    private List<FamilyMemberEntity> listActiveMembers(Long familyId) {
        return familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMemberEntity>()
            .eq(FamilyMemberEntity::getFamilyId, familyId)
            .eq(FamilyMemberEntity::getStatus, "ACTIVE"));
    }

    private MealRequestResponseEntity findUserResponse(List<MealRequestResponseEntity> responses, Long userId) {
        for (MealRequestResponseEntity response : responses) {
            if (userId.equals(response.getUserId())) {
                return response;
            }
        }
        return null;
    }

    private int countPendingResponses(List<MealRequestResponseEntity> responses) {
        int count = 0;
        for (MealRequestResponseEntity response : responses) {
            if ("PENDING".equals(response.getDecision())) {
                count++;
            }
        }
        return count;
    }

    private boolean hasPendingResponse(List<MealRequestResponseEntity> responses) {
        for (MealRequestResponseEntity response : responses) {
            if ("PENDING".equals(response.getDecision())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRejectedResponse(List<MealRequestResponseEntity> responses) {
        for (MealRequestResponseEntity response : responses) {
            if ("REJECTED".equals(response.getDecision())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeView(String view) {
        if (!StringUtils.hasText(view)) {
            return "ALL";
        }
        String normalized = view.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(normalized) || "MINE".equals(normalized) || "ALL".equals(normalized)) {
            return normalized;
        }
        throw new BizException("不支持的点餐视图");
    }

    private String normalizeDecision(String decision) {
        if (!StringUtils.hasText(decision)) {
            throw new BizException("请选择同意或不同意");
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if ("APPROVED".equals(normalized) || "REJECTED".equals(normalized)) {
            return normalized;
        }
        throw new BizException("不支持的表态结果");
    }

    private String buildTitle(CreateMealRequestRequest request) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle().trim();
        }
        Set<String> names = new LinkedHashSet<String>();
        for (MealRequestRecipeSelectionRequest selection : request.getRecipes()) {
            RecipeEntity recipe = recipeMapper.selectById(selection.getRecipeId());
            if (recipe != null) {
                names.add(recipe.getName());
            }
        }
        if (names.isEmpty()) {
            return "今晚吃什么";
        }
        StringBuilder builder = new StringBuilder("想吃 ");
        int index = 0;
        for (String name : names) {
            if (index > 0) {
                builder.append("、");
            }
            builder.append(name);
            index++;
        }
        return builder.toString();
    }

    private UnitDimension detectDimension(String unit) {
        String normalized = normalizeUnit(unit);
        if ("mg".equals(normalized) || "g".equals(normalized) || "kg".equals(normalized) || "斤".equals(normalized)) {
            return UnitDimension.WEIGHT;
        }
        if ("ml".equals(normalized) || "l".equals(normalized) || "升".equals(normalized)) {
            return UnitDimension.VOLUME;
        }
        return UnitDimension.COUNT;
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal toCanonical(BigDecimal quantity, String unit, UnitDimension dimension) {
        String normalized = normalizeUnit(unit);
        if (dimension == UnitDimension.WEIGHT) {
            if ("mg".equals(normalized)) {
                return quantity.divide(THOUSAND, 6, RoundingMode.HALF_UP);
            }
            if ("g".equals(normalized)) {
                return quantity;
            }
            if ("kg".equals(normalized)) {
                return quantity.multiply(THOUSAND);
            }
            if ("斤".equals(normalized)) {
                return quantity.multiply(WEIGHT_JIN_IN_GRAMS);
            }
        }
        if (dimension == UnitDimension.VOLUME) {
            if ("ml".equals(normalized)) {
                return quantity;
            }
            if ("l".equals(normalized) || "升".equals(normalized)) {
                return quantity.multiply(THOUSAND);
            }
        }
        return quantity;
    }

    private BigDecimal fromCanonical(BigDecimal quantity, String unit, UnitDimension dimension) {
        String normalized = normalizeUnit(unit);
        if (dimension == UnitDimension.WEIGHT) {
            if ("mg".equals(normalized)) {
                return quantity.multiply(THOUSAND);
            }
            if ("g".equals(normalized)) {
                return quantity;
            }
            if ("kg".equals(normalized)) {
                return quantity.divide(THOUSAND, 2, RoundingMode.HALF_UP);
            }
            if ("斤".equals(normalized)) {
                return quantity.divide(WEIGHT_JIN_IN_GRAMS, 2, RoundingMode.HALF_UP);
            }
        }
        if (dimension == UnitDimension.VOLUME) {
            if ("ml".equals(normalized)) {
                return quantity;
            }
            if ("l".equals(normalized) || "升".equals(normalized)) {
                return quantity.divide(THOUSAND, 2, RoundingMode.HALF_UP);
            }
        }
        return quantity;
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    private enum UnitDimension {
        WEIGHT,
        VOLUME,
        COUNT
    }

    private static class GapBucket {
        private String name;
        private String category;
        private String displayUnit;
        private UnitDimension dimension;
        private BigDecimal requiredCanonical = BigDecimal.ZERO;
        private BigDecimal availableCanonical = BigDecimal.ZERO;
        private String note;

        private String resolveStatus() {
            if (availableCanonical.compareTo(BigDecimal.ZERO) <= 0) {
                return "MISSING";
            }
            if (availableCanonical.compareTo(requiredCanonical) >= 0) {
                return "SUFFICIENT";
            }
            return "INSUFFICIENT";
        }
    }
}
