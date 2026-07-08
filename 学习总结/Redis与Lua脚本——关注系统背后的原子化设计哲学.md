# Redis + Lua 脚本：关注系统背后的"原子化"设计哲学深度解析

> 本文以"小蓝书"（BlueNoteBook）项目为实际案例，结合 `bluenote-user-relation` 服务中的三份 Lua 脚本（`follow_check_and_add.lua`、`follow_add_and_expire.lua`、`follow_batch_add_and_expire.lua`）以及 `RelationServiceImpl.java` 的完整业务代码，深入讲解：为什么关注功能需要用 Redis + Lua？ZSet 在这个场景中为什么是最优数据结构？"原子化脚本"解决了什么 Java 代码无法解决的问题？以及这套方案背后的设计思想是什么？

---

## 一、开篇：一个"关注"按钮，背后有多少步操作？

小蓝书的关注功能看起来极其简单：用户 A 点击"关注"按钮，关注用户 B。如果让你用 Java 代码实现，你可能第一时间想到这样写：

```java
// 新手的第一反应：用 Java 操作 Redis，分步执行

public void follow(Long userId, Long followUserId) {
    String key = "following:" + userId;

    // 第一步：检查是否已关注
    Double score = redisTemplate.opsForZSet().score(key, followUserId);
    if (score != null) {
        throw new BizException("已经关注了该用户");
    }

    // 第二步：检查关注数量是否超限
    Long size = redisTemplate.opsForZSet().zCard(key);
    if (size != null && size >= 1000) {
        throw new BizException("关注人数已达上限");
    }

    // 第三步：执行关注
    redisTemplate.opsForZSet().add(key, followUserId, System.currentTimeMillis());
}
```

这段代码**逻辑上完全正确**。但如果在生产环境中运行，它会暴露出一个你作为新手可能完全没想到的问题。而且这个问题的答案，正是你看到的 Lua 脚本方案的**全部存在理由**。

### 1.1 那个"看不见"的问题：Redis 命令之间的"时间裂缝"

看回上面的 Java 代码。`zCard`（查数量）和 `add`（添加）是**两个独立的 Redis 命令**，它们在网络上分两次发送，在 Redis 服务端分两次执行。

```text
时间轴 →

线程 A（用户 A 关注用户 B）:
  [t1] zCard → Redis 返回 999（还差 1 个到上限）
  [t2] zAdd  → Redis 添加成功

线程 B（用户 A 同时关注用户 C）:
  [t1] zCard → Redis 返回 999（线程 A 还没执行 zAdd！）
  [t2] 判断 999 < 1000 → OK，可以关注
  [t3] zAdd  → Redis 添加成功

结果：关注数 = 1001，突破了 1000 的上限！
```

两个线程在 `zCard` 和 `zAdd` 之间发生了**交错执行**。虽然 Redis 本身是单线程执行命令的，但两个线程的两次命令序列可以在 Redis 的执行队列中交错排列。Thread A 的 zCard 和 Thread B 的 zCard 都读到了 999，然后两条 zAdd 都成功执行——上限被打破了。

**根本原因**：`zCard` + 判断 + `zAdd` 这三个操作不是原子的。它们之间有"时间裂缝"，其他线程的 Redis 命令可以在这个裂缝中插入执行。

### 1.2 Java 的解决方案为什么不够好？

你可能会想：加个 `synchronized` 或者分布式锁不就行了？

**方案一：JVM 级别的 `synchronized`**

```java
public synchronized void follow(...) { ... }
```

这只能保证**同一个 JVM 进程内**的线程互斥。但微服务架构中，`bluenote-user-relation` 通常有多个实例。实例 1 和实例 2 各有一个线程，`synchronized` 管不到另一个 JVM 里的线程。

**方案二：Redis 分布式锁**

```java
public void follow(...) {
    String lockKey = "lock:following:" + userId;
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
    if (!locked) throw new BizException("操作频繁");

    try {
        // zCard → 判断 → zAdd
    } finally {
        redisTemplate.delete(lockKey);
    }
}
```

这个方案**确实可以解决并发问题**，但有代价：
- **性能开销**：每次关注操作需要额外的 `SETNX` + `DEL`，增加了网络往返
- **锁超时风险**：TTL 到期了但业务还没执行完，锁自动释放，并发问题又回来
- **代码复杂度上升**：需要考虑锁续期、死锁、Redlock 等

**有没有一种方式，让"检查 + 添加"这些步骤在 Redis 内部作为一个不可分割的整体执行？**

这就是 Lua 脚本的用武之地。

---

## 二、Redis + Lua：把"多步操作"变成一个"原子指令"

### 2.1 核心原理：Redis 服务端的 Lua 解释器

Redis 从 2.6 版本开始内置了 Lua 5.1 解释器。你可以把一段 Lua 脚本发送给 Redis，Redis 会在**服务端单线程地**执行整段脚本。在脚本执行期间，**其他任何 Redis 命令都不会被插入执行**。

用原生的 `EVAL` 命令来理解：

```bash
EVAL "
  local exists = redis.call('EXISTS', KEYS[1])
  if exists == 0 then
    return -1
  end
  local size = redis.call('ZCARD', KEYS[1])
  if size >= 1000 then
    return -2
  end
  if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    return -3
  end
  redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
  return 0
" 1 "following:1001" "2002" "1720345678123"
```

这段 Lua 脚本在 Redis 内部执行时，`EXISTS` → `ZCARD` → 判断 → `ZSCORE` → 判断 → `ZADD`，整个过程**一气呵成，不可分割**。即使有 100 个 Java 线程同时发送这个脚本，Redis 会将它们**排队串行执行**——这就是原子性的保证。

现在回看你的 `follow_check_and_add.lua`，它正是这个逻辑：

```lua
-- follow_check_and_add.lua
local key = KEYS[1]
local followUserId = ARGV[1]
local timestamp = ARGV[2]

-- 1. 检查 ZSet 是否存在（缓存是否命中）
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1    -- 返回 -1 → Java 层触发"从 MySQL 加载数据到 Redis"
end

-- 2. 检查关注人数是否达到上限
local size = redis.call('ZCARD', key)
if size >= 1000 then
    return -2    -- 返回 -2 → Java 层抛出"关注已达上限"异常
end

-- 3. 检查是否已经关注了该用户
if redis.call('ZSCORE', key, followUserId) then
    return -3    -- 返回 -3 → Java 层抛出"已关注"异常
end

-- 4. 所有检查通过，执行关注
redis.call('ZADD', key, timestamp, followUserId)
return 0         -- 返回 0 → 关注成功
```

这 20 行 Lua 代码实现了**4 个步骤的原子化**。Java 代码只需要调用一次 `redisTemplate.execute(script, keys, args)`，剩下的全交给 Redis。

### 2.2 Lua 脚本 vs 分布式锁：一个直观对比

| 维度 | Java 分布式锁方案 | Redis Lua 脚本方案 |
|------|------------------|-------------------|
| **网络往返** | `SETNX` + `ZCARD` + `ZADD` + `DEL` = 4 次 | `EVAL` = 1 次 |
| **原子性保证** | 依赖锁的正确实现（超时、续期、死锁…） | Redis 单线程天然保证 |
| **并发性能** | 同一个 userId 的请求全部串行（锁互斥） | 同一个 userId 的请求在 Redis 排队，但不同 userId 的请求并发执行 |
| **故障恢复** | 锁超时未释放 → 并发问题；锁误删 → 并发问题 | Redis 挂了脚本自然执行不了，没有"残留状态" |
| **代码复杂度** | 需要处理锁超时、锁续期、锁释放、异常回滚 | 一个 Lua 文件 + 一行 Java 调用 |

**Lua 脚本的本质**：把"多步操作"打包成一个"原子指令"发送给 Redis。Redis 收到后一气呵成执行完毕，中间不会有任何其他命令插入。这比分布式锁更**简洁、高效、安全**。

---

## 三、为什么用 ZSet（有序集合）存储关注关系？

你已经看到 Redis 有多种数据结构：String、Hash、List、Set、ZSet。为什么关注关系偏偏选择 ZSet？

### 3.1 ZSet 的双重身份：既是集合，又带排序

ZSet（Sorted Set）是 Redis 中最特殊的数据结构。它像一个"自带排名的 Set"——每个元素（member）关联一个分数（score），元素唯一，按分数排序。

在你的关注场景中：

```text
Key:   following:1001          ← 用户 1001 的关注列表
ZSet:
  score (关注时间戳)          member (被关注的用户 ID)
  1720345678123          →     2002
  1720345689234          →     2005
  1720345701345          →     2010
  1720345712456          →     2015
```

ZSet 天然契合关注系统的需求：

1. **元素唯一**：你只能关注一个用户一次——ZSet 的 member 唯一，重复 `ZADD` 同一 member 只会更新 score，不会产生重复元素
2. **按时间排序**：score 存关注时间戳 → `ZREVRANGE` 可以按时间倒序拉取关注列表 → "你最早关注了谁？最近关注了谁？"一个命令搞定
3. **O(log N) 的查询和插入**：`ZADD`、`ZSCORE` 的时间复杂度都是 O(log N)，即使是 1000 个关注（你设定的上限），性能依然极快
4. **支持范围查询**：分页拉取关注列表只需要一条命令，不需要在 Java 层做排序

相比之下，如果用 Set（无序集合），你可以存关注关系，但没法按时间排序。如果用 List，插入和去重都会很麻烦。

### 3.2 ZSet 在这个场景下的关键命令

| 命令 | 用途 | 在关注场景中的含义 |
|------|------|-------------------|
| `ZADD key score member` | 添加元素 | 执行关注（关注时间作为 score） |
| `ZSCORE key member` | 查询某个 member 的分数 | 检查是否已关注（score ≠ nil → 已关注） |
| `ZCARD key` | 查询元素个数 | 查询关注人数 |
| `ZREVRANGE key 0 9 WITHSCORES` | 按分数倒序取前 10 个 | 取最近关注的 10 个用户 |
| `EXISTS key` | 检查 key 是否存在 | 检查缓存是否命中 |
| `EXPIRE key seconds` | 设置过期时间 | 缓存过期策略 |

---

## 四、你的三份 Lua 脚本——逐份拆解设计意图

现在理解了"为什么用 Lua"和"为什么用 ZSet"，我们逐份分析三份 Lua 脚本各自解决什么问题。

### 4.1 `follow_check_and_add.lua` —— "带前置检查的关注操作"

```lua
local key = KEYS[1]
local followUserId = ARGV[1]
local timestamp = ARGV[2]

local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1        -- ZSet 不存在 → 缓存未命中，需要从 MySQL 加载
end

local size = redis.call('ZCARD', key)
if size >= 1000 then
    return -2        -- 关注已达上限
end

if redis.call('ZSCORE', key, followUserId) then
    return -3        -- 已经关注过
end

redis.call('ZADD', key, timestamp, followUserId)
return 0             -- 关注成功
```

这是**核心脚本**。设计要点：

**检查顺序是有深意的**：先 `EXISTS`（O(1)），再 `ZSCORE`（O(log N)，提前发现"已关注"快速短路），最后 `ZCARD`。每一步失败都能节省后续开销。

**返回状态码的编码协议**：

```java
// LuaResultEnum.java
ZSET_NOT_EXIST(-1L),   // → 触发缓存回源（从 MySQL 加载数据到 Redis）
FOLLOW_LIMIT(-2L),     // → 抛出异常"关注已达上限"
ALREADY_FOLLOWED(-3L), // → 抛出异常"已关注"
FOLLOW_SUCCESS(0L),    // → 关注成功
```

**为什么 `ZSET_NOT_EXIST` 不直接在 Lua 脚本里处理？**因为"从 MySQL 加载数据并写入 Redis"涉及到数据库连接、对象映射——这些不是 Redis Lua 脚本该做的事。Lua 脚本只做"Redis 内部的原子操作"，重量操作交给 Java 层。这是**职责分离**的体现。

### 4.2 `follow_add_and_expire.lua` —— "单个添加 + 设置过期"

```lua
local key = KEYS[1]
local followUserId = ARGV[1]
local timestamp = ARGV[2]
local expireSeconds = ARGV[3]

redis.call('ZADD', key, timestamp, followUserId)
redis.call('EXPIRE', key, expireSeconds)
return 0
```

看起来只有两步，为什么也要用 Lua 包装？**因为 `ZADD` 和 `EXPIRE` 也需要原子性。**

如果分两次 Java 调用：`ZADD` 成功 → 网络抖动 → `EXPIRE` 没发出去 → 这个 Key 永远不会过期 → **内存泄漏**。

Lua 脚本保证了两步要么全部执行，要么全部不执行。使用场景：缓存未命中（`follow_check_and_add.lua` 返回 -1），且 MySQL 中该用户**没有历史关注记录**时调用。

### 4.3 `follow_batch_add_and_expire.lua` —— "批量同步 + 设置过期"

```lua
local key = KEYS[1]

local zaddArgs = {}
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs, ARGV[i])      -- score
    table.insert(zaddArgs, ARGV[i+1])    -- member
end

redis.call('ZADD', key, unpack(zaddArgs))

local expireTime = ARGV[#ARGV]           -- 最后一个参数是过期时间
redis.call('EXPIRE', key, expireTime)
return 0
```

这个脚本的使用场景：缓存未命中，且 MySQL 中该用户**有大量历史关注记录**，需要全量同步到 Redis。

**为什么需要专门的批量脚本？**假设用户关注了 500 个人：
- 逐条 `ZADD`：500 次网络往返，总耗时 ≈ 500ms
- 批量 Lua：1 次网络往返 + ZADD 一次性写入 500 对 score/member，总耗时 ≈ 1ms

Java 层通过 `buildLuaArgs` 构造参数：

```java
// 参数结构: [score1, member1, score2, member2, ..., scoreN, memberN, expireSeconds]
private static Object[] buildLuaArgs(List<FollowingDO> followingDOs, long expireSeconds) {
    int argsLength = followingDOs.size() * 2 + 1;
    Object[] luaArgs = new Object[argsLength];
    int i = 0;
    for (FollowingDO following : followingDOs) {
        luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime());
        luaArgs[i + 1] = following.getFollowingUserId();
        i += 2;
    }
    luaArgs[argsLength - 1] = expireSeconds;
    return luaArgs;
}
```

---

## 五、在 Java 中执行 Lua 脚本 —— Spring Data Redis 的 API

上面讲了三份脚本的设计意图，现在用一个完整的代码示例来展示如何在 Java 中加载和执行它们。Spring Data Redis 提供了 `DefaultRedisScript` 来封装 Lua 脚本的执行。

### 5.1 核心 API：三步走

```java
// 第一步：创建 RedisScript 对象，加载 Lua 脚本文件
DefaultRedisScript<Long> script = new DefaultRedisScript<>();
script.setScriptSource(
    new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua"))
);
script.setResultType(Long.class);  // 声明 Lua 返回值类型

// 第二步：准备参数
// KEYS: 传给 Lua 的 KEYS[] 数组，通常用 Collections.singletonList() 包装
// ARGV[]: 传给 Lua 的可变长参数
List<String> keys = Collections.singletonList("following:1001");
Object[] args = {2002L, 1720345678123L};  // followUserId, timestamp

// 第三步：执行脚本
Long result = redisTemplate.execute(script, keys, args);
```

几行代码就把 "Lua 脚本加载 → 参数绑定 → EVAL 执行 → 返回值接收" 整个流程串起来了。

### 5.2 关键 API 拆解

**`DefaultRedisScript<T>`**：Spring 对 Redis Lua 脚本的封装类。泛型 `T` 指定返回值类型，通过 `setResultType()` 设置。常见返回值类型：
- `Long.class`：Lua 返回整数（最常用，适合状态码场景）
- `Boolean.class`：Lua 返回 1/0
- `String.class`：Lua 返回字符串

**`ResourceScriptSource`**：从 classpath 加载 `.lua` 文件。你的脚本放在 `src/main/resources/lua/` 下，编译后在 classpath 根路径的 `lua/` 目录中。

**`redisTemplate.execute(script, keys, args)`**：底层原理是 `EVALSHA`（脚本缓存优化）。首次执行时 Redis 会通过 `EVAL` 发送完整脚本并计算 SHA1 指纹；后续执行同一个脚本，Spring 自动改用 `EVALSHA sha1` —— 只传指纹不传脚本，减少网络传输。

**`Collections.singletonList(key)`**：对应 Lua 中的 `KEYS[1]`。如果脚本需要多个 Key 就用 `Arrays.asList(key1, key2)`。注意 Key 和 Arg 的区分——Key 用于 Redis Cluster 的路由计算，Arg 则不影响路由。

### 5.3 你代码中的实际用法

你的 `RelationServiceImpl.follow()` 方法中，三种场景分别用了三份脚本：

```java
// 场景1：核心关注流程 —— 先执行 follow_check_and_add.lua
DefaultRedisScript<Long> script = new DefaultRedisScript<>();
script.setScriptSource(new ResourceScriptSource(
    new ClassPathResource("/lua/follow_check_and_add.lua")));
script.setResultType(Long.class);

// KEYS = ["following:1001"], ARGV = [followUserId, timestamp]
Long result = redisTemplate.execute(
    script,
    Collections.singletonList(followingRedisKey),  // KEYS
    followUserId, timestamp                         // ARGV (可变参数)
);

// 场景2：全量同步历史数据 —— 执行 follow_batch_add_and_expire.lua
DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
script3.setScriptSource(new ResourceScriptSource(
    new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
script3.setResultType(Long.class);

// KEYS = ["following:1001"], ARGV = [score1, member1, ..., expireSeconds]
redisTemplate.execute(
    script3,
    Collections.singletonList(followingRedisKey),  // KEYS
    luaArgs                                        // ARGV (Object[] 可变参数)
);
```

注意 `redisTemplate.execute()` 的 `args` 参数支持两种传法：
- **逐个传入**：`execute(script, keys, arg1, arg2, arg3)` → Lua 中对应 `ARGV[1]`、`ARGV[2]`、`ARGV[3]`
- **数组传入**：`execute(script, keys, new Object[]{...})` → Lua 中 `#ARGV` 获取长度，`ARGV[1]` 逐个访问

### 5.4 返回值处理：枚举映射模式

Lua 返回的 `Long` 型状态码，通过枚举转换为 Java 业务语义：

```java
LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

switch (luaResultEnum) {
    case ZSET_NOT_EXIST -> {
        // 缓存未命中 → 从 MySQL 回源
    }
    case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
    case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
}
```

这种模式可以总结为：**Lua 返回状态码 → 枚举映射 → switch 分流**。用简单的数字代替复杂的 JSON 返回值，Lua 和 Java 之间的"通信协议"清晰、高效。

---

## 六、三份脚本的协作：一次关注操作的完整旅程

现在把三份脚本和 Java 代码串起来，看一次完整的"用户关注"流程。

```text
用户 A (id=1001) 点击关注用户 B (id=2002)

═══════════════════════════════════════════════════════════════
[Java] RelationServiceImpl.follow(followUserReqVO)
    │
    ├── [1] 前置校验（纯 Java）
    │   ├── 不能关注自己: userId == followUserId? → 否
    │   └── 关注对象存在: userRpcService.findById(2002) → 存在
    │
    ├── [2] 执行 Lua: follow_check_and_add.lua
    │   │  key = "following:1001"
    │   │  args = [2002, timestamp]
    │   │
    │   ├── EXISTS following:1001 → ?
    │   │
    │   ├── 情况 A: ZSet 存在（缓存命中）
    │   │   ├── ZCARD → 500 → 检查通过
    │   │   ├── ZSCORE 2002 → nil（未关注），通过
    │   │   ├── ZADD → return 0 → FOLLOW_SUCCESS ✓
    │   │
    │   └── 情况 B: ZSet 不存在 → return -1 → ZSET_NOT_EXIST
    │       │
    │       ├── [3] 从 MySQL 回源: followingDOMapper.selectByUserId(1001)
    │       │
    │       ├── 情况 B1: 无历史记录
    │       │   ├── 执行 Lua: follow_add_and_expire.lua
    │       │   └── ZADD + EXPIRE(1~2天随机) → 关注成功 ✓
    │       │
    │       └── 情况 B2: 有历史记录
    │           ├── 执行 Lua: follow_batch_add_and_expire.lua
    │           │   → 批量 ZADD + EXPIRE
    │           ├── 再执行: follow_check_and_add.lua
    │           └── → 关注成功 ✓
    │
    └── [4] TODO: 发送 MQ（异步写入 MySQL）
```

这个流程展示了三条核心设计原则：

**原则一：Lua 做"判断 + 修改"，Java 做"回源 + 协调"**

Lua 脚本只负责 Redis 层面的原子操作。当发现缓存未命中，不是自己去查 MySQL，而是返回状态码（-1），让 Java 层去处理缓存回源。

**原则二：缓存回源的"全量同步"策略**

当 Redis 缓存过期，从 MySQL 加载该用户的**全部**历史关注数据，而不是只加载当前这一条。因为关注列表的读取频率远高于写入频率——全量同步确保了缓存中的 ZSet 是完整的，后续查询全部命中缓存。

**原则三：随机过期时间避免"缓存雪崩"**

```java
long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
//                   保底 1 天    +    随机 0~1 天
```

如果所有 Key 的 TTL 都一样，它们会在同一时刻集中过期，Redis 瞬间承受大量回源压力——这就是**缓存雪崩**。随机 TTL 把过期时间打散，避免了流量尖刺。

---

## 七、设计思想总结：Redis + Lua 在业务中的"正确打开方式"

### 7.1 什么场景适合用 Lua 脚本？

1. **需要原子性地执行多个 Redis 命令**——检查 + 修改之间有逻辑依赖
2. **命令之间有判断逻辑**——"如果 ZCARD < 1000，才执行 ZADD"
3. **操作不涉及外部系统**——只操作 Redis 内部数据
4. **脚本是轻量的**——执行时间在毫秒级，不会长时间阻塞 Redis

### 7.2 什么时候不应该用 Lua 脚本？

- **单条命令能搞定的**：别为了炫技上 Lua
- **需要访问外部系统的**：Lua 在 Redis 沙箱内运行，没有网络 I/O 能力
- **逻辑极其复杂的**：Lua 的可读性和可维护性不如 Java

### 7.3 你学到的不仅仅是怎么写 Lua

回看你的 `bluenote-user-relation` 模块，它体现的设计思想值得反复品味：

1. **Redis 不只是"缓存"**：关注列表存在 Redis 中不是因为"MySQL 慢所以加个缓存"，而是因为 ZSet 的数据结构天然适合这个场景。这是**以数据结构驱动设计**，而不是简单地"加一层缓存"。

2. **原子性不能靠"希望"来保证**：多步操作必须要么全做要么全不做。`synchronized` 不能跨 JVM，分布式锁有代价，Lua 脚本用 Redis 的单线程特性天然解决——**用正确的工具解决正确的问题**。

3. **缓存策略不是"过期了就重查"那么简单**：你展示了全量回源 + 随机 TTL + 分级缓存预留——这些细节决定了方案在生产环境中的表现。

4. **"轻"和"重"的分离**：Lua 做轻量级的原子检查，Java 做重量级的数据回源。混淆这两层，维护会变成噩梦。

你从一个"点关注按钮"的简单需求，一路走到 Redis Lua 脚本的原子操作——这条路体现的是**从"能跑就行"到"正确且高性能"的工程化思维转变**。这就是微服务开发的真正魅力所在。

---

> **延伸思考**：你的关注系统目前用的是 Redis ZSet + MySQL 的双写模型。Redis 是"热缓存"，MySQL 是"冷持久"。目前的实现中关注操作先写缓存，通过 MQ 异步落库（`// TODO: 发送 MQ`）。这是**"先写缓存，异步落库"**的模式——用最终一致性换低延迟。但这会带来新问题：MQ 消息丢失则关注记录永久丢失。如何平衡？这又是另一个值得深入的话题了。
