package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.entity.ReminderSubscriptionEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.ReminderSubscriptionMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.SubscriptionService;
import com.life.server.util.IdGenerator;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final ReminderSubscriptionMapper reminderSubscriptionMapper;
    private final UserMapper userMapper;

    public SubscriptionServiceImpl(ReminderSubscriptionMapper reminderSubscriptionMapper, UserMapper userMapper) {
        this.reminderSubscriptionMapper = reminderSubscriptionMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExpiryReminder(Long userId, Boolean accepted) {
        updateSubscription(userId, accepted, "EXPIRY");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMealRequestReminder(Long userId, Boolean accepted) {
        updateSubscription(userId, accepted, "MEAL_REQUEST");
    }

    private void updateSubscription(Long userId, Boolean accepted, String templateType) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        boolean newEntity = false;
        ReminderSubscriptionEntity entity = reminderSubscriptionMapper.selectOne(new LambdaQueryWrapper<ReminderSubscriptionEntity>()
            .eq(ReminderSubscriptionEntity::getUserId, userId)
            .eq(ReminderSubscriptionEntity::getTemplateType, templateType)
            .last("limit 1"));
        if (entity == null) {
            entity = new ReminderSubscriptionEntity();
            entity.setId(IdGenerator.nextId());
            entity.setUserId(userId);
            entity.setTemplateType(templateType);
            newEntity = true;
        }
        entity.setAccepted(accepted);
        entity.setLastAcceptedAt(Boolean.TRUE.equals(accepted) ? LocalDateTime.now() : entity.getLastAcceptedAt());
        if (newEntity) {
            reminderSubscriptionMapper.insert(entity);
        } else {
            reminderSubscriptionMapper.updateById(entity);
        }
    }
}
