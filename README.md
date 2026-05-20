# 家庭生活小程序

同仓维护的小程序前端和 Java 后端项目，一期和当前二期能力包含：

- 微信登录
- 创建家庭 / 邀请码加入家庭
- 冰箱库存管理
- 过期提醒摘要
- 家庭自定义食材维护
- 家庭菜谱库
- 随机选菜
- 家庭点餐审批与历史
- 点餐缺料分析

## 目录

- `miniprogram/`：微信小程序原生前端
- `server/`：Spring Boot 后端

## 后端本地配置

仓库内提交的是示例配置：

- `server/src/main/resources/application.yml`
- `server/src/main/resources/application-local.example.yml`

本地真实配置放在未提交的 `server/src/main/resources/application-local.yml`。

默认本地开发配置：

- MySQL：`127.0.0.1:3306/life`
- Redis：`127.0.0.1:6379`
- 后端端口：`8080`

## 启动前准备

1. 手动创建数据库 `life`
2. 确认本地 MySQL 和 Redis 已启动
3. 在微信开发者工具中打开本仓库，项目根目录为 `miniprogram/`

## 后端说明

- Java 运行环境当前按 `Java 8` 兼容实现，因此后端使用 `Spring Boot 2.7`
- Flyway 会在启动时初始化表结构和系统预置食材
- 微信真实 `AppID / AppSecret / 订阅模板 ID` 需要你后续补到本地配置中
- OCR / 图片识别相关配置本期仅预留 TODO，不会实际调用外部识别服务

## 小程序说明

- 启动页会自动登录并判断是否已加入家庭
- 本地开发默认请求 `http://127.0.0.1:8080/api`
- 如果未配置微信正式密钥，后端会走本地调试 openid 逻辑，方便先开发业务流程
- “小票识别入冰箱 / 图片识别存菜谱” 当前是规划中入口，会提示优先走手动添加
