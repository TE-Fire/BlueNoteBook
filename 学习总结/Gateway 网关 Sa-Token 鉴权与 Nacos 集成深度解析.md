# Gateway 网关 Sa-Token 鉴权与 Nacos 集成深度解析

> 本文以"小蓝书"（BlueNoteBook）项目的 `bluenote-gateway` 模块为实际案例，深入讲解网关层如何使用 Sa-Token 完成接口鉴权，以及 Nacos 服务发现在网关路由中扮演的角色。我们不会罗列"Sa-Token 有哪些配置项"或"Nacos 有哪些功能"，而是沿着"一个 HTTP 请求从客户端发出到收到响应的完整旅程"，把每一个环节的**为什么**和**怎么做**讲透。

---

## 一、开篇：网关在微服务架构中的角色

### 1.1 从用户的请求到达系统讲起

假设你打开小蓝书 App，点了一下"发布笔记"。这个操作会触发一个 HTTP 请求：

```
POST /note/publish
Authorization: Bearer xxxxx_token_xxxxx
Body: { "title": "...", "content": "...", "images": [...] }
```

这个请求的目的地是 `bluenote-note`（笔记服务）。但从客户端的视角看，它只知道一个统一的域名——比如 `api.bluenote.com`。它不知道、也不应该知道后台有多少个微服务、每个微服务部署在哪些 IP 的哪些端口上。

**网关（Gateway）就是那个站在所有微服务前面的"门卫"。** 它的工作可以用一句话概括：

> 把每一个外部请求，**正确地**转发给**正确的**后端服务，在转发之前**检查**这个请求有没有资格进来。

"正确地"是路由转发，"检查资格"是认证鉴权。这两个职责，就是网关存在的根本原因。

### 1.2 为什么鉴权必须放在网关？

你可能会问：登录校验和权限检查为什么不能放在每个微服务自己里面做？

**可以，但不应该。** 假设你有 10 个微服务，每个都自己写一遍 Token 校验逻辑，会出现三个问题：

1. **代码重复**：同一套"解析 Token → 查 Redis → 校验有效期"的逻辑写了 10 遍。哪天 Token 格式要改，你得改 10 个地方。
2. **安全漏洞**：每个服务的开发者对安全的理解不同。A 服务校验得很严格，B 服务的开发者赶进度忘了加校验——黑客直接绕过网关，打 B 服务的内网地址，数据就裸奔了。
3. **性能浪费**：如果请求先经过网关到达 A 服务，A 服务再远程调用 B 服务，B 服务再做一次鉴权，那就是重复劳动。

**在网关层统一做鉴权，是微服务架构的最佳实践。** 请求到了网关这道"大门"就被校验，放行之后的内部服务之间可以信任彼此，不再重复鉴权。

### 1.3 小蓝书网关的技术选型

小蓝书的网关选择了三个核心组件：

| 组件 | 角色 | 为什么选它 |
|------|------|-----------|
| **Spring Cloud Gateway** | 网关框架 | 基于 Reactive（WebFlux）非阻塞模型，性能远高于传统的 Zuul 1.x；与 Spring Cloud 生态无缝集成 |
| **Sa-Token** | 权限认证框架 | 轻量、解耦、API 简洁；天然支持分布式（Redis 集成）；比 Spring Security 学习成本低一个数量级 |
| **Nacos** | 服务发现 | 网关需要知道 `bluenote-auth` 部署在哪些 IP 和端口上——Nacos 维护了这份"地址簿" |

下面我们逐一深入拆解每个组件在小蓝书中的具体工作方式。

---

## 二、网关路由转发 —— Nacos 服务发现的角色

> 本章聚焦网关如何使用 Nacos 服务发现完成路由转发。如果你对 Nacos 本身的注册中心原理（心跳机制、CAP 取舍、AP 模型等）已经了解，可以直接读下去。如果想系统了解 Nacos 的完整原理，请参阅同目录下的 [Nacos 微服务注册中心与配置中心深度解析.md](./Nacos%20微服务注册中心与配置中心深度解析.md)。

### 2.1 问题：网关怎么知道后端服务在哪？

先看小蓝书网关的路由配置（`application.yml`）：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth
          uri: lb://bluenote-auth
          predicates:
            - Path=/auth/**
          filters:
            - StripPrefix=1
```

这里最关键的一行是 `uri: lb://bluenote-auth`。它包含两个信息：

- `bluenote-auth` —— 目标服务的**名称**，不是 IP，不是域名
- `lb://` —— 代表 **Load Balance**，即负载均衡

这意味着网关在写路由规则时，**不关心** `bluenote-auth` 服务具体部署在哪台机器的哪个端口上。它只需要知道服务名，剩下的交给 Nacos。

### 2.2 Nacos 在网关中的角色：服务发现（只做这一件事）

网关的 `bootstrap.yml` 配置：

```yaml
spring:
  application:
    name: bluenote-gateway
  cloud:
    nacos:
      discovery:
        enabled: true
        group: DEFAULT_GROUP
        namespace: bluenote
        server-addr: 127.0.0.1:8848
```

注意：网关只启用了 `discovery`（服务发现），**没有启用 `config`（配置中心）**。这与 `bluenote-auth` 模块不同——auth 模块同时启用了 discovery 和 config。

这个设计是合理的：网关的配置通常是路由规则和安全规则，属于应用架构层面的决策，不适合频繁热更新。而 auth 服务的业务配置（如短信告警开关）需要动态调整，所以它额外使用了配置中心。

### 2.3 `lb://bluenote-auth` 的完整工作流程

当网关收到一个匹配 `/auth/**` 的请求时，它执行以下步骤：

```
1. 解析路由规则
   uri: lb://bluenote-auth  → 提取服务名 "bluenote-auth"

2. 查询 Nacos
   向 Nacos Server (127.0.0.1:8848) 查询：命名空间 "bluenote"、分组 "DEFAULT_GROUP" 下，
   服务名为 "bluenote-auth" 的所有健康实例

3. Nacos 返回实例列表
   [
     { ip: "192.168.1.10", port: 8080, healthy: true, weight: 1 },
     { ip: "192.168.1.11", port: 8081, healthy: true, weight: 1 },
   ]

4. 负载均衡选一个实例
   Spring Cloud LoadBalancer 根据策略（默认轮询）选出一个实例

5. 路径改写（StripPrefix=1）
   /auth/user/login  →  去掉第一段  →  /user/login

6. 转发请求
   将改写后的请求转发到选中的实例：http://192.168.1.10:8080/user/login
```

这个过程有几点值得展开：

**为什么是 `lb://` 而不是 `http://`？** `http://` 意味着你写死了目标地址，Nacos 不参与。`lb://` 告诉 Spring Cloud Gateway："去注册中心查这个服务名对应的实例列表，并用负载均衡选一个"。这是网关与 Nacos 集成的核心桥梁。

**负载均衡在哪个层面？** 是在网关这一层完成的。网关拿到 Nacos 返回的 3 个实例后，自己用 LoadBalancer 选一个转发。不是 Nacos 帮你选，也不是客户端帮你选。

**StripPrefix=1 的意义**：外部路径 `/auth/user/login` 到了网关，网关匹配到路由规则后，去掉第一段路径 `/auth`，变成 `/user/login` 再转发给 auth 服务。这样 auth 服务的 Controller 上写 `@RequestMapping("/user")` 就能正常工作——它不需要感知网关前缀的存在。

### 2.4 网关 vs Auth：Nacos 使用的差异一览

| 维度 | bluenote-gateway | bluenote-auth |
|------|-----------------|---------------|
| **服务发现** | ✅ 启用（注册自身 + 发现其他服务） | ✅ 启用 |
| **配置中心** | ❌ 未启用 | ✅ 启用（动态刷新） |
| **注册服务名** | `bluenote-gateway` | `bluenote-auth` |
| **核心用途** | 发现下游服务地址，完成路由转发 | 注册自己供网关发现；动态配置管理 |

---

## 三、Sa-Token 鉴权过滤器 —— 第一道防线

路由转发解决了"请求去哪"的问题。接下来解决"请求能不能去"的问题。

### 3.1 SaReactorFilter：网关层的鉴权入口

小蓝书网关中，所有鉴权逻辑都集中在一个 Bean 中：[SaTokenConfigure.java](bluenote/bluenote-gateway/src/main/java/com/tefire/gateway/auth/SaTokenConfigure.java)

```java
@Configuration
public class SaTokenConfigure {

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 1. 拦截所有路径
                .addInclude("/**")
                // 2. 鉴权规则
                .setAuth(obj -> {
                    // 登录校验：除白名单外全部需要登录
                    SaRouter.match("/**")
                            .notMatch("/auth/user/login")
                            .notMatch("/auth/verification/code/send")
                            .check(r -> StpUtil.checkLogin());

                    // 权限校验：退出登录需要特定权限
                    SaRouter.match("/auth/user/logout",
                            r -> StpUtil.checkPermission("app:note:delete"));
                })
                // 3. 异常处理
                .setError(e -> SaResult.error(e.getMessage()));
    }
}
```

这个配置虽然只有二十几行，但包含了网关鉴权的全部核心逻辑。我们拆解来看。

### 3.2 `addInclude("/**")` —— 拦截范围

`/**` 意味着**所有**到达网关的 HTTP 请求都必须经过这个过滤器。不管你访问 `/auth/user/login` 还是 `/auth/note/publish` 还是任何其他路径，都得先过这一关。

但"全部拦截"不代表"全部校验"——过滤器只是拿到了检查权，具体什么规则、什么路径放行，由下面的 `setAuth()` 决定。

**为什么用 `SaReactorFilter` 而不是 Spring 的 `WebFilter`？** 因为 Spring Cloud Gateway 底层是 WebFlux（响应式），传统的 Servlet Filter 在这里不工作。`SaReactorFilter` 是 Sa-Token 为响应式网关专门提供的过滤器实现，它内部对接了 WebFlux 的请求响应模型。

### 3.3 路径匹配规则：白名单设计

```java
SaRouter.match("/**")
        .notMatch("/auth/user/login")
        .notMatch("/auth/verification/code/send")
        .check(r -> StpUtil.checkLogin());
```

这段代码的逻辑是：

1. `match("/**")` —— 匹配所有请求
2. `.notMatch("/auth/user/login")` —— 排除登录接口
3. `.notMatch("/auth/verification/code/send")` —— 排除验证码发送接口
4. `.check(r -> StpUtil.checkLogin())` —— 对剩余的所有路径，强制执行登录校验

这是一个典型的**白名单模式**：默认全部需要登录，只显式排除少数公开接口。这种设计更安全——新加一个接口默认就是受保护的，开发者必须**有意识地**把它加入白名单才能对外开放。

为什么这两个接口不需要登录？

- `/auth/user/login`：用户还没登录呢，当然不能要求登录
- `/auth/verification/code/send`：用户正在"准备登录"（获取验证码），也还没登录

### 3.4 `StpUtil.checkLogin()` 内部做了什么？

当网关执行 `StpUtil.checkLogin()` 时，Sa-Token 框架内部执行如下流程：

```
1. 从请求头中读取 Token
   配置中指定了 token-name: Authorization, token-prefix: Bearer
   → 解析请求头 "Authorization: Bearer xxxxx_token_xxxxx"
   → 提取 Token 值 "xxxxx_token_xxxxx"

2. 根据 Token 去 Redis 查询登录会话
   Sa-Token 内部以 "Authorization:login:token:{tokenValue}" 为 key 查 Redis
   → 取出 value：{ "loginId": "10001", "loginType": "login", ... }

3. 判断
   - 会话存在且未过期 → 放行，并把 loginId 存入当前请求上下文
   - 会话不存在或已过期 → 抛出 NotLoginException
```

关键点：**Sa-Token 不做用户密码校验，它只做 Token 校验。** 用户密码校验是 `UserServiceImpl.loginAndRegister()` 做的事——验证通过后调用 `StpUtil.login(userId)` 把登录态写入 Redis。到了网关层，只认 Token 不认人。

### 3.5 `StpUtil.checkPermission()` —— 权限校验

```java
SaRouter.match("/auth/user/logout",
        r -> StpUtil.checkPermission("app:note:delete"));
```

这行代码的含义是：访问 `/auth/user/logout` 的用户，除了必须登录之外，还必须拥有 `"app:note:delete"` 这个权限。

注意这里的写法——`checkPermission` 不是一个返回 boolean 的方法，而是**不满足就抛异常**。所以：

- 权限匹配 → 静默通过，代码继续往下走
- 权限不匹配 → 抛出 `NotPermissionException` → 被 `setError()` 捕获 → 返回错误信息给客户端

本项目中 `/auth/user/logout` 要求 `"app:note:delete"` 权限，这个组合看起来有些不自然（退出登录通常不需要笔记删除权限）。这更像是开发初期的占位验证——确认权限校验机制能正常工作后，后续会换成更合理的权限定义。

### 3.6 `setError()` —— 异常统一处理

```java
.setError(e -> SaResult.error(e.getMessage()));
```

所有在 `setAuth()` 中抛出的异常（`NotLoginException`、`NotPermissionException`、`NotRoleException`），都会被这个 lambda 捕获。它把异常信息包装成 `SaResult.error()` 返回给客户端，格式统一，不会把堆栈信息泄露到前端。

---

## 四、权限数据模型 —— Redis 中的用户-角色-权限体系

第三章讲了 Sa-Token **怎么校验**权限。但校验需要一个"对答案"的环节——你说用户有权限 A，依据是什么？这个依据存储在 Redis 中。

### 4.1 三张核心数据表与它们的 Redis 映射

小蓝书的权限体系基于经典的 **RBAC（Role-Based Access Control）** 模型。数据库中有三张核心表：

```
t_user ──多对多── t_user_role_rel ──多对多── t_role
                                                  │
                                           t_role_permission_rel
                                                  │
                                              t_permission
```

但在 Redis 中，这个关系被**扁平化**为两层 key-value：

```
Redis Key                        Redis Value (JSON)
─────────────────────────────────────────────────────
user:roles:10001                 ["common_user"]
user:roles:10002                 ["admin", "editor"]

role:permissions:common_user     ["app:note:view", "app:note:create"]
role:permissions:admin           ["app:note:delete", "app:user:manage", ...]
role:permissions:editor          ["app:note:edit", "app:note:publish"]
```

这个设计有几个精妙之处：

**为什么不在网关里直接查数据库？** 因为性能。网关是流量入口，每个请求都要鉴权。如果每次鉴权都去 MySQL 走三表 JOIN 查权限，数据库很快会成为瓶颈。Redis 的内存读写是微秒级，可以支撑高并发。

**为什么是两层而不是一层？** 如果只有"用户-权限"一层（`user:permissions:10001 → ["perm1", "perm2", ...]`），当管理员修改了一个角色的权限定义时，需要遍历所有拥有该角色的用户，逐个更新他们的权限列表。两层设计让角色成为"中间层"——改角色的权限只需要更新 `role:permissions:{roleKey}` 一条 key，所有拥有该角色的用户自动生效。

### 4.2 权限数据如何进入 Redis？

权限数据的写入由 **`PushRolePermissions2RedisRunner`** 负责。它是一个 `ApplicationRunner`，在 `bluenote-auth` 服务启动时执行：

```java
@Component
public class PushRolePermissions2RedisRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // 1. 幂等控制：24 小时内只执行一次
        boolean canPush = redisTemplate.opsForValue()
            .setIfAbsent("push.permission.flag", "1", 1, TimeUnit.DAYS);
        if (!canPush) return;

        // 2. 查所有启用的角色
        List<RoleDO> roles = roleDOMapper.selectEnabledList();

        // 3. 批量查角色-权限关联
        List<RolePermissionDO> rolePerms = rolePermissionDOMapper.selectByRoleIds(roleIds);

        // 4. 查所有 APP 类型权限（type=3）
        List<PermissionDO> perms = permissionDOMapper.selectAppEnabledList();

        // 5. 组装 Map<roleKey, List<permissionKey>>
        Map<String, List<String>> roleKeyPermissionsMap = ...;

        // 6. 写入 Redis
        roleKeyPermissionsMap.forEach((roleKey, permissions) -> {
            String key = "role:permissions:" + roleKey;
            redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(permissions));
        });
    }
}
```

值得关注的几个设计细节：

**幂等控制（`setIfAbsent`）**：如果 auth 服务重启了，权限数据不会重复写入——24小时内只同步一次。`push.permission.flag` 这个 key 的 TTL 设为 1 天，过期后下次启动会重新同步。

**只有 `type=3` 的权限被同步**：`PermissionDOMapper.selectAppEnabledList()` 的 SQL 条件是 `type = 3`。这意味着只有 APP 级别的权限（接口访问权限）才进入 Redis。菜单权限（type=1）、按钮权限（type=2）不走网关鉴权，由前端或其他服务自行处理。

**启动时同步的局限性**：如果运维在数据库里新增了一个权限，赋给了某个角色，这个权限要等到下次 `push.permission.flag` 过期（24小时后）或手动删除该 Redis key 后才能生效。生产环境通常会配合配置中心（如 Nacos config）做一个"权限刷新"的管理接口，运维手动触发即时刷新。

### 4.3 用户角色数据如何写入？

角色-权限的映射由启动 Runner 同步，但**用户的角色分配**是在注册/登录时写入的。看 `UserServiceImpl.registerUser()` 的关键代码：

```java
public Long registerUser(String phone) {
    return transactionTemplate.execute(status -> {
        try {
            // 1. 生成用户 ID（Redis 自增）
            Long userId = redisTemplate.opsForValue().increment("bluenote.id.generator");
            // 2. 插入用户记录到 t_user
            UserDO userDO = buildUserDO(phone, userId);
            userDOMapper.insert(userDO);
            // 3. 分配默认角色（COMMON_USER_ROLE_ID = 1L）
            UserRoleDO userRoleDO = new UserRoleDO();
            userRoleDO.setUserId(userId);
            userRoleDO.setRoleId(RoleConstants.COMMON_USER_ROLE_ID);
            userRoleDOMapper.insert(userRoleDO);
            // 4. 查角色的 roleKey
            RoleDO roleDO = roleDOMapper.selectById(RoleConstants.COMMON_USER_ROLE_ID);
            // 5. 写入 Redis: user:roles:{userId} → ["common_user"]
            String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
            List<String> roleKeys = List.of(roleDO.getRoleKey());
            redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roleKeys));
            return userId;
        } catch (Exception e) {
            status.setRollbackOnly();
            return null;
        }
    });
}
```

这里有三个关键点：

**编程式事务**：使用的是 `TransactionTemplate`，不是 `@Transactional` 注解。选它是因为 Redis 操作（第 1 步的 `increment` 和第 5 步的 `set`）不受 Spring 事务管理——如果数据库操作失败，Redis 的 `increment` 已经执行了（Redis 事务是独立的），需要你在代码里做补偿决策。编程式事务让你能显式控制回滚逻辑。

**ID 生成策略**：使用 Redis 的 `INCR` 命令做全局自增 ID。这不是业界最优方案（Twitter Snowflake 雪花算法更好），但在小规模场景下足够简单可靠。`INCR` 是原子操作，不存在并发竞争。

**Gateway 与 Auth 共享 Redis**：网关和 auth 服务连接的是同一个 Redis 实例（`localhost:6379, database:0`）。auth 写入 `user:roles:{userId}`，网关读取 `user:roles:{userId}` 做鉴权。这是两者协作的基础——如果它们连的不是同一个 Redis，网关查不到任何权限数据，所有已登录用户的请求都会被拒绝。

---

## 五、StpInterfaceImpl —— 鉴权数据的"数据源"

第四章讲了 Redis 里存了什么数据。但 Sa-Token 框架并不知道你的数据存在哪里、格式是什么。**`StpInterface` 就是你和 Sa-Token 框架之间的"翻译官"**——框架说"我要这个用户的权限列表"，`StpInterface` 负责从 Redis 取数据并返回。

### 5.1 `StpInterface` 在 Sa-Token 框架中的角色

Sa-Token 框架的鉴权流程可以用下面这张图概括：

```
StpUtil.checkPermission("app:note:delete")
         │
         ▼
   Sa-Token 框架内核
   "用户 10001 有 app:note:delete 权限吗？我不知道，去问 StpInterface"
         │
         ▼
   StpInterfaceImpl.getPermissionList(10001, "login")
         │
         ▼
   查 Redis: user:roles:10001 → ["common_user"]
   返回 ["common_user"]  ← 等等，这不是权限，这是角色！
```

这里有一个本项目的特殊设计：**方法名和实际返回值是"交叉"的。** 往下看你就明白了。

### 5.2 两个方法的分工（与本项目的特殊设计）

```java
@Component
public class StpInterfaceImpl implements StpInterface {

    // 方法名：getPermissionList —— "获取权限列表"
    // 实际返回：用户的角色 keys（如 ["common_user"]）
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String userRolesKey = "user:roles:" + loginId;
        String json = redisTemplate.opsForValue().get(userRolesKey);
        if (StringUtils.isBlank(json)) return null;
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    // 方法名：getRoleList —— "获取角色列表"
    // 实际返回：用户的权限 keys（如 ["app:note:view", "app:note:create"]）
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 1. 取用户角色
        String userRolesKey = "user:roles:" + loginId;
        String json = redisTemplate.opsForValue().get(userRolesKey);
        List<String> userRoleKeys = objectMapper.readValue(json, ...);

        // 2. 角色 → 权限映射
        List<String> rolePermissionKeys = userRoleKeys.stream()
            .map(roleKey -> "role:permissions:" + roleKey)
            .toList();

        // 3. 批量查 Redis
        List<String> permissionsJson = redisTemplate.opsForValue()
            .multiGet(rolePermissionKeys);

        // 4. 合并所有权限到一个列表
        List<String> allPermissions = new ArrayList<>();
        for (String permJson : permissionsJson) {
            List<String> perms = objectMapper.readValue(permJson, ...);
            allPermissions.addAll(perms);
        }
        return allPermissions;
    }
}
```

为什么这么设计？这和 `SaTokenConfigure` 中的鉴权代码对应：

```java
// SaTokenConfigure 中使用了 checkPermission（不是 checkRole）
SaRouter.match("/auth/user/logout",
        r -> StpUtil.checkPermission("app:note:delete"));
```

而数据模型是"用户 → 角色 → 权限"。如果 `getPermissionList` 直接返回权限，就需要在 `getPermissionList` 里做角色到权限的转换（查两次 Redis）。当前的实现把角色查询放在 `getPermissionList`，权限查询放在 `getRoleList`，分工更清晰。

> **注意**：这种交叉设计不是 Sa-Token 框架的要求，而是本项目的实现选择。如果调用 `StpUtil.checkRole("admin")`，框架会调用 `getRoleList()`，此时返回的却是权限列表，角色校验就会失败。目前项目中只用到了 `checkPermission`，没有用到 `checkRole`，所以这个问题暂时没有暴露。

### 5.3 `multiGet` 批量查询的性能考量

```java
List<String> rolePermissionsKeys = userRoleKeys.stream()
    .map(roleKey -> "role:permissions:" + roleKey)
    .toList();

List<String> rolePermissionsValues = redisTemplate.opsForValue()
    .multiGet(rolePermissionsKeys);
```

这段代码值得单独提出来讲。一个用户通常有 1~3 个角色，需要查询 1~3 个 Redis key。

- **不推荐的写法**：在 for 循环里逐个调用 `redisTemplate.opsForValue().get(key)`。3 次 get 就是 3 次网络往返（Redis 是 TCP 协议）。
- **推荐写法**：用 `multiGet` 一次性提交所有 key，Redis 服务端一次返回所有结果。**1 次网络往返搞定。**

在网关这种每个请求都要鉴权的高频场景下，减少网络往返次数的收益是指数级的。`multiGet` 是 Redis 提供的原生批量查询命令，不是 pipeline 模拟——它一次 TCP 包携带多个 key，返回一次 TCP 包携带多个 value。

### 5.4 从 role keys 到 permission keys 的转换过程

```java
// userRoleKeys = ["common_user", "editor"]
//      ↓ stream().map(RedisKeyConstants::buildRolePermissionsKey)
// rolePermissionsKeys = ["role:permissions:common_user", "role:permissions:editor"]
//      ↓ multiGet()
// rolePermissionsValues = [
//     "[\"app:note:view\",\"app:note:create\"]",    ← common_user 的权限
//     "[\"app:note:edit\",\"app:note:publish\"]"     ← editor 的权限
// ]
//      ↓ forEach → readValue → addAll
// allPermissions = ["app:note:view", "app:note:create", "app:note:edit", "app:note:publish"]
```

`RedisKeyConstants.buildRolePermissionsKey()` 做的事情极其简单——字符串拼接：

```java
public static String buildRolePermissionsKey(String roleKey) {
    return "role:permissions:" + roleKey;
}
```

但不要因为它简单就忽视它的价值。**把这个拼接逻辑封装成一个方法，而不是在 StpInterfaceImpl 里直接写 `"role:permissions:" + roleKey`**，带来的好处是：

- **统一管理**：如果将来 key 前缀从 `role:permissions:` 改成 `perm:role:`，只改一个地方
- **避免拼写错误**：手写字符串 `"role:permissionss:"` 多了一个 s，编译器不会报错，但查不出数据
- **语义清晰**：`buildRolePermissionsKey(roleKey)` 比 `"role:permissions:" + roleKey` 更能表达意图

### 5.5 框架判断与放行机制（核心）

这是整个鉴权流程中最容易被忽略但又最关键的一环：**Sa-Token 框架内部是如何根据 StpInterface 返回的数据，决定放行还是拒绝一个请求的？**

#### 5.5.1 `StpUtil.checkLogin()` 的判断链路

当 `SaTokenConfigure` 中执行 `StpUtil.checkLogin()` 时：

```
1. 框架从请求头提取 Token
   "Authorization: Bearer abc123..." → 提取 "abc123..."

2. 框架根据 Token 去 Redis 查会话
   内部 key: "Authorization:login:token:abc123..."
   Redis 返回: { "loginId": "10001", "loginType": "login", "tokenName": "Authorization", ... }

3. 判断分支
   ┌─ Redis 中存在且未过期 → 静默通过，把 loginId 绑定到当前线程上下文
   │
   └─ Redis 中不存在或已过期 → 抛出 NotLoginException(message: "未登录")
                                 → SaReactorFilter.setError() 捕获
                                 → 返回 {"code": 501, "msg": "未登录"} 给客户端
```

**关键点**：框架不关心你是谁（用户名/密码），只关心你的 Token 是否在 Redis 中对应一个有效的登录会话。这个登录会话是 `StpUtil.login(userId)` 写入的，发生在 auth 服务的登录接口中。

#### 5.5.2 `StpUtil.checkPermission("app:note:delete")` 的判断链路

```
1. 框架获取当前请求的 loginId
   从上一步 checkLogin() 绑定的上下文中拿到 loginId = 10001

2. 框架调用 StpInterfaceImpl.getPermissionList(10001, "login")
   返回: ["common_user"]  ← 注意，本项目中返回的是角色 keys

3. 框架遍历返回列表，逐个比对
   for (String perm : ["common_user"]) {
       if ("app:note:delete".equals(perm)) {
           return;  // 匹配成功，放行
       }
   }

4. 遍历完没找到匹配
   → 抛出 NotPermissionException(message: "无此权限：app:note:delete")
   → SaReactorFilter.setError() 捕获
   → 返回 {"code": 502, "msg": "无此权限：app:note:delete"} 给客户端
```

**这里揭示了本项目的设计问题**：`getPermissionList` 返回的是角色 keys（`["common_user"]`），不是权限 keys（`["app:note:delete", ...]`）。所以 `StpUtil.checkPermission("app:note:delete")` 拿 `"app:note:delete"` 去和 `"common_user"` 比对，**永远匹配不上**。

正确的做法应该是：

- **方案 A**：`getPermissionList` 返回权限列表（从 `user:roles:{userId}` → `role:permissions:{roleKey}` 转换后返回）
- **方案 B**：在 `SaTokenConfigure` 中使用 `StpUtil.checkRole("common_user")` 来做角色校验，框架就会调用 `getRoleList()` 来比对

> 这是项目当前开发阶段的一个遗留问题，不影响理解整体鉴权机制的设计思路。

#### 5.5.3 `StpUtil.checkRole("admin")` 的判断链路（补充）

```
1. 框架调用 StpInterfaceImpl.getRoleList(10001, "login")
   返回: ["app:note:view", "app:note:create", ...]  ← 本项目返回的是权限列表

2. 框架遍历返回列表，逐个比对
   for (String role : ["app:note:view", ...]) {
       if ("admin".equals(role)) {
           return;  // 匹配成功
       }
   }

3. 遍历完没找到
   → 抛出 NotRoleException(message: "无此角色：admin")
```

#### 5.5.4 核心设计哲学：框架是"裁判"，StpInterface 是"证据提供方"

理解这个类比非常重要：

| 角色 | 对应组件 | 职责 |
|------|---------|------|
| **裁判** | Sa-Token 框架内核 | 接收鉴权请求（`checkXxx`），向 `StpInterface` 索要数据，执行比对，判断通过或拒绝 |
| **证据提供方** | `StpInterfaceImpl` | 从 Redis/数据库/文件等任何数据源获取角色和权限列表，返回给框架 |
| **规则制定者** | `SaTokenConfigure` | 定义哪些路径需要登录、哪些路径需要什么权限 |

**框架不存储任何业务数据。** 它不维护用户表、角色表、权限表。它只是一个**规则引擎**——你告诉它规则（什么路径要什么权限），你提供数据源（`StpInterface`），它负责执行判断。

这也意味着：`StpInterface` 返回 `null` 或空列表时，框架不会"宽容"地放行。**没有数据 = 没有权限 = 拒绝。** 这是一个"默认拒绝"的安全模型，符合安全设计的基本原则。

---

## 六、完整鉴权链路串联 —— 一次请求的完整旅程

把前面五章的内容串联起来，用一个具体请求走一遍完整流程。

### 6.1 场景一：用户登录

```
客户端                                    Gateway(8000)                    Auth(8080)
  │                                           │                               │
  │  POST /auth/user/login                     │                               │
  │  Body: {phone, code, type:1}               │                               │
  │ ─────────────────────────────────────────→ │                               │
  │                                           │                               │
  │                           ① SaReactorFilter 拦截                          │
  │                           match("/**")                                    │
  │                           .notMatch("/auth/user/login") → 跳过登录校验     │
  │                                                                          │
  │                           ② 路由匹配                                     │
  │                           Path=/auth/** → 命中 auth 路由                   │
  │                           StripPrefix=1 → /auth/user/login → /user/login │
  │                           uri: lb://bluenote-auth                         │
  │                           → 查 Nacos 获取 auth 实例列表                    │
  │                           → 负载均衡选一个实例                            │
  │                           → 转发                                          │
  │                                           │ ──────────────────────────────→│
  │                                           │                               │
  │                                           │              ③ UserController.loginAndRegister()
  │                                           │                 → 验证码校验（查 Redis）
  │                                           │                 → 查用户或注册新用户
  │                                           │                 → 分配默认角色
  │                                           │                 → 写 user:roles:{userId} 到 Redis
  │                                           │                               │
  │                                           │              ④ StpUtil.login(userId)
  │                                           │                 → 生成 Token (random-128)
  │                                           │                 → 写登录会话到 Redis
  │                                           │                    key: "Authorization:login:token:{token}"
  │                                           │                    value: {loginId, loginType, ...}
  │                                           │                               │
  │                                           │ ←──────────────────────────── │
  │                                           │     返回 {success: true,      │
  │                                           │            data: "abc123..."} │
  │ ←─────────────────────────────────────────│                               │
  │    返回 Token: "abc123..."                │                               │
```

**关键步骤说明**：

- 步骤①：`SaReactorFilter` 虽然拦截了请求，但因为路径在 `.notMatch()` 白名单中，跳过了 `checkLogin()`，直接放行到路由匹配
- 步骤②：网关通过 Nacos 发现 auth 服务，`lb://` 做负载均衡。如果 auth 部署了 3 个实例，网关选一个转发
- 步骤③：auth 服务的业务逻辑——校验验证码、创建用户、分配角色、写 Redis
- 步骤④：`StpUtil.login()` 是 Sa-Token 的核心方法，它生成 Token 并写入 Redis 登录会话。注意这个会话和 `user:roles:{userId}` 是不同的 Redis key：
  - Sa-Token 会话 key：`Authorization:login:token:{tokenValue}` → 用于 `checkLogin()` 校验
  - 角色数据 key：`user:roles:{userId}` → 用于 `getPermissionList()` / `getRoleList()` 查询

### 6.2 场景二：已登录用户请求受保护接口（如退出登录）

```
客户端                                    Gateway(8000)
  │                                           │
  │  POST /auth/user/logout                    │
  │  Authorization: Bearer abc123...           │
  │ ─────────────────────────────────────────→ │
  │                                           │
  │                           ① SaReactorFilter 拦截
  │                           match("/**")
  │                           .notMatch → 不匹配白名单，进入 checkLogin()
  │
  │                           ② StpUtil.checkLogin()
  │                           → 从请求头提取 Token: "abc123..."
  │                           → 查 Redis: "Authorization:login:token:abc123..."
  │                           → 存在 → 放行，loginId=10001 绑定到上下文
  │                           (如果不存在 → 抛 NotLoginException → 返回 501)
  │
  │                           ③ match("/auth/user/logout")
  │                           → StpUtil.checkPermission("app:note:delete")
  │                           → 框架调用 StpInterfaceImpl.getPermissionList(10001)
  │                           → 查 Redis: user:roles:10001 → ["common_user"]
  │                           → 框架比对: "app:note:delete" ∈ ["common_user"] ?
  │                           → 不在 → 抛 NotPermissionException → 返回 502
  │
  │                           (如果权限匹配 → 继续路由转发)
  │                                           │
  │ ←─────────────────────────────────────────│
  │    401/500 错误: "无此权限：app:note:delete"
```

**关键点**：

- 步骤②的 `checkLogin()` 发生在步骤③的 `checkPermission()` 之前。如果登录都不通过，根本不会走到权限校验——"你是谁" 在 "你能干什么" 之前
- 步骤③中，框架不关心 `getPermissionList` 内部查了几次 Redis、做了什么映射，它只关心最终返回的 `List<String>`
- 权限校验失败时，网关直接返回错误，**请求不会到达 auth 服务**——这就是"在网关层拦住非法请求"的意义

### 6.3 架构全景图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Nacos Server (8848)                           │
│  ┌─────────────────────────┐    ┌─────────────────────────────────┐    │
│  │  服务注册 (Discovery)     │    │  配置中心 (Config) — auth 使用  │    │
│  │  bluenote-auth: [       │    │  bluenote-auth.yaml             │    │
│  │    192.168.1.10:8080,   │    │  (动态刷新 @RefreshScope)       │    │
│  │    192.168.1.11:8081    │    │                                  │    │
│  │  ]                      │    │                                  │    │
│  └─────────────────────────┘    └─────────────────────────────────┘    │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │ 查询服务实例
                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        bluenote-gateway (:8000)                           │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  SaReactorFilter                                                  │   │
│  │  ┌─────────────┐   ┌──────────────────┐   ┌──────────────────┐   │   │
│  │  │ 路径匹配     │ → │ checkLogin()     │ → │ checkPermission()│   │   │
│  │  │ + 白名单     │   │ 查 Redis 会话    │   │ 调 StpInterface  │   │   │
│  │  └─────────────┘   └──────────────────┘   └──────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                              │                                           │
│  ┌───────────────────────────┴──────────────────────────────────────┐   │
│  │  StpInterfaceImpl                                                 │   │
│  │  user:roles:{userId} ──→ role:permissions:{roleKey} ──→ 权限列表  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                              │                                           │
│  ┌───────────────────────────┴──────────────────────────────────────┐   │
│  │  路由转发: lb://bluenote-auth → Nacos 发现 → 负载均衡 → 转发      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │ lb://bluenote-auth
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                  bluenote-auth (:8080) × N 实例                           │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  启动时: PushRolePermissions2RedisRunner                         │    │
│  │  t_role + t_role_permission_rel + t_permission → Redis           │    │
│  │  role:permissions:common_user → ["app:note:view", ...]          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  登录时: UserServiceImpl.loginAndRegister()                      │    │
│  │  StpUtil.login(userId) → 写登录会话到 Redis                      │    │
│  │  user:roles:{userId} → ["common_user"]                          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          Redis (localhost:6379)                           │
│                                                                          │
│  Authorization:login:token:abc123... → {loginId: 10001, ...}             │
│  user:roles:10001 → ["common_user"]                                      │
│  role:permissions:common_user → ["app:note:view", "app:note:create"]     │
│  push.permission.flag → "1" (TTL: 1 day)                                 │
│  verification_code:138xxxx → "123456" (TTL: 3 min)                       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 七、总结与设计启示

### 7.1 核心设计原则回顾

小蓝书网关的鉴权体系体现了几个关键的设计原则：

**1. 关注点分离**

网关负责"请求能不能进来"，auth 服务负责"用户怎么认证身份"，数据库负责"数据怎么存储"，Redis 负责"查询怎么加速"。每一层只做自己最擅长的事，互不越界。`StpInterfaceImpl` 就是这种分层的粘合剂——它不关心 Sa-Token 内部怎么比对权限，也不关心 Redis 内部怎么存储数据，它只做一件事：**提供数据**。

**2. 默认拒绝，显式放行**

```
SaRouter.match("/**")            // 默认全部拦截
        .notMatch("/auth/user/login")  // 只有显式声明的路径才放行
        .notMatch("/auth/verification/code/send")
        .check(r -> StpUtil.checkLogin());
```

这不是"把所有路径列出来再剔除几个"，而是"全部堵上，只开几个口子"。安全设计的铁律：**白名单永远比黑名单安全。** 你不可能穷举所有可能的攻击路径，但你可以只开放必要的路径。

**3. 无状态 Token，有状态会话**

客户端拿到的是一个自包含的随机 Token（`random-128`），这个 Token 本身不包含任何用户信息。真正的会话数据（loginId、过期时间等）存在 Redis 中。这样做的好处是：
- Token 泄露后可以通过删除 Redis 中的会话立即使其失效（而 JWT 一旦签发就无法撤销）
- Token 本身不包含敏感信息，被截获也无法直接解读
- 缺点是需要 Redis 查询，但这个成本在网关鉴权场景下是必须付出的

**4. RBAC 模型的扁平化缓存**

数据库中的多表 JOIN 关系不适合高并发查询，所以在 Redis 中用两层 key-value 来缓存。`用户 → 角色` 和 `角色 → 权限` 分开存储，让角色成为可以独立更新的维度——修改角色的权限定义，不需要遍历所有用户。

### 7.2 现有设计的改进方向

作为开发阶段的项目，当前设计存在几个可以后续优化的点：

- **`StpInterfaceImpl` 中 `getPermissionList` 和 `getRoleList` 的返回语义应该对齐**：`getPermissionList` 应返回权限列表，`getRoleList` 应返回角色列表，这样 `checkPermission` 和 `checkRole` 都能正常工作
- **权限缓存的实时更新**：`PushRolePermissions2RedisRunner` 的 24 小时幂等机制适合稳定环境，但应该配合一个管理接口支持手动刷新
- **网关应启用 Nacos 配置中心**：路由规则、白名单路径这些配置如果能在 Nacos 中集中管理并动态刷新，就不用每次都改代码重新部署

### 7.3 关键文件索引

| 文件 | 模块 | 作用 |
|------|------|------|
| [SaTokenConfigure.java](bluenote/bluenote-gateway/src/main/java/com/tefire/gateway/auth/SaTokenConfigure.java) | gateway | SaReactorFilter 配置，定义拦截规则和鉴权逻辑 |
| [StpInterfaceImpl.java](bluenote/bluenote-gateway/src/main/java/com/tefire/gateway/auth/StpInterfaceImpl.java) | gateway | 自定义权限/角色数据获取，对接 Redis |
| [RedisKeyConstants.java](bluenote/bluenote-gateway/src/main/java/com/tefire/gateway/constant/RedisKeyConstants.java) | gateway | Redis Key 构建常量 |
| [bootstrap.yml](bluenote/bluenote-gateway/src/main/resources/bootstrap.yml) | gateway | Nacos 服务发现配置 |
| [application.yml](bluenote/bluenote-gateway/src/main/resources/application.yml) | gateway | 路由规则、Redis、Sa-Token 配置 |
| [UserServiceImpl.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/service/impl/UserServiceImpl.java) | auth | 登录注册、用户角色写入 Redis |
| [PushRolePermissions2RedisRunner.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/runner/PushRolePermissions2RedisRunner.java) | auth | 启动时同步角色-权限到 Redis |
