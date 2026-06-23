# ThreadLocal 与 TransmittableThreadLocal 深度解析

> 本文以"小蓝书"（BlueNoteBook）项目中 `LoginUserContextHolder` 从 `ThreadLocal` 升级为阿里的 `TransmittableThreadLocal` 的真实演进为线索，深入讲解 ThreadLocal 的设计原理、三大痛点、`InheritableThreadLocal` 的局限性，以及 `TransmittableThreadLocal` 如何从根本上解决了线程池场景下的上下文传递问题。

---

## 一、ThreadLocal 是什么 —— 线程级别的"私有储物柜"

### 1.1 一个场景开篇

在讲 ThreadLocal 之前，先看一个真实的业务场景。

小蓝书的网关把用户请求转发给 `bluenote-auth` 服务时，会在请求头里塞一个 `userId`，表示"这个请求来自哪个用户"。auth 服务的 `HeaderUserId2ContextFilter` 把这个 `userId` 提取出来，放在一个地方存着，方便后续的业务代码（Controller、Service、Mapper）随时获取。

问题来了：**这个"存着"的地方应该是什么？**

你可能会想：存到方法参数里，一层一层往下传。这确实能工作——但意味着你需要在每个 Service 方法签名里加一个 `Long userId` 参数，哪怕这个 Service 根本不直接用 userId，只是因为它调用的另一个方法需要。这叫做"参数穿透"，是典型的代码坏味道。

你可能会想：存到全局静态变量里。这更糟——所有的请求共用一个变量，A 用户的请求刚把 userId 设成 10001，B 用户的请求进来设成 10002，A 的线程再去读，读到的就是 B 的 ID。数据串了。

**ThreadLocal 就是为这个场景设计的。** 它的语义是：

> 每个线程都有自己独享的一份变量副本。线程 A 往里放的东西，线程 B 看不见。

### 1.2 用生活类比理解 ThreadLocal

想象公司有一个食堂，员工刷卡打饭：

- **全局静态变量** = 只用一张公共饭卡，所有人共用。张三刷了卡，李四再拿这张卡，里面就是张三的信息。
- **方法参数传递** = 每次打饭前，主管把饭卡塞到你手里说"这是你的卡"。你打完饭再还给主管。每个环节的人都要经手这张卡。
- **ThreadLocal** = 食堂门口有一排储物柜，每个储物格上贴着员工的名字。张三进食堂时把自己的饭卡放进写着"张三"的格子里，然后去窗台打饭。打饭阿姨问"你的卡呢？"，张三说"在我储物柜里"，阿姨去张三的格子里拿。李四同时也在打饭，但他去的是"李四"那个格子——互不干扰。

在 Java 里，**线程**就是那个"员工"，**ThreadLocal** 就是那排储物柜，**线程 ID** 就是格子上的名字。

### 1.3 ThreadLocal 的核心原理

ThreadLocal 的实现只有三个核心要素：**Thread 对象**、**ThreadLocalMap**、**ThreadLocal 实例本身**。

```java
// Thread 类中有一个成员变量（简化展示）：
public class Thread {
    ThreadLocal.ThreadLocalMap threadLocals;  // ← 这就是那排"储物柜"
}

// ThreadLocalMap 是一个自定义的哈希表，它的 Entry 继承了 WeakReference：
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;  // ← 你存进去的数据（强引用）
}
```

当你调用 `threadLocal.set(value)` 时，发生了什么：

```
1. 获取当前线程的 ThreadLocalMap
   Thread t = Thread.currentThread();
   ThreadLocalMap map = t.threadLocals;
   if (map == null) {
       map = new ThreadLocalMap();
       t.threadLocals = map;
   }

2. 以 ThreadLocal 实例自身为 key，value 为值，存入 map
   map.put(this, value);  // this 就是当前 ThreadLocal 实例
```

当你调用 `threadLocal.get()` 时：

```
1. 获取当前线程的 ThreadLocalMap
   Thread t = Thread.currentThread();
   ThreadLocalMap map = t.threadLocals;

2. 以 ThreadLocal 实例自身为 key，从 map 中取值
   return map.get(this);
```

**关键洞察**：ThreadLocal 自己不存数据，它只是一个"key"，真正的数据存在 **Thread 对象的 ThreadLocalMap** 里。这就是为什么每个线程能独享一份——因为每个 Thread 对象有自己的 ThreadLocalMap。

### 1.4 小蓝书项目中的实际使用

[LoginUserContextHolder.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/filter/LoginUserContextHolder.java) 是一个典型的 ThreadLocal 工具类：

```java
public class LoginUserContextHolder {

    private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
        = TransmittableThreadLocal.withInitial(HashMap::new);

    public static void setUserId(Object value) {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.get().put(GlobalConstants.USER_ID, value);
    }

    public static Long getUserId() {
        Object value = LOGIN_USER_CONTEXT_THREAD_LOCAL.get().get(GlobalConstants.USER_ID);
        if (Objects.isNull(value)) return null;
        return Long.valueOf(value.toString());
    }

    public static void remove() {
        LOGIN_USER_CONTEXT_THREAD_LOCAL.remove();
    }
}
```

在 [HeaderUserId2ContextFilter.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/filter/HeaderUserId2ContextFilter.java) 中被使用：

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

    String userId = request.getHeader(GlobalConstants.USER_ID);

    if (StringUtils.isBlank(userId)) {
        filterChain.doFilter(request, response);
        return;
    }

    LoginUserContextHolder.setUserId(userId);   // ← 设置到当前请求线程
    try {
        filterChain.doFilter(request, response); // ← 业务逻辑可以随时 getUserId()
    } finally {
        LoginUserContextHolder.remove();          // ← 必须清理
    }
}
```

整个流程：

```
请求到达 → Filter 从 Header 取 userId → 设入 ThreadLocal → Controller/Service 任意位置获取
                                                                        ↓
                                   响应返回 ← Filter finally 移除 ThreadLocal ← 业务执行完毕
```

到这里，ThreadLocal 看起来是一个完美的方案——优雅、简洁、无侵入。但它的"完美"有一个隐含前提：**一个请求从头到尾由同一个线程处理。**

当这个前提被打破时，问题就来了。

---

## 二、ThreadLocal 的三个痛点

### 2.1 痛点一：内存泄漏 —— "储物柜里的东西没人收"

ThreadLocal 的内存泄漏问题出在它的内部数据结构上。回顾 `ThreadLocalMap.Entry`：

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;
}
```

注意这里的设计：
- **key（ThreadLocal 实例）** 是**弱引用**（WeakReference）
- **value（你存的数据）** 是**强引用**

所谓"弱引用"，指的是：当 GC 发生时，如果一个对象只有弱引用指向它，那它就会被回收。但 value 是强引用，只要线程还活着 → ThreadLocalMap 还活着 → value 就不会被 GC。

**泄漏路径**：

```
Thread（强引用）
  └→ ThreadLocalMap（强引用）
       └→ Entry（key 是弱引用 → ThreadLocal 实例，value 是强引用 → 你的数据）
                ↑
                key 被 GC 回收了 → Entry 变成了 (null → value)
                                     但这个 value 永远不会被访问到，也永远不会被回收！
```

用储物柜的类比来解释：

> ThreadLocal 实例 = 储物柜的钥匙（弱引用，容易丢）
> value = 储物柜里的物品（强引用，锁在柜子里）
> 
> 你把钥匙弄丢了，柜子还在、里面的东西还在，但你永远打不开这个柜子了。更糟的是，储物室管理员（JVM GC）来清理时，他看到柜子上还贴着"有人在用"的标签（Thread 还活着），所以不敢清走里面的东西。

**在 Web 应用中，这个问题有多严重？**

Tomcat 用线程池处理请求——线程是复用的，永远不会"死"。如果每次请求在 ThreadLocal 里存了数据但忘了调 `remove()`，数据就会在线程的 ThreadLocalMap 里越积越多。

`HeaderUserId2ContextFilter` 中的 `finally { remove(); }` 正是为了解决这个问题。**但如果你调 `remove()` 之前抛了异常，或者异步任务还没执行完就 `remove()` 了，泄漏的风险依然存在。**

### 2.2 痛点二：线程池数据污染 —— "上一个客人留下的东西"

线程池的核心特征是**线程复用**。线程 A 处理完请求 1，回到池子里；被取出来处理请求 2。

如果请求 1 的 ThreadLocal 没有清理干净：

```
线程 A 处理请求 1：
  setUserId(10001)    → Thread A 的 ThreadLocalMap: {"userId" → 10001}
  业务处理完成...
  remove() 被遗漏！   → Thread A 的 ThreadLocalMap: {"userId" → 10001}  ← 脏数据！

线程 A 回到池子，被取出处理请求 2：
  getUserId()         → 返回 10001  ← 这不是请求 2 的用户！
```

这是一个**数据安全**问题。请求 2 的用户可能因为这个漏洞看到请求 1 的用户的数据。

**`HeaderUserId2ContextFilter` 的防护措施**：每个请求进来时会**覆盖**写入 `setUserId(newValue)`，所以读到的不会是旧数据。但如果某个接口路径没有经过 `HeaderUserId2ContextFilter`（比如白名单路径），而业务代码里又调了 `getUserId()`，就可能读到上一个请求残留的数据。

这就是为什么 `remove()` 一定要在 `finally` 块里做——它是防御性编程的最后一道防线。

### 2.3 痛点三：跨线程上下文丢失 —— "你去仓库查库存，你的饭卡还在办公室"

这是三个痛点中**最致命、也最容易被忽视**的一个。

在业务方法中，使用线程池做异步操作是非常常见的需求：

```java
@Service
public class VerificationCodeServiceImpl {

    @Override
    public void send(SendVerificationCodeReqVO reqVO) {
        // 当前在 Tomcat 请求线程中
        Long currentUserId = LoginUserContextHolder.getUserId(); // ✅ 能拿到

        // 异步发送短信（使用了线程池）
        threadPoolExecutor.execute(() -> {
            // 现在在池里的另一个线程中
            Long userId = LoginUserContextHolder.getUserId(); // ❌ null！
            log.info("为用户 {} 发送短信", userId); // userId 是 null
        });

        // 主线程继续...
    }
}
```

为什么异步线程拿不到？回顾 ThreadLocal 的原理：

```
ThreadLocal.set(userId)
  → 把 {ThreadLocal实例 → userId} 存入【当前线程】的 ThreadLocalMap

线程池的任务在另一个线程执行
  → ThreadLocal.get()
    → 去【另一个线程】的 ThreadLocalMap 里找 {ThreadLocal实例 → ???}
    → 另一个线程的 ThreadLocalMap 里根本没有这个 key！
    → 返回 null
```

**结合小蓝书项目的具体场景**：

`HeaderUserId2ContextFilter` 在 Tomcat 线程（假设叫 `http-nio-8080-exec-1`）中设置了 `userId`。在 `filterChain.doFilter()` 执行链路中，如果某个 Service 用 `@Async` 或线程池执行了异步逻辑，异步线程（假设叫 `pool-1-thread-3`）中调用 `LoginUserContextHolder.getUserId()` 就是 `null`。

更糟的是 **"提前清理"问题**：

```java
LoginUserContextHolder.setUserId(userId);  // Tomcat 线程
try {
    filterChain.doFilter(request, response);
    // 假设这里提交了一个异步任务，但还没执行完
} finally {
    LoginUserContextHolder.remove();  // ← Tomcat 线程先执行到了这里！
}
```

即使异步线程通过某种方式"继承"了上下文，等它执行的时候，Filter 的 finally 块可能已经（在主线程上）把 ThreadLocal 清掉了。不过这个场景在纯 ThreadLocal 下不会发生——因为异步线程根本继承不到上下文，所以"提前清理"的问题在引入了上下文传递方案（如 InheritableThreadLocal 或 TTL）后才真正凸显。

---

## 三、InheritableThreadLocal —— "遗产继承"与它的天花板

### 3.1 Java 的初步尝试：让子线程"继承"父线程的数据

Java 的开发者很早就意识到了"子线程需要父线程的上下文"这个需求。于是从 JDK 1.2 开始，就有了 `InheritableThreadLocal`。

它的原理很简单：**在子线程创建时，把父线程的 InheritableThreadLocal 数据拷贝一份给子线程。**

```java
// java.lang.Thread 的 init() 方法（简化）：
private void init(ThreadGroup g, Runnable target, ...) {
    Thread parent = currentThread();

    // 如果父线程有 inheritableThreadLocals，拷贝给子线程
    if (parent.inheritableThreadLocals != null) {
        this.inheritableThreadLocals =
            ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
    }
}
```

`InheritableThreadLocal` 和 `ThreadLocal` 的区别只在于：
- `ThreadLocal` 的数据存在 Thread 的 `threadLocals` 字段里
- `InheritableThreadLocal` 的数据存在 Thread 的 `inheritableThreadLocals` 字段里
- 子线程创建时，`inheritableThreadLocals` 会被拷贝，`threadLocals` 不会被拷贝

### 3.2 使用 InheritableThreadLocal 的效果

```java
public class InheritableThreadLocalDemo {
    static InheritableThreadLocal<String> context = new InheritableThreadLocal<>();

    public static void main(String[] args) {
        context.set("父亲的数据");  // 在主线程设置

        new Thread(() -> {
            System.out.println(context.get());  // ✅ 输出: "父亲的数据"
        }).start();
    }
}
```

**它能解决 `new Thread()` 创建子线程的场景。** 这在小蓝书项目中也是一个进步——如果某个 Service 里直接 `new Thread(() -> {...}).start()`，子线程能拿到 `userId`。

### 3.3 为什么 InheritableThreadLocal 搞不定线程池？

InheritableThreadLocal 的致命缺陷藏在这一行代码里：

```java
if (parent.inheritableThreadLocals != null) {
    this.inheritableThreadLocals = ThreadLocal.createInheritedMap(...);
}
```

**拷贝发生在 `Thread.init()` 时——即 `new Thread()` 的那一刻。只发生一次。**

线程池的工作模式是：

```
1. 池启动时，创建 10 个核心线程（new Thread() × 10）
   → 此时父线程是 main 线程
   → InheritableThreadLocal 从 main 线程拷贝 ← 拷贝了 10 次，都来自 main

2. 请求 1 到达，从池中取出一个空闲线程
   → 这个线程在池子里泡了很久了，不会再经历 init()
   → InheritableThreadLocal 里还是启动时从 main 拷贝的那份数据

3. 在请求 1 的处理中，主线程设置了新的 InheritableThreadLocal 值
   → 池子里的线程不知道，它的 inheritableThreadLocals 没有更新！

4. 在请求 1 的处理中，又提交了一个异步任务到同一个线程池
   → 子线程拿到的还是旧数据（来自 main 的拷贝）
```

**具体到小蓝书项目**：

```
Tomcat 启动 → 创建工作线程池 (http-nio-8080-exec-1, exec-2, ...)
            → InheritableThreadLocal 从哪个线程拷贝？Tomcat 内部线程
            → 拷贝的是"空"值

请求 1 到达 → exec-1 处理
            → Filter 设置 userId = 10001 到 InheritableThreadLocal
            → 业务代码提交异步任务到自定义线程池
            → 异步线程从池子取出 → inheritableThreadLocals 还是启动时的"空"值
            → getUserId() = null ❌
```

**总结 InheritableThreadLocal 的局限**：它只能把数据从"创建子线程的那个父线程"拷贝给子线程，**拷贝时机只有 `new Thread()` 这一次**。线程池的线程是预先创建、反复使用的，不会再触发 `init()`，因此 InheritableThreadLocal 对线程池完全无效。

---

## 四、TransmittableThreadLocal (TTL) —— 阿里开源的"上下文快递员"

### 4.1 重新思考问题：InheritableThreadLocal 错在哪？

InheritableThreadLocal 的思路是"让子线程在**出生时**继承父线程的数据"。这在 `new Thread()` 的场景下是对的——子线程确实只出生一次。

但在线程池场景下，问题的本质变了：**线程池中的"工作线程"和"任务提交线程"不是父子关系。** 工作线程早就"出生"了，它一直在池子里等着接任务。真正需要传递上下文的时刻不是"线程创建时"，而是**"任务提交时"**和**"任务执行时"**。

阿里开源的 **TransmittableThreadLocal（TTL）** 正是基于这个洞察设计的。

### 4.2 TTL 的核心思想：在"任务提交"这个环节做钩子

TTL 不改变 ThreadLocal 的存储机制（数据还是存在线程的 ThreadLocalMap 里）。它做的是**在任务提交和执行这两个时间点插入自己的逻辑**：

```
任务提交时（在主线程）：
  1. 捕获当前线程中所有 TTL 变量的值 → 拍一个"快照"（snapshot）

任务执行时（在工作线程）：
  2. 先把快照中的值恢复到工作线程的 TTL 中
  3. 执行真正的业务逻辑（业务代码可以正常 get 到值）
  4. 清理工作线程的 TTL（恢复原状，防止污染）
```

**用储物柜的类比**：

- ThreadLocal = 每个人的储物柜是固定的，你不能带着它去别的楼层
- InheritableThreadLocal = 你的孩子出生时，你把自己的储物柜钥匙复制了一把给他，但仅此一次
- **TransmittableThreadLocal** = 你每次派人去别的楼层办事时，办事员出发前**复印一份你储物柜里东西的清单**，到了目的地按清单把东西摆好，办完事再清理干净

TTL 通过**装饰器模式**实现了这个机制。核心类是 `TtlRunnable`：

```java
// TtlRunnable 的核心逻辑（简化）：
public class TtlRunnable implements Runnable {
    private final Runnable runnable;
    private final Object captured;  // ← 快照：任务提交时捕获的上下文

    private TtlRunnable(Runnable runnable) {
        this.runnable = runnable;
        this.captured = TransmittableThreadLocal.Transmitter.capture(); // ← 拍快照
    }

    @Override
    public void run() {
        Object backup = TransmittableThreadLocal.Transmitter.replay(captured); // ← 恢复快照
        try {
            runnable.run();  // ← 执行真正的业务逻辑
        } finally {
            TransmittableThreadLocal.Transmitter.restore(backup); // ← 清理，恢复原状
        }
    }

    public static TtlRunnable get(Runnable runnable) {
        return new TtlRunnable(runnable);
    }
}
```

三步走：

1. **`capture()`** —— 在任务提交线程（主线程）中，把所有 TTL 变量的当前值拍一个快照
2. **`replay(captured)`** —— 在任务执行线程（工作线程）中，把快照中的值恢复到当前线程的 TTL 中。同时把工作线程原本的 TTL 值备份下来（backup）
3. **`restore(backup)`** —— 任务执行完毕后，把工作线程的 TTL 恢复原状。**这是防止线程池数据污染的关键！**

### 4.3 TTL 的三种使用方式

**方式一：修饰 Runnable / Callable（最常用）**

```java
// 普通 Runnable —— 异步线程中 TTL 变量不可见
threadPool.execute(() -> {
    Long userId = LoginUserContextHolder.getUserId(); // null
});

// TtlRunnable 包装 —— 异步线程中 TTL 变量可见
threadPool.execute(TtlRunnable.get(() -> {
    Long userId = LoginUserContextHolder.getUserId(); // ✅ 正确值！
}));
```

Callable 同理，使用 `TtlCallable.get(callable)`。

**方式二：修饰线程池（一劳永逸）**

如果你不想每次 `execute()` 时都手动包一层 `TtlRunnable.get()`，可以直接用 TTL 提供的工具方法包装整个线程池：

```java
Executor originalPool = Executors.newFixedThreadPool(10);
Executor ttlPool = TtlExecutors.getTtlExecutor(originalPool);

// 之后通过 ttlPool 提交的所有任务都会自动传递 TTL 上下文
ttlPool.execute(() -> {
    Long userId = LoginUserContextHolder.getUserId(); // ✅ 自动可用
});
```

`TtlExecutors` 内部做的事情就是把线程池的 `execute()` 方法包装了一下，每个传入的 Runnable 自动被 `TtlRunnable.get()` 包裹。

**方式三：Java Agent（完全无侵入）**

如果项目非常庞大，到处都用了线程池，一个个改 `TtlRunnable.get()` 不现实。TTL 提供了 Java Agent 方案——启动时加一个 JVM 参数：

```bash
java -javaagent:transmittable-thread-local.jar -jar your-app.jar
```

Agent 会在字节码层面拦截所有线程池的 `execute()` 方法，自动给每个 Runnable 包上 TTL 装饰器。业务代码不需要改动任何一行。

### 4.4 小蓝书项目中的实际使用

回头再看 [LoginUserContextHolder.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/filter/LoginUserContextHolder.java) 中那一行关键代码：

```java
import com.alibaba.ttl.TransmittableThreadLocal;

public class LoginUserContextHolder {

    private static final ThreadLocal<Map<String, Object>> LOGIN_USER_CONTEXT_THREAD_LOCAL
        = TransmittableThreadLocal.withInitial(HashMap::new);
    //  ↑ 这里！从 ThreadLocal.withInitial 换成了 TransmittableThreadLocal.withInitial
```

`TransmittableThreadLocal` 继承了 `InheritableThreadLocal`，继承了 `ThreadLocal`，所以 API 完全兼容——`set()`、`get()`、`remove()`、`withInitial()` 的调用方式一模一样。**业务代码不需要任何改动。**

但光把 ThreadLocal 的类型换掉是不够的——你还需要确保线程池也经过了 TTL 的包装。否则 TTL 的 `capture()` → `replay()` → `restore()` 流程不会被触发。

在小蓝书的场景下，如果 `VerificationCodeServiceImpl` 里的线程池是用 `TtlExecutors` 包装过的：

```java
// 配置类中
@Bean("smsThreadPool")
public Executor smsThreadPool() {
    Executor pool = new ThreadPoolExecutor(...);
    return TtlExecutors.getTtlExecutor(pool);  // ← TTL 包装
}

// 业务代码中
@Async("smsThreadPool")
public void sendSms(String phone, String code) {
    Long userId = LoginUserContextHolder.getUserId(); // ✅ 有值！
}
```

Filter 设置了 TTL 变量 → 业务代码提交异步任务 → `TtlExecutors` 在提交时自动 `capture()` 快照 → 工作线程执行前自动 `replay()` 恢复 → 业务代码能拿到 userId → 执行完毕自动 `restore()` 清理。

### 4.5 深入：replay 和 restore 为什么要分开？

你可能注意到 TTL 的流程是三步骤（capture → replay → restore），而不是两步骤（capture → restore）。这是有意设计的。

`replay(captured)` 做了两件事：
1. 用快照覆盖工作线程当前的 TTL 值
2. **返回工作线程原本的 TTL 值作为 backup**

`restore(backup)` 把 backup 写回去。

为什么要备份？因为**工作线程本身可能有自己的 TTL 值**。假设：

```
工作线程 pool-1-thread-3 正在执行任务 A：
  TTL 中 userId = 10001（任务 A 的用户）

任务 B 提交到同一个线程（复用）：
  replay 把 userId 覆盖为 10002
  执行任务 B 的业务逻辑...
  restore 把 userId 恢复为 10001  ← 如果不恢复，任务 A 的数据就丢了
```

在这个视角下，`restore` 并不仅仅是为了"清理"，而是为了**恢复工作线程原有的上下文链**。这是 TTL 在线程池嵌套场景下（任务 A 在执行中又提交了任务 B 到同一个线程池）依然正确工作的关键。

---

## 五、总结与对比

### 5.1 三者能力矩阵

| 维度 | ThreadLocal | InheritableThreadLocal | TransmittableThreadLocal |
|------|-------------|------------------------|--------------------------|
| **单线程内共享** | ✅ | ✅ | ✅ |
| **`new Thread()` 子线程传递** | ❌ | ✅（拷贝一次） | ✅（每次提交都捕获恢复） |
| **线程池传递** | ❌ | ❌（拷贝只在 `init()` 时） | ✅（装饰器 + 快照机制） |
| **API 兼容性** | 原始 | 继承 ThreadLocal，API 相同 | 继承 InheritableThreadLocal，API 相同 |
| **性能开销** | 无额外开销 | 子线程创建时有一次拷贝 | 每次任务提交和执行各有一次快照捕获和恢复 |
| **使用条件** | 无 | 无 | 需要配合 `TtlRunnable` 或 `TtlExecutors` 包装 |

### 5.2 使用建议

**用 ThreadLocal 就够了**：你的代码确定在单线程内执行，不涉及线程池、不涉及 `@Async`，ThreadLocal 就是最轻量的选择。

**考虑升级到 TTL**：出现以下任一信号时：
- 业务代码中有 `@Async` 注解的方法需要读取用户上下文
- 使用了自定义线程池处理异步任务
- 使用了响应式编程（WebFlux），请求可能在不同线程间跳转

**不要用 InheritableThreadLocal**：它的存在更多是历史原因。它只解决 `new Thread()` 场景，而现代 Java 应用中线程池才是主流。TTL 是它的完全上位替代。

### 5.3 小蓝书中的设计启示

回到 `HeaderUserId2ContextFilter` 中的 `try-finally`：

```java
try {
    filterChain.doFilter(request, response);
} finally {
    LoginUserContextHolder.remove();
}
```

即使升级到了 TTL，这个 `finally { remove() }` 仍然必要。原因有三：

1. **Tomcat 线程池复用**：请求线程也会被复用。如果不清理，下一个请求在同一条 Tomcat 线程上执行时，如果 `HeaderUserId2ContextFilter` 判断请求头中没有 userId 直接放行，业务代码就能读到上一个请求的脏数据
2. **内存泄漏**：虽然 TTL 的 `TtlRunnable` 在任务执行后会 `restore(backup)`，但主线程的 TTL 值并不会被清理。主线程回到 Tomcat 池子前，必须手动 `remove()`
3. **防御性编程**：你不能保证所有使用 `LoginUserContextHolder` 的代码都正确配置了 TTL 线程池。主线程的 `remove()` 是最后的兜底

**一句话总结**：TTL 解决了"跨线程传递"的问题，但"用完清理"的纪律仍然需要 `remove()` 来保障。

---

### 5.4 关键文件索引

| 文件 | 作用 |
|------|------|
| [LoginUserContextHolder.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/filter/LoginUserContextHolder.java) | TTL 实际使用，`TransmittableThreadLocal.withInitial(HashMap::new)` |
| [HeaderUserId2ContextFilter.java](bluenote/bluenote-auth/src/main/java/com/tefire/auth/filter/HeaderUserId2ContextFilter.java) | Filter 中设置和 finally 清理 TTL 上下文 |
| [GlobalConstants.java](bluenote/bluenote-framework/bluenote-common/src/main/java/com/tefire/framework/common/constant/GlobalConstants.java) | 定义了 `USER_ID = "userId"` 常量 |
| [记忆：ThreadLocal 线程池局限性](threadlocal-threadpool-limitation.md) | 记录了项目中 ThreadLocal 升级为 TTL 的上下文 |
