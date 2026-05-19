package com.life.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Auth auth = new Auth();
    private Fridge fridge = new Fridge();
    private Wechat wechat = new Wechat();

    public Auth getAuth() {
        return auth;
    }

    public Fridge getFridge() {
        return fridge;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public static class Auth {
        private String tokenPrefix;
        private long tokenTtlSeconds;
        private String headerName;

        public String getTokenPrefix() {
            return tokenPrefix;
        }

        public void setTokenPrefix(String tokenPrefix) {
            this.tokenPrefix = tokenPrefix;
        }

        public long getTokenTtlSeconds() {
            return tokenTtlSeconds;
        }

        public void setTokenTtlSeconds(long tokenTtlSeconds) {
            this.tokenTtlSeconds = tokenTtlSeconds;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }

    public static class Fridge {
        private int expiringDays;

        public int getExpiringDays() {
            return expiringDays;
        }

        public void setExpiringDays(int expiringDays) {
            this.expiringDays = expiringDays;
        }
    }

    public static class Wechat {
        private String appId;
        private String appSecret;
        private String subscriptionTemplateId;
        private String reminderPage;
        private TemplateFields templateFields = new TemplateFields();

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getSubscriptionTemplateId() {
            return subscriptionTemplateId;
        }

        public void setSubscriptionTemplateId(String subscriptionTemplateId) {
            this.subscriptionTemplateId = subscriptionTemplateId;
        }

        public String getReminderPage() {
            return reminderPage;
        }

        public void setReminderPage(String reminderPage) {
            this.reminderPage = reminderPage;
        }

        public TemplateFields getTemplateFields() {
            return templateFields;
        }

        public void setTemplateFields(TemplateFields templateFields) {
            this.templateFields = templateFields;
        }
    }

    public static class TemplateFields {
        private String productName;
        private String expiryDate;
        private String remainingDays;
        private String inventoryQuantity;
        private String remark;

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
        }

        public String getRemainingDays() {
            return remainingDays;
        }

        public void setRemainingDays(String remainingDays) {
            this.remainingDays = remainingDays;
        }

        public String getInventoryQuantity() {
            return inventoryQuantity;
        }

        public void setInventoryQuantity(String inventoryQuantity) {
            this.inventoryQuantity = inventoryQuantity;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
