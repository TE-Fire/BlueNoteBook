# Feign 声明式 HTTP 客户端与微服务上下文透传深度解析

> 本文以"小蓝书"（BlueNoteBook）项目为实际案例，结合项目中 `FeignRequestInterceptor`、`LoginUserContextHolder`、`UserFeignApi`、`FileFeignApi` 等真实代码，深入讲解 Feign 在微服务架构中的设计思想、框架结构、核心原理，以及你实现的"用户 ID 跨服务透传"机制。

---

## 一、开篇：微服务间的"打电话"问题

在上一篇 Nacos 文章中，我们解决了"服务 A 如何找到服务 B"的问题——Nacos 服务发现让 `bluenote-auth` 知道 `bluenote-user` 有哪些实例、IP 和端口是什么。但这只解决了**寻址**问题。找到之后呢？

**找到地址只是第一步，你还需要真正"打通电话"。**

### 1.1 单体架构中：本地方法调用，零成本

在单体架构中，`bluenote-auth` 需要注册用户，直接调用 `UserService.registerUser()` 就行：

```java
// 单体架构：这就是一行本地调用，JVM 内部完成，毫秒级
@Service
public class AuthService {
    @Autowired
    private UserService userService;  // 同一个 Spring 容器中的 Bean

    public Long register(String phone) {
        return userService.registerUser(phone);  // 本地方法调用
    }
}
```

这行代码背后没有任何网络通信。JVM 在同一个进程内完成参数传递、方法调用、结果返回。没有序列化、没有 HTTP、没有网络超时。

### 1.2 微服务架构中：HTTP 远程调用，沉重且容易出错

但在微服务架构中，`bluenote-auth` 和 `bluenote-user` 是两个独立的进程，运行在不同的 JVM 中。要调用 `bluenote-user` 的注册功能，你必须通过 **HTTP 协议**发送请求。

在没有 Feign 的世界里，你会这样写：

```java
// 原生 RestTemplate 写法：手工拼 URL、手工序列化、手工处理响应
@Service
public class AuthService {

    // 你需要知道 bluenote-user 的物理地址
    // 但这违背了"动态发现"的原则——服务地址会变
    private static final String USER_SERVICE_URL = "http://192.168.1.10:8082";

    @Autowired
    private RestTemplate restTemplate;

    public Long register(String phone) {
        // 1. 手动构造请求体
        RegisterUserReqDTO dto = new RegisterUserReqDTO();
        dto.setPhone(phone);

        // 2. 手动构造 HTTP 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 还需要手动透传 userId 等上下文信息
        headers.set("userId", String.valueOf(LoginUserContextHolder.getUserId()));

        // 3. 手动拼接 URL
        String url = USER_SERVICE_URL + "/user/register";

        // 4. 手动发起 HTTP 调用
        HttpEntity<RegisterUserReqDTO> entity = new HttpEntity<>(dto, headers);

        // 5. 手动解析响应
        ResponseEntity<Response<Long>> response = restTemplate.exchange(
            url, HttpMethod.POST, entity,
            new ParameterizedTypeReference<Response<Long>>() {}
        );

        // 6. 手动提取数据
        Response<Long> body = response.getBody();
        if (body == null || !body.isSuccess()) {
            return null;
        }
        return body.getData();
    }
}
```

这段代码是"机械性劳动"的典型代表。它没有业务价值——你做的事情无非是"把 `phone` 参数包装成 HTTP 请求发到一个已知接口，然后把响应解析回来"。这件事的逻辑对任何接口都一样，但你却需要为每个接口各写一遍。`registerUser` 这样写，`getUserProfile` 也要这样写，`uploadFile` 也要这样写——**60% 的代码都是重复的"HTTP 请求脚手架"**。

而且这段代码在微服务环境下还有几个致命问题：

1. **URL 硬编码**：`http://192.168.1.10:8082` 是写死的。服务扩容了？IP 变了？你得改代码重新部署。
2. **上下文丢失**：每次调用都要记得手动把 `userId` 从当前上下文塞进 HTTP Header。忘了一次就全线崩溃。
3. **类型安全缺失**：`ParameterizedTypeReference<Response<Long>>` 这种写法又臭又长，而且运行时才能发现类型不匹配。

### 1.3 Feign 的宣言：让远程调用回归"声明"

Feign 做的事情，本质上是回答一个问题：**能不能让 HTTP 调用看起来像本地方法调用？**

答案是：**接口 + 注解**。你在小蓝书项目中已经这样写了：

```java
// 这就是你写的 UserFeignApi —— Feign 的世界里，远程调用就是一个接口
@FeignClient(name = "bluenote-user")    // 目标服务名
public interface UserFeignApi {

    @PostMapping("/user/register")      // HTTP 路径和方法
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO dto);  // 参数的序列化方式
}
```

然后在 `UserRpcService` 中使用它：

```java
@Component
public class UserRpcService {
    @Resource
    private UserFeignApi userFeignApi;   // 像注入普通 Bean 一样注入 Feign 客户端

    public Long registerUser(String phone) {
        RegisterUserReqDTO dto = new RegisterUserReqDTO();
        dto.setPhone(phone);

        Response<Long> response = userFeignApi.registerUser(dto);  // 像调本地方法一样调远程接口

        if (!response.isSuccess()) {
            return null;
        }
        return response.getData();
    }
}
```

看这段代码，你几乎感觉不到这是一个网络调用。没有 URL 拼接，没有 `RestTemplate`，没有 `HttpEntity`，没有类型擦除的 `ParameterizedTypeReference`。你只是在调一个接口的方法，剩下的全部交给 Feign。

**这就是 Feign 的核心理念：声明式 HTTP 调用。你只需要声明"我要调哪个服务的哪个路径、传什么参数"，不需要手动执行调用的每一个步骤。**

---

## 二、Feign 的设计哲学——"像调本地方法一样调远程接口"

这一节放慢脚步，认真理解 Feign 为什么这样设计。这对你后续理解它在 Spring 容器中如何运作至关重要。

### 2.1 类比：MyBatis 之于 JDBC，Feign 之于 HTTP

你已经在用 MyBatis 操作数据库了。回想一下，用原生 JDBC 操作数据库是什么样的：

```java
// 原生 JDBC：Connection、PreparedStatement、ResultSet，一套流程下来十几行
Connection conn = null;
PreparedStatement stmt = null;
ResultSet rs = null;
try {
    conn = dataSource.getConnection();
    stmt = conn.prepareStatement("SELECT * FROM user WHERE phone = ?");
    stmt.setString(1, phone);
    rs = stmt.executeQuery();
    while (rs.next()) {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setPhone(rs.getString("phone"));
        // ... 几十行样板代码
    }
} catch (SQLException e) {
    // 异常处理
} finally {
    // 资源关闭（又是十几行）
}
```

而 MyBatis 让你怎么写？

```java
// MyBatis：声明一个 Mapper 接口，SQL 写在 XML 或注解里
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User findByPhone(@Param("phone") String phone);
}

// 使用时：
@Autowired
private UserMapper userMapper;
User user = userMapper.findByPhone("13800138000");  // 一行搞定
```

**MyBatis 做的事情**：把你声明的接口（`UserMapper`）和注解（`@Select`），在运行时转换成一个代理对象。当你调用 `findByPhone` 时，这个代理对象内部执行了获取连接、创建语句、绑定参数、执行查询、映射结果、关闭资源这一整套流程。

**Feign 做的事情完全一样，只是目标从数据库变成了 HTTP 接口**：

| 维度 | JDBC | MyBatis | RestTemplate | Feign |
|------|------|---------|-------------|-------|
| 目标系统 | 数据库 | 数据库 | HTTP 服务 | HTTP 服务 |
| 调用方式 | 编程式 | 声明式（接口+注解） | 编程式 | 声明式（接口+注解） |
| 核心痛点 | 样板代码多 | 隐藏样板 | 样板代码多 | 隐藏样板 |
| 资源管理 | 手动 | 自动 | 手动 | 自动 |
| 类型映射 | 手动 | 自动 | 手动（ParameterizedTypeReference） | 自动（泛型推断） |

理解了这个类比，你就明白了 Feign 在整个 Spring Cloud 体系中扮演的角色：**它是微服务间 HTTP 通信的"MyBatis"**。

### 2.2 声明式 vs 编程式：为什么"接口 + 注解"比"流水线代码"好？

"声明式"和"编程式"的区别，用一句话概括：**编程式是告诉计算机"每一步怎么做"，声明式是告诉计算机"我想要什么结果"**。

```text
编程式（RestTemplate）：
  第一步：new 一个 HttpHeaders
  第二步：set Content-Type
  第三步：构造 URL
  第四步：new HttpEntity
  第五步：restTemplate.exchange()
  第六步：解析 ResponseEntity
  第七步：提取 body

声明式（Feign）：
  @FeignClient(name = "bluenote-user")
  @PostMapping("/user/register")
  Response<Long> registerUser(@RequestBody RegisterUserReqDTO dto);
```

声明式的优势不只是"少写几行代码"。更深层的价值在于：

1. **语义清晰**：看 `@FeignClient(name = "bluenote-user")` 一眼就知道这是调用 `bluenote-user` 服务。看 `@PostMapping("/user/register")` 一眼就知道路径和 HTTP 方法。而 RestTemplate 的 `exchange(url, HttpMethod.POST, entity, typeRef)` 需要你从一大堆参数中推导意图。

2. **关注点分离**：接口定义（调用哪个服务、哪个路径）和接口调用（传什么参数、怎么处理结果）被清晰地分开了。FeignClient 接口负责"定义"，RpcService 负责"使用"。

3. **可测试性**：FeignClient 是一个接口，你可以很容易地 mock 它进行单元测试。而 RestTemplate 的调用代码和内联的 URL 构造混在一起，mock 起来麻烦得多。

4. **集中管理**：所有对同一个外部服务的调用都定义在一个 Interface 里（比如 `UserFeignApi` 包含了所有对 `bluenote-user` 的调用）。后续要改路径、加接口、看调用关系，一眼就清楚。

---

## 三、Feign 框架结构——一座三层小楼

现在从原理层面拆解 Feign 的架构。理解了架构，你就知道 `FeignRequestInterceptor` 在哪个位置发挥作用、`FeignFormConfig` 为什么能处理文件上传、`@FeignClient` 注解背后到底发生了什么。

### 3.1 整体架构总览

Feign 的源码架构可以理解为一座"三层小楼"，每一层负责不同的职责：

```text
┌─────────────────────────────────────────────────────────────────────┐
│                        上层：接口层（面向开发者）                       │
│                                                                      │
│   @FeignClient + 方法上的 Spring MVC 注解                             │
│   (@RequestMapping, @PostMapping, @RequestBody, @RequestPart ...)    │
│                                                                      │
│   开发者只需要定义接口和注解 —— 这是你直接打交道的部分                    │
├─────────────────────────────────────────────────────────────────────┤
│                       中层：核心处理层                                 │
│                                                                      │
│   ┌──────────────┐  ┌──────────────┐  ┌─────────────────────────┐   │
│   │   Contract    │  │   Encoder    │  │      Decoder            │   │
│   │  (契约解析器)  │  │  (请求编码器) │  │     (响应解码器)          │   │
│   │               │  │              │  │                         │   │
│   │ 把注解翻译成   │  │ 把 Java 对象 │  │  把 HTTP 响应           │   │
│   │ HTTP 请求模板  │  │ 编码为请求体  │  │  解码为 Java 对象       │   │
│   └──────────────┘  └──────────────┘  └─────────────────────────┘   │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │              RequestInterceptor (请求拦截器)                    │   │
│   │                                                               │   │
│   │   在请求发送之前，对 RequestTemplate 做最后的修改               │   │
│   │   ↑ 你的 FeignRequestInterceptor 就在这里工作                  │   │
│   └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │              Client (HTTP 客户端执行器)                        │   │
│   │                                                               │   │
│   │   真正发送 HTTP 请求的组件，把 RequestTemplate 转成 Request     │   │
│   │   发送出去，拿到 Response                                       │   │
│   └──────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                      底层：HTTP 通信引擎                              │
│                                                                      │
│   默认：java.net.HttpURLConnection (JDK 内置，零配置)                  │
│   可选：Apache HttpClient (连接池，生产推荐)                            │
│   可选：OkHttp (高效的连接复用)                                        │
│   可选：http2client (Java 11+ HTTP/2 Client)                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 底层：HTTP 通信引擎的选择

Feign 本身不绑定任何一种 HTTP 库。它定义了一个 `Client` 接口，具体的 HTTP 请求由实现了这个接口的类来完成。

默认情况下，Feign 使用的是 `feign.Client.Default`，它的实现基于 JDK 自带的 `HttpURLConnection`。这个选择对初学者友好——不需要引入任何额外依赖就能跑起来。

但在生产环境中，`HttpURLConnection` 有几个显著的缺陷：不支持连接池（每个请求都要经历 TCP 三次握手）、不支持 HTTP/2、配置项太少。所以生产环境通常会替换为 Apache HttpClient 或 OkHttp：

```java
// 生产环境推荐：使用 Apache HttpClient + 连接池
// 连接池复用 TCP 连接，减少握手开销，提升性能
feign:
  httpclient:
    enabled: true        # 启用 Apache HttpClient
    max-connections: 200 # 最大连接数
    max-connections-per-route: 50  # 每个路由（目标主机）最大连接数
```

你的项目目前用默认的 `HttpURLConnection`，这在开发阶段完全够用。等服务的调用量和并发上来之后，切换到 Apache HttpClient 就是改一行配置的事——这就是接口抽象的好处。

### 3.3 中层：Encoder、Decoder、Contract——Feign 的核心引擎

这三个组件是 Feign 中最核心的概念，理解它们才能真正理解 Feign 的工作方式。

#### Contract（契约解析器）—— "把注解翻译成 HTTP 模板"

`Contract` 是 Feign 的"翻译官"。它的职责是：**读取你接口方法的 Spring MVC 注解，翻译成 Feign 内部使用的元数据（MethodMetadata）**。

当你写：

```java
@FeignClient(name = "bluenote-user")
public interface UserFeignApi {
    @PostMapping("/user/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO dto);
}
```

`Contract` 会解析出：

```text
MethodMetadata {
    configKey: "UserFeignApi#registerUser(RegisterUserReqDTO)"
    template: RequestTemplate {
        method: "POST"
        url: "/user/register"
        headers: {"Content-Type": "application/json"}
        bodyTemplate: "%7B%22phone%22%3A%20%22{phone}%22%7D"  // JSON 模板
        // ↑ 这是你在方法参数上标注的 @RequestBody 的结果
    }
    returnType: Response<Long>
}
```

你用的 `spring-cloud-starter-openfeign` 默认使用 `SpringMvcContract`——它能识别 Spring MVC 的全部注解（`@RequestMapping`、`@PostMapping`、`@RequestBody`、`@RequestParam`、`@RequestPart`、`@PathVariable` 等等）。这是 Feign 和 Spring 生态深度融合的关键。

#### Encoder（请求编码器）—— "把 Java 对象变成 HTTP 请求体"

`Encoder` 负责把方法参数（Java 对象）转换成 HTTP 请求体的字节流。

对于 `@RequestBody` 标注的参数，默认的 `SpringEncoder` 使用 Jackson 将 Java 对象序列化为 JSON 字符串，然后写入请求体：

```java
// 你的 RegisterUserReqDTO {"phone": "13800138000"}
// 经 SpringEncoder (Jackson) → {"phone":"13800138000"} 作为 HTTP Body
```

对于 `@RequestPart` 标注的文件上传（你的 `FileFeignApi`），默认的编码器无法处理 `multipart/form-data` 格式。这就是为什么你的 `FileFeignApi` 配置了 `FeignFormConfig`：

```java
@Configuration
public class FeignFormConfig {
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();  // 特殊编码器：支持 multipart/form-data
    }
}
```

`SpringFormEncoder` 会把 `MultipartFile` 参数编码为 `multipart/form-data` 格式的消息体。你用 `@FeignClient(name = ApiConstants.SERVICE_NAME, configuration = FeignFormConfig.class)` 指定了这个定制编码器，Feign 在构造这个接口的调用链时就会用 `SpringFormEncoder` 而不是默认的 `SpringEncoder`。

#### Decoder（响应解码器）—— "把 HTTP 响应变成 Java 对象"

`Decoder` 是 `Encoder` 的逆过程。它负责把 HTTP 响应的字节流转成你接口方法声明的返回类型。

你声明的 `Response<Long> registerUser(...)`，返回的是一个泛型类型。`SpringDecoder`（底层是 Jackson）会读取响应体的 JSON 字符串，根据方法签名的返回类型进行反序列化：

```text
HTTP Response Body: {"code":200, "data":1001, "message":"success"}
                         ↓  SpringDecoder (Jackson)
Java 对象: Response<Long> { code=200, data=1001, message="success" }
```

#### RequestInterceptor（请求拦截器）—— "在请求发出前做最后修饰"

这是最关键的一个组件，也是你的 `FeignRequestInterceptor` 挂载的地方。

`RequestInterceptor` 的接口定义极其简单：

```java
public interface RequestInterceptor {
    void apply(RequestTemplate template);
}
```

它只有一个方法 `apply`，接收一个 `RequestTemplate` 参数。在这个方法里，你可以对即将发出的 HTTP 请求做任何修改——添加 Header、修改 URL、设置 Query 参数等等。

**所有 `RequestInterceptor` 的实现会在 Feign 构造 HTTP 请求时被依次调用**。你的 `FeignRequestInterceptor` 做的事情正是：

```java
@Override
public void apply(RequestTemplate requestTemplate) {
    Long userId = LoginUserContextHolder.getUserId();  // 从当前线程上下文获取 userId
    if (Objects.nonNull(userId)) {
        requestTemplate.header("userId", String.valueOf(userId));  // 添加到请求头
    }
}
```

这个拦截器对**这个服务中所有 Feign 调用**都生效。当 `bluenote-auth` 调用 `bluenote-user` 时，这个拦截器自动把 userId 注入到 HTTP Header 中。下游的 `bluenote-user` 的 `HeaderUserId2ContextFilter` 从 Header 中读出 userId，存回 ThreadLocal——上下文就透传过去了。

### 3.4 上层：动态代理——一个接口为什么能"跑起来"

这是 Feign 最精妙的设计，值得单独一节讲解。

---

## 四、Feign 动态代理的魔法——一个接口为什么能"跑起来"

你在写 `@Resource private UserFeignApi userFeignApi;` 时，`userFeignApi` 到底是什么？它不是一个普通的对象——`UserFeignApi` 只是一个接口，接口本身无法实例化。你拿到的其实是一个 **JDK 动态代理对象**。

### 4.1 从 `@EnableFeignClients` 开始

在你的 `BlueNoteAuthApplication` 上：

```java
@EnableFeignClients("com.tefire.user.api")   // 扫描这个包下的 @FeignClient 接口
public class BlueNoteAuthApplication { ... }
```

`@EnableFeignClients` 启动时做了这些事情：

1. **扫描指定包**：Spring 在 `com.tefire.user.api` 包下扫描所有标注了 `@FeignClient` 的接口。找到了 `UserFeignApi`。

2. **为每个接口注册一个 FactoryBean**：对于每个 `@FeignClient` 接口，Spring 注册一个 `FeignClientFactoryBean`。这个 FactoryBean 不是普通的 Bean——它在 `getObject()` 方法中创建动态代理。

3. **构建 Feign 调用链**：`FeignClientFactoryBean` 读取 `@FeignClient` 的属性（name、path、configuration 等），组装出一个完整的 Feign 实例。具体来说：
   - 根据 `name = "bluenote-user"` 配置目标服务名
   - 根据 `configuration`（如果有）应用定制的 Encoder/Decoder
   - 从 Spring 容器中收集所有 `RequestInterceptor` 实现（包括你的 `FeignRequestInterceptor`）
   - 选择底层 HTTP Client

4. **创建动态代理**：调用 `Feign.builder().target(UserFeignApi.class, "http://bluenote-user")`，返回一个 JDK 动态代理对象。

5. **注入到 Spring 容器**：这个代理对象作为 `UserFeignApi` 类型的 Bean 注册到容器中。所以你的 `@Resource UserFeignApi userFeignApi` 注入的是一个 JDK 代理。

### 4.2 `registerUser(dto)` 被调用时发生了什么？

当你写下：

```java
Response<Long> response = userFeignApi.registerUser(dto);
```

这行代码触发了一连串的内部处理：

```text
[1] JDK 动态代理的 InvocationHandler 截获调用
     |
     ↓
[2] 根据方法签名查找 MethodMetadata（Contract 已提前解析好的）
     |
     ↓
[3] 创建 RequestTemplate，填充：
     - URL: /user/register
     - HTTP Method: POST
     - 请求参数 dto → 委托给 Encoder 编码为 JSON
     - Content-Type: application/json
     |
     ↓
[4] 依次调用所有 RequestInterceptor.apply(template)
     → FeignRequestInterceptor: 往 template.header 加 userId
     → (其他拦截器...)
     |
     ↓
[5] LoadBalancer 介入：
     - 从 template 中获取目标服务名 "bluenote-user"
     - 向 Nacos 查询 bluenote-user 的健康实例列表
     - 选择一个实例（轮询/随机/最小连接数）
     - 把 template 中的服务名替换为实际 IP:Port
     - RequestTemplate: http://bluenote-user/user/register
     - → 替换为 http://192.168.1.11:8082/user/register
     |
     ↓
[6] Client 执行 HTTP 请求
     - RequestTemplate → Request (真实的 HTTP 请求)
     - 发送到 http://192.168.1.11:8082/user/register
     - 拿到 Response
     |
     ↓
[7] Decoder 解码响应
     - 响应体 JSON → Response<Long> Java 对象
     |
     ↓
[8] 返回 Response<Long> 给你的代码
```

整个过程中，你只做了一件事：调了一个接口方法。Feign + Spring Cloud 在背后替你执行了 8 个步骤。

### 4.3 这个代理不是"黑盒"——它给了你足够的插手空间

Feign 的动态代理不是一个封闭的系统。每一步都可以被定制：

- **Encder/Decoder 定制**（你的 `FeignFormConfig` 用了这个能力）—— 处理特殊的数据格式
- **RequestInterceptor**（你的 `FeignRequestInterceptor` 用了这个能力）—— 在请求发出去前做修饰
- **Client 替换** —— 换成 OkHttp 或 Apache HttpClient
- **Contract 替换** —— 用 JAX-RS 注解而不是 Spring MVC 注解
- **ErrorDecoder** —— 自定义错误处理逻辑

这种"框架提供默认行为，允许逐层定制"的设计，就是**可扩展性**的最佳实践。

---

## 五、Feign + Spring Cloud 整合——"无感服务发现"

你的 `@FeignClient(name = "bluenote-user")` 中的 `name = "bluenote-user"` 在 HTTP 请求中是如何变成实际 IP 的？这背后是 Feign 和 Spring Cloud LoadBalancer（以及 Nacos 服务发现）的协作。

### 5.1 name 的双重身份

`@FeignClient(name = "bluenote-user")` 中，`name` 属性有两个身份：

1. **作为服务名的标识**：Feign 用这个名字到 Nacos 中查询该服务的实例列表。
2. **作为 URL 中的 Host**：Feign 构造的 `RequestTemplate` 中，URL 是 `http://bluenote-user/user/register`。注意这里 `bluenote-user` 并不是一个真正的 IP，只是一个占位符。

### 5.2 LoadBalancer 的介入

当 `spring-cloud-starter-loadbalancer`（或旧的 Ribbon）在 classpath 中时，Feign 会自动使用 `LoadBalancerFeignClient` 而不是默认的 `Client.Default`。

`LoadBalancerFeignClient` 在发送请求前会：

1. **从 `RequestTemplate` 的 URL 中提取 Host**——得到 `bluenote-user`。
2. **用这个 Host 作为 serviceId**，从 Nacos 查询实例列表。
3. **选择一个实例**——默认轮询策略，依次选取不同的实例实现负载均衡。
4. **把 Host 替换为选中的实例 IP 和端口**——`http://192.168.1.11:8082/user/register`。
5. **发起真实的 HTTP 请求**。

```text
@FeignClient(name = "bluenote-user")
                    │
                    ▼
         Nacos Server (注册中心)
         ┌─────────────────────────────┐
         │ 服务: bluenote-user          │
         │  ├─ 192.168.1.10:8082 健康   │
         │  ├─ 192.168.1.11:8082 健康   │
         │  └─ 192.168.1.12:8082 不健康 │
         └─────────────────────────────┘
                    │
                    ▼  LoadBalancer 轮询选择
             http://192.168.1.11:8082/user/register
```

### 5.3 name 和 Spring Boot 服务名的关系

这里有一个重要的细节。`@FeignClient(name = "bluenote-user")` 中的 `name` 必须和**目标服务的 `spring.application.name`** 一致。因为目标服务注册到 Nacos 时用的名字就是 `spring.application.name`。

在你的项目中：
- 调用方写的是 `@FeignClient(name = "bluenote-user")`
- 目标服务的 `spring.application.name = bluenote-user`
- 两者匹配，Feign 才能在 Nacos 中找到正确的服务

如果写错了（比如写成 `@FeignClient(name = "user-service")`），Feign 会向 Nacos 查询 `user-service`，结果当然找不到——因为这个名字没有注册过。

---

## 六、上下文透传——"接力棒不能掉"

这是你项目中设计最精妙的部分。我们从"为什么会有问题"开始，逐步拆解整个解决方案。

### 6.1 问题的本质：ThreadLocal 与微服务调用链的冲突

在单体应用中，用户 ID 通常存在 ThreadLocal 里：

```java
// 请求进来 → Filter 拦截 → 存到 ThreadLocal
LoginUserContextHolder.setUserId(userId);

// 业务代码中任何地方都可以直接获取
Long userId = LoginUserContextHolder.getUserId();
```

因为单体应用中一个请求从进到出，始终在同一个线程上，ThreadLocal 完全够用。

但微服务引入了**跨进程调用**。当 `bluenote-auth` 调用 `bluenote-user` 时：

```text
请求链路：
  [Gateway] → [bluenote-auth] → [bluenote-user] → [bluenote-oss]

ThreadLocal 的视野范围：
  [Gateway] → [bluenote-auth]  ← ThreadLocal 在这里有效
                 │
                 │  执行 Feign 调用 → HTTP 请求发送到 bluenote-user
                 │  这个 HTTP 请求是"新的 HTTP 请求"
                 │  ↓
                 [bluenote-user]  ← ThreadLocal 默认是空的！
```

**ThreadLocal 不能跨进程传播**。`bluenote-auth` 的 ThreadLocal 里存了 userId，但 `bluenote-user` 是另一个独立的进程——它有自己的内存空间、自己的 ThreadLocal。如果不做任何处理，`bluenote-user` 服务中 `LoginUserContextHolder.getUserId()` 返回的是 `null`。

### 6.2 你的解决方案：一套"接力棒"机制

你的项目用了一套精巧的四件套解决了这个问题。让我们顺着一次完整的请求链路来拆解每一步。

#### 链路全景图

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                          一次注册请求的完整链路                                │
│                                                                               │
│  用户请求: POST /api/auth/register  (携带 Sa-Token)                           │
│                                                                               │
│  ═══════════════════════════════════════════════════════════════════════════   │
│  [1] bluenote-gateway (端口 8000)                                             │
│      │                                                                        │
│      │ Sa-Token 认证 → 解析 Token → 得到 userId = 1001                        │
│      │                                                                        │
│      │ AddUserId2HeaderFilter                                                 │
│      │   → 将 userId=1001 写入请求头 Header                                    │
│      │   → Header: "userId" = "1001"                                          │
│      │                                                                        │
│      │ 路由转发 → lb://bluenote-auth/auth/register                            │
│      │                                                                        │
│  ═══════════════════════════════════════════════════════════════════════════   │
│  [2] bluenote-auth (端口 8080)                                                │
│      │                                                                        │
│      │ HeaderUserId2ContextFilter (OncePerRequestFilter)                      │
│      │   → 从请求 Header 中读取 "userId" = "1001"                             │
│      │   → LoginUserContextHolder.setUserId("1001")                           │
│      │   → ThreadLocal 中现在有了 userId                                      │
│      │                                                                        │
│      │ AuthController → AuthService → UserRpcService                          │
│      │   → userFeignApi.registerUser(dto)  ← Feign 调用                       │
│      │                                                                        │
│      │ FeignRequestInterceptor.apply(RequestTemplate)                         │
│      │   → userId = LoginUserContextHolder.getUserId()  // 拿到 1001          │
│      │   → requestTemplate.header("userId", "1001")                           │
│      │   → Feign 发出的 HTTP 请求自动携带 Header: "userId" = "1001"           │
│      │                                                                        │
│      │ (请求结束时) HeaderUserId2ContextFilter.finally                        │
│      │   → LoginUserContextHolder.remove()  ← 防止内存泄漏                    │
│      │                                                                        │
│  ═══════════════════════════════════════════════════════════════════════════   │
│  [3] bluenote-user (端口 8082)                                                │
│      │                                                                        │
│      │ HeaderUserId2ContextFilter (同一个 Filter，来自 biz-context starter)    │
│      │   → 从请求 Header 中读取 "userId" = "1001"                             │
│      │   → LoginUserContextHolder.setUserId("1001")                           │
│      │   → ThreadLocal 中现在有了 userId ← 上下文成功透传！                    │
│      │                                                                        │
│      │ UserController → UserService → 执行业务逻辑                              │
│      │   → LoginUserContextHolder.getUserId() == 1001  ✓                      │
│      │                                                                        │
│      │ (请求结束时) LoginUserContextHolder.remove()                           │
│      │                                                                        │
│  ═══════════════════════════════════════════════════════════════════════════   │
│  结果：bluenote-user 正确地知道这次请求来自 userId=1001 的用户                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 四件套逐一定位

**第一件：Gateway 的 `AddUserId2HeaderFilter`（认证边界）**

```java
// 位置: bluenote-gateway
// 作用: 认证完成后，把 userId 写进向下的请求 Header
// 这是整个上下文透传链的"源头"

@Component
public class AddUserId2HeaderFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Long userId = StpUtil.getLoginIdAsLong();  // 从 Sa-Token 拿到登录用户 ID

        // 重新构建 Request，往 Header 里塞 userId
        ServerWebExchange newExchange = exchange.mutate()
            .request(builder -> builder.header("userId", String.valueOf(userId)))
            .build();
        return chain.filter(newExchange);
    }
}
```

**为什么放在网关？**因为网关是整个系统的"认证边界"。所有的外部请求都先经过网关，网关用 Sa-Token 验证请求的合法性，解析出用户 ID 后，通过请求头"向下渗透"到所有下游服务。这样下游服务不需要再自己做认证——它们只需要从 Header 里读 userId 就够了。

**第二件：下游服务的 `HeaderUserId2ContextFilter`（上下文恢复）**

```java
// 位置: bluenote-framework/bluenote-spring-boot-starter-biz-context
// 作用: 从请求 Header 读取 userId，恢复 ThreadLocal 上下文
// 每个下游服务（auth、user、oss）都通过 biz-context starter 自动获得这个 Filter

public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) {
        String userId = request.getHeader("userId");  // 从 Header 中读
        if (StringUtils.isNotBlank(userId)) {
            LoginUserContextHolder.setUserId(userId);  // 写入 ThreadLocal
        }
        try {
            chain.doFilter(request, response);  // 执行业务逻辑
        } finally {
            LoginUserContextHolder.remove();  // 清理 ThreadLocal，防止内存泄漏
        }
    }
}
```

**为什么用 `OncePerRequestFilter`？**这确保了一个请求在处理过程中只执行一次过滤逻辑。如果请求经过多个 Filter——比如 Spring Security 的 Filter Chain——用普通的 `Filter` 可能会被调用多次。`OncePerRequestFilter` 保证只执行一次，既高效又安全。

**为什么 `try-finally` 中必须 `remove()`？**这正是你之前学过的 ThreadLocal 内存泄漏问题。Tomcat 使用线程池处理请求，这个线程在处理完当前请求后不会被销毁，而是回收复用。如果你不清理 ThreadLocal，下一个被分配到同一个线程的请求会看到上一个请求遗留下来的 userId——这就是**数据污染**。

**第三件：Feign 调用方的 `FeignRequestInterceptor`（上下文传播）**

```java
// 位置: bluenote-framework/bluenote-spring-boot-starter-biz-context
// 作用: Feign 发出请求前，从 ThreadLocal 取出 userId，写入 Feign 请求 Header
// 这是"接力棒"的交接点——把当前线程上下文"装进"即将发出的 HTTP 请求

public class FeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.nonNull(userId)) {
            requestTemplate.header("userId", String.valueOf(userId));
        }
    }
}
```

这个拦截器是**全局生效**的。也就是说，`bluenote-auth` 中所有的 Feign 调用——不管目标是 `bluenote-user` 还是 `bluenote-oss` 还是未来的任何服务——都会自动携带这个 Header。你不需要在每个 Feign 调用处手动加 Header，一次配置，永久生效。

**第四件：`FeignContextAutoConfiguration`（自动装配）**

```java
// 位置: bluenote-framework/bluenote-spring-boot-starter-biz-context
// 作用: 把 FeignRequestInterceptor 注册为全局 Feign 拦截器
// 任何依赖了 biz-context-starter 的服务，启动时自动装配这个拦截器

@AutoConfiguration
public class FeignContextAutoConfiguration {
    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }
}
```

这个配置通过 Spring Boot 3.x 的自动装配机制（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件）自动生效。任何引入了 `bluenote-spring-boot-starter-biz-context` 依赖的服务，启动时 Spring 会自动发现并执行这个配置类，创建 `FeignRequestInterceptor` Bean。Feign 在构造调用链时从 Spring 容器中收集所有 `RequestInterceptor` 实现——所以这个拦截器自动"挂载"到所有 Feign 调用上。

### 6.3 为什么用 `TransmittableThreadLocal` 而不是普通 `ThreadLocal`？

看你的 `LoginUserContextHolder`：

```java
private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
        = TransmittableThreadLocal.withInitial(HashMap::new);
```

你用的是 Alibaba 开源的 `TransmittableThreadLocal`（TTL），而不是 JDK 原生的 `ThreadLocal`，也不是 Spring 的 `InheritableThreadLocal`。这三点有什么区别？

- **`ThreadLocal`**：父子线程之间**不共享**。主线线程中 set 了 userId，子线程（如 `@Async` 方法、线程池任务）中 get 出来是 null。

- **`InheritableThreadLocal`**：父子线程**在创建时**会复制一份。但如果使用的是**线程池**，线程不是每次新建的，而是复用的——这意味着子线程创建时复制了一次 ThreadLocal，之后任务结束线程回收，下一个任务复用到同一个线程时不会再重新复制。而且如果主线程在线程池任务执行之前改了 ThreadLocal，子线程也感知不到。

- **`TransmittableThreadLocal`**：解决了**线程池场景下的上下文传递**问题。TTL 会对线程池进行"装饰"，让你 `execute()` 提交的任务在执行时能够自动从主线程"抓取"最新的上下文值。无论这个线程是新建的还是复用的，TTL 都能保证上下文正确传递。

在你的项目中，如果未来某个服务需要异步处理（`@Async`），比如发送短信验证码是一个异步任务，TTL 能保证在异步线程中 `LoginUserContextHolder.getUserId()` 仍然能拿到正确的值。这是你对 ThreadLocal 在微服务中局限性的深刻理解——你之前记忆中也记录了这一点：[[threadlocal-threadpool-limitation]]。

### 6.4 "全局生效"的设计为什么重要

你可能会想：能不能把 `FeignRequestInterceptor` 直接写在 `bluenote-auth` 项目里，而不是单独抽一个 starter？

技术上可以，但那样做的话，每新增一个服务，你都需要在新服务里**复制粘贴**一样的拦截器代码。如果 `bluenote-user` 也需要 Feign 调用下游服务（它确实需要——调 `bluenote-oss`），你就得在 `bluenote-user` 里再写一遍。

你通过**将拦截器和 Filter 抽取到独立的 `biz-context-starter` 模块**，并利用 Spring Boot 的自动装配机制，实现了"一次编写，全局生效"。任何新创建的微服务只要引入这个 starter 依赖，就自动获得：

1. **入站拦截**：`HeaderUserId2ContextFilter`——从请求 Header 中恢复 userId 到 ThreadLocal
2. **出站拦截**：`FeignRequestInterceptor`——从 ThreadLocal 取出 userId，注入 Feign 请求 Header

这就是**基础设施代码的复用**——你的 `biz-context-starter` 本质上就是团队内部的"微服务基础设施"。

---

## 七、使用教程——从小蓝书项目看 Feign 的完整用法

到现在为止，我们已经深入讲解了 Feign 的原理和源码结构。这一节把视角拉回到实践层面，以你的项目为蓝本，梳理 Feign 远程调用的标准操作步骤。

### 7.1 架构模式：api 模块 + biz 模块

你的项目采用了微服务领域非常推荐的一种模块划分方式：

```text
bluenote-user/
├── bluenote-user-api/       ← Feign 接口定义（对外暴露的契约）
│   ├── UserFeignApi.java     ← @FeignClient 接口
│   ├── RegisterUserReqDTO.java  ← 请求 DTO
│   └── ApiConstants.java     ← 服务名常量
└── bluenote-user-biz/       ← 接口的实际实现（业务逻辑）
    └── UserController.java   ← @RestController，实现 UserFeignApi 定义的路径
```

**为什么需要 api 模块？**如果 `UserFeignApi` 直接放在 `bluenote-user-biz` 里，那 `bluenote-auth` 想调用它时，需要依赖整个 `bluenote-user-biz`——这意味着会把 `bluenote-user` 的所有业务逻辑、数据库配置、第三方依赖全部拖进来。而通过拆出 `bluenote-user-api`（只包含接口定义和 DTO），调用方只需要依赖这个轻量级的 api 模块就够了。

### 7.2 第一步：定义 Feign 接口

```java
// 文件: bluenote-user-api/.../UserFeignApi.java
@FeignClient(name = ApiConstants.SERVICE_NAME,  // 目标服务在 Nacos 中的名字
             path = "/user")                    // 可选：统一路径前缀
public interface UserFeignApi {

    @PostMapping("/register")                                      // HTTP 方法 + 路径
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO dto);  // @RequestBody → JSON

    @GetMapping("/profile")
    Response<UserProfileDTO> getUserProfile(@RequestParam("userId") Long userId); // @RequestParam → Query参数
}
```

注解解释：
- `@FeignClient(name = "bluenote-user")`：声明这是一个 Feign 客户端，目标服务的 Nacos 注册名为 `bluenote-user`
- `@PostMapping`、`@GetMapping`：使用你熟悉的 Spring MVC 注解 —— Feign 会自动识别并转换成 HTTP 请求
- `@RequestBody`：参数会被 Jackson 序列化为 JSON，放入请求体
- `@RequestParam`：参数会作为 URL Query String（`?userId=1001`）
- `@PathVariable`：参数会替换 URL 中的占位符（`/user/{id}`）
- `@RequestPart`：文件上传（见下一小节）

### 7.3 第二步：在调用方启动类上启用 Feign

```java
// 文件: bluenote-auth/.../BlueNoteAuthApplication.java
@SpringBootApplication
@EnableFeignClients("com.tefire.user.api")  // 扫描 UserFeignApi 所在的包
public class BlueNoteAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlueNoteAuthApplication.class, args);
    }
}
```

`@EnableFeignClients` 告诉 Spring："去 `com.tefire.user.api` 包里找所有标了 `@FeignClient` 的接口，为它们创建动态代理 Bean。"

### 7.4 第三步：封装 RpcService（推荐）

虽然可以直接在业务代码中 `@Resource UserFeignApi`，但更好的做法是封装一个 `RpcService` 层：

```java
// 文件: bluenote-auth/.../rpc/UserRpcService.java
@Component
public class UserRpcService {
    @Resource
    private UserFeignApi userFeignApi;

    public Long registerUser(String phone) {
        RegisterUserReqDTO dto = new RegisterUserReqDTO();
        dto.setPhone(phone);

        Response<Long> response = userFeignApi.registerUser(dto);

        if (!response.isSuccess()) {
            throw new BusinessException("用户注册失败：" + response.getMessage());
        }
        return response.getData();
    }
}
```

封装的三个好处：
1. **隔离 Feign 细节**：上层业务代码不直接接触 Feign 的 `Response` 对象——这和一个 Service 调一个 DAO 不应该接触 JDBC 的 `ResultSet` 是一个道理
2. **统一异常处理**：在 RpcService 层处理 Feign 调用的失败情况，转换成本地业务异常
3. **可替换性**：将来如果不用 Feign 了（比如改成 gRPC），只需改 RpcService 内部实现，上层代码无需修改

### 7.5 文件上传的特殊处理

```java
// 文件: bluenote-oss-api/.../FileFeignApi.java
@FeignClient(name = ApiConstants.SERVICE_NAME,
             configuration = FeignFormConfig.class)  // 注意：指定了定制配置
public interface FileFeignApi {

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);
    // ↑ @RequestPart 是文件上传的注解，不是 @RequestBody
}
```

```java
// 文件: bluenote-oss-api/.../FeignFormConfig.java
@Configuration
public class FeignFormConfig {
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();  // 替换默认的 JSON 编码器，支持 multipart/form-data
    }
}
```

为什么文件上传需要特殊处理？Feign 的默认 `Encoder` 只会把对象序列化为 JSON（`application/json`）。但文件上传是 `multipart/form-data` 格式——它会把文件字节流和普通字段打包成一个特定的 multipart 消息体。`SpringFormEncoder` 处理的就是这种格式。如果不指定这个配置，Feign 会尝试把 `MultipartFile` 当作普通 JSON 对象编码，直接报错。

### 7.6 上下文透传的配置（你的亮点）

讲完了基础用法，这里补充一套上下文透传的标准操作步骤：

1. **创建 biz-context starter 模块**，包含：
   - `LoginUserContextHolder`（TransmittableThreadLocal）
   - `HeaderUserId2ContextFilter`（入站拦截）
   - `FeignRequestInterceptor`（出站拦截）
   - `ContextAutoConfiguration` + `FeignContextAutoConfiguration`（自动装配）

2. **每个微服务都引入这个 starter**：
   ```xml
   <dependency>
       <groupId>com.tefire</groupId>
       <artifactId>bluenote-spring-boot-starter-biz-context</artifactId>
   </dependency>
   ```

3. **网关配置认证拦截**：`AddUserId2HeaderFilter` 从 Sa-Token 获取 userId，写入 Header。

4. **业务代码中直接使用**：任何微服务的任何方法中，直接 `LoginUserContextHolder.getUserId()` 获取当前用户 ID，无需手动透传。

---

## 八、总结：Feign 的三层抽象

回到开头的问题：**Feign 到底做了什么？**

如果你只能记住一句话：**Feign 通过"接口 + 注解"的声明式编程模型，把复杂的 HTTP 远程调用封装成"看起来像本地方法调用"的体验，同时通过拦截器机制（RequestInterceptor）让上下文透传这类横切关注点变得透明化。**

但更深入地看，Feign 提供了三层递进的抽象：

1. **第一层：隐藏 HTTP 通信的机械性重复**
   - URL 拼接 → `@PostMapping("/user/register")`
   - 请求体序列化 → `@RequestBody RegisterUserReqDTO dto`
   - 响应反序列化 → `Response<Long> registerUser(dto)`
   - 你的小手从 RestTemplate 的样板代码中解放出来

2. **第二层：隐藏服务发现的复杂性**
   - `name = "bluenote-user"` → Feign + LoadBalancer + Nacos → 自动解析为实际 IP
   - 你不需要关心目标服务有几台机器、IP 是什么、谁下线了——那是 Nacos 的事

3. **第三层：提供横切关注点的扩展口（RequestInterceptor）**
   - 上下文透传（你的 `FeignRequestInterceptor`）
   - 认证 Token 透传
   - 链路追踪 ID 透传（Sleuth / Zipkin）
   - 自定义 Header
   - 这些"所有调用都需要的共同逻辑"通过拦截器机制一次配置、全局生效

第一层是 Feign 自己的职责。第二层是 Spring Cloud 整合赋予的能力。第三层是你利用 Feign 的扩展点实现的**微服务基础设施**——你的 `biz-context-starter` 就是第三层的最佳实践。

回到你最开始看到的 `FeignRequestInterceptor`，它不再是孤立的 30 行代码。它是你的上下文透传完整链路中**出站环节的关键节点**——当 `bluenote-auth` 需要调用 `bluenote-user` 时，它负责把接力棒（userId）从当前线程的 ThreadLocal 传递到即将发出的 HTTP 请求中。下游服务的 `HeaderUserId2ContextFilter` 接住这根接力棒，恢复上下文，完成了一次完整的"跨进程上下文透传"。

这就是 Feign 在微服务中的全部秘密：**声明式接口 + 动态代理 + 可插拔的扩展点机制 = 一个让你几乎忘记这是远程调用的 HTTP 客户端。**

---

> **延伸思考**：除了 Feign，Spring 生态中还有一个声明式 HTTP 客户端——`RestClient`（Spring 6 引入）和 `HttpExchange`（Spring 6 声明式接口）。它们和 Feign 的区别是什么？什么时候该用哪一个？简单来说，Feign 的优势在于和 Spring Cloud 生态（Nacos、LoadBalancer、Sentinel）的深度整合；HttpExchange 的优势在于它是 Spring 的原生方案，不依赖第三方库。如果你的架构是 Spring Cloud Alibaba 体系（就像小蓝书），Feign 是更自然的选择。如果你只是在一个普通 Spring Boot 项目间做 HTTP 调用，不需要服务发现和负载均衡，HttpExchange 可能更轻量。
