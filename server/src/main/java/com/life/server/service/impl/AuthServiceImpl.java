package com.life.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.server.common.BizException;
import com.life.server.config.AppProperties;
import com.life.server.dto.request.WxLoginRequest;
import com.life.server.dto.response.AuthLoginResponse;
import com.life.server.entity.UserEntity;
import com.life.server.mapper.UserMapper;
import com.life.server.service.AuthService;
import com.life.server.util.IdGenerator;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AuthServiceImpl(UserMapper userMapper,
                           StringRedisTemplate stringRedisTemplate,
                           AppProperties appProperties,
                           RestTemplate restTemplate,
                           ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.appProperties = appProperties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthLoginResponse login(WxLoginRequest request) {
        String openid = resolveOpenid(request);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
            .eq(UserEntity::getOpenid, openid)
            .last("limit 1"));
        if (user == null) {
            user = new UserEntity();
            user.setId(IdGenerator.nextId());
            user.setOpenid(openid);
            user.setNickname(StrUtil.blankToDefault(request.getNickname(), "生活用户"));
            user.setAvatar(request.getAvatar());
            user.setStatus("ACTIVE");
            userMapper.insert(user);
        } else {
            if (StrUtil.isNotBlank(request.getNickname())) {
                user.setNickname(request.getNickname());
            }
            if (StrUtil.isNotBlank(request.getAvatar())) {
                user.setAvatar(request.getAvatar());
            }
            userMapper.updateById(user);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
            appProperties.getAuth().getTokenPrefix() + token,
            String.valueOf(user.getId()),
            appProperties.getAuth().getTokenTtlSeconds(),
            TimeUnit.SECONDS
        );
        return AuthLoginResponse.builder()
            .token(token)
            .userId(user.getId())
            .joinedFamily(user.getCurrentFamilyId() != null)
            .build();
    }

    private String resolveOpenid(WxLoginRequest request) {
        if (StrUtil.isBlank(appProperties.getWechat().getAppId())
            || "CHANGE_ME".equalsIgnoreCase(appProperties.getWechat().getAppId())
            || StrUtil.isBlank(appProperties.getWechat().getAppSecret())
            || "CHANGE_ME".equalsIgnoreCase(appProperties.getWechat().getAppSecret())) {
            String debugKey = StrUtil.blankToDefault(request.getDebugUserKey(), request.getCode());
            return "debug_" + debugKey;
        }

        URI uri = UriComponentsBuilder
            .fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
            .queryParam("appid", appProperties.getWechat().getAppId())
            .queryParam("secret", appProperties.getWechat().getAppSecret())
            .queryParam("js_code", request.getCode())
            .queryParam("grant_type", "authorization_code")
            .build(true)
            .toUri();
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        String bodyText = response.getBody();
        if (StrUtil.isBlank(bodyText)) {
            throw new BizException("微信登录失败，微信返回内容异常");
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(bodyText);
        } catch (Exception ex) {
            throw new BizException("微信登录失败，微信返回内容异常");
        }
        if (!body.hasNonNull("openid")) {
            String errorMessage = body.has("errmsg") ? body.get("errmsg").asText() : null;
            if (StrUtil.isNotBlank(errorMessage)) {
                throw new BizException("微信登录失败: " + errorMessage);
            }
            throw new BizException("微信登录失败，请检查小程序配置");
        }
        return body.get("openid").asText();
    }
}
