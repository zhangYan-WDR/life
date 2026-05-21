package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.config.AppProperties;
import com.life.server.dto.request.RecipeIngredientRequest;
import com.life.server.dto.request.UpsertRecipeRequest;
import com.life.server.dto.response.RecipeCoverUploadPolicyResponse;
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RecipeServiceImpl implements RecipeService {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s]+)|(www\\.[^\\s]+)");

    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper recipeIngredientMapper;
    private final SystemIngredientMapper systemIngredientMapper;
    private final FamilyIngredientMapper familyIngredientMapper;
    private final UserMapper userMapper;
    private final AppProperties appProperties;

    public RecipeServiceImpl(RecipeMapper recipeMapper,
                             RecipeIngredientMapper recipeIngredientMapper,
                             SystemIngredientMapper systemIngredientMapper,
                             FamilyIngredientMapper familyIngredientMapper,
                             UserMapper userMapper,
                             AppProperties appProperties) {
        this.recipeMapper = recipeMapper;
        this.recipeIngredientMapper = recipeIngredientMapper;
        this.systemIngredientMapper = systemIngredientMapper;
        this.familyIngredientMapper = familyIngredientMapper;
        this.userMapper = userMapper;
        this.appProperties = appProperties;
    }

    @Override
    public List<RecipeListItemView> listRecipes(Long userId) {
        UserEntity user = requireFamilyUser(userId);
        AppProperties.Oss oss = appProperties.getOss();
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
                .coverUrl(resolveReadableCoverUrl(recipe, oss))
                .updatedAt(recipe.getUpdatedAt())
                .build());
        }
        return views;
    }

    @Override
    public RecipeDetailResponse getRecipe(Long userId, Long recipeId) {
        UserEntity user = requireFamilyUser(userId);
        RecipeEntity recipe = requireRecipe(user, recipeId);
        return toDetail(recipe, appProperties.getOss());
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
        entity.setInstructions(normalizeText(request.getInstructions()));
        entity.setNote(normalizeText(request.getNote()));
        entity.setCoverUrl(normalizeText(request.getCoverUrl()));
        entity.setCoverObjectKey(normalizeText(request.getCoverObjectKey()));
        entity.setReferenceUrl(normalizeReferenceUrl(request.getReferenceUrl()));
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
        entity.setInstructions(normalizeText(request.getInstructions()));
        entity.setNote(normalizeText(request.getNote()));
        entity.setCoverUrl(normalizeText(request.getCoverUrl()));
        entity.setCoverObjectKey(normalizeText(request.getCoverObjectKey()));
        entity.setReferenceUrl(normalizeReferenceUrl(request.getReferenceUrl()));
        recipeMapper.updateById(entity);
        replaceIngredients(entity.getId(), user, request.getIngredients());
        return getRecipe(userId, entity.getId());
    }

    @Override
    public RecipeCoverUploadPolicyResponse createCoverUploadPolicy(Long userId, Long recipeId, String originalFileName) {
        UserEntity user = requireFamilyUser(userId);
        RecipeEntity recipe = requireRecipe(user, recipeId);
        AppProperties.Oss oss = requireOssConfig();
        String extension = resolveImageExtension(originalFileName);
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String objectKey = buildObjectKey(oss.getCoverDir(), datePart, recipe.getId(), extension);
        Instant expireAt = Instant.now().plusSeconds(resolvePolicyExpireSeconds(oss));
        String policy = buildPolicy(oss.getBucket(), objectKey, resolveMaxSizeBytes(oss), expireAt);
        String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        String signature = signPolicy(encodedPolicy, oss.getAccessKeySecret());

        return RecipeCoverUploadPolicyResponse.builder()
            .uploadHost(buildUploadHost(oss.getBucket(), oss.getEndpoint()))
            .accessKeyId(oss.getAccessKeyId())
            .policy(encodedPolicy)
            .signature(signature)
            .objectKey(objectKey)
            .publicUrl(buildPublicUrl(oss.getPublicDomain(), objectKey))
            .successActionStatus("200")
            .expireAt(expireAt.toEpochMilli())
            .maxSizeBytes(resolveMaxSizeBytes(oss))
            .build();
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
        return toDetail(recipe, appProperties.getOss());
    }

    private RecipeDetailResponse toDetail(RecipeEntity recipe, AppProperties.Oss oss) {
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
            .coverUrl(resolveReadableCoverUrl(recipe, oss))
            .coverObjectKey(recipe.getCoverObjectKey())
            .referenceUrl(recipe.getReferenceUrl())
            .status(recipe.getStatus())
            .updatedAt(recipe.getUpdatedAt())
            .ingredients(views)
            .build();
    }

    private String resolveReadableCoverUrl(RecipeEntity recipe, AppProperties.Oss oss) {
        String objectKey = normalizeText(recipe.getCoverObjectKey());
        if (!StringUtils.hasText(objectKey)) {
            return normalizeText(recipe.getCoverUrl());
        }
        if (oss == null || !Boolean.TRUE.equals(oss.getEnabled())) {
            return normalizeText(recipe.getCoverUrl());
        }
        if (!StringUtils.hasText(oss.getBucket())
            || !StringUtils.hasText(oss.getEndpoint())
            || !StringUtils.hasText(oss.getAccessKeyId())
            || !StringUtils.hasText(oss.getAccessKeySecret())) {
            return normalizeText(recipe.getCoverUrl());
        }
        return buildSignedGetUrl(oss, objectKey);
    }

    private AppProperties.Oss requireOssConfig() {
        AppProperties.Oss oss = appProperties.getOss();
        if (oss == null || !Boolean.TRUE.equals(oss.getEnabled())) {
            throw new BizException("封面上传暂未开启");
        }
        if (!StringUtils.hasText(oss.getEndpoint())
            || !StringUtils.hasText(oss.getBucket())
            || !StringUtils.hasText(oss.getPublicDomain())
            || !StringUtils.hasText(oss.getAccessKeyId())
            || !StringUtils.hasText(oss.getAccessKeySecret())) {
            throw new BizException("OSS 配置不完整");
        }
        return oss;
    }

    private String buildObjectKey(String coverDir, String datePart, Long recipeId, String extension) {
        String normalizedDir = normalizeDir(coverDir);
        String randomPart = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
        return normalizedDir + "/" + datePart + "/" + datePart + "_" + recipeId + "_" + randomPart + "." + extension;
    }

    private String buildPolicy(String bucket, String objectKey, long maxSizeBytes, Instant expireAt) {
        return "{\"expiration\":\"" + DateTimeFormatter.ISO_INSTANT.format(expireAt) + "\","
            + "\"conditions\":["
            + "{\"bucket\":\"" + bucket + "\"},"
            + "{\"key\":\"" + objectKey + "\"},"
            + "{\"success_action_status\":\"200\"},"
            + "[\"content-length-range\",0," + maxSizeBytes + "]"
            + "]}";
    }

    private String signPolicy(String encodedPolicy, String accessKeySecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(encodedPolicy.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new BizException("生成上传签名失败");
        }
    }

    private long resolveMaxSizeBytes(AppProperties.Oss oss) {
        return oss.getMaxSizeBytes() == null || oss.getMaxSizeBytes() <= 0 ? 2L * 1024 * 1024 : oss.getMaxSizeBytes();
    }

    private long resolvePolicyExpireSeconds(AppProperties.Oss oss) {
        return oss.getPolicyExpireSeconds() == null || oss.getPolicyExpireSeconds() <= 0 ? 300 : oss.getPolicyExpireSeconds();
    }

    private long resolveReadUrlExpireSeconds(AppProperties.Oss oss) {
        return oss.getReadUrlExpireSeconds() == null || oss.getReadUrlExpireSeconds() <= 0 ? 1800 : oss.getReadUrlExpireSeconds();
    }

    private String normalizeDir(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return "recipe-covers";
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeUrl(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }

    private String buildPublicUrl(String publicDomain, String objectKey) {
        return normalizeUrl(publicDomain) + "/" + objectKey;
    }

    private String buildSignedGetUrl(AppProperties.Oss oss, String objectKey) {
        long expires = Instant.now().getEpochSecond() + resolveReadUrlExpireSeconds(oss);
        String canonicalizedResource = "/" + oss.getBucket() + "/" + objectKey;
        String stringToSign = "GET\n\n\n" + expires + "\n" + canonicalizedResource;
        String signature = signPolicy(stringToSign, oss.getAccessKeySecret());
        return buildUploadHost(oss.getBucket(), oss.getEndpoint())
            + "/" + encodeObjectKey(objectKey)
            + "?OSSAccessKeyId=" + urlEncode(oss.getAccessKeyId())
            + "&Expires=" + expires
            + "&Signature=" + urlEncode(signature);
    }

    private String buildUploadHost(String bucket, String endpoint) {
        String normalizedEndpoint = normalizeText(endpoint);
        if (!StringUtils.hasText(normalizedEndpoint)) {
            throw new BizException("OSS Endpoint 未配置");
        }
        if (normalizedEndpoint.startsWith("http://") || normalizedEndpoint.startsWith("https://")) {
            String protocol = normalizedEndpoint.startsWith("https://") ? "https://" : "http://";
            String host = normalizedEndpoint.substring(protocol.length());
            return protocol + buildBucketHost(bucket, host);
        }
        return "https://" + buildBucketHost(bucket, normalizedEndpoint);
    }

    private String buildBucketHost(String bucket, String host) {
        if (host.startsWith(bucket + ".")) {
            return host;
        }
        return bucket + "." + host;
    }

    private String encodeObjectKey(String objectKey) {
        try {
            return URLEncoder.encode(objectKey, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("%2F", "/");
        } catch (UnsupportedEncodingException ex) {
            throw new BizException("生成封面地址失败");
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            throw new BizException("生成封面地址失败");
        }
    }

    private String resolveImageExtension(String originalFileName) {
        String normalized = normalizeText(originalFileName);
        if (!StringUtils.hasText(normalized) || !normalized.contains(".")) {
            throw new BizException("封面图片格式不支持");
        }
        String extension = normalized.substring(normalized.lastIndexOf('.') + 1).toLowerCase();
        if ("jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension) || "webp".equals(extension) || "gif".equals(extension)) {
            return extension;
        }
        throw new BizException("封面仅支持 jpg、png、webp、gif");
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeReferenceUrl(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw new BizException("参考链接里没有找到可用地址");
        }
        String url = trimTrailingPunctuation(matcher.group());
        if (url.startsWith("www.")) {
            url = "https://" + url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BizException("参考链接格式不支持");
        }
        return url;
    }

    private String trimTrailingPunctuation(String value) {
        String normalized = value;
        while (StringUtils.hasText(normalized)
            && ".,;:!?)]}>\u3002\uff0c\uff1b\uff1a\uff01\uff1f\u3001".indexOf(normalized.charAt(normalized.length() - 1)) >= 0) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
