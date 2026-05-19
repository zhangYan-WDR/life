package com.life.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.life.server.config.AppProperties;
import com.life.server.dto.response.ExpiryReminderMessage;
import com.life.server.service.WechatSubscriptionMessageService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class WechatSubscriptionMessageServiceImpl implements WechatSubscriptionMessageService {

    private static final String ACCESS_TOKEN_CACHE_KEY = "life:wechat:access-token";

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public WechatSubscriptionMessageServiceImpl(AppProperties appProperties,
                                                RestTemplate restTemplate,
                                                StringRedisTemplate stringRedisTemplate) {
        this.appProperties = appProperties;
        this.restTemplate = restTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean sendExpiryReminder(ExpiryReminderMessage message) {
        if (isWechatConfigMissing()) {
            log.info("Skip real subscribe message send. payload={}", message);
            return true;
        }
        String accessToken = getAccessToken();
        URI uri = UriComponentsBuilder
            .fromHttpUrl("https://api.weixin.qq.com/cgi-bin/message/subscribe/send")
            .queryParam("access_token", accessToken)
            .build(true)
            .toUri();

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("touser", message.getOpenid());
        payload.put("template_id", appProperties.getWechat().getSubscriptionTemplateId());
        payload.put("page", appProperties.getWechat().getReminderPage());
        payload.put("data", buildTemplateData(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(uri, new HttpEntity<Object>(payload, headers), Map.class);
        Map body = response.getBody();
        if (body == null) {
            log.warn("Wechat subscribe send failed: empty response");
            return false;
        }
        Object errCode = body.get("errcode");
        if (errCode == null || Integer.valueOf(String.valueOf(errCode)) == 0) {
            log.info("Wechat subscribe message sent. userId={}, productName={}", message.getUserId(), message.getProductName());
            return true;
        }
        log.warn("Wechat subscribe send failed: {}", body);
        return false;
    }

    private Map<String, Object> buildTemplateData(ExpiryReminderMessage message) {
        AppProperties.TemplateFields fields = appProperties.getWechat().getTemplateFields();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put(fields.getProductName(), wrapValue(message.getProductName()));
        data.put(fields.getExpiryDate(), wrapValue(message.getExpiryDate()));
        data.put(fields.getRemainingDays(), wrapValue(message.getRemainingDays()));
        data.put(fields.getInventoryQuantity(), wrapValue(message.getInventoryQuantity()));
        data.put(fields.getRemark(), wrapValue(message.getRemark()));
        return data;
    }

    private Map<String, String> wrapValue(String value) {
        Map<String, String> wrapped = new HashMap<String, String>();
        wrapped.put("value", value);
        return wrapped;
    }

    private String getAccessToken() {
        String cached = stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_CACHE_KEY);
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        URI uri = UriComponentsBuilder
            .fromHttpUrl("https://api.weixin.qq.com/cgi-bin/token")
            .queryParam("grant_type", "client_credential")
            .queryParam("appid", appProperties.getWechat().getAppId())
            .queryParam("secret", appProperties.getWechat().getAppSecret())
            .build(true)
            .toUri();
        ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);
        Map body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("获取微信 access_token 失败");
        }
        String accessToken = String.valueOf(body.get("access_token"));
        stringRedisTemplate.opsForValue().set(ACCESS_TOKEN_CACHE_KEY, accessToken, 7000, TimeUnit.SECONDS);
        return accessToken;
    }

    private boolean isWechatConfigMissing() {
        AppProperties.Wechat wechat = appProperties.getWechat();
        AppProperties.TemplateFields fields = wechat.getTemplateFields();
        return isPlaceholder(wechat.getAppId())
            || isPlaceholder(wechat.getAppSecret())
            || isPlaceholder(wechat.getSubscriptionTemplateId())
            || isPlaceholder(fields.getProductName())
            || isPlaceholder(fields.getExpiryDate())
            || isPlaceholder(fields.getRemainingDays())
            || isPlaceholder(fields.getInventoryQuantity())
            || isPlaceholder(fields.getRemark());
    }

    private boolean isPlaceholder(String value) {
        return StrUtil.isBlank(value) || "CHANGE_ME".equalsIgnoreCase(value);
    }
}
