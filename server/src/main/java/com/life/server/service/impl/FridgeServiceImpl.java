package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.config.AppProperties;
import com.life.server.dto.request.ConsumeFridgeItemRequest;
import com.life.server.dto.request.UpsertFridgeItemRequest;
import com.life.server.dto.response.FridgeItemView;
import com.life.server.dto.response.FridgeReminderSummaryResponse;
import com.life.server.entity.FamilyIngredientEntity;
import com.life.server.entity.FridgeChangeLogEntity;
import com.life.server.entity.FridgeItemEntity;
import com.life.server.entity.SystemIngredientEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyIngredientMapper;
import com.life.server.mapper.FridgeChangeLogMapper;
import com.life.server.mapper.FridgeItemMapper;
import com.life.server.mapper.SystemIngredientMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.FridgeService;
import com.life.server.util.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FridgeServiceImpl implements FridgeService {

    private final FridgeItemMapper fridgeItemMapper;
    private final FridgeChangeLogMapper fridgeChangeLogMapper;
    private final UserMapper userMapper;
    private final SystemIngredientMapper systemIngredientMapper;
    private final FamilyIngredientMapper familyIngredientMapper;
    private final AppProperties appProperties;

    public FridgeServiceImpl(FridgeItemMapper fridgeItemMapper,
                             FridgeChangeLogMapper fridgeChangeLogMapper,
                             UserMapper userMapper,
                             SystemIngredientMapper systemIngredientMapper,
                             FamilyIngredientMapper familyIngredientMapper,
                             AppProperties appProperties) {
        this.fridgeItemMapper = fridgeItemMapper;
        this.fridgeChangeLogMapper = fridgeChangeLogMapper;
        this.userMapper = userMapper;
        this.systemIngredientMapper = systemIngredientMapper;
        this.familyIngredientMapper = familyIngredientMapper;
        this.appProperties = appProperties;
    }

    @Override
    public List<FridgeItemView> listItems(Long userId, String status) {
        UserEntity user = requireFamilyUser(userId);
        List<FridgeItemEntity> items = fridgeItemMapper.selectList(new LambdaQueryWrapper<FridgeItemEntity>()
            .eq(FridgeItemEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FridgeItemEntity::getStatus, "ACTIVE")
            .orderByAsc(FridgeItemEntity::getExpiresAt)
            .orderByDesc(FridgeItemEntity::getCreatedAt));
        List<FridgeItemView> views = new ArrayList<FridgeItemView>();
        for (FridgeItemEntity item : items) {
            String reminderState = getReminderState(item.getExpiresAt());
            if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status) && !status.equalsIgnoreCase(reminderState)) {
                continue;
            }
            views.add(toView(item, reminderState));
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FridgeItemView saveItem(Long userId, UpsertFridgeItemRequest request) {
        UserEntity user = requireFamilyUser(userId);
        IngredientSnapshot snapshot = resolveIngredientSnapshot(user, request);
        FridgeItemEntity item = request.getId() == null ? new FridgeItemEntity() : requireOwnedItem(user, request.getId());
        if (request.getId() == null) {
            item.setId(IdGenerator.nextId());
            item.setFamilyId(user.getCurrentFamilyId());
            item.setCreatedBy(userId);
        }
        item.setSourceType(request.getSourceType());
        item.setSourceId(request.getSourceId());
        item.setNameSnapshot(snapshot.name);
        item.setCategorySnapshot(snapshot.category);
        if (request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("库存数量必须大于0");
        }
        item.setQuantity(request.getQuantity());
        item.setUnit(request.getUnit());
        item.setProducedAt(request.getProducedAt());
        item.setExpiresAt(request.getExpiresAt());
        item.setLocation(request.getLocation());
        item.setNote(request.getNote());
        item.setStatus("ACTIVE");
        if (request.getId() == null) {
            fridgeItemMapper.insert(item);
            insertLog(item.getId(), item.getFamilyId(), "CREATE", item.getQuantity(), userId, "新增库存");
        } else {
            fridgeItemMapper.updateById(item);
            insertLog(item.getId(), item.getFamilyId(), "UPDATE", null, userId, "编辑库存");
        }
        return toView(item, getReminderState(item.getExpiresAt()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FridgeItemView consumeItem(Long userId, Long itemId, ConsumeFridgeItemRequest request) {
        UserEntity user = requireFamilyUser(userId);
        FridgeItemEntity item = requireOwnedItem(user, itemId);
        if (request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("消耗数量必须大于0");
        }
        if (item.getQuantity().compareTo(request.getQuantity()) < 0) {
            throw new BizException("库存不足");
        }
        item.setQuantity(item.getQuantity().subtract(request.getQuantity()));
        if (item.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            item.setStatus("CONSUMED");
        }
        fridgeItemMapper.updateById(item);
        insertLog(item.getId(), item.getFamilyId(), "CONSUME", request.getQuantity().negate(), userId, request.getNote());
        return toView(item, getReminderState(item.getExpiresAt()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FridgeItemView discardItem(Long userId, Long itemId) {
        UserEntity user = requireFamilyUser(userId);
        FridgeItemEntity item = requireOwnedItem(user, itemId);
        item.setStatus("DISCARDED");
        fridgeItemMapper.updateById(item);
        insertLog(item.getId(), item.getFamilyId(), "DISCARD", item.getQuantity().negate(), userId, "丢弃库存");
        return toView(item, getReminderState(item.getExpiresAt()));
    }

    @Override
    public FridgeReminderSummaryResponse getReminderSummary(Long userId) {
        UserEntity user = requireFamilyUser(userId);
        List<FridgeItemEntity> items = fridgeItemMapper.selectList(new LambdaQueryWrapper<FridgeItemEntity>()
            .eq(FridgeItemEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FridgeItemEntity::getStatus, "ACTIVE"));
        long expiringSoon = 0;
        long expired = 0;
        for (FridgeItemEntity item : items) {
            String reminderState = getReminderState(item.getExpiresAt());
            if ("EXPIRING".equals(reminderState)) {
                expiringSoon++;
            }
            if ("EXPIRED".equals(reminderState)) {
                expired++;
            }
        }
        return FridgeReminderSummaryResponse.builder()
            .total(items.size())
            .expiringSoon(expiringSoon)
            .expired(expired)
            .build();
    }

    private IngredientSnapshot resolveIngredientSnapshot(UserEntity user, UpsertFridgeItemRequest request) {
        if ("CUSTOM".equalsIgnoreCase(request.getSourceType())) {
            if (!StringUtils.hasText(request.getCustomName())) {
                throw new BizException("自定义名称不能为空");
            }
            return new IngredientSnapshot(request.getCustomName(), "未分类");
        }
        if (request.getSourceId() == null) {
            throw new BizException("请选择食材");
        }
        if ("SYSTEM".equalsIgnoreCase(request.getSourceType())) {
            SystemIngredientEntity entity = systemIngredientMapper.selectById(request.getSourceId());
            if (entity == null) {
                throw new BizException("系统食材不存在");
            }
            return new IngredientSnapshot(entity.getName(), entity.getCategory());
        }
        if ("FAMILY".equalsIgnoreCase(request.getSourceType())) {
            FamilyIngredientEntity entity = familyIngredientMapper.selectById(request.getSourceId());
            if (entity == null || !user.getCurrentFamilyId().equals(entity.getFamilyId())) {
                throw new BizException("家庭食材不存在");
            }
            return new IngredientSnapshot(entity.getName(), entity.getCategory());
        }
        throw new BizException("不支持的食材来源");
    }

    private UserEntity requireFamilyUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        return user;
    }

    private FridgeItemEntity requireOwnedItem(UserEntity user, Long itemId) {
        FridgeItemEntity item = fridgeItemMapper.selectById(itemId);
        if (item == null || !user.getCurrentFamilyId().equals(item.getFamilyId())) {
            throw new BizException("库存不存在");
        }
        return item;
    }

    private void insertLog(Long itemId, Long familyId, String actionType, BigDecimal quantityChange, Long userId, String note) {
        FridgeChangeLogEntity log = new FridgeChangeLogEntity();
        log.setId(IdGenerator.nextId());
        log.setFridgeItemId(itemId);
        log.setFamilyId(familyId);
        log.setActionType(actionType);
        log.setQuantityChange(quantityChange);
        log.setOperatorUserId(userId);
        log.setNote(note);
        fridgeChangeLogMapper.insert(log);
    }

    private String getReminderState(LocalDate expiresAt) {
        if (expiresAt == null) {
            return "NORMAL";
        }
        LocalDate today = LocalDate.now();
        if (expiresAt.isBefore(today)) {
            return "EXPIRED";
        }
        if (!expiresAt.isAfter(today.plusDays(appProperties.getFridge().getExpiringDays()))) {
            return "EXPIRING";
        }
        return "NORMAL";
    }

    private FridgeItemView toView(FridgeItemEntity item, String reminderState) {
        return FridgeItemView.builder()
            .id(item.getId())
            .sourceType(item.getSourceType())
            .sourceId(item.getSourceId())
            .name(item.getNameSnapshot())
            .category(item.getCategorySnapshot())
            .quantity(item.getQuantity())
            .unit(item.getUnit())
            .producedAt(item.getProducedAt())
            .expiresAt(item.getExpiresAt())
            .location(item.getLocation())
            .note(item.getNote())
            .status(item.getStatus())
            .reminderState(reminderState)
            .build();
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
