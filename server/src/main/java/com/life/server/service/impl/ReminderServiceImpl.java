package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.config.AppProperties;
import com.life.server.dto.response.ExpiryReminderMessage;
import com.life.server.entity.FamilyMemberEntity;
import com.life.server.entity.FridgeItemEntity;
import com.life.server.entity.ReminderDeliveryLogEntity;
import com.life.server.entity.ReminderSubscriptionEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyMemberMapper;
import com.life.server.mapper.FridgeItemMapper;
import com.life.server.mapper.ReminderDeliveryLogMapper;
import com.life.server.mapper.ReminderSubscriptionMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.ReminderService;
import com.life.server.service.WechatSubscriptionMessageService;
import com.life.server.util.IdGenerator;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ReminderServiceImpl implements ReminderService {

    private final FridgeItemMapper fridgeItemMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final ReminderSubscriptionMapper reminderSubscriptionMapper;
    private final ReminderDeliveryLogMapper reminderDeliveryLogMapper;
    private final UserMapper userMapper;
    private final AppProperties appProperties;
    private final WechatSubscriptionMessageService wechatSubscriptionMessageService;

    public ReminderServiceImpl(FridgeItemMapper fridgeItemMapper,
                               FamilyMemberMapper familyMemberMapper,
                               ReminderSubscriptionMapper reminderSubscriptionMapper,
                               ReminderDeliveryLogMapper reminderDeliveryLogMapper,
                               UserMapper userMapper,
                               AppProperties appProperties,
                               WechatSubscriptionMessageService wechatSubscriptionMessageService) {
        this.fridgeItemMapper = fridgeItemMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.reminderSubscriptionMapper = reminderSubscriptionMapper;
        this.reminderDeliveryLogMapper = reminderDeliveryLogMapper;
        this.userMapper = userMapper;
        this.appProperties = appProperties;
        this.wechatSubscriptionMessageService = wechatSubscriptionMessageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchExpiryReminders() {
        LocalDate today = LocalDate.now();
        LocalDate expiringBoundary = today.plusDays(appProperties.getFridge().getExpiringDays());
        List<FridgeItemEntity> items = fridgeItemMapper.selectList(new LambdaQueryWrapper<FridgeItemEntity>()
            .eq(FridgeItemEntity::getStatus, "ACTIVE")
            .isNotNull(FridgeItemEntity::getExpiresAt)
            .le(FridgeItemEntity::getExpiresAt, expiringBoundary));
        for (FridgeItemEntity item : items) {
            String type = item.getExpiresAt().isBefore(today) ? "EXPIRED" : "EXPIRING";
            List<FamilyMemberEntity> members = familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMemberEntity>()
                .eq(FamilyMemberEntity::getFamilyId, item.getFamilyId())
                .eq(FamilyMemberEntity::getStatus, "ACTIVE"));
            for (FamilyMemberEntity member : members) {
                if (!isSubscribed(member.getUserId())) {
                    continue;
                }
                if (alreadyDelivered(member.getUserId(), item.getId(), type, today)) {
                    continue;
                }
                UserEntity user = userMapper.selectById(member.getUserId());
                if (user == null) {
                    continue;
                }
                ExpiryReminderMessage message = buildMessage(user, item, type, today);
                boolean sent = wechatSubscriptionMessageService.sendExpiryReminder(message);
                if (sent) {
                    insertDeliveryLog(member.getUserId(), item.getId(), type, today);
                }
            }
        }
    }

    @Override
    public boolean triggerTestReminder(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        FridgeItemEntity item = fridgeItemMapper.selectOne(new LambdaQueryWrapper<FridgeItemEntity>()
            .eq(FridgeItemEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FridgeItemEntity::getStatus, "ACTIVE")
            .isNotNull(FridgeItemEntity::getExpiresAt)
            .orderByAsc(FridgeItemEntity::getExpiresAt)
            .last("limit 1"));
        if (item == null) {
            throw new BizException("请先添加一个带有效期的库存");
        }
        LocalDate today = LocalDate.now();
        String type = item.getExpiresAt().isBefore(today) ? "EXPIRED" : "EXPIRING";
        ExpiryReminderMessage message = buildMessage(user, item, type, today);
        return wechatSubscriptionMessageService.sendExpiryReminder(message);
    }

    private ExpiryReminderMessage buildMessage(UserEntity user, FridgeItemEntity item, String type, LocalDate today) {
        long remainingDays = ChronoUnit.DAYS.between(today, item.getExpiresAt());
        String remainingText = "EXPIRED".equals(type) ? "已过期" : remainingDays + "天";
        String remark = "EXPIRED".equals(type)
            ? "库存还有" + item.getQuantity().stripTrailingZeros().toPlainString() + item.getUnit() + "，请尽快处理"
            : "库存还有" + item.getQuantity().stripTrailingZeros().toPlainString() + item.getUnit() + "，建议优先食用";
        return ExpiryReminderMessage.builder()
            .userId(user.getId())
            .openid(user.getOpenid())
            .productName(item.getNameSnapshot())
            .expiryDate(String.valueOf(item.getExpiresAt()))
            .remainingDays(remainingText)
            .inventoryQuantity(item.getQuantity().stripTrailingZeros().toPlainString())
            .remark(remark)
            .reminderType(type)
            .build();
    }

    private boolean isSubscribed(Long userId) {
        ReminderSubscriptionEntity entity = reminderSubscriptionMapper.selectOne(new LambdaQueryWrapper<ReminderSubscriptionEntity>()
            .eq(ReminderSubscriptionEntity::getUserId, userId)
            .eq(ReminderSubscriptionEntity::getTemplateType, "EXPIRY")
            .eq(ReminderSubscriptionEntity::getAccepted, true)
            .last("limit 1"));
        return entity != null;
    }

    private boolean alreadyDelivered(Long userId, Long itemId, String type, LocalDate deliveredOn) {
        Long count = reminderDeliveryLogMapper.selectCount(new LambdaQueryWrapper<ReminderDeliveryLogEntity>()
            .eq(ReminderDeliveryLogEntity::getUserId, userId)
            .eq(ReminderDeliveryLogEntity::getFridgeItemId, itemId)
            .eq(ReminderDeliveryLogEntity::getReminderType, type)
            .eq(ReminderDeliveryLogEntity::getDeliveredOn, deliveredOn));
        return count != null && count > 0;
    }

    private void insertDeliveryLog(Long userId, Long itemId, String type, LocalDate deliveredOn) {
        ReminderDeliveryLogEntity entity = new ReminderDeliveryLogEntity();
        entity.setId(IdGenerator.nextId());
        entity.setUserId(userId);
        entity.setFridgeItemId(itemId);
        entity.setReminderType(type);
        entity.setDeliveredOn(deliveredOn);
        reminderDeliveryLogMapper.insert(entity);
    }
}
