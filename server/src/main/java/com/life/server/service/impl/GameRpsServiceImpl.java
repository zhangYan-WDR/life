package com.life.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.dto.request.CreateRpsRoomRequest;
import com.life.server.dto.request.SubmitGestureRequest;
import com.life.server.dto.response.RpsRoomStatusResponse;
import com.life.server.dto.response.RpsRoomStatusResponse.ParticipantView;
import com.life.server.entity.FamilyMemberEntity;
import com.life.server.entity.GameRpsGestureEntity;
import com.life.server.entity.GameRpsParticipantEntity;
import com.life.server.entity.GameRpsRoomEntity;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.FamilyMemberMapper;
import com.life.server.mapper.GameRpsGestureMapper;
import com.life.server.mapper.GameRpsParticipantMapper;
import com.life.server.mapper.GameRpsRoomMapper;
import com.life.server.mapper.UserMapper;
import com.life.server.service.GameRpsService;
import com.life.server.util.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameRpsServiceImpl implements GameRpsService {

    private final GameRpsRoomMapper roomMapper;
    private final GameRpsParticipantMapper participantMapper;
    private final GameRpsGestureMapper gestureMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final UserMapper userMapper;

    public GameRpsServiceImpl(GameRpsRoomMapper roomMapper,
                              GameRpsParticipantMapper participantMapper,
                              GameRpsGestureMapper gestureMapper,
                              FamilyMemberMapper familyMemberMapper,
                              UserMapper userMapper) {
        this.roomMapper = roomMapper;
        this.participantMapper = participantMapper;
        this.gestureMapper = gestureMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RpsRoomStatusResponse createRoom(Long userId, CreateRpsRoomRequest request) {
        Long familyId = requireFamilyMember(userId);

        List<Long> participantIds = request.getParticipantUserIds();
        if (!participantIds.contains(userId)) {
            participantIds = new ArrayList<>(participantIds);
            participantIds.add(userId);
        }
        if (participantIds.size() < 2) {
            throw new BizException("至少需要 2 名参与者");
        }

        // 校验所有参与者都是家庭成员
        List<Long> familyMemberIds = familyMemberMapper.selectList(
                new LambdaQueryWrapper<FamilyMemberEntity>()
                        .eq(FamilyMemberEntity::getFamilyId, familyId)
                        .eq(FamilyMemberEntity::getStatus, "ACTIVE")
        ).stream().map(FamilyMemberEntity::getUserId).collect(Collectors.toList());

        for (Long pid : participantIds) {
            if (!familyMemberIds.contains(pid)) {
                throw new BizException("参与者不在当前家庭中");
            }
        }

        GameRpsRoomEntity room = new GameRpsRoomEntity();
        room.setId(IdGenerator.nextId());
        room.setFamilyId(familyId);
        room.setCreatorId(userId);
        room.setStatus("OPEN");
        roomMapper.insert(room);

        for (Long pid : participantIds) {
            GameRpsParticipantEntity p = new GameRpsParticipantEntity();
            p.setId(IdGenerator.nextId());
            p.setRoomId(room.getId());
            p.setUserId(pid);
            participantMapper.insert(p);
        }

        return buildStatus(room, participantIds, false);
    }

    @Override
    public RpsRoomStatusResponse getStatus(Long userId, Long roomId) {
        GameRpsRoomEntity room = requireRoom(roomId);
        requireParticipant(userId, roomId);
        boolean revealed = "REVEALED".equals(room.getStatus());
        List<Long> participantIds = participantMapper.selectList(
                new LambdaQueryWrapper<GameRpsParticipantEntity>()
                        .eq(GameRpsParticipantEntity::getRoomId, roomId)
        ).stream().map(GameRpsParticipantEntity::getUserId).collect(Collectors.toList());
        return buildStatus(room, participantIds, revealed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitGesture(Long userId, Long roomId, SubmitGestureRequest request) {
        GameRpsRoomEntity room = requireRoom(roomId);
        if ("REVEALED".equals(room.getStatus())) {
            throw new BizException("该局已揭晓，无法再出拳");
        }
        requireParticipant(userId, roomId);

        long alreadySubmitted = gestureMapper.selectCount(
                new LambdaQueryWrapper<GameRpsGestureEntity>()
                        .eq(GameRpsGestureEntity::getRoomId, roomId)
                        .eq(GameRpsGestureEntity::getUserId, userId)
        );
        if (alreadySubmitted > 0) {
            throw new BizException("你已经出拳了");
        }

        // 提交时直接存明文 gesture，同时存 hash 供前端验证防篡改
        String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String hash = sha256(request.getGesture() + ":" + salt);

        GameRpsGestureEntity gesture = new GameRpsGestureEntity();
        gesture.setId(IdGenerator.nextId());
        gesture.setRoomId(roomId);
        gesture.setUserId(userId);
        gesture.setGesture(request.getGesture());
        gesture.setSalt(salt);
        gesture.setGestureHash(hash);
        gesture.setSubmittedAt(LocalDateTime.now());
        gestureMapper.insert(gesture);

        // 检查是否所有参与者都已出拳
        long participantCount = participantMapper.selectCount(
                new LambdaQueryWrapper<GameRpsParticipantEntity>()
                        .eq(GameRpsParticipantEntity::getRoomId, roomId)
        );
        long submittedCount = gestureMapper.selectCount(
                new LambdaQueryWrapper<GameRpsGestureEntity>()
                        .eq(GameRpsGestureEntity::getRoomId, roomId)
        );

        if (submittedCount >= participantCount) {
            room.setStatus("REVEALED");
            room.setRevealedAt(LocalDateTime.now());
            roomMapper.updateById(room);
        }
    }

    private String determineWinner(Set<String> gestureSet) {
        List<String> list = new ArrayList<>(gestureSet);
        String a = list.get(0);
        String b = list.get(1);
        if (beats(a, b)) return a;
        return b;
    }

    private boolean beats(String a, String b) {
        return ("ROCK".equals(a) && "SCISSORS".equals(b))
                || ("SCISSORS".equals(a) && "PAPER".equals(b))
                || ("PAPER".equals(a) && "ROCK".equals(b));
    }

    private RpsRoomStatusResponse buildStatus(GameRpsRoomEntity room, List<Long> participantIds, boolean revealed) {
        List<UserEntity> users = userMapper.selectList(
                new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getId, participantIds)
        );
        Map<Long, String> nicknameMap = users.stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getNickname));

        List<GameRpsGestureEntity> gestures = gestureMapper.selectList(
                new LambdaQueryWrapper<GameRpsGestureEntity>()
                        .eq(GameRpsGestureEntity::getRoomId, room.getId())
        );
        Map<Long, GameRpsGestureEntity> gestureMap = gestures.stream()
                .collect(Collectors.toMap(GameRpsGestureEntity::getUserId, g -> g));

        String winnerGesture = null;
        if (revealed && !gestures.isEmpty()) {
            Set<String> gestureSet = gestures.stream()
                    .map(GameRpsGestureEntity::getGesture)
                    .collect(Collectors.toSet());
            if (gestureSet.size() == 2) {
                winnerGesture = determineWinner(gestureSet);
            }
        }
        final String finalWinnerGesture = winnerGesture;
        final boolean isDraw = revealed && (gestures.stream()
                .map(GameRpsGestureEntity::getGesture)
                .collect(Collectors.toSet()).size() != 2);

        List<ParticipantView> views = participantIds.stream().map(uid -> {
            GameRpsGestureEntity g = gestureMap.get(uid);
            boolean submitted = g != null;
            String gesture = (revealed && submitted) ? g.getGesture() : null;
            String outcome = null;
            if (revealed && submitted) {
                if (isDraw) {
                    outcome = "DRAW";
                } else {
                    outcome = g.getGesture().equals(finalWinnerGesture) ? "WIN" : "LOSE";
                }
            }
            return ParticipantView.builder()
                    .userId(uid)
                    .nickname(nicknameMap.getOrDefault(uid, "未知"))
                    .submitted(submitted)
                    .gesture(gesture)
                    .outcome(outcome)
                    .build();
        }).collect(Collectors.toList());

        return RpsRoomStatusResponse.builder()
                .roomId(room.getId())
                .status(room.getStatus())
                .participants(views)
                .build();
    }

    private Long requireFamilyMember(Long userId) {
        FamilyMemberEntity member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMemberEntity>()
                        .eq(FamilyMemberEntity::getUserId, userId)
                        .eq(FamilyMemberEntity::getStatus, "ACTIVE")
                        .last("limit 1")
        );
        if (member == null) throw new BizException("你还没有加入家庭");
        return member.getFamilyId();
    }

    private GameRpsRoomEntity requireRoom(Long roomId) {
        GameRpsRoomEntity room = roomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        return room;
    }

    private void requireParticipant(Long userId, Long roomId) {
        long count = participantMapper.selectCount(
                new LambdaQueryWrapper<GameRpsParticipantEntity>()
                        .eq(GameRpsParticipantEntity::getRoomId, roomId)
                        .eq(GameRpsParticipantEntity::getUserId, userId)
        );
        if (count == 0) throw new BizException("你不是该局的参与者");
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}
