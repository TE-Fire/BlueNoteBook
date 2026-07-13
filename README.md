# BlueNote 小蓝书

> 仿小红书的内容分享平台，基于 Spring Cloud Alibaba 微服务架构实现

---

## 一、项目简介

BlueNote（小蓝书）是一个内容分享社交平台，用户可以发布图文笔记、视频笔记，关注话题，与其他用户互动。项目采用微服务架构设计，具备良好的扩展性和可维护性。

**技术定位**：面向学习和实战的微服务架构项目，涵盖主流微服务技术栈。

---

## 二、技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud | 2023.0.0 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.0 |
| 注册中心 | Nacos | 2.2.x |
| 网关 | Spring Cloud Gateway | - |
| 认证 | Sa-Token | 1.38.0 |
| ORM | MyBatis | 3.0.3 |
| 数据库 | MySQL | 8.0+ |
| 键值存储 | Cassandra | - |
| 对象存储 | MinIO | 8.2.1 |
| 缓存 | Redis | - |
| 本地缓存 | Caffeine | 3.1.8 |
| 消息队列 | RocketMQ | 5.3.1 |
| 分布式ID | 美团Leaf（Segment/Snowflake） | - |
| HTTP客户端 | OpenFeign | - |
| JSON处理 | Jackson | 2.16.1 |
| 工具类 | Hutool | 5.8.26 |
| 工具类 | Guava | 33.0.0-jre |
| 连接池 | Druid | 1.2.23 |

---

## 三、架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      客户端（Web/App）                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   bluenote-gateway（网关层）                    │
│  • 请求路由转发 • 统一认证鉴权（Sa-Token） • 全局异常处理       │
└─────────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┼────────────────────┐
         ▼                    ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ bluenote-auth   │  │ bluenote-user   │  │ bluenote-note   │  │bluenote-user-rel│
│   认证服务       │  │   用户服务       │  │   笔记服务       │  │   用户关系服务    │
│  • 登录注册     │  │  • 用户管理     │  │  • 笔记CRUD     │  │  • 关注/取关     │
│  • 验证码       │  │  • 权限管理     │  │  • 话题管理     │  │  • 粉丝列表     │
│  • Sa-Token     │  │  • 角色同步     │  │  • 缓存策略     │  │  • Redis ZSet   │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│bluenote-oss     │  │bluenote-kv      │  │bluenote-id-gen  │
│   对象存储       │  │   键值存储       │  │   分布式ID      │
│  • MinIO        │  │  • Cassandra    │  │  • Leaf         │
│  • 文件上传     │  │  • 笔记内容     │  │  • Segment      │
└─────────────────┘  └─────────────────┘  │  • Snowflake    │
                                          └─────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    基础设施层                                   │
│  MySQL • Redis • Cassandra • MinIO • RocketMQ • Nacos         │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 模块划分

| 模块 | 职责 | 端口 |
|------|------|------|
| `bluenote-gateway` | API网关，路由转发，统一鉴权 | 8000 |
| `bluenote-auth` | 用户认证，登录注册，验证码 | 8081 |
| `bluenote-user` | 用户管理，角色权限，信息更新 | 8082 |
| `bluenote-user-relation` | 用户关系，关注/取关，粉丝列表 | 8087 |
| `bluenote-note` | 笔记发布，内容管理，话题管理 | 8086 |
| `bluenote-oss` | 文件存储，图片/视频上传 | 8083 |
| `bluenote-kv` | 笔记内容存储，基于Cassandra | 8084 |
| `bluenote-distributed-id-generator` | 分布式ID生成（Segment/Snowflake） | 8085 |
| `bluenote-framework` | 框架基础组件，Starter封装 | - |

### 3.3 框架层组件

| 组件 | 说明 |
|------|------|
| `bluenote-common` | 通用工具类，响应体，异常处理 |
| `bluenote-spring-boot-starter-biz-context` | 用户上下文传递，TransmittableThreadLocal |
| `bluenote-spring-boot-starter-biz-operationlog` | 接口操作日志切面 |
| `bluenote-spring-boot-starter-jackson` | Jackson配置，Java8日期序列化 |

---

## 四、核心功能

### 4.1 认证模块（bluenote-auth）

- **登录认证**：手机号+验证码登录，账号密码登录
- **验证码服务**：阿里云短信验证码发送
- **Sa-Token集成**：登录态管理，Token认证
- **告警通知**：短信告警、邮件告警接口

### 4.2 用户模块（bluenote-user）

- **用户管理**：用户注册、信息查询、信息更新
- **权限管理**：角色-权限数据同步到Redis
- **Feign调用**：提供用户信息查询接口给其他服务

### 4.3 用户关系模块（bluenote-user-relation）

- **关注功能**：关注用户，Redis ZSet存储关注列表
- **取关功能**：取消关注，同步更新Redis和数据库
- **关注列表**：分页查询用户关注列表
- **粉丝列表**：分页查询用户粉丝列表
- **Lua脚本**：原子性操作保证数据一致性
- **MQ异步**：异步写入数据库，提升接口响应速度

### 4.4 笔记模块（bluenote-note）

- **笔记发布**：图文笔记、视频笔记发布
- **笔记更新**：内容更新，KV存储同步
- **笔记删除**：逻辑删除，缓存清理
- **缓存策略**：Redis + Caffeine多级缓存
- **消息广播**：RocketMQ广播模式清除本地缓存
- **延迟双删**：解决缓存一致性问题

### 4.5 对象存储模块（bluenote-oss）

- **文件上传**：图片、视频上传到MinIO
- **Feign接口**：提供文件上传API供其他服务调用

### 4.5 键值存储模块（bluenote-kv）

- **笔记内容存储**：基于Cassandra的分布式存储
- **高可用**：支持多数据中心部署

### 4.6 分布式ID模块（bluenote-distributed-id-generator）

- **号段模式（Segment）**：高性能ID生成，适用于高并发场景
- **雪花算法（Snowflake）**：分布式唯一ID，支持多节点

---

## 五、运行环境要求

### 5.1 基础设施

| 服务 | 端口 | 说明 |
|------|------|------|
| Nacos | 8848 | 服务注册与配置中心 |
| MySQL | 3306 | 关系型数据库 |
| Redis | 6379 | 缓存服务 |
| Cassandra | 9042 | 键值存储 |
| MinIO | 9000 | 对象存储 |
| RocketMQ NameServer | 9876 | 消息队列 |
| RocketMQ Broker | 10911 | 消息队列 |

### 5.2 环境变量

```bash
# 阿里云短信配置（bluenote-auth）
ALIBABA_CLOUD_ACCESS_KEY_ID=your_access_key_id
ALIBABA_CLOUD_ACCESS_KEY_SECRET=your_access_key_secret

# MinIO配置（bluenote-oss）
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

---

## 六、快速开始

### 6.1 启动顺序

```bash
# 1. 启动基础设施
# Nacos、MySQL、Redis、Cassandra、MinIO、RocketMQ

# 2. 启动分布式ID服务
cd bluenote/bluenote-distributed-id-generator/bluenote-distributed-id-generator-biz
mvn spring-boot:run

# 3. 启动用户服务
cd bluenote/bluenote-user/bluenote-user-biz
mvn spring-boot:run

# 4. 启动认证服务
cd bluenote/bluenote-auth
mvn spring-boot:run

# 5. 启动对象存储服务
cd bluenote/bluenote-oss/bluenote-oss-biz
mvn spring-boot:run

# 6. 启动键值存储服务
cd bluenote/bluenote-kv/bluenote-kv-biz
mvn spring-boot:run

# 7. 启动笔记服务
cd bluenote/bluenote-note/bluenote-note-biz
mvn spring-boot:run

# 8. 启动用户关系服务
cd bluenote/bluenote-user-relation/bluenote-user-relation-biz
mvn spring-boot:run

# 9. 启动网关服务（最后启动）
cd bluenote/bluenote-gateway
mvn spring-boot:run
```

### 6.2 配置文件说明

| 文件 | 说明 |
|------|------|
| `application.yml` | 基础配置 |
| `application-dev.yml` | 开发环境配置 |
| `application-prod.yml` | 生产环境配置 |
| `bootstrap.yml` | 启动引导配置（Nacos配置中心） |
| `logback-spring.xml` | 日志配置 |

---

## 七、项目结构

```
bluenote/
├── bluenote-auth/                    # 认证服务
│   ├── src/main/java/com/tefire/auth/
│   │   ├── controller/              # 控制器
│   │   ├── service/                 # 服务层
│   │   ├── config/                  # 配置类
│   │   ├── sms/                     # 短信服务
│   │   └── alarm/                   # 告警服务
│   └── src/main/resources/
├── bluenote-gateway/                # 网关服务
│   ├── src/main/java/com/tefire/gateway/
│   │   ├── auth/                    # 认证配置
│   │   ├── filter/                  # 过滤器
│   │   └── exception/               # 异常处理
│   └── src/main/resources/
├── bluenote-user/                   # 用户服务
│   ├── bluenote-user-api/           # API接口
│   └── bluenote-user-biz/           # 业务实现
├── bluenote-note/                   # 笔记服务
│   ├── bluenote-note-api/           # API接口
│   └── bluenote-note-biz/           # 业务实现
├── bluenote-oss/                    # 对象存储服务
│   ├── bluenote-oss-api/            # API接口
│   └── bluenote-oss-biz/            # 业务实现
├── bluenote-kv/                     # 键值存储服务
│   ├── bluenote-kv-api/             # API接口
│   └── bluenote-kv-biz/             # 业务实现
├── bluenote-distributed-id-generator/  # 分布式ID服务
│   ├── bluenote-distributed-id-generator-api/
│   └── bluenote-distributed-id-generator-biz/
├── bluenote-user-relation/            # 用户关系服务
│   ├── bluenote-user-relation-api/    # API接口
│   └── bluenote-user-relation-biz/    # 业务实现
├── bluenote-framework/              # 框架层
│   ├── bluenote-common/             # 通用组件
│   ├── bluenote-spring-boot-starter-biz-context/
│   ├── bluenote-spring-boot-starter-biz-operationlog/
│   └── bluenote-spring-boot-starter-jackson/
└── pom.xml                          # 父POM
```

---

## 八、关键设计

### 8.1 认证流程

```
客户端请求 → Gateway → Sa-Token验证 → 业务服务
                              │
                              ▼
                    白名单接口（无需登录）
                    登录接口（获取Token）
                    其他接口（验证Token）
```

### 8.2 缓存策略

```
查询请求 → Caffeine本地缓存 → Redis缓存 → MySQL数据库
              ↓                  ↓
            更新成功          更新成功
              ↓                  ↓
         RocketMQ广播      延迟双删策略
         清除其他实例缓存    防止缓存不一致
```

### 8.3 笔记内容存储

```
笔记元数据（MySQL）←→ content_uuid ←→ 笔记内容（Cassandra）
         │                                 │
         │                                 │
         └────────── RocketMQ ─────────────┘
                   缓存同步通知
```

### 8.4 分布式事务处理

由于涉及跨存储（MySQL + Cassandra），采用**最终一致性**方案：

1. **先写次要存储（Cassandra）**，失败则不更新主存储
2. **再写主存储（MySQL）**，成功后发送MQ通知
3. **MQ失败通过重试机制补偿**

---

## 九、接口文档

项目提供完整的接口文档：

- [API文档（MD格式）](api-documentation.md) - 便于阅读
- [API文档（JSON格式）](api-documentation.json) - 用于APIFox测试

---

## 十、学习资源

项目包含详细的技术学习总结：

| 文档 | 主题 |
|------|------|
| [Nacos深度解析](学习总结/Nacos%20微服务注册中心与配置中心深度解析.md) | 服务注册与配置管理 |
| [Gateway鉴权解析](学习总结/Gateway%20网关%20Sa-Token%20鉴权与%20Nacos%20集成深度解析.md) | 网关鉴权与路由 |
| [分布式ID解析](学习总结/分布式ID生成服务——Segment与Snowflake双模式深度解析.md) | ID生成策略 |
| [ThreadLocal解析](学习总结/ThreadLocal%20与%20TransmittableThreadLocal%20深度解析.md) | 线程上下文传递 |
| [Spring事务解析](学习总结/Spring%20声明式%20vs%20编程式事务管理深度解析.md) | 事务管理 |
| [自定义Starter解析](学习总结/Spring%20Boot%20自定义%20Starter%20深度解析.md) | Starter封装 |
| [Cassandra解析](学习总结/Cassandra%20分布式NoSQL数据库深度解析.md) | 分布式存储 |
| [Feign解析](学习总结/Feign%20声明式HTTP客户端与微服务上下文透传深度解析.md) | HTTP客户端 |
| [Java Stream解析](学习总结/Java%20Stream%20流式编程深度解析.md) | 流式编程 |

---

## 十一、开发进度

| 模块 | 状态 | 完成度 |
|------|------|--------|
| bluenote-auth | ✅ 已完成 | 100% |
| bluenote-gateway | ✅ 已完成 | 100% |
| bluenote-user | ✅ 已完成 | 100% |
| bluenote-oss | ✅ 已完成 | 100% |
| bluenote-kv | ✅ 已完成 | 100% |
| bluenote-distributed-id-generator | ✅ 已完成 | 100% |
| bluenote-note | ✅ 已完成 | 100% |
| bluenote-user-relation | ✅ 已完成 | 100% |
| bluenote-framework | ✅ 已完成 | 100% |

---

## 十二、许可证

MIT License

---

## 十三、联系方式

如有问题或建议，欢迎提交 Issue 或 PR。