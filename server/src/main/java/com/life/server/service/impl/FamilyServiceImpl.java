package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.dto.request.CreateFamilyRequest;
import com.life.server.dto.request.JoinFamilyRequest;
import com.life.server.dto.response.FamilyCurrentResponse;
import com.life.server.dto.response.FamilyMemberView;
import com.life.server.entity.FamilyEntity;
import com.life.server.entity.FamilyMemberEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyMapper;
import com.life.server.mapper.FamilyMemberMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.FamilyService;
import com.life.server.util.IdGenerator;
import com.life.server.util.InviteCodeGenerator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyServiceImpl implements FamilyService {

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final UserMapper userMapper;

    public FamilyServiceImpl(FamilyMapper familyMapper, FamilyMemberMapper familyMemberMapper, UserMapper userMapper) {
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyCurrentResponse createFamily(Long userId, CreateFamilyRequest request) {
        UserEntity user = requireUser(userId);
        ensureNoFamily(user);
        FamilyEntity family = new FamilyEntity();
        family.setId(IdGenerator.nextId());
        family.setName(request.getFamilyName());
        family.setInviteCode(generateInviteCode());
        family.setOwnerUserId(userId);
        family.setStatus("ACTIVE");
        familyMapper.insert(family);

        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setId(IdGenerator.nextId());
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole("OWNER");
        member.setStatus("ACTIVE");
        familyMemberMapper.insert(member);

        user.setCurrentFamilyId(family.getId());
        userMapper.updateById(user);
        return getCurrentFamily(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyCurrentResponse joinFamily(Long userId, JoinFamilyRequest request) {
        UserEntity user = requireUser(userId);
        ensureNoFamily(user);
        FamilyEntity family = familyMapper.selectOne(new LambdaQueryWrapper<FamilyEntity>()
            .eq(FamilyEntity::getInviteCode, request.getInviteCode())
            .eq(FamilyEntity::getStatus, "ACTIVE")
            .last("limit 1"));
        if (family == null) {
            throw new BizException("邀请码无效");
        }
        FamilyMemberEntity member = new FamilyMemberEntity();
        member.setId(IdGenerator.nextId());
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        member.setStatus("ACTIVE");
        familyMemberMapper.insert(member);

        user.setCurrentFamilyId(family.getId());
        userMapper.updateById(user);
        return getCurrentFamily(userId);
    }

    @Override
    public FamilyCurrentResponse getCurrentFamily(Long userId) {
        UserEntity user = requireUser(userId);
        if (user.getCurrentFamilyId() == null) {
            throw new BizException("当前用户还未加入家庭");
        }
        FamilyEntity family = familyMapper.selectById(user.getCurrentFamilyId());
        List<FamilyMemberEntity> members = familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMemberEntity>()
            .eq(FamilyMemberEntity::getFamilyId, family.getId())
            .eq(FamilyMemberEntity::getStatus, "ACTIVE"));
        List<FamilyMemberView> views = new ArrayList<FamilyMemberView>();
        for (FamilyMemberEntity member : members) {
            UserEntity memberUser = userMapper.selectById(member.getUserId());
            views.add(FamilyMemberView.builder()
                .userId(memberUser.getId())
                .nickname(memberUser.getNickname())
                .avatar(memberUser.getAvatar())
                .role(member.getRole())
                .build());
        }
        return FamilyCurrentResponse.builder()
            .familyId(family.getId())
            .familyName(family.getName())
            .inviteCode(family.getInviteCode())
            .members(views)
            .build();
    }

    private void ensureNoFamily(UserEntity user) {
        if (user.getCurrentFamilyId() != null) {
            throw new BizException("当前用户已经加入家庭");
        }
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private String generateInviteCode() {
        String code;
        do {
            code = InviteCodeGenerator.generate(6);
        } while (familyMapper.selectCount(new LambdaQueryWrapper<FamilyEntity>()
            .eq(FamilyEntity::getInviteCode, code)) > 0);
        return code;
    }
}
