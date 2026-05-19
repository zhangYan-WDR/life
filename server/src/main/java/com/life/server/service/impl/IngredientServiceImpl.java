package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.dto.request.CreateFamilyIngredientRequest;
import com.life.server.dto.response.IngredientCatalogResponse;
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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientServiceImpl implements IngredientService {

    private final SystemIngredientMapper systemIngredientMapper;
    private final FamilyIngredientMapper familyIngredientMapper;
    private final UserMapper userMapper;

    public IngredientServiceImpl(SystemIngredientMapper systemIngredientMapper,
                                 FamilyIngredientMapper familyIngredientMapper,
                                 UserMapper userMapper) {
        this.systemIngredientMapper = systemIngredientMapper;
        this.familyIngredientMapper = familyIngredientMapper;
        this.userMapper = userMapper;
    }

    @Override
    public IngredientCatalogResponse getCatalog(Long userId) {
        UserEntity user = requireFamilyUser(userId);
        List<SystemIngredientEntity> systemIngredients = systemIngredientMapper.selectList(new LambdaQueryWrapper<SystemIngredientEntity>()
            .eq(SystemIngredientEntity::getEnabled, true)
            .orderByAsc(SystemIngredientEntity::getSortOrder));
        List<FamilyIngredientEntity> familyIngredients = familyIngredientMapper.selectList(new LambdaQueryWrapper<FamilyIngredientEntity>()
            .eq(FamilyIngredientEntity::getFamilyId, user.getCurrentFamilyId())
            .eq(FamilyIngredientEntity::getEnabled, true)
            .orderByDesc(FamilyIngredientEntity::getCreatedAt));

        List<IngredientView> systemViews = new ArrayList<IngredientView>();
        for (SystemIngredientEntity item : systemIngredients) {
            systemViews.add(IngredientView.builder()
                .id(item.getId())
                .sourceType("SYSTEM")
                .name(item.getName())
                .category(item.getCategory())
                .defaultUnit(item.getDefaultUnit())
                .build());
        }

        List<IngredientView> familyViews = new ArrayList<IngredientView>();
        for (FamilyIngredientEntity item : familyIngredients) {
            familyViews.add(IngredientView.builder()
                .id(item.getId())
                .sourceType("FAMILY")
                .name(item.getName())
                .category(item.getCategory())
                .defaultUnit(item.getDefaultUnit())
                .build());
        }
        return IngredientCatalogResponse.builder()
            .systemIngredients(systemViews)
            .familyIngredients(familyViews)
            .build();
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
        entity.setDefaultUnit(request.getDefaultUnit());
        entity.setEnabled(true);
        familyIngredientMapper.insert(entity);
        return IngredientView.builder()
            .id(entity.getId())
            .sourceType("FAMILY")
            .name(entity.getName())
            .category(entity.getCategory())
            .defaultUnit(entity.getDefaultUnit())
            .build();
    }

    private UserEntity requireFamilyUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        return user;
    }
}
