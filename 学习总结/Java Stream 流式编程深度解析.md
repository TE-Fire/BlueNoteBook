# Java Stream 流式编程深度解析

## 一、基本思想：从"怎么做"到"要什么"

### 1.1 两种思维方式的对立

Stream API 的背后是一种**编程范式的切换**——从命令式编程到声明式编程。它不是 Java 的语法糖，而是一套全新的数据操作模型。

拿项目中最简单的一个需求来说明。需求："从角色列表中提取所有角色的 ID"。

先用命令式写法：

```java
// 命令式：你告诉 JVM 每一步怎么做
List<Long> roleIds = new ArrayList<>();        // 1. 先准备一个空筐
for (RoleDO roleDO : roleDOS) {                // 2. 对原集合中的每个元素
    roleIds.add(roleDO.getId());               // 3. 取出它的ID，扔进筐里
}
```

再用 Stream 写法：

```java
// 声明式：你只说你想要什么
List<Long> roleIds = roleDOS.stream()
        .map(RoleDO::getId)    // "把每个RoleDO映射为它的ID"
        .toList();             // "收集成列表"
```

两段代码做的是同一件事，但思考过程完全不同。

命令式写法中，你站在**执行者**角度。脑子里的流程是："我得先建个空 List → 然后写一个 for 循环 → 每次循环调用 getId() → 把返回值 add 进去"。你把"怎么迭代"、"怎么收集"这些机械细节跟"提取角色 ID"这个业务意图写在同一段代码里，两者谁也离不开谁。读代码的人也要从这些机械细节中反向推导出你的意图——"哦，你在提取 ID"。

Stream 写法中，你站在**设计者**角度。你只声明数据应该经过什么变换：`map(RoleDO::getId)` 声明了"要做类型映射"，`toList()` 声明了"要收集成 List"。至于怎么迭代（用 Iterator 还是 Spliterator）、怎么收集（用 ArrayList 还是 LinkedList）、中间要不要优化（短路、并行）——这些你全部交给 JDK。**业务意图和机械细节彻底分离了。**

这不是多写几行少写几行的问题。随着操作变复杂，命令式的意图淹没得越来越快。后面你会看到第 72-75 行的 groupingBy 操作——3 行 Stream 代码替代了 6-8 行带嵌套逻辑的命令式循环，而且 Stream 版本的意图更加清晰。

### 1.2 类比 SQL —— 最经典的声明式数据操作

如果你用过 SQL，你其实早就习惯了声明式思维：

```sql
SELECT id FROM roles WHERE status = 1 ORDER BY create_time DESC LIMIT 10;
```

你写这条 SQL 的时候，脑子里想的是"我要 status=1 的角色的 id，按创建时间倒序，只要前 10 条"。你不会告诉数据库"先扫描索引 idx_status → 回表读 create_time → 用堆排序 → 逐条输出"。你把这些执行细节全部交给数据库优化器。

Stream 完全同理。你写 `roleDOS.stream().filter(r -> r.getStatus() == 1).map(RoleDO::getId).limit(10).toList()`，本质上就是在用 Java 写一条 SQL。你声明意图，JDK 决定怎么执行。这是声明式编程的统一哲学：**描述 what，不规定 how。**

### 1.3 流式编程的三个核心原则

这三个原则是理解 Stream 一切行为的基础，后面所有内容都会回到这三点上：

**原则一：不存储数据。** Stream 不是容器，不分配额外内存来存放数据。它像一个传送带——货物（数据元素）从源头（List/Set/数组）被放上传送带，经过各道工序（map/filter），最终落入成品箱（collect 的结果）。传送带本身不存货物，用过就报废了。

这意味着：如果你不调用 collect 或其他终端操作，数据不会从源头被取出哪怕一条。Stream 只是"准备好了操作流水线"，数据还在源头原封未动。

**原则二：不修改数据源。** map 生成新对象，filter 跳过不满足条件的元素，但它们都不会修改原始集合。Stream 把它处理的数据源视为只读。这不是语法限制（你可以在 map 的 lambda 里写副作用代码），而是契约——遵守这个契约，你的流操作才能安全组合、安全并行。

**原则三：惰性求值。** 中间操作（map/filter/sorted 等）被调用时不会立即执行，它们只是在"注册"一道工序到流水线上。只有终端操作（collect/toList/forEach 等）被调用时，整条流水线才真正启动，数据才开始从源头被逐个拉取、逐站传递。

这个设计不是可有可无的优化，它是 Stream 能高效运转的核心原因：惰性求值让 JDK 有机会在启动前审视整条流水线，做执行优化（合并操作、短路求值、并行化），而不是傻傻地每次 map 完都创建一个中间集合。

---

## 二、数据以何种形式存在

这个问题是关键——很多人用了很久 Stream 也不知道 Stream 里的数据到底是什么形态。搞清楚这一点，惰性求值、一次消费、管道模型这些东西就全通了。

### 2.1 物理层面：Stream 不是容器，是"迭代器 + 操作链"

从源码层面看，`Stream<T>` 是一个接口，它的核心组件就两个：

- **Spliterator\<T\>**（可分割迭代器）：负责从数据源逐个拉取元素。它有一个 `tryAdvance(Consumer)` 方法——每次调用，如果还有下一个元素，就把它传给 Consumer 并返回 true；没有下一个了就返回 false。
- **操作链**：一个由中间操作串起来的单向链表。每个中间操作（map/filter 等）是一个节点，持有指向上一节点（或数据源）的引用。

当你调用 `.stream()` 时，JDK 做的事很简单：创建一个 `ReferencePipeline.Head` 对象，把数据源的 Spliterator 挂上去。此时没有任何元素被读取。

当你调用 `.map(RoleDO::getId)` 时，JDK 做的事也很简单：创建一个 `ReferencePipeline` 的新节点，把 map 的 lambda 存进去，把上一个节点设为它的上游。此时仍然没有任何元素被读取。

当你调用 `.toList()` 时，事情终于发生了：toList 说"我要数据了"，然后往上推——它向上游说"给我一个元素"，上游再往上推，一直推到 Head。Head 用 Spliterator.tryAdvance() 从数据源拉取一个元素，交给下一站 map，map 调用 lambda 变换后交给下一站 toList，toList 把它放进内部的 ArrayList。然后重复这个过程，直到 Spliterator 返回 false（没元素了）。

**关键结论：在任何时刻，整条流水线中只有一个元素在流动。** 不存在"Stream 里有 100 个元素在同时被处理"的情况（串行流下）。元素一个接一个地从源头被拉出来、走完全程、进入结果容器，然后下一个元素才开始。

### 2.2 逻辑层面：数据以"流"的形式存在——流动中被消费

如果你把 `List<RoleDO>` 想象成一个仓库，那么：

- **仓库形态（List）**：数据是静止的、完整的、可反复访问的。你可以 `list.get(3)` 任意跳转，可以 `list.size()` 知道总数，可以遍历多遍。
- **流动形态（Stream）**：数据是运动的、逐个出现的、只能前进不能后退的。你不知道还有多少元素在后面，你不能中途返回去看上一个元素，你只能处理当前这个，然后它就过去了。

从 List 变成 Stream，本质上是把数据从"空间中的存在"变成了"时间中的存在"。List 中的数据在空间中平铺（占据一堆连续或不连续的内存地址），Stream 中的数据在时间中展开（一个接一个地被生产-消费）。

这种形态切换是 Stream 最核心的设计：因为数据是流动的，所以中间操作可以惰性（还没到就不处理），终端操作必须消费（到了就要决定放哪），流不能复用（过去了就过去了）。

### 2.3 逐行追踪：项目代码中数据形态的完整变化

以 PushRolePermissions2RedisRunner.java 第 67 行为例，把数据在每一步的形态完整呈现：

```
========== 数据源 ==========

roleDOS = [
    RoleDO{id=1, name="管理员", status=1, ...},
    RoleDO{id=2, name="普通用户", status=1, ...},
    RoleDO{id=3, name="编辑", status=1, ...}
]

数据形态：List<RoleDO>
存在方式：3个 RoleDO 对象在堆内存中，roleDOS 变量持有数组引用
可做的事：随机访问(roleDOS.get(2))、获取大小(roleDOS.size())、反复遍历

========== .stream() ==========

Stream<RoleDO>

数据形态改变了：
  - roleDOS 中的3个对象还在原地，一个也没有被拷贝
  - Stream 对象被创建，内部持有一个 Spliterator，指向 roleDOS 的内部数组
  - 此时没有任何元素被"取出"。Stream 只是一个"准备好了，但还没开始"的状态
  - 类比：传送带已经就位，电源打开了，但货物还在仓库里放着

========== .map(RoleDO::getId) ==========

Stream<Long>

数据形态再次改变：
  - 仍然没有任何元素被处理！map 只是在操作链上多挂了一个节点
  - 这个节点记录着："当有元素经过时，调用它的 getId() 方法，把返回值传给下一站"
  - 原来的 Stream<RoleDO> 变成了 Stream<Long>——类型变了，但数据还没动
  - 类比：在传送带上安装了一个"拆卸工位"，但传送带还没开始转

========== .toList() ==========

List<Long> = [1, 2, 3]

终端操作触发，真正的执行开始：
  1. toList 内部创建一个空的 ArrayList
  2. toList 向上游(map)说："给我一个元素"
  3. map 向上游(数据源)说："给我一个元素"
  4. 数据源的 Spliterator 从 roleDOS 内部数组中取出第0个元素：RoleDO{id=1, name="管理员"}
  5. 这个 RoleDO 经过 map 的 lambda（getId()）→ 变成 Long 值 1
  6. 1 被 toList 放入 ArrayList → ArrayList 现在是 [1]
  7. 重复步骤2-6，取出 RoleDO{id=2} → 2 → [1, 2]
  8. 重复步骤2-6，取出 RoleDO{id=3} → 3 → [1, 2, 3]
  9. Spliterator.tryAdvance() 返回 false（没元素了）
  10. Stream 关闭，ArrayList 作为结果返回

最终结果：
  数据重新以"集合形态"存在——List<Long> = [1, 2, 3]
  这是一个全新的 ArrayList，与原始 roleDOS 在内存中完全独立
  Stream 本身被标记为"已消费"，不可再使用
```

整个过程，数据形态经历了三次切换：

```
List<RoleDO>   →   Stream<RoleDO>   →   Stream<Long>   →   List<Long>
(仓库/静止)        (管道/流动)          (管道/类型已变)     (新仓库/静止)
```

只有两端是"静止的集合"，中间全部是"流动的管道"。管道不存数据，只传递数据。

### 2.4 为什么流不能复用？

Stream 被消费（执行了终端操作）后就无法再用了。现在你应该能理解原因了：

流不是容器，它本质上是一个"单向移动的指针"。Spliterator 内部有一个游标，每次 tryAdvance() 游标后移一位。流消费完，游标指向末尾，没有"重置游标"的操作。这是有意设计——如果 Stream 可以重复消费，它就必须在内存中缓存所有数据，这就违背了"不存储数据"的原则。

---

## 三、数据对象在传输中发生的转换

这一部分以项目中最复杂的 Stream 操作——PushRolePermissions2RedisRunner.java 第 72-75 行的 groupingBy——为例，逐元素、逐步骤追踪数据在传输中的每一次变化。

### 3.1 业务场景

数据库有三张表：角色表（role）、权限表（permission）、角色-权限关联表（role_permission）。第 70 行执行了 `rolePermissionDOMapper.selectByRoleIds(roleIds)`，查出了指定角色的所有关联记录。

拿到手的是一组扁平的关联行，但业务需要的是**按角色 ID 分组的权限 ID 列表**——即 `Map<Long, List<Long>>`（角色ID → 该角色的权限ID列表）。

### 3.2 初始数据形态

```java
// rolePermissionDOS: List<RolePermissionDO>
// 假设数据库返回了 5 条关联记录：
[
  第0条 → RolePermissionDO{roleId: 1, permissionId: 10},
  第1条 → RolePermissionDO{roleId: 1, permissionId: 20},
  第2条 → RolePermissionDO{roleId: 2, permissionId: 30},
  第3条 → RolePermissionDO{roleId: 2, permissionId: 40},
  第4条 → RolePermissionDO{roleId: 3, permissionId: 50},
]
```

一个典型的"多对多关联表查询结果"——同一个 roleId 出现在多行，每行带一个不同的 permissionId。

### 3.3 转换代码

```java
// 来源：bluenote-auth/.../runner/PushRolePermissions2RedisRunner.java 第 72-75 行
Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
        Collectors.groupingBy(RolePermissionDO::getRoleId,
                Collectors.mapping(RolePermissionDO::getPermissionId, Collectors.toList()))
);
```

### 3.4 逐元素执行追踪

`.stream()` 创建了 `Stream<RolePermissionDO>`，数据以流动形态就位。`.collect(...)` 触发了终端操作。collect 内部做了什么？它创建了一个空的 `HashMap<Long, List<Long>>` 作为累加器，然后从数据源逐个拉取元素：

**处理第 0 条：`{roleId: 1, permissionId: 10}`**

```
groupingBy 做的事：
  1. 调用分类器 RolePermissionDO::getRoleId → 得到 key = 1L
  2. 在累加器 Map 中查找 key=1 → 不存在
  3. 调用下游收集器(toList)的 supplier → 创建一个新的 ArrayList
  4. 将 1 → [] 放入累加器 Map

mapping 做的事：
  5. 对该元素调用映射器 RolePermissionDO::getPermissionId → 得到 value = 10L
  6. 将 10L 交给下游收集器(toList)的 accumulator
  7. toList 将 10L 放入步骤3创建的 ArrayList

累加器当前状态：{ 1L: [10] }
                    ↑       ↑
                  桶的key  桶内的List（toList管理的ArrayList）
```

**处理第 1 条：`{roleId: 1, permissionId: 20}`**

```
groupingBy 做的事：
  1. 调用分类器 → key = 1L
  2. 在累加器 Map 中查找 key=1 → 已存在！不需要创建新桶
  3. 直接拿到已有的 ArrayList: [10]

mapping 做的事：
  4. 调用映射器 → value = 20L
  5. 将 20L 放入已有的 ArrayList

累加器当前状态：{ 1L: [10, 20] }
```

注意这里的关键：因为 key=1 的桶已经存在，所以 groupingBy 跳过了"创建新桶"步骤，直接把元素交给 mapping，mapping 提到 permissionId 后追加到已有 List 中。**同样是 key=1，第 0 条触发"建桶"，第 1 条只触发"追加"——分组逻辑是动态的，桶在需要时才创建。**

**处理第 2 条：`{roleId: 2, permissionId: 30}`**

```
groupingBy:
  key = 2L → Map中没有 → 创建新ArrayList → 放入Map
mapping:
  value = 30L → 放入新ArrayList

累加器当前状态：{ 1L: [10, 20], 2L: [30] }
```

**处理第 3 条：`{roleId: 2, permissionId: 40}`**

```
groupingBy:
  key = 2L → 已存在 → 直接拿桶
mapping:
  value = 40L → 追加

累加器当前状态：{ 1L: [10, 20], 2L: [30, 40] }
```

**处理第 4 条：`{roleId: 3, permissionId: 50}`**

```
groupingBy:
  key = 3L → 新桶
mapping:
  value = 50L → 放入新桶

最终状态：{ 1L: [10, 20], 2L: [30, 40], 3L: [50] }
```

### 3.5 关键洞察

**第一，数据不是先全部分组、再统一投影。** 一个常见的误解是认为 groupingBy 先把所有元素按 key 分组（产生 `Map<Long, List<RolePermissionDO>>`），然后 mapping 再把这个中间产物转成 `Map<Long, List<Long>>`。不是这样的。

实际执行是**逐元素、一次性**完成的。每个元素进入 collect 后，groupingBy 确定它属于哪个桶，mapping 立刻把它投影为 permissionId，放入那个桶。然后这个元素就被丢弃了，下一个元素开始。整个过程中间没有任何"临时分组结果"的集合产生。元素从进入 collect 到落入桶中，一次完成。

**第二，mapping 不是独立的步骤，是 groupingBy 的下游装饰器。** 你可以这样理解代码的嵌套结构：

```
Collectors.groupingBy(
    按什么分桶,    ← 第一参数：分类器
    桶里怎么处理   ← 第二参数：下游收集器（可选，默认是toList）
)
```

当下游收集器是 `mapping(getPermissionId, toList())` 时，它做了什么？它说："在元素落入桶之前，先把它映射为 permissionId，然后交给 toList 收集"。mapping"装饰"了 toList——它不改变 toList 的收集行为（依然是放进 List），只是改变了**放进 List 之前元素要经过的变换**。

这就是 Collectors 的**可组合设计**。groupingBy 定义了"分桶"框架，下游收集器定义了"桶内行为"。你可以把 mapping 换成 counting()，变成"每个角色有多少权限"；换成 summingInt()，变成"每个角色的权限分数总和"。groupingBy 的框架不变，下游换了，整套逻辑就变了。

**第三，和命令式写法对比，差异一目了然。**

把同样的逻辑用命令式写一遍：

```java
Map<Long, List<Long>> result = new HashMap<>();
for (RolePermissionDO rp : rolePermissionDOS) {
    Long key = rp.getRoleId();              // ← 对应 groupingBy 的 classifier
    Long value = rp.getPermissionId();      // ← 对应 mapping 的 mapper
    if (!result.containsKey(key)) {          // ← 对应 groupingBy 的"桶不存在则创建"
        result.put(key, new ArrayList<>());  //
    }                                        //
    result.get(key).add(value);             // ← 对应 toList 的 accumulator
}
```

两段代码做的事情一模一样。但命令式的 8 行代码中，"确定 key→提 value→建桶→加桶"这四个动作是线性平铺的，你读的时候需要自己在脑子里把它们归纳为"哦，这是在分组"。Stream 的 `groupingBy + mapping + toList` 直接把这三个概念命名了——读者不需要推导意图，意图就写在 API 的名字里。

---

## 四、常见 API 操作——从 0 到 1 建立认知框架

学 Stream API 最大的问题不是"记不住有哪些方法"，而是**没有分类框架，看到一个陌生方法不知道它属于哪一类、起什么作用**。这一部分的目标不是把所有 API 罗列一遍，而是帮你建立一个可迁移的分类体系。以后遇到新方法，你对号入座就能理解。

### 4.1 总框架：两条轴线划分所有 Stream 操作

所有 Stream 操作可以沿两条轴线分类：

**第一条轴线：操作在流水线中的位置。**

```
数据源 ──→ 中间操作(0~n个, 惰性) ──→ 终端操作(恰好1个, 触发执行) ──→ 结果
```

这条轴线是最基本的分法。判断方法很简单：**看返回值。** 返回 `Stream` 就是中间操作；返回其他类型（`List`、`Map`、`Optional`、`boolean`、`long`...）就是终端操作。

| 维度 | 中间操作 | 终端操作 |
|------|---------|---------|
| 返回值 | Stream（可继续 `.xxx()`） | 具体类型 |
| 执行时机 | 惰性，声明后不执行 | 立即触发整条流水线 |
| 可组合性 | 可以链式串多个 | 只能用 1 个，用了流就关了 |
| 类比 | 设计图纸上的工序 | 按下"开工"按钮 |

一个流写成链式调用，不管中间有多少个 `.map().filter().sorted()`，只要没写终端操作，就没有任何一行 lambda 被执行。这就是惰性求值在代码层面的直接体现。

**第二条轴线：中间操作的"无状态 vs 有状态"。**

| 类型 | 特点 | 代表方法 | 类比 |
|------|------|----------|------|
| **无状态** | 处理当前元素时不需要知道其他元素 | map, filter, flatMap, peek | 安检门：每个人独立过检 |
| **有状态** | 需要先看到其他元素（甚至全部元素）才能处理当前元素 | sorted, distinct, limit, skip | 按身高排队：必须所有人到齐 |

有状态操作比无状态操作开销大，因为它们需要在内部维护缓冲区。sorted 把整个流倒进临时数组排序完再逐个输出；distinct 用一个内部 HashSet 记录已见过的元素来判断重复。理解这个区分，你就知道为什么 `filter().map()` 可以随便串，但 `sorted().filter()` 的顺序不同会影响性能——先 filter 减少元素数量，再 sorted，比先 sorted 再 filter 更快。

### 4.2 中间操作：按"对数据做什么"分四类

#### A 类：形态变换 —— 改变元素的类型或结构

**map：一对一变换。**

每个输入元素独立地变换为一个输出元素。输入 N 个，输出 N 个。

项目案例（第 67 行）：

```java
List<Long> roleIds = roleDOS.stream()
        .map(RoleDO::getId)   // RoleDO 进去，Long 出来
        .toList();
```

元素层面的变换：

```
RoleDO{id=1, name="管理员"}  ──map(getId)──→  1L
RoleDO{id=2, name="普通用户"}  ──map(getId)──→  2L
RoleDO{id=3, name="编辑"}      ──map(getId)──→  3L

Stream<RoleDO>  ────────────────→  Stream<Long>
```

**flatMap：一对多变换 + 展平。**

每个输入元素变换为一个 **Stream**，然后所有子 Stream 被拼接成一个大 Stream。这是专门用来把**嵌套结构拍平**的操作。

项目里没有直接用到 flatMap，但有一个潜在的场景能说明它的价值。假设 `RoleDO` 里有一个方法 `getPermissionNames()` 返回该角色的所有权限名称列表：

```java
// 不存在的需求：列出所有角色拥有的所有权限名（去重）
// 如果用 map：
roleDOS.stream()
    .map(RoleDO::getPermissionNames)   // 返回 Stream<List<String>>
    // 此时流里装的是 List<String>，不是 String！
    // 想对每个权限名做操作？你还得再套一层循环
    .collect(...);  // 不知道怎么直接收集成 Set<String>

// 用 flatMap：
roleDOS.stream()
    .flatMap(r -> r.getPermissionNames().stream())  // 每个角色的权限List被"展平"
    // 此时流里装的是 String，每个权限名作为一个独立元素
    .distinct()
    .toList();
// 结果：["查看", "编辑", "删除", ...]——所有权限名平铺在一个List中
```

flatMap 的关键理解：

```
角色1 ──→ ["查看", "编辑"]  ─┐
                             ├──展平──→  "查看", "编辑", "查看", "编辑", "删除"
角色2 ──→ ["查看", "编辑", "删除"] ─┘

如果不展平：List<List<String>> = [["查看","编辑"], ["查看","编辑","删除"]]
展平后：    List<String>       = ["查看","编辑","查看","编辑","删除"]
```

map 和 flatMap 的区别一句话：**map 是 T→R，flatMap 是 T→Stream\<R\>→展平为 R。** 如果你用 map 返回了一个 List，流里的元素还是 List（嵌套未解）；用 flatMap 才能把嵌套解开。

#### B 类：过滤筛选 —— 减少元素数量

**filter：按条件保留。**

最直观的中间操作。对每个元素问一句"你满足条件吗？"，满足的放行，不满足的丢弃。

```java
// 假设只要状态为启用的角色
roleDOS.stream()
    .filter(roleDO -> roleDO.getStatus() == StatusEnum.ENABLE.getValue())
    .toList();
```

逻辑等价于：

```java
if (条件满足) {
    继续传递给下一站;
} else {
    跳过，直接从数据源拉下一个;
}
```

**distinct：去重。**

依赖元素的 `equals()` 方法判断是否重复。内部维护一个 HashSet，每个元素先查 HashSet 中有没有，没有就放行并记录，有就跳过。这是有状态操作——Set 会随元素增多而变大。

**limit(n)：截断前 n 个。**

只保留流的前 n 个元素。内部维护一个计数器，输出一个元素计数器 +1，计数器达到 n 后直接关闭流（后面的元素不再从数据源拉取）。这是**短路操作**——数据源可能有 10000 个元素，但 limit(5) 只用处理 5 个。

**skip(n)：跳过前 n 个。**

跳过前 n 个元素，从第 n+1 个开始保留。limit 和 skip 常配合做分页：

```java
// 第3页，每页10条：跳过前20条，取10条
list.stream().skip(20).limit(10).toList();
```

这 4 个操作的关系：filter 按条件删，distinct 按相等性删，limit 按数量截，skip 按数量跳。

#### C 类：排序 —— 改变顺序

`sorted()` 和 `sorted(Comparator)` 都是**有状态**操作——必须等所有元素到达，倒进临时数组，排序，然后按排序后的顺序逐个输出。这意味着 sorted 是**无法短路**的：即使你写 `.sorted().limit(3)`，sorted 还是要先把全部元素排序完，然后 limit 才从排好的结果中取前 3 个。

#### D 类：窥视 —— 只看不改

`peek(Consumer)` 让每个元素经过时执行一个副作用（如打日志），然后原样传递给下一站：

```java
roleDOS.stream()
    .peek(r -> log.info("处理角色: {}", r.getId()))  // 看一眼，不改
    .map(RoleDO::getId)
    .toList();
```

peek 主要用于调试——在流水线中插入一个"观察点"，看看经过它的元素长什么样。它不改变流的内容。

### 4.3 Collectors 体系 —— 终端操作的核心

Collectors 是所有终端操作中最丰富的一类。它用一个统一的模板——`Collector<T, A, R>`——覆盖了所有"把流收集成某种结果"的需求。

**理解 Collector 的三个泛型参数，就理解了所有 Collectors：**

| 泛型 | 含义 | 在 groupingBy 中的体现 |
|------|------|------------------------|
| `T` | 输入元素类型——流里装的什么 | RolePermissionDO |
| `A` | 累加器类型——收集过程中用的临时容器 | `Map<Long, List<Long>>` |
| `R` | 最终结果类型 | `Map<Long, List<Long>>` |

任何 Collector 都是：逐个接收 T 类型的元素 → 放入 A 类型的内部累加器 → 最后把累加器"A 整理"（或直接作为）R 类型的结果。

按 R（结果类型）的不同，可以把 Collectors 分为四类。

#### 第一类：收集成集合 —— R = 集合容器

**toList()：**

```java
T = RoleDO, A = ArrayList, R = ArrayList

roleDOS.stream()
    .filter(r -> r.getStatus() == 1)
    .collect(Collectors.toList());  // → List<RoleDO>
```

**toSet()：**

```java
T = RoleDO, A = HashSet, R = HashSet

// 去重收集——按 equals 判断重复
collect(Collectors.toSet())  // → Set<RoleDO>
```

**toMap(keyMapper, valueMapper)：**

这是 Stream 中最实用的收集器之一，直接把 List 转成 Map，建立 O(1) 查找。

项目案例（第 80-82 行）：

```java
// 来源：bluenote-auth/.../runner/PushRolePermissions2RedisRunner.java 第 80-82 行
Map<Long, PermissionDO> permissionIdDOMap = permissionDOS.stream().collect(
        Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO)
);
// T = PermissionDO, A = HashMap<Long, PermissionDO>, R = HashMap<Long, PermissionDO>
//
// 进去：[PermissionDO{id=10, name="查看"}, PermissionDO{id=20, name="编辑"}]
// 出来：{10: PermissionDO{id=10, name="查看"}, 20: PermissionDO{id=20, name="编辑"}}
```

这个 Map 在后续代码中（第 96 行）被用来做 O(1) 查找：拿到一个 permissionId，直接从 Map 中取出对应的 PermissionDO 对象，避免了双重 for 循环的 O(n²) 复杂度。这是 Stream 实践中一种常见模式——**先用 toMap 建索引，再用索引做高效查找。**

注意：toMap 默认不允许 key 重复。如果两个元素有相同的 key，会抛 `IllegalStateException`。如果可能重复，需要用三参数版本指定合并策略：

```java
Collectors.toMap(
    UserDO::getPhone,          // keyMapper
    user -> user,              // valueMapper
    (existing, replacement) -> existing  // 合并策略：冲突时保留已有的
)
```

#### 第二类：分组 —— R = Map，按维度分桶

`groupingBy` 是 Collectors 中最强大也最需要深度理解的。

**单参数版：** `groupingBy(分类器)`

```java
// 按角色ID分组，保留完整对象
Map<Long, List<RolePermissionDO>> map = rolePermissionDOS.stream()
    .collect(Collectors.groupingBy(RolePermissionDO::getRoleId));
// 结果：{1: [RolePermissionDO{roleId:1, permId:10}, RolePermissionDO{roleId:1, permId:20}],
//        2: [RolePermissionDO{roleId:2, permId:30}]}
```

**双参数版：** `groupingBy(分类器, 下游收集器)`

这才是 groupingBy 真正的威力所在。下游收集器定义了**每个桶内的元素怎么处理**。

项目案例（第 72-75 行）：

```java
Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
        Collectors.groupingBy(
                RolePermissionDO::getRoleId,                    // 分类器
                Collectors.mapping(                              // 下游：桶内先映射
                        RolePermissionDO::getPermissionId,       //        提取permissionId
                        Collectors.toList()                      //        再收集成List
                )
        )
);
```

把 groupingBy 的结构展开来看：

```
groupingBy(
    分类器,           ← "按什么分组"——这个决定每个元素进哪个桶
    下游收集器         ← "每组里面怎么处理"——这个决定桶里元素怎么收集
)

在这段代码中：
  分类器 = RolePermissionDO::getRoleId  → 按roleId分桶
  下游收集器 = mapping(getPermissionId, toList())
             = "把每个 RolePermissionDO 映射为 permissionId，然后收集成 List"
```

groupingBy 的下游可以是任何 Collector。更换下游，同一组数据能回答完全不同的问题：

```java
// 每个角色有多少权限？
groupingBy(RolePermissionDO::getRoleId, counting())
// → {1: 2, 2: 1, 3: 1}

// 每个角色最大的权限ID？
groupingBy(RolePermissionDO::getRoleId,
    mapping(RolePermissionDO::getPermissionId, maxBy(Long::compareTo)))
// → {1: Optional[20], 2: Optional[30], 3: Optional[50]}

// 按角色是否管理员分组
partitioningBy(RoleDO::getIsAdmin)
// → {true: [角色列表], false: [角色列表]}
```

#### 第三类：聚合/拼接 —— R = 单个值或字符串

这些 Collectors 把流"压缩"为一个结果值：

| Collector | 结果 | 用途 |
|-----------|------|------|
| `counting()` | Long | 计数 |
| `summingInt/Long/Double()` | 数值 | 对某个字段求和 |
| `averagingInt/Long/Double()` | double | 平均值 |
| `maxBy(Comparator)` | Optional\<T\> | 找最大 |
| `minBy(Comparator)` | Optional\<T\> | 找最小 |
| `joining(delimiter)` | String | 字符串拼接 |

项目案例——ApiOperationLogAspect.java 第 42 行：

```java
// AOP 切面中，把方法参数序列化为日志字符串
String argsJsonStr = Arrays.stream(args)
        .map(toJsonStr())                           // 每个参数 → JSON字符串
        .collect(Collectors.joining(", "));         // 用 ", " 拼接
// 结果："{\"phone\":\"138...\"}, {\"code\":\"1234\"}"
```

joining 的三个重载版本：

```java
joining()              // "abc"
joining(", ")          // "a, b, c"
joining(", ", "[", "]") // "[a, b, c]"
```

#### 第四类：装饰 Collectors —— 组合与嵌套

mapping、filtering、collectingAndThen、flatMapping 这四个 Collector 不独立使用，它们**装饰**另一个 Collector，在元素进入被装饰的 Collector 之前做一道加工。

```java
// mapping:  在收集前先做 map 变换
mapping(RolePermissionDO::getPermissionId, toList())
// 等价于：.map(getPermissionId).collect(toList())
// 但 mapping 可以作为 groupingBy 的下游使用（map 不能直接写在下游位置）

// filtering: 在收集前先做 filter 过滤
filtering(r -> r.getStatus() == 1, toList())
// 等价于：.filter(r -> r.getStatus() == 1).collect(toList())

// collectingAndThen: 收集完再对结果做一次转换
collectingAndThen(toList(), Collections::unmodifiableList)
// 先收集成List，然后包装为不可修改List

// flatMapping: 在收集前先做 flatMap 展平
flatMapping(r -> r.getPermissions().stream(), toList())
```

这些"装饰型 Collector"的存在理由是**组合性**。在 groupingBy 的第二参数位置，你不能写 `.map().collect()`（因为那是对分组后的子流操作，语法上不支持），但你**可以**写 `mapping(xxx, toList())`。装饰 Collectors 就是把通常写在 `.xxx()` 中间操作位置的东西"下放"到了 Collector 参数位置，保持了 groupingBy 框架的可组合性。

### 4.4 短路终端操作

一些终端操作不需要遍历完所有元素就能给出结论，这在处理大量数据时非常重要。

**判定类：**

```java
// 是否存在满足条件的元素？（找到第一个就返回true，后面的不再处理）
boolean hasAdmin = roles.stream().anyMatch(r -> r.getType() == 1);

// 是否所有元素都满足条件？（找到第一个不满足的就返回false）
boolean allEnabled = roles.stream().allMatch(r -> r.getStatus() == 1);

// 是否没有元素满足条件？
boolean noDeleted = roles.stream().noneMatch(r -> r.getIsDeleted() == 1);
```

三种方法的短路行为：

```
anyMatch:  第一个true  → 返回true（短路，后面的不看了）
allMatch:  第一个false → 返回false（短路，后面的不看了）
noneMatch: 第一个true  → 返回false（短路，后面的不看了）
```

**查找类：**

```java
// 找到第一个元素
Optional<RoleDO> first = roles.stream().findFirst();

// 找到任意一个元素（并行流下性能更好，串行流下等价于findFirst）
Optional<RoleDO> any = roles.stream().findAny();
```

短路的能力来源于 Stream 的惰性求值 + 逐元素处理模型。因为元素是一个一个被拉取的，判定/查找类操作可以随时说"够了，不用再拉了"。这和传统 for 循环中的 `break` 是对应的。

### 4.5 认知框架全景图

```
Stream API 全景

中间操作（返回Stream，惰性）
│
├── 无状态（当前元素独立处理）
│   ├── 形态变换：map(T→R), flatMap(T→Stream<R>)
│   ├── 条件过滤：filter(条件)
│   └── 窥视：    peek(副作用)
│
└── 有状态（需要知道其他元素）
    ├── 排序：sorted(), sorted(Comparator)
    ├── 去重：distinct()
    └── 截断：limit(n), skip(n)

终端操作（返回具体类型，触发执行）
│
├── 收集为集合  ── toList(), toSet(), toMap(), toCollection()
├── 分组/分区  ── groupingBy(分类器, 下游?), partitioningBy(条件)
├── 聚合计算   ── counting(), summingX(), averagingX(), maxBy(), minBy()
├── 字符串拼接 ── joining(), joining(分隔符)
├── 装饰Collector ─ mapping(), filtering(), collectingAndThen(), flatMapping()
│
├── 短路判定   ── anyMatch(), allMatch(), noneMatch()
├── 短路查找   ── findFirst(), findAny()
├── 归约       ── reduce(初始值, 累加器)
├── 遍历       ── forEach(), forEachOrdered()
└── 统计       ── count(), min(), max()
```

拿到这个框架后，看到一个陌生的 Stream 方法时，你可以用三个问题定位它：

1. **它返回 Stream 还是别的类型？** → 确定它是中间操作还是终端操作。
2. **如果是中间操作：它处理当前元素时需要知道其他元素吗？** → 确定它是无状态还是有状态。
3. **如果是终端操作：它是用 Collector（收集/分组/聚合），还是自己做短路判定/归约/遍历？** → 确定它的执行模式。

分类对了，方法的作用就基本清楚了——同类操作共享相同的执行逻辑和性能特征。

---

## 总结

Stream 流式编程的核心，不是记住几个 API 方法名，而是理解三个根本问题：

**思想层面：** Stream 是一种声明式编程范式。你描述"数据应该怎样变换"，JDK 决定"怎样迭代和收集"。这和 SQL 是同一哲学——描述 what，不规定 how。业务意图从机械细节中分离，代码更短，意图更清。

**数据层面：** 数据在 Stream 中以"流动形态"存在。它从源头（List/Set/数组）被 Spliterator 逐个拉取，在操作链中逐站传递，最后被终端操作固化到新的容器中。在整个过程中，Stream 本身不存储数据，数据只是一个接一个地从入口流向出口。理解了这一点，惰性求值、一次消费、不可重用这些行为就不再是"规则"，而是"理所当然"。

**API 层面：** 所有 Stream 操作可以沿两条轴线分类——中间 vs 终端（看返回值），无状态 vs 有状态（看是否需要缓冲区）。Collectors 的 `T→A→R` 模板是理解一切收集操作的统一框架。建立这个分类体系后，你不需要记住每个方法的作用，只需要看到新方法时对号入座。
