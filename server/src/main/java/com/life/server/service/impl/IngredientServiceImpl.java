package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.life.server.common.BizException;
import com.life.server.dto.request.CreateFamilyIngredientRequest;
import com.life.server.dto.response.IngredientCatalogResponse;
import com.life.server.dto.response.IngredientCategoryNode;
import com.life.server.dto.response.IngredientCategoryTreeResponse;
import com.life.server.dto.response.IngredientGroupView;
import com.life.server.dto.response.IngredientSearchResponse;
import com.life.server.dto.response.IngredientSubCategoryNode;
import com.life.server.dto.response.IngredientView;
import com.life.server.entity.FamilyIngredientEntity;
import com.life.server.entity.SystemIngredientEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyIngredientMapper;
import com.life.server.mapper.SystemIngredientMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.IngredientService;
import com.life.server.util.IdGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientServiceImpl implements IngredientService {

    private static final String CACHE_KEY_SYSTEM_CATEGORY_TREE = "ingredients:system-category-tree:v1";
    private static final String CACHE_KEY_PREFIX_BY_CAT = "ingredients:by-cat:v1:";
    private static final long CACHE_TTL_SECONDS = 6 * 3600L;

    private final SystemIngredientMapper systemIngredientMapper;
    private final FamilyIngredientMapper familyIngredientMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public IngredientServiceImpl(SystemIngredientMapper systemIngredientMapper,
                                 FamilyIngredientMapper familyIngredientMapper,
                                 UserMapper userMapper,
                                 StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper) {
        this.systemIngredientMapper = systemIngredientMapper;
        this.familyIngredientMapper = familyIngredientMapper;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public IngredientCatalogResponse getCatalog(Long userId, boolean includeSystem) {
        UserEntity user = requireFamilyUser(userId);
        List<FamilyIngredientEntity> familyIngredients = familyIngredientMapper.selectList(new LambdaQueryWrapper<FamilyIngredientEntity>()
            .eq(FamilyIngredientEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FamilyIngredientEntity::getEnabled, true)
            .orderByDesc(FamilyIngredientEntity::getCreatedAt));

        List<IngredientView> systemViews = new ArrayList<IngredientView>();
        if (includeSystem) {
            List<SystemIngredientEntity> systemIngredients = systemIngredientMapper.selectList(new LambdaQueryWrapper<SystemIngredientEntity>()
                .eq(SystemIngredientEntity::getEnabled, true)
                .orderByAsc(SystemIngredientEntity::getSortOrder));
            for (SystemIngredientEntity item : systemIngredients) {
                systemViews.add(toSystemView(item));
            }
        }

        List<IngredientView> familyViews = new ArrayList<IngredientView>();
        for (FamilyIngredientEntity item : familyIngredients) {
            familyViews.add(toFamilyView(item));
        }
        return IngredientCatalogResponse.builder()
            .systemIngredients(systemViews)
            .familyIngredients(familyViews)
            .build();
    }

    @Override
    public List<IngredientGroupView> getSystemGroups(int previewSize) {
        int safePreviewSize = Math.min(Math.max(previewSize, 1), 24);
        List<SystemIngredientEntity> systemIngredients = systemIngredientMapper.selectList(new LambdaQueryWrapper<SystemIngredientEntity>()
            .eq(SystemIngredientEntity::getEnabled, true)
            .orderByAsc(SystemIngredientEntity::getSortOrder));

        Map<String, IngredientGroupAccumulator> groupMap = new LinkedHashMap<String, IngredientGroupAccumulator>();
        for (SystemIngredientEntity item : systemIngredients) {
            String category = item.getCategory() == null ? "未分类" : item.getCategory();
            String secondaryCategory = item.getSecondaryCategory() == null ? "其他" : item.getSecondaryCategory();
            String key = category + "__" + secondaryCategory;
            IngredientGroupAccumulator accumulator = groupMap.get(key);
            if (accumulator == null) {
                accumulator = new IngredientGroupAccumulator(category, secondaryCategory);
                groupMap.put(key, accumulator);
            }
            accumulator.count++;
            if (accumulator.previewItems.size() < safePreviewSize) {
                accumulator.previewItems.add(toSystemView(item));
            }
        }

        List<IngredientGroupView> groups = new ArrayList<IngredientGroupView>();
        for (Map.Entry<String, IngredientGroupAccumulator> entry : groupMap.entrySet()) {
            IngredientGroupAccumulator accumulator = entry.getValue();
            groups.add(IngredientGroupView.builder()
                .key(entry.getKey())
                .category(accumulator.category)
                .secondaryCategory(accumulator.secondaryCategory)
                .count(accumulator.count)
                .previewItems(accumulator.previewItems)
                .build());
        }
        return groups;
    }

    @Override
    public IngredientSearchResponse searchCatalog(Long userId, String keyword, int limit) {
        UserEntity user = requireFamilyUser(userId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        int fetchLimit = Math.min(Math.max(safeLimit * 4, 50), 200);

        List<IngredientView> items = new ArrayList<IngredientView>();
        if (!normalizedKeyword.isEmpty()) {
            List<FamilyIngredientEntity> familyIngredients = familyIngredientMapper.selectList(new LambdaQueryWrapper<FamilyIngredientEntity>()
                .eq(FamilyIngredientEntity::getFamilyId, user.getCurrentFamilyId())
                .eq(FamilyIngredientEntity::getEnabled, true)
                .and(wrapper -> wrapper
                    .like(FamilyIngredientEntity::getName, normalizedKeyword)
                    .or()
                    .like(FamilyIngredientEntity::getCategory, normalizedKeyword)
                    .or()
                    .like(FamilyIngredientEntity::getSecondaryCategory, normalizedKeyword))
                .orderByDesc(FamilyIngredientEntity::getCreatedAt)
                .last("LIMIT " + fetchLimit));
            for (FamilyIngredientEntity item : familyIngredients) {
                items.add(toFamilyView(item));
            }

            int remaining = Math.max(fetchLimit - items.size(), 0);
            if (remaining > 0) {
                List<SystemIngredientEntity> systemIngredients = systemIngredientMapper.selectList(new LambdaQueryWrapper<SystemIngredientEntity>()
                    .eq(SystemIngredientEntity::getEnabled, true)
                    .and(wrapper -> wrapper
                        .like(SystemIngredientEntity::getName, normalizedKeyword)
                        .or()
                        .like(SystemIngredientEntity::getCategory, normalizedKeyword)
                        .or()
                        .like(SystemIngredientEntity::getSecondaryCategory, normalizedKeyword))
                    .orderByAsc(SystemIngredientEntity::getSortOrder)
                    .last("LIMIT " + remaining));
                for (SystemIngredientEntity item : systemIngredients) {
                    items.add(toSystemView(item));
                }
            }
            items.sort((left, right) -> {
                int scoreCompare = Integer.compare(matchScore(left, normalizedKeyword), matchScore(right, normalizedKeyword));
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                int sourceCompare = Integer.compare(sourcePriority(left.getSourceType()), sourcePriority(right.getSourceType()));
                if (sourceCompare != 0) {
                    return sourceCompare;
                }
                int lengthCompare = Integer.compare(safeText(left.getName()).length(), safeText(right.getName()).length());
                if (lengthCompare != 0) {
                    return lengthCompare;
                }
                return safeText(left.getName()).compareTo(safeText(right.getName()));
            });
            if (items.size() > safeLimit) {
                items = new ArrayList<IngredientView>(items.subList(0, safeLimit));
            }
        } else {
            List<FamilyIngredientEntity> familyIngredients = familyIngredientMapper.selectList(new LambdaQueryWrapper<FamilyIngredientEntity>()
                .eq(FamilyIngredientEntity::getFamilyId, user.getCurrentFamilyId())
                .eq(FamilyIngredientEntity::getEnabled, true)
                .orderByDesc(FamilyIngredientEntity::getCreatedAt)
                .last("LIMIT " + fetchLimit));
            for (FamilyIngredientEntity item : familyIngredients) {
                items.add(toFamilyView(item));
            }
            int remaining = Math.max(fetchLimit - items.size(), 0);
            if (remaining > 0) {
                List<SystemIngredientEntity> systemIngredients = systemIngredientMapper.selectList(new LambdaQueryWrapper<SystemIngredientEntity>()
                    .eq(SystemIngredientEntity::getEnabled, true)
                    .orderByAsc(SystemIngredientEntity::getSortOrder)
                    .last("LIMIT " + remaining));
                for (SystemIngredientEntity item : systemIngredients) {
                    items.add(toSystemView(item));
                }
            }
            if (items.size() > safeLimit) {
                items = new ArrayList<IngredientView>(items.subList(0, safeLimit));
            }
        }

        return IngredientSearchResponse.builder()
            .items(items)
            .build();
    }

    @Override
    public IngredientCategoryTreeResponse getCategoryTree(Long userId) {
        UserEntity user = requireFamilyUser(userId);

        List<IngredientCategoryNode> systemCategories = readSystemCategoryTreeFromCache();
        if (systemCategories == null) {
            systemCategories = buildSystemCategoryTree();
            writeSystemCategoryTreeToCache(systemCategories);
        }

        List<FamilyIngredientEntity> familyIngredients = familyIngredientMapper.selectList(new LambdaQueryWrapper<FamilyIngredientEntity>()
            .eq(FamilyIngredientEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FamilyIngredientEntity::getEnabled, true)
            .orderByDesc(FamilyIngredientEntity::getCreatedAt));
        List<IngredientView> familyViews = new ArrayList<IngredientView>();
        for (FamilyIngredientEntity item : familyIngredients) {
            familyViews.add(toFamilyView(item));
        }

        return IngredientCategoryTreeResponse.builder()
            .systemCategories(systemCategories)
            .familyIngredients(familyViews)
            .build();
    }

    @Override
    public List<IngredientView> listByCategory(Long userId, String category, String secondaryCategory, int offset, int limit) {
        requireFamilyUser(userId);
        if (category == null || category.trim().isEmpty()) {
            return new ArrayList<IngredientView>();
        }
        String normalizedCategory = category.trim();
        String normalizedSub = secondaryCategory == null ? "" : secondaryCategory.trim();
        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.min(Math.max(limit, 1), 200);

        List<IngredientView> all = readByCategoryFromCache(normalizedCategory, normalizedSub);
        if (all == null) {
            LambdaQueryWrapper<SystemIngredientEntity> wrapper = new LambdaQueryWrapper<SystemIngredientEntity>()
                .eq(SystemIngredientEntity::getEnabled, true)
                .eq(SystemIngredientEntity::getCategory, normalizedCategory)
                .orderByAsc(SystemIngredientEntity::getSortOrder);
            if (normalizedSub.isEmpty()) {
                wrapper.and(w -> w.isNull(SystemIngredientEntity::getSecondaryCategory)
                    .or().eq(SystemIngredientEntity::getSecondaryCategory, ""));
            } else {
                wrapper.eq(SystemIngredientEntity::getSecondaryCategory, normalizedSub);
            }
            List<SystemIngredientEntity> entities = systemIngredientMapper.selectList(wrapper);
            all = new ArrayList<IngredientView>();
            for (SystemIngredientEntity item : entities) {
                all.add(toSystemView(item));
            }
            writeByCategoryToCache(normalizedCategory, normalizedSub, all);
        }

        if (safeOffset >= all.size()) {
            return new ArrayList<IngredientView>();
        }
        int end = Math.min(safeOffset + safeLimit, all.size());
        return new ArrayList<IngredientView>(all.subList(safeOffset, end));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IngredientView createFamilyIngredient(Long userId, CreateFamilyIngredientRequest request) {
        UserEntity user = requireFamilyUser(userId);
        Long count = familyIngredientMapper.selectCount(new LambdaQueryWrapper<FamilyIngredientEntity>()
            .eq(FamilyIngredientEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FamilyIngredientEntity::getName, request.getName()));
        if (count != null && count > 0) {
            throw new BizException("该家庭食材已存在");
        }
        FamilyIngredientEntity entity = new FamilyIngredientEntity();
        entity.setId(IdGenerator.nextId());
        entity.setFamilyId(user.getCurrentFamilyId());
        entity.setName(request.getName());
        entity.setCategory(request.getCategory());
        entity.setSecondaryCategory(request.getSecondaryCategory());
        entity.setDefaultUnit(request.getDefaultUnit());
        entity.setEnabled(true);
        familyIngredientMapper.insert(entity);
        return toFamilyView(entity);
    }

    private UserEntity requireFamilyUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        return user;
    }

    private IngredientView toSystemView(SystemIngredientEntity item) {
        return IngredientView.builder()
            .id(item.getId())
            .sourceType("SYSTEM")
            .name(item.getName())
            .category(item.getCategory())
            .secondaryCategory(item.getSecondaryCategory())
            .defaultUnit(item.getDefaultUnit())
            .build();
    }

    private IngredientView toFamilyView(FamilyIngredientEntity item) {
        return IngredientView.builder()
            .id(item.getId())
            .sourceType("FAMILY")
            .name(item.getName())
            .category(item.getCategory())
            .secondaryCategory(item.getSecondaryCategory())
            .defaultUnit(item.getDefaultUnit())
            .build();
    }

    private List<IngredientCategoryNode> readSystemCategoryTreeFromCache() {
        try {
            String json = stringRedisTemplate.opsForValue().get(CACHE_KEY_SYSTEM_CATEGORY_TREE);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<IngredientCategoryNode>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeSystemCategoryTreeToCache(List<IngredientCategoryNode> nodes) {
        try {
            String json = objectMapper.writeValueAsString(nodes);
            stringRedisTemplate.opsForValue().set(CACHE_KEY_SYSTEM_CATEGORY_TREE, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            // best-effort cache; ignore serialization failures
        }
    }

    private List<IngredientCategoryNode> buildSystemCategoryTree() {
        List<SystemIngredientEntity> systemIngredients = systemIngredientMapper.selectList(new LambdaQueryWrapper<SystemIngredientEntity>()
            .eq(SystemIngredientEntity::getEnabled, true)
            .orderByAsc(SystemIngredientEntity::getSortOrder));

        Map<String, Map<String, Integer>> categoryMap = new LinkedHashMap<String, Map<String, Integer>>();
        Map<String, Integer> categoryTotals = new LinkedHashMap<String, Integer>();
        for (SystemIngredientEntity item : systemIngredients) {
            String category = item.getCategory() == null ? "未分类" : item.getCategory();
            String secondary = item.getSecondaryCategory() == null ? "" : item.getSecondaryCategory();
            Map<String, Integer> subMap = categoryMap.get(category);
            if (subMap == null) {
                subMap = new LinkedHashMap<String, Integer>();
                categoryMap.put(category, subMap);
            }
            Integer subCount = subMap.get(secondary);
            subMap.put(secondary, (subCount == null ? 0 : subCount) + 1);
            Integer total = categoryTotals.get(category);
            categoryTotals.put(category, (total == null ? 0 : total) + 1);
        }

        List<IngredientCategoryNode> nodes = new ArrayList<IngredientCategoryNode>();
        for (Map.Entry<String, Map<String, Integer>> entry : categoryMap.entrySet()) {
            List<IngredientSubCategoryNode> subs = new ArrayList<IngredientSubCategoryNode>();
            for (Map.Entry<String, Integer> subEntry : entry.getValue().entrySet()) {
                subs.add(IngredientSubCategoryNode.builder()
                    .secondaryCategory(subEntry.getKey())
                    .count(subEntry.getValue())
                    .build());
            }
            nodes.add(IngredientCategoryNode.builder()
                .category(entry.getKey())
                .totalCount(categoryTotals.get(entry.getKey()))
                .subCategories(subs)
                .build());
        }
        return nodes;
    }

    private List<IngredientView> readByCategoryFromCache(String category, String secondaryCategory) {
        try {
            String key = CACHE_KEY_PREFIX_BY_CAT + category + ":" + secondaryCategory;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<IngredientView>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeByCategoryToCache(String category, String secondaryCategory, List<IngredientView> views) {
        try {
            String key = CACHE_KEY_PREFIX_BY_CAT + category + ":" + secondaryCategory;
            String json = objectMapper.writeValueAsString(views);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            // best-effort cache; ignore serialization failures
        }
    }

    private int matchScore(IngredientView item, String keyword) {
        String normalizedKeyword = compactText(keyword);
        String name = compactText(item.getName());
        String category = compactText(item.getCategory());
        String secondaryCategory = compactText(item.getSecondaryCategory());
        if (name.equals(normalizedKeyword)) {
            return 0;
        }
        if (name.startsWith(normalizedKeyword)) {
            return 1;
        }
        if (name.contains(normalizedKeyword)) {
            return 2;
        }
        if (secondaryCategory.equals(normalizedKeyword) || category.equals(normalizedKeyword)) {
            return 3;
        }
        if (secondaryCategory.contains(normalizedKeyword) || category.contains(normalizedKeyword)) {
            return 4;
        }
        return 5;
    }

    private int sourcePriority(String sourceType) {
        return "FAMILY".equalsIgnoreCase(sourceType) ? 0 : 1;
    }

    private String compactText(String value) {
        return safeText(value).replace(" ", "").toLowerCase();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private static class IngredientGroupAccumulator {
        private final String category;
        private final String secondaryCategory;
        private int count;
        private final List<IngredientView> previewItems = new ArrayList<IngredientView>();

        private IngredientGroupAccumulator(String category, String secondaryCategory) {
            this.category = category;
            this.secondaryCategory = secondaryCategory;
        }
    }
}
