# Spring Boot 自定义 Starter 深度解析

## 一、概述

### 1.1 Starter 概念

Spring Boot Starter 是一种**自动配置模块**，通过约定优于配置的理念，实现"开箱即用"的开发体验。Starter 本质上是一个 Maven/Gradle 依赖包，包含了一组相关的依赖和自动配置类。

### 1.2 Starter 与普通模块的区别

| 特性 | 普通 Maven 模块 | Spring Boot Starter |
|------|----------------|---------------------|
| **核心目标** | 代码复用 | 自动配置 + 代码复用 |
| **Bean 注册方式** | `@Component` 注解扫描 | `@Bean` 编程式注册 |
| **发现机制** | 需要手动 `@ComponentScan` | `AutoConfiguration.imports` 自动发现 |
| **加载条件** | 无条件加载 | 条件装配（按需加载） |
| **用户体验** | 需要手动配置 | 零配置开箱即用 |

---

## 二、自定义 Starter 的设计思想

### 2.1 设计原则

#### 2.1.1 约定优于配置（Convention over Configuration）

Starter 遵循 Spring Boot 的核心设计哲学，通过合理的默认配置减少用户配置工作。

#### 2.1.2 开闭原则（Open/Closed Principle）

Starter 对扩展开放，对修改关闭。用户可以通过配置属性覆盖默认行为，而无需修改 Starter 源码。

#### 2.1.3 单一职责原则

每个 Starter 应该只关注一个特定领域的功能，保持职责单一。

### 2.2 Starter 命名规范

- **官方 Starter**：`spring-boot-starter-{功能名}`（如 `spring-boot-starter-web`）
- **第三方 Starter**：`{模块名}-spring-boot-starter`（如 `mybatis-spring-boot-starter`）

---

## 三、自定义 Starter 的实现方式

### 3.1 基本结构

一个标准的 Starter 模块包含以下文件：

```
my-starter-spring-boot-starter/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/starter/
│       │       ├── config/
│       │       │   └── MyStarterAutoConfiguration.java  # 自动配置类
│       │       ├── core/
│       │       │   └── MyService.java                    # 核心功能类
│       │       └── annotation/
│       │           └── EnableMyStarter.java              # 启用注解（可选）
│       └── resources/
│           └── META-INF/
│               └── spring/
│                   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── pom.xml
```

### 3.2 实现步骤

#### 步骤一：创建自动配置类

```java
@AutoConfiguration
public class MyStarterAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public MyService myService() {
        return new MyService();
    }
}
```

#### 步骤二：配置 `AutoConfiguration.imports` 文件

文件路径：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.example.starter.config.MyStarterAutoConfiguration
```

#### 步骤三：定义配置属性（可选）

```java
@ConfigurationProperties(prefix = "example.my-starter")
@Data
public class MyStarterProperties {
    
    private boolean enabled = true;
    private String defaultName = "default";
    private int timeout = 5000;
}
```

在自动配置类中启用配置属性：

```java
@AutoConfiguration
@EnableConfigurationProperties(MyStarterProperties.class)
public class MyStarterAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public MyService myService(MyStarterProperties properties) {
        return new MyService(properties);
    }
}
```

---

## 四、Spring Boot 对 Starter 的加载机制

### 4.1 自动装配流程

Spring Boot 启动时的自动装配流程如下：

```
1. SpringApplication.run() 启动
       ↓
2. SpringFactoriesLoader 加载 META-INF/spring.factories
       ↓
3. 加载 AutoConfigurationImportSelector
       ↓
4. 扫描所有 JAR 中的 AutoConfiguration.imports 文件
       ↓
5. 解析并加载所有自动配置类
       ↓
6. 条件装配判断
       ↓
7. 注册满足条件的 Bean
```

### 4.2 核心类：`AutoConfigurationImportSelector`

`AutoConfigurationImportSelector` 是自动装配的核心类，负责：

1. 读取 `AutoConfiguration.imports` 文件
2. 根据条件筛选配置类
3. 导入符合条件的配置类

关键方法：

```java
public String[] selectImports(AnnotationMetadata annotationMetadata) {
    // 1. 获取所有候选配置类
    List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
    
    // 2. 去重和过滤
    configurations = removeDuplicates(configurations);
    
    // 3. 根据 @Conditional 注解筛选
    Set<String> exclusions = getExclusions(annotationMetadata, attributes);
    configurations.removeAll(exclusions);
    
    // 4. 排序并返回
    return StringUtils.toStringArray(configurations);
}
```

### 4.3 配置文件加载顺序

Spring Boot 按以下顺序加载配置：

1. `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Spring Boot 2.7+ 推荐）
2. `META-INF/spring.factories`（旧版方式，已Deprecated）

---

## 五、条件装配的实现逻辑

### 5.1 条件装配的作用

条件装配允许 Starter 根据运行时环境动态决定是否加载某个 Bean 或配置类。这是 Starter "智能"的核心所在。

### 5.2 常用条件注解

| 注解 | 作用 | 示例 |
|------|------|------|
| `@ConditionalOnClass` | 当指定类存在时 | `@ConditionalOnClass(RedisTemplate.class)` |
| `@ConditionalOnMissingClass` | 当指定类不存在时 | `@ConditionalOnMissingClass("org.springframework.data.redis.core.RedisTemplate")` |
| `@ConditionalOnBean` | 当指定 Bean 存在时 | `@ConditionalOnBean(RedisTemplate.class)` |
| `@ConditionalOnMissingBean` | 当指定 Bean 不存在时 | `@ConditionalOnMissingBean(MyService.class)` |
| `@ConditionalOnProperty` | 当指定配置属性满足条件时 | `@ConditionalOnProperty(name = "my.starter.enabled", havingValue = "true")` |
| `@ConditionalOnResource` | 当指定资源存在时 | `@ConditionalOnResource(resources = "classpath:my-config.xml")` |
| `@ConditionalOnWebApplication` | 当是 Web 应用时 | `@ConditionalOnWebApplication(type = Type.SERVLET)` |
| `@ConditionalOnNotWebApplication` | 当不是 Web 应用时 | `@ConditionalOnNotWebApplication` |

### 5.3 条件装配的实现原理

所有 `@Conditional` 注解最终都实现了 `Condition` 接口：

```java
public interface Condition {
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

Spring 在注册 Bean 前会调用 `matches()` 方法判断是否满足条件。

### 5.4 组合条件示例

```java
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, StringRedisTemplate.class})
public class RedisCacheAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "example.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public RedisCacheManager cacheManager(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheManager(redisTemplate);
    }
}
```

---

## 六、核心注解详解

### 6.1 `@AutoConfiguration`

**作用**：标记一个类为自动配置类，替代旧版的 `@Configuration + @EnableAutoConfiguration`。

**属性**：
- `after`：指定在哪些配置类之后加载
- `before`：指定在哪些配置类之前加载

```java
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
public class MyAutoConfiguration { }
```

### 6.2 `@ConditionalOnMissingBean`

**作用**：仅当容器中不存在指定类型的 Bean 时才注册。

**属性**：
- `value`/`type`：指定 Bean 类型
- `name`：指定 Bean 名称
- `ignored`：忽略的 Bean 类型（不会覆盖这些 Bean）

```java
@Bean
@ConditionalOnMissingBean(type = "com.example.MyService")
public MyService myService() {
    return new DefaultMyService();
}
```

### 6.3 `@ConditionalOnProperty`

**作用**：根据配置属性的值决定是否加载。

**属性**：
- `prefix`：属性前缀
- `name`：属性名称（支持数组）
- `havingValue`：期望的值
- `matchIfMissing`：当属性不存在时是否匹配（默认 false）
- `relaxedNames`：是否支持松散绑定（默认 true）

```java
@Bean
@ConditionalOnProperty(
    prefix = "example",
    name = {"feature.enabled", "feature.active"},
    havingValue = "true",
    matchIfMissing = true
)
public FeatureService featureService() {
    return new FeatureService();
}
```

### 6.4 `@EnableConfigurationProperties`

**作用**：启用配置属性类，使其能够被 `@Autowired` 注入。

```java
@AutoConfiguration
@EnableConfigurationProperties(MyProperties.class)
public class MyAutoConfiguration {
    
    private final MyProperties properties;
    
    public MyAutoConfiguration(MyProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    public MyService myService() {
        return new MyService(properties.getConfig());
    }
}
```

### 6.5 `@ConfigurationProperties`

**作用**：将配置文件中的属性绑定到 POJO 类。

**属性**：
- `prefix`：属性前缀
- `ignoreInvalidFields`：忽略无效字段（默认 false）
- `ignoreUnknownFields`：忽略未知字段（默认 true）

```java
@ConfigurationProperties(prefix = "example.datasource")
@Validated
public class DataSourceProperties {
    
    @NotBlank
    private String url;
    
    @NotBlank  
    private String username;
    
    @NotBlank
    private String password;
    
    // getters and setters
}
```

---

## 七、实战案例：接口操作日志 Starter

### 7.1 需求分析

实现一个自动记录接口操作日志的 Starter，功能包括：
- 自定义注解标记需要记录日志的方法
- AOP 切面自动拦截并记录日志
- 支持通过配置开关控制功能

### 7.2 实现代码

#### 7.2.1 自定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiOperationLog {
    
    String value() default "";
    
    String module() default "";
}
```

#### 7.2.2 配置属性类

```java
@ConfigurationProperties(prefix = "framework.operation-log")
@Data
public class OperationLogProperties {
    
    private boolean enabled = true;
    private String defaultModule = "default";
    private boolean logParams = true;
    private boolean logResult = false;
}
```

#### 7.2.3 AOP 切面类

```java
@Aspect
public class ApiOperationLogAspect {
    
    private final OperationLogProperties properties;
    
    public ApiOperationLogAspect(OperationLogProperties properties) {
        this.properties = properties;
    }
    
    @Pointcut("@annotation(com.example.starter.annotation.ApiOperationLog)")
    public void operationLogPointcut() { }
    
    @Around("operationLogPointcut()")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        ApiOperationLog annotation = signature.getMethod().getAnnotation(ApiOperationLog.class);
        
        // 记录日志
        String module = StringUtils.hasText(annotation.module()) 
            ? annotation.module() 
            : properties.getDefaultModule();
        
        String methodName = signature.getName();
        
        if (properties.isLogParams()) {
            String params = Arrays.toString(joinPoint.getArgs());
            System.out.printf("[操作日志] 模块:%s, 方法:%s, 参数:%s%n", module, methodName, params);
        }
        
        Object result = joinPoint.proceed();
        
        if (properties.isLogResult()) {
            System.out.printf("[操作日志] 模块:%s, 方法:%s, 结果:%s%n", module, methodName, result);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.printf("[操作日志] 模块:%s, 方法:%s, 耗时:%dms%n", module, methodName, duration);
        
        return result;
    }
}
```

#### 7.2.4 自动配置类

```java
@AutoConfiguration
@EnableConfigurationProperties(OperationLogProperties.class)
@ConditionalOnClass(Aspect.class)
public class OperationLogAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "framework.operation-log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public ApiOperationLogAspect apiOperationLogAspect(OperationLogProperties properties) {
        return new ApiOperationLogAspect(properties);
    }
}
```

#### 7.2.5 `AutoConfiguration.imports` 文件

```
com.example.starter.config.OperationLogAutoConfiguration
```

### 7.3 使用方式

#### 7.3.1 添加依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>operation-log-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 7.3.2 在 Controller 中使用

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @ApiOperationLog(module = "用户管理", value = "查询用户列表")
    @GetMapping
    public List<User> listUsers() {
        // 业务逻辑
    }
}
```

#### 7.3.3 配置属性

```yaml
framework:
  operation-log:
    enabled: true           # 是否启用日志记录
    default-module: system  # 默认模块名
    log-params: true        # 是否记录请求参数
    log-result: false       # 是否记录返回结果
```

---

## 八、高级特性

### 8.1 配置元数据

为了提供更好的 IDE 自动补全支持，Starter 可以提供配置元数据文件：

文件路径：`src/main/resources/META-INF/spring-configuration-metadata.json`

```json
{
  "properties": [
    {
      "name": "framework.operation-log.enabled",
      "type": "java.lang.Boolean",
      "description": "是否启用操作日志功能",
      "defaultValue": true
    },
    {
      "name": "framework.operation-log.default-module",
      "type": "java.lang.String", 
      "description": "默认模块名称",
      "defaultValue": "default"
    }
  ]
}
```

### 8.2 条件依赖

使用 `@ConditionalOnClass` 确保只在特定依赖存在时加载配置：

```java
@AutoConfiguration
@ConditionalOnClass({RedisTemplate.class, JedisConnectionFactory.class})
public class RedisAutoConfiguration {
    // 仅当 Redis 相关类存在时才加载此配置
}
```

### 8.3 自动配置报告

启动时添加 `--debug` 参数可以查看自动配置报告：

```bash
java -jar my-app.jar --debug
```

报告会显示：
- **Positive matches**：满足条件并加载的配置
- **Negative matches**：不满足条件未加载的配置
- **Exclusions**：被排除的配置

---

## 九、总结

### 9.1 Starter 的核心价值

1. **开箱即用**：用户只需添加依赖即可使用功能
2. **按需加载**：通过条件装配避免不必要的资源占用
3. **可配置性**：通过配置属性灵活调整行为
4. **解耦合**：将通用功能封装为独立模块

### 9.2 开发 Starter 的要点

1. **命名规范**：遵循 `xxx-spring-boot-starter` 命名约定
2. **条件装配**：合理使用条件注解控制加载时机
3. **配置属性**：提供清晰的配置选项和默认值
4. **文档完善**：提供配置元数据和使用说明

### 9.3 最佳实践

1. **单一职责**：每个 Starter 只关注一个领域
2. **向后兼容**：保持配置属性的兼容性
3. **优雅降级**：当依赖缺失时提供友好的错误提示
4. **测试覆盖**：编写完善的单元测试和集成测试

---

## 附录：参考资料

1. [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
2. [Spring Boot Auto-configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.auto-configuration)
3. [Creating Your Own Starter](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-developing-auto-configuration.html)
4. [Spring Factories Loader](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/support/SpringFactoriesLoader.html)