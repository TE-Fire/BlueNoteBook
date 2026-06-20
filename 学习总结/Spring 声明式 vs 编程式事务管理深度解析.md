# Spring 声明式 vs 编程式事务管理深度解析

## 一、两种方式的本质差异："谁在控制事务？"

声明式事务（`@Transactional`）和编程式事务（`TransactionTemplate`）都能管理事务，但它们的差异远不止是"一个用注解、一个用代码"这么表面。两者的根本分歧在于：**事务的控制权归属于谁？**

### 1.1 声明式 `@Transactional`：框架控制，约定优于配置

声明式事务的核心理念是：**事务是横切关注点，应该从业务代码中剥离**。

```java
@Transactional(rollbackFor = Exception.class)
public void createOrder(OrderDTO dto) {
    // 业务代码里完全没有事务控制的痕迹
    orderMapper.insert(order);
    inventoryMapper.deduct(order.getItems());
}
```

你在方法上贴一个注解，Spring 在运行时通过 AOP 代理拦截这个方法调用，在方法执行前开启事务、方法正常返回时提交、方法抛异常时回滚。你的业务代码完全不知道事务的存在。

这本质上是 **"声明你要什么"**，而不是 **"命令怎么做"**。你说"这个方法需要事务"，Spring 替你处理开启、提交、回滚这些机械动作。这跟 SQL 是声明式语言一个道理——你写 `SELECT * FROM user WHERE age > 18`，说的是"我要这些数据"，而不是"先读索引、再回表、最后过滤"。

**控制权在框架手里。** 你放弃了对事务边界的精细控制，换来的是代码的简洁。这在大多数 CRUD 场景下是完全合理的——反正整个方法就是一个事务，成功就提交，失败就回滚，不需要更多微操。

### 1.2 编程式 `TransactionTemplate`：开发者控制，显式优于隐式

编程式事务的核心理念是：**事务是业务逻辑的一部分，不是横切关注点**。

```java
public Long registerUser(String phone) {
    return transactionTemplate.execute(status -> {
        try {
            // 事务代码块
            userDOMapper.insert(userDO);
            userRoleDOMapper.insert(userRoleDO);
            return userId;
        } catch (Exception e) {
            status.setRollbackOnly();  // ← 回滚是一个显式的业务动作
            return null;               // ← 回滚后返回什么、外层怎么处理，都是业务决策
        }
    });
}
```

注意看 `status.setRollbackOnly()` 这行代码。它不是框架替你做的，而是**你亲手写的**。这表明了一个态度：**"回滚"是一个携带业务含义的决策，不是一个可以交给框架的机械动作。** 回滚之后返回 `null` 还是抛异常、外层代码拿到结果后怎么走分支——这些在你写代码的时候就有意识地做了选择。

**控制权在你手里。** 事务边界、回滚时机、异常处理策略，全部是你显式编码的，没有 AOP 代理的"魔法"，没有隐式行为带来的意外。

### 1.3 不是语法糖的差异

很多人把这两种方式理解成"注解版"和"代码版"的语法糖差异，认为只是写法不同。但本质上是**控制权归属**的差异：

| 维度 | 声明式 `@Transactional` | 编程式 `TransactionTemplate` |
|------|------------------------|------------------------------|
| **控制权** | 框架 | 开发者 |
| **哲学** | 约定优于配置（Convention over Configuration） | 显式优于隐式（Explicit over Implicit） |
| **事务边界** | 方法签名决定，编译期固定 | 代码块决定，运行时灵活 |
| **回滚决策** | 注解属性静态配置 | 代码内动态判断 |
| **异常处理** | 回滚必然伴随异常抛出 | 回滚可以不抛异常，返回业务值 |

---

## 二、使用场景：核心判断标准

两种方式没有绝对的优劣，选哪种取决于你的场景。核心的判断标准只有一个：

> **事务边界是否等于方法边界？**

### 2.1 场景 A：事务边界 == 方法边界 → 声明式最自然

这是声明式事务的主场。整个方法就是一个事务单位，只有两种结局：

```
方法执行成功 → 提交事务
方法抛异常   → 回滚事务
```

```java
@Transactional(rollbackFor = Exception.class)
public void createOrder(OrderDTO dto) {
    orderMapper.insert(order);           // 插入订单
    orderItemMapper.batchInsert(items);  // 插入订单明细
    couponMapper.markUsed(dto.getCouponId());  // 标记优惠券已用
    // 任何一步失败，全部回滚
}
```

这种场景下，声明式是最优解：无冗余代码，意图清晰，团队协作成本低。

**但有一个重要的细节：`rollbackFor = Exception.class` 为什么要写？**

Spring 的默认行为有一个反直觉的设计：只对 `RuntimeException` 和 `Error` 自动回滚，对**受检异常（checked exception）不回滚**。这意味着如果你的代码抛出了 `SQLException`（受检异常），事务会照常提交——这显然不是你想要的结果。

```java
// 默认行为：RuntimeException → 回滚，checked exception → 不回滚
@Transactional  // 危险！受检异常不会触发回滚

// 推荐写法：所有异常都回滚
@Transactional(rollbackFor = Exception.class)
```

所以 `rollbackFor = Exception.class` 本质是在**修正框架的默认行为**，让它符合直觉。这也暴露了声明式的一个尴尬：你需要在注解上提前声明"哪些异常应该回滚"——如果业务中出现了你没预料到的异常类型，行为就可能不符合预期。

### 2.2 场景 B：事务边界 < 方法边界 → 编程式更合适

当事务只需要覆盖方法内的**一部分逻辑**时，声明式就不好使了。回看项目中的实际代码：

```java
// UserServiceImpl.java — loginAndRegister 方法
public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
    // 1. 验证码校验 —— 不需要事务
    // 2. 查用户是否存在 —— 不需要事务
    // 3. 如果未注册，自动注册 —— 只有这一步需要事务！
    if (Objects.isNull(userDo)) {
        userId = registerUser(phone);  // 事务发生在 registerUser 内部
    }
    // 4. 发 token —— 不需要事务
    StpUtil.login(userId);
    return Response.success(tokenInfo.tokenValue);
}
```

整个 `loginAndRegister()` 的流程是：校验验证码 → 查用户 → **注册（需要事务）** → 发 token。只有"注册"这一小段逻辑需要事务保护，整个方法不需要。

你用 `@Transactional` 怎么搞？只能把注册逻辑拆到另一个类（因为自调用不走代理）的独立方法上。拆出来之后还有一个更棘手的问题：**注册失败后，外层怎么办？**

**声明式的困境：回滚一定抛异常**

```java
// 如果你把注册拆成一个 @Transactional 方法
@Transactional(rollbackFor = Exception.class)
public Long registerUser(String phone) {
    // ... 插入用户、分配角色 ...
    // 如果这里出错，事务回滚，异常向上抛给 loginAndRegister()
}

// 外层必须 try-catch
public Response<String> loginAndRegister(...) {
    // ...
    try {
        userId = registerUser(phone);
    } catch (Exception e) {
        // 注册失败，但验证码已经校验通过了，你就只因为注册没成功
        // 而让整个登录失败吗？还是降级处理？
        return Response.fail("注册失败");  // 但用户验证码白输了
    }
    // ...
}
```

声明式事务的机制决定了：回滚 = 抛异常。异常会中断外层流程，强制外层做 try-catch。这不是说做不到，但**异常变成了控制流**，代码会变得别扭。

**编程式的优势：回滚可以不抛异常**

```java
// 项目中的实际写法
public Long registerUser(String phone) {
    return transactionTemplate.execute(status -> {
        try {
            // ... 插入用户、分配角色 ...
            return userId;
        } catch (Exception e) {
            status.setRollbackOnly();  // 事务回滚了
            return null;               // 但没有抛异常！调用方拿到 null 做判断
        }
    });
}
```

回滚了，但没有抛异常。外层代码自然地用返回值做后续判断：

```java
// 外层调用
userId = registerUser(phone);
if (userId == null) {
    // 注册失败，但不影响登录流程的其他部分
    // 你可以返回特定错误、或者降级处理
}
```

**关键洞察：声明式的回滚必然伴随异常抛出；编程式的回滚可以不抛异常。** 当你的业务需要"事务回滚了，但外层逻辑继续跑"时，编程式是自然的选择。

### 2.3 场景 C：事务内有非事务资源 → 编程式更诚实

回看 `registerUser()` 的事务代码块：

```java
transactionTemplate.execute(status -> {
    // 1. Redis INCR —— 不受事务保护！
    Long xiaohashuId = redisTemplate.opsForValue().increment(RedisKeyConstants.BLUENOTE_ID_GENERATOR_KEY);

    // 2. MySQL INSERT —— 受事务保护
    UserDO userDO = UserDO.builder()
            .xiaohashuId(String.valueOf(xiaohashuId))
            // ...
            .build();
    userDOMapper.insert(userDO);

    // 3. MySQL INSERT —— 受事务保护
    userRoleDOMapper.insert(userRoleDO);

    // 4. Redis SET —— 不受事务保护！
    redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));
});
```

这里面有四种操作，但只有两种在 JDBC 事务的保护范围内。Redis 的 `INCR` 和 `SET` 一旦执行就不可回滚。如果第 3 步（插入角色）失败，事务回滚了，但 Redis 的 ID 已经 +1 了，这个 ID 就永久丢失了。

用声明式 `@Transactional` 时，你很容易产生一种错觉：整个方法都受事务保护。注解给人一种"这个方法里的操作都在一个事务里"的安心感，但这是假的——Redis 操作根本不是 JDBC 事务的一部分。

用编程式 `TransactionTemplate` 时，你亲手写了 `execute(status -> {...})`，你会下意识地想："这段代码块里的东西在事务保护下吗？哪些是？哪些不是？" 这种**显式的事务边界**让你对非事务资源更加警觉。你不会产生"注解保护了一切"的错觉，因为你没有注解——你只有一段你自己圈出来的事务代码块。

### 2.4 场景 D：需要条件性回滚 → 只有编程式做得到

声明式的回滚规则是**静态的**：你在注解上声明"遇到 X 异常就回滚"，规则在编译期就固定了。

但现实中有很多场景，回滚的条件不是"发生了某种异常"，而是**业务状态不满足**：

```java
transactionTemplate.execute(status -> {
    // 扣库存
    int rows = inventoryMapper.deductWithVersion(skuId, quantity, version);
    
    // 这不是异常，是正常的并发竞争
    if (rows == 0) {
        status.setRollbackOnly();  // 更新影响 0 行 = 被人抢了 → 回滚
        return false;
    }
    
    // 下单
    orderMapper.insert(order);
    return true;
});
```

"更新影响行数为 0"不是异常——乐观锁更新返回 0 行是一个**正常的业务结果**，表示有人同时也在扣库存。你需要在代码里判断 `if (rows == 0)` 然后回滚。声明式做不到这一点，因为没有异常可以触发它的回滚规则。

再比如：

```java
transactionTemplate.execute(status -> {
    BigDecimal balance = accountMapper.getBalance(userId);
    if (balance.compareTo(amount) < 0) {
        status.setRollbackOnly();  // 余额不足 → 回滚
        return "余额不足";
    }
    accountMapper.deduct(userId, amount);
    return "扣款成功";
});
```

余额不足不应该抛异常——它是一个预期的业务分支。但声明式的回滚机制完全绑死在异常上，这就意味着你不得不把"余额不足"包装成异常来触发回滚，这扭曲了异常的本意（异常应该是意外情况，不是预计中的业务分支）。

---

## 三、本项目实际选择分析

以 `UserServiceImpl.registerUser()` 为例，逐层分析当前的选择。

### 3.1 当前实现

```java
// 来源：bluenote-auth/.../service/impl/UserServiceImpl.java 第 110-155 行
public Long registerUser(String phone) {
    return transactionTemplate.execute(status -> {
        try {
            // 获取全局自增的 ID（Redis INCR）
            Long xiaohashuId = redisTemplate.opsForValue()
                    .increment(RedisKeyConstants.BLUENOTE_ID_GENERATOR_KEY);

            // 构建并插入用户记录
            UserDO userDO = UserDO.builder()
                    .phone(phone)
                    .xiaohashuId(String.valueOf(xiaohashuId))
                    .nickname("小红薯" + xiaohashuId)
                    .status(StatusEnum.ENABLE.getValue())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(DeletedEnum.NO.getValue())
                    .build();
            userDOMapper.insert(userDO);

            Long userId = userDO.getId();

            // 分配默认角色
            UserRoleDO userRoleDO = UserRoleDO.builder()
                    .userId(userId)
                    .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(DeletedEnum.NO.getValue())
                    .build();
            userRoleDOMapper.insert(userRoleDO);

            // 角色信息缓存到 Redis
            List<Long> roles = Lists.newArrayList();
            roles.add(RoleConstants.COMMON_USER_ROLE_ID);
            String userRolesKey = RedisKeyConstants.buildUserRoleKey(phone);
            redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

            return userId;
        } catch (Exception e) {
            status.setRollbackOnly();   // 显式标记回滚
            log.error("==> 系统注册用户异常: ", e);
            return null;                // 返回 null 而非抛出异常
        }
    });
}
```

### 3.2 为什么这里编程式比声明式更合适

三个原因，按重要性排序：

**第一：注册是主流程的子步骤，回滚不应中断主流程。**

`registerUser()` 被 `loginAndRegister()` 调用。注册只是登录流程中的一个分支（"如果用户不存在，则自动注册"），不是主流程本身。如果注册失败，应该让事务回滚（数据库干净），但不应该炸掉整个登录请求。编程式用 `return null` 做到了"回滚但不抛异常"，声明式做不到。

**第二：Redis 和 MySQL 混在一个事务块里。**

事务内的 Redis `INCR` 是 JDBC 事务管不到的。编程式用显式的 `execute` 代码块把这个问题暴露了出来——开发者必须意识到 Redis 操作在这段代码里的位置和后果。如果用 `@Transactional` 注解，这个隐患会被隐藏。

**第三：事务粒度精确匹配需求。**

只有插入用户 + 插入角色 + 缓存角色这三步需要原子性。前面的 ID 生成（Redis INCR）和后面的外层逻辑都不需要事务保护。编程式可以把事务精确地框在这三步上。

### 3.3 如果改成声明式会怎样？

为了让声明式生效，你必须把事务逻辑拆到**另一个 Spring Bean** 上（因为同类自调用不走代理）：

```java
// ===== UserServiceImpl.java =====
@Service
public class UserServiceImpl implements UserService {
    
    @Resource
    private UserRegisterService userRegisterService;  // 注入另一个 Bean

    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        // ...
        if (Objects.isNull(userDo)) {
            try {
                userId = userRegisterService.registerUser(phone);
            } catch (RuntimeException e) {
                // 声明式回滚必然抛异常，外层必须 try-catch
                // 但异常的类型是什么？你需要约定一种"注册失败"异常
                log.error("注册失败", e);
                return Response.fail("注册失败");
            }
        }
        // ...
    }
}

// ===== UserRegisterService.java（新建一个类）=====
@Service
public class UserRegisterService {
    
    @Transactional(rollbackFor = Exception.class)
    public Long registerUser(String phone) {
        // ... 同样的业务逻辑 ...
        // 如果中间出错，抛出 RuntimeException → 事务回滚
        // 异常会传播到 loginAndRegister()，中断主流程
        throw new RuntimeException("注册失败");  
    }
}
```

对比之下，声明式版本多了一个类、多了一组异常控制流，还丢失了"回滚但不抛异常"的灵活性。不是说声明式不行，而是**在这个具体场景下它增加了复杂度，而不是减少复杂度**——而声明式的本意恰恰是"简化"。

---

## 四、决策速查表

| 判断条件 | 倾向 | 原因 |
|----------|------|------|
| 整个方法就是一个事务 | **声明式** | 最简洁，没有冗余代码 |
| 只有方法内的部分逻辑需要事务 | **编程式** | 事务边界精确，不污染整个方法 |
| 回滚后外层需要继续处理 | **编程式** | 声明式回滚 = 抛异常，会中断外层 |
| 回滚一定意味着业务流程失败 | **声明式** | 抛异常是正确的做法 |
| 事务内只有数据库操作 | 都可以 | 两者都能胜任 |
| 事务内混有 Redis / MQ 等非事务资源 | **编程式** | 显式边界让人对非事务资源保持警觉 |
| 回滚条件 = 某个异常类型 | **声明式** | 注解里配 `rollbackFor` 就够了 |
| 回滚条件 = 业务状态判断 | **编程式** | 声明式的回滚规则绑死在异常上 |
| 团队对 Spring AOP 代理机制不熟 | **编程式** | 避免自调用失效、非 public 等坑 |
| 想最快上手、代码最简洁 | **声明式** | 一个注解搞定，Spring 社区的标准实践 |

---

## 总结

声明式事务和编程式事务不是"哪个更好"的问题，是**控制权放在哪**的问题。

声明式把控制权交给框架，换来简洁。当你的事务就是整个方法、回滚就是抛异常时，这是最自然的写法。

编程式把控制权留给自己，换来灵活。当你的方法内有多个关注点、回滚只是其中一条分支、或者事务里混了不受保护的外部资源时，显式控制让你不必跟 AOP 代理的隐式行为较劲。

当前项目中 `registerUser()` 选择编程式的决策是合理的——它匹配了"子步骤事务 + 回滚不抛异常 + 混有非事务资源"这三重需求。当你遇到类似场景时，编程式应该是你的首选。
