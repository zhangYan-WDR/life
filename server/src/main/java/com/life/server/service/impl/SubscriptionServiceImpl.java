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
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        ReminderSubscriptionEntity entity = reminderSubscriptionMapper.selectOne(new LambdaQueryWrapper<ReminderSubscriptionEntity>()
            .eq(ReminderSubscriptionEntity::getUserId, userId)
            .eq(ReminderSubscriptionEntity::getTemplateType, "EXPIRY")
            .last("limit 1"));
        if (entity == null) {
            entity = new ReminderSubscriptionEntity();
            entity.setId(IdGenerator.nextId());
            entity.setUserId(userId);
            entity.setTemplateType("EXPIRY");
        }
        entity.setAccepted(accepted);
        entity.setLastAcceptedAt(Boolean.TRUE.equals(accepted) ? LocalDateTime.now() : entity.getLastAcceptedAt());
        if (entity.getId() == null) {
            reminderSubscriptionMapper.insert(entity);
        } else {
            reminderSubscriptionMapper.updateById(entity);
        }
    }
}
