package com.life.server.security;

import com.life.server.common.BizException;
import com.life.server.config.AppProperties;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final AppProperties appProperties;

    public AuthInterceptor(StringRedisTemplate stringRedisTemplate, AppProperties appProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.appProperties = appProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String headerName = appProperties.getAuth().getHeaderName();
        String authorization = request.getHeader(headerName);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException("未登录或登录已失效");
        }
        String token = authorization.substring(7);
        String redisKey = appProperties.getAuth().getTokenPrefix() + token;
        String userId = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(userId)) {
            throw new BizException("未登录或登录已失效");
        }
        stringRedisTemplate.expire(redisKey, appProperties.getAuth().getTokenTtlSeconds(), TimeUnit.SECONDS);
        AuthContext.setUserId(Long.valueOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
