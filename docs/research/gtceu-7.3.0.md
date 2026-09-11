# GregTech CEu Modern 7.3.0 (GTM, Minecraft 1.20.1 / Forge 47.4.10) API 调研报告

> 调研目标：为编写 Forge 附属模组（addon）提供**精确**的 API 事实，用于实现：
> (a) 让 AE2 样板供应器把物品推入 GT 单方块机器并**按样板设置编程电路**；
> (b) 合成结束后把未消耗的剩余物**回收进 ME 网络**；
> (c) 为 GT ME 样板总成（多方块）的**每个样板槽提供独立电路**；
> (d) 概率产出未掉落时**重推样板**；
> (e) 依据 AE 合成任务的**需求量**处理概率产出。

---

## 0. 版本、来源与可信度声明

| 项目 | 值 | 证据 |
| --- | --- | --- |
| mod id | `gtceu` | `gradle.properties`（`mod_id = gtceu`） |
| mod 版本 | `7.3.0` | `gradle.properties`（`mod_version = 7.3.0`） |
| maven group / Java 包根 | `com.gregtechceu.gtceu` | `gradle.properties`（`maven_group`） |
| Git tag | `v7.3.0-1.20.1` | 全部源码取自 `https://raw.githubusercontent.com/GregTechCEu/GregTech-Modern/v7.3.0-1.20.1/...` |
| Mixin 包 | `com.gregtechceu.gtceu.core.mixins` | `src/main/resources/gtceu.mixins.json`（`"package"`） |
| Mixin 插件 | `com.gregtechceu.gtceu.core.mixins.GTMixinPlugin` | 同上（`"plugin"`） |
| refmap | `gtceu.refmap.json` | 同上 |

**本地逐字源码副本**：全部文件保存在 `refsrc\gtceu\<包路径>\<类名>.java`，内容与 GitHub 上 `v7.3.0-1.20.1` 标签完全一致（未摘要、未重排）。文中的 `文件:行号` 均指这些本地副本的行号。

### 可信度标记约定
- **【已核实】**：直接读到 7.3.0 源码原文。
- **【部分核实】**：源码获取受工具单次抓取体积上限限制而被截断，只核实了部分内容。
- **【未核实/推测】**：无法从 7.3.0 源码确认，报告中显式标注，**请勿直接依赖**。

### 重要环境限制
`web_fetch` 的传输层对单个响应体有 **≈100 KB 的硬上限**。`src/main/java/com/gregtechceu/gtceu/common/data/GTItems.java` 全文 **162503 字节**，只能取回前 **100001 字节（61.5%，到第 1637 行，止于 `CAPACITOR = REGISTRATE.item("capaci` 一行中间）**；`PROGRAMMED_CIRCUIT` 的声明位于**不可达的尾部**（第 100002-162503 字节），因此 `GTItems.PROGRAMMED_CIRCUIT` 的**注册语句原文与行号无法核实**（本报告显式标注为【部分核实】）。

已尝试并**全部失败**的绕行路线：`raw.githubusercontent.com`、`cdn.jsdelivr.net`（两者在同**字节**处截断）、GitHub contents/blob API（base64 反而更大 4/3）、`#L1500-L1700` 片段（被忽略）、grep.app（429）、Sourcegraph（被防火墙 403）、emgithub（返回 JS）。`web_fetch` **不支持 Range 请求头**。 **替代方案（已采用）**：工作区内已存在**同版本编译产物** `libs\gtceu-1.20.1-7.3.0.jar`。本报告第 1.1 节关于 `gtceu:programmed_circuit` 的结论即从该 jar 的 `GTItems.class` 常量池中直接读出（已在本地独立复核）。若还需 `.java` 原文，只能在能自由联网的构建机上执行 `Invoke-WebRequest -Uri <raw url> -OutFile ...\GTItems.java`，验收标准为**恰好 162503 字节且以 `}` 结尾**。

其余所有被要求的文件均已**完整、逐字节**取回（并用 GitHub API 的 `size` 字段逐个比对确认未被截断或重排）。

此外，官方文档站 `https://gregtechceu.github.io/GregTech-Modern/1.20.1/` **跟踪的是 1.20.1 分支的最新状态，而不是 7.3.0 标签**。文档中的示例（如 `SimpleMachineBuilder`、`GTMachineUtils.registerSimpleSteamMachines`）在 7.3.0 中**并不存在**，本报告凡引用文档之处均已用 7.3.0 源码交叉验证，并标出不一致点。

---

## 1. 编程电路物品（Programmed Circuit）

### 1.1 注册 id 与 Java 类

- **ItemEntry 字段**：`GTItems.PROGRAMMED_CIRCUIT`，位于 `common/data/GTItems.java`。
- **注册 id**：**`gtceu:programmed_circuit`**。
  - **证据 1（本地同版本编译产物，最强）**：工作区内的 `libs\gtceu-1.20.1-7.3.0.jar` 里 `com/gregtechceu/gtceu/common/data/GTItems.class`（140489 字节）常量池中，`programmed_circuit` / `PROGRAMMED_CIRCUIT` / `Programmed Circuit` / `circuit` / `IntCircuitBehaviour` / `getCircuitConfiguration` **各出现 1 次**；常量的原始排布为
    `… uv_solar_panel, "Ultimate Voltage Solar Panel", ®, programmed_circuit, "Programmed Circuit", "circuit", … gelled_toluene, "Gelled Toluene", purple_drink, "Purple Drink" …`
    —— 完全符合 `REGISTRATE.item("programmed_circuit", …).lang("Programmed Circuit")` 的注册形态，且与 `IntCircuitBehaviour` 绑定。这是**同版本 mod 的编译产物**，可信度远高于第三方资料；唯一保留意见是它来自 `.class` 而非 `.java` 源码文本。

  - **证据 2（源码侧引用）**：`IntCircuitBehaviour` 与 `IntCircuitIngredient` 均通过 `GTItems.PROGRAMMED_CIRCUIT` 引用该物品（`common/item/IntCircuitBehaviour.java:41`、`api/recipe/ingredient/IntCircuitIngredient.java:51`）。
  - **证据 3（外部佐证）**：MC 百科 GTM 编程电路词条给出游戏内命令 `/give @p gtceu:programmed_circuit 64`（<https://www.mcmod.cn/item/772155.html>）。
  - **仍未核实的一点**：`GTItems.java` 源码中该注册语句的**行号与逐字原文**（原因见第 0 节；该文件超过 `web_fetch` 传输上限）。
  - **建议**：附属模组不要硬编码该字符串，改用 `GTItems.PROGRAMMED_CIRCUIT.get()` / `GTItems.PROGRAMMED_CIRCUIT.isIn(stack)`，或 `BuiltInRegistries.ITEM.getKey(GTItems.PROGRAMMED_CIRCUIT.get())` 反查。这样即使 id 变化也不会失效。
- **物品基类**：普通 `net.minecraft.world.item.Item`（`ItemEntry<Item>`）；其行为由挂在物品上的 `IntCircuitBehaviour` 组件提供（`IntCircuitBehaviour implements IItemUIFactory, IAddInformation`，`common/item/IntCircuitBehaviour.java:33`）。堆叠上限 64。

> 注意区分两个 id：
> - `gtceu:programmed_circuit` = **物品** registry name。
> - `gtceu:circuit` = **配方原料序列化器** id，即 `IntCircuitIngredient.TYPE = GTCEu.id("circuit")`（`api/recipe/ingredient/IntCircuitIngredient.java:22`，用于配方 JSON 的 `"type"` 字段）。二者不可混用。

### 1.2 电路号的存储 / 读取 —— NBT 键 `"Configuration"`

文件 `common/item/IntCircuitBehaviour.java`：

```java
public static final int CIRCUIT_MAX = 32;                                  // 第 38 行

public static ItemStack stack(int configuration) {                          // 第 40 行
    var stack = GTItems.PROGRAMMED_CIRCUIT.asStack();
    setCircuitConfiguration(stack, configuration);
    return stack;
}

public static void setCircuitConfiguration(ItemStack itemStack, int configuration) {  // 第 51 行
    if (configuration < 0 || configuration > CIRCUIT_MAX)
        throw new IllegalArgumentException("Given configuration number is out of range!");
    var tagCompound = itemStack.getOrCreateTag();
    tagCompound.putInt("Configuration", configuration);        // <-- NBT 键就是 "Configuration"
}

public static int getCircuitConfiguration(ItemStack itemStack) {            // 第 58 行
    if (!isIntegratedCircuit(itemStack)) return 0;
    var tagCompound = itemStack.getTag();
    if (tagCompound != null) {
        return tagCompound.getInt("Configuration");
    }
    return 0;
}

public static boolean isIntegratedCircuit(ItemStack itemStack) {            // 第 67 行
    boolean isCircuit = GTItems.PROGRAMMED_CIRCUIT.isIn(itemStack);
    if (isCircuit && !itemStack.hasTag()) {
        var compound = new CompoundTag();
        compound.putInt("Configuration", 0);
        itemStack.setTag(compound);          // <-- 副作用：会就地修改传入的 ItemStack！
    }
    return isCircuit;
}
```

要点：
- **NBT 键：`"Configuration"`（int）**，位于物品根 tag 下（非子 tag）。
- `getCircuitConfiguration` 对非电路物品返回 `0`；因此**不能**用返回值区分“配置 0”和“不是电路”——必须先 `isIntegratedCircuit`。
- `isIntegratedCircuit(ItemStack)` **有副作用**：若该物品是电路且无 tag，会写入 `Configuration=0`。传入不可变的 `ItemStack` 副本时要注意。
- 取值范围 `0..32`（`CIRCUIT_MAX = 32`）；`setCircuitConfiguration` 越界抛 `IllegalArgumentException`。
- `IntCircuitIngredient` 另有一份常量：`CIRCUIT_MIN = 0`、`CIRCUIT_MAX = 32`（`api/recipe/ingredient/IntCircuitIngredient.java:24-25`）。

### 1.3 官方“从外部给机器设置电路”的既有实现（重要参考）

`IntCircuitBehaviour.useOn(UseOnContext)`（第 140 行）就是**官方从外部写电路槽**的方式——手持电路潜行右键机器：

```java
@Override
public InteractionResult useOn(UseOnContext context) {
    var stack = context.getItemInHand();
    int circuitSetting = getCircuitConfiguration(stack);
    BlockEntity entity = context.getLevel().getBlockEntity(context.getClickedPos());
    if (entity instanceof MetaMachineBlockEntity machineEntity && context.isSecondaryUseActive()) {
        if (machineEntity.metaMachine instanceof IHasCircuitSlot circuitMachine &&
                circuitMachine.getCircuitInventory().getSlots() > 0) {
            setCircuitConfig(circuitMachine.getCircuitInventory(), circuitSetting);
        }
        if (!ConfigHolder.INSTANCE.machines.ghostCircuit)
            stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
    return IItemUIFactory.super.useOn(context);
}

void setCircuitConfig(NotifiableItemStackHandler circuit, int value) {      // 第 156 行：包级私有
    circuit.setStackInSlot(0, IntCircuitBehaviour.stack(value));
}
```

这段代码**证明了官方认可的路径**：`IHasCircuitSlot#getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(n))`。三个成员中前两个是 public 接口方法，`setStackInSlot` 是 public 的 `IItemHandlerModifiable` 方法。**这是附属模组最安全的写法**（见第 3 节）。

---

## 2. 电路在配方中如何匹配 / 是否消耗 / 电路槽

### 2.1 `IntCircuitIngredient`

文件 `api/recipe/ingredient/IntCircuitIngredient.java`（全文 100 行）：

```java
public class IntCircuitIngredient extends StrictNBTIngredient {            // 第 20 行

    public static final ResourceLocation TYPE = GTCEu.id("circuit");        // 第 22 行
    public static final int CIRCUIT_MIN = 0;                                // 第 24 行
    public static final int CIRCUIT_MAX = 32;                               // 第 25 行

    private static final IntCircuitIngredient[] INGREDIENTS = new IntCircuitIngredient[CIRCUIT_MAX + 1];

    public static IntCircuitIngredient of(int configuration) {              // 第 29 行：带缓存
        ...
        INGREDIENTS[configuration] = ingredient = new IntCircuitIngredient(configuration);
        return ingredient;
    }

    private IntCircuitIngredient(int configuration) {                       // 私有构造
        super(IntCircuitBehaviour.stack(configuration));
        this.configuration = configuration;
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {                        // 第 49 行
        if (stack == null) return false;
        return stack.is(GTItems.PROGRAMMED_CIRCUIT.get()) &&
                IntCircuitBehaviour.getCircuitConfiguration(stack) == this.configuration;
    }
    ...
}
```

- 它是一个 **Forge `Ingredient`**（`StrictNBTIngredient` 子类），并覆写了 `test()`。
- `of(int)` 是**唯一可用的构造入口**（构造器 private），返回**全局单例**数组中的实例。因此配方匹配中的 `IntCircuitIngredient` 实例可以用 `==`/`equals` 安全比较，也可直接 `new`——不行，必须用 `of`。
- `toJson()` 产出 `{"type":"gtceu:circuit","configuration":N}`。
- **匹配语义**：`test` 只比较“是不是编程电路”+“Configuration 数值是否相等”；因为覆写了 `test`，对 NBT 键顺序不敏感（这正是它继承 `StrictNBTIngredient` 但仍能稳定工作的原因）。

### 2.2 电路会被消耗吗？—— **不会**，机制是 `Content.chance == 0`

7.3.0 中 `GTRecipeBuilder.circuitMeta(int)` 的实现（`data/recipe/builder/GTRecipeBuilder.java:718`）：

```java
public GTRecipeBuilder circuitMeta(int configuration) {
    if (configuration < 0 || configuration > IntCircuitBehaviour.CIRCUIT_MAX) {
        GTCEu.LOGGER.error("Circuit configuration must be in the bounds 0 - 32");
    }
    return notConsumable(IntCircuitIngredient.of(configuration));   // <-- 走 notConsumable
}
```

而 `notConsumable(Ingredient)`（同一文件第 665 行）只是**临时把 `chance` 置 0**：

```java
public GTRecipeBuilder notConsumable(Ingredient ingredient) {
    int lastChance = this.chance;
    this.chance = 0;
    inputItems(ingredient);
    this.chance = lastChance;
    return this;
}
```

真正保证“不消耗”的是 `RecipeRunner.fillContentMatchList`（`api/recipe/RecipeRunner.java`，第 85-96 行）：

```java
   85:                 searchContentList.add(cont.content);
   ...
   91:                 if (cont.chance >= cont.maxChance) {
   92:                     contentList.add(cont.content);
   93:                 } else if (cont.chance > 0 || cont.tierChanceBoost > 0) {
   94:                     chancedContents.add(cont);
   95:                 }
   96:                 // Do not add Non-Consumed ingredients; they'd just get dropped after the chance roll anyway
```

结论：
- **配方匹配（`simulated == true`）时，`chance == 0` 的输入仍然必须存在**（会被加入 `searchRecipeContents`），所以电路**必须真实存在于某个输入 handler 中**才能匹配到该配方。
- **实际执行（`simulated == false`）时，`chance == 0` 的输入不会进入待消耗列表**，因此永远不会被 `extractItem`。

### 2.3 电路放在普通输入槽/输入总线里能匹配吗？—— **能**

配方匹配通过 `IRecipeHandler#handleRecipe(IO, GTRecipe, List, boolean)` 在**每个** item handler 的槽位上线性搜索（`api/machine/trait/NotifiableItemStackHandler.java:160-195`，`io == IO.IN` 分支逐槽 `ingredient.test(current)`）。

- 所以电路**放在机器的 `importItems` 输入槽**里同样能满足 `IntCircuitIngredient`（这也是 MC 百科所述“可放入单方块机器输入槽或输入总线”的代码依据）。`ItemRecipeCapability.getMaxParallelByInput` 中对电路做了特判 `if (ing instanceof IntCircuitIngredient) continue;`（`api/capability/recipe/ItemRecipeCapability.java:218`），即**电路不计入并行数量限制**。
- 但放在专用电路槽更干净：`ItemRecipeCapability.applyWidgetInfo` 中，仅当 `content.chance == 0` 或原料是 `IntCircuitIngredient` 时，槽位才被标成 `IngredientIO.CATALYST`（JEI 中显示为催化剂，`ItemRecipeCapability.java:492-493`），说明 GT 语义上把电路视为“非消耗催化剂”。

### 2.4 电路槽（circuit slot）相关 API

| 成员 | 声明 | 位置 | 可见性 |
| --- | --- | --- | --- |
| `IHasCircuitSlot` | `public interface IHasCircuitSlot` | `api/machine/feature/IHasCircuitSlot.java` | public |
| `IHasCircuitSlot#getCircuitInventory()` | `NotifiableItemStackHandler getCircuitInventory();` | 同上 第 11 行 | **public（接口方法）** |
| `IHasCircuitSlot#isCircuitSlotEnabled()` | `default boolean isCircuitSlotEnabled() { return true; }` | 同上 第 7 行 | **public default** |
| `ItemBusPartMachine#circuitInventory` | `@Getter @Persisted protected final NotifiableItemStackHandler circuitInventory;` | `common/machine/multiblock/part/ItemBusPartMachine.java:76` | 字段 protected，**getter public** |
| `ItemBusPartMachine#hasCircuitSlot` | `@Getter(AccessLevel.PROTECTED) private boolean hasCircuitSlot = true;` | 同上 第 68 行 | **getter 为 protected**（即 `isHasCircuitSlot()` 包外不可见） |
| `ItemBusPartMachine#circuitSlotEnabled` | `@Getter @Setter @Persisted @DescSynced protected boolean circuitSlotEnabled;` | 同上 第 73 行 | 字段 protected，**getter/setter public** |
| `ItemBusPartMachine#getHandlerList()` | 来自 `IDistinctPart` | — | public |

`ItemBusPartMachine` 的电路槽创建（第 106 行）：

```java
protected NotifiableItemStackHandler createCircuitItemHandler(Object... args) {
    if (args.length > 0 && args[0] instanceof IO io && io == IO.IN) {
        return new NotifiableItemStackHandler(this, 1, IO.IN, IO.NONE)
                .setFilter(IntCircuitBehaviour::isIntegratedCircuit);
    } else {
        hasCircuitSlot = false;
        setCircuitSlotEnabled(false);
        return new NotifiableItemStackHandler(this, 0, IO.NONE);
    }
}
```

**关键：`new NotifiableItemStackHandler(this, 1, IO.IN, IO.NONE)` 的第四个参数是 `capabilityIO = IO.NONE`。** 这直接决定第 3 节的结论——电路槽**不通过 Forge capability 暴露**。

**单方块电动机器**（例如研磨机 `RockCrusherMachine extends SimpleTieredMachine`）继承链为： `SimpleTieredMachine extends WorkableTieredMachine implements …, IHasCircuitSlot`（`api/machine/SimpleTieredMachine.java`，类声明处），其电路槽同样由 `createCircuitItemHandler` 创建：

```java
protected NotifiableItemStackHandler createCircuitItemHandler(Object... args) {   // SimpleTieredMachine
    return new NotifiableItemStackHandler(this, 1, IO.IN, IO.NONE)
            .setFilter(IntCircuitBehaviour::isIntegratedCircuit);
}
// 字段：
@Getter @Persisted protected final NotifiableItemStackHandler circuitInventory;
```

所以 **`SimpleTieredMachine` 与 `ItemBusPartMachine` 都实现了 `IHasCircuitSlot`，且 `getCircuitInventory()` 都是 public。** 【已核实】

`MEPatternBufferPartMachine` 继承 `MEBusPartMachine extends ItemBusPartMachine`，所以它也有同一个电路槽（全多方块共享一个，见第 8 节）。

---

## 3. 【最重要】从外部给机器设置电路号 —— 全部可行路径与可见性

给定 `Level level` 与 `BlockPos pos`（单方块机器或多方块部件的坐标）：

### 3.0 统一入口：`MetaMachine.getMachine`

`api/machine/MetaMachine.java:488-494`：

```java
@Nullable
public static MetaMachine getMachine(BlockGetter level, BlockPos pos) {
    if (level.getBlockEntity(pos) instanceof IMachineBlockEntity machineBlockEntity) {
        return machineBlockEntity.getMetaMachine();
    }
    return null;
}
```

- `public static`，**返回 `@Nullable`**，参数类型是 `BlockGetter`（`Level` 可传），**公共 API，安全**。
- 这是 GT 自己的代码里到处在用的取机器方式（如 `MEPatternBufferProxyPartMachine.setBuffer` 第 78 行）。

### 3.1 ✅ 推荐路径（官方同款，纯 public API）

```java
MetaMachine machine = MetaMachine.getMachine(level, pos);
if (machine instanceof IHasCircuitSlot circuitMachine
        && circuitMachine.isCircuitSlotEnabled()
        && circuitMachine.getCircuitInventory().getSlots() > 0) {
    circuitMachine.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(circuitNumber));
}
```

| 调用 | 可见性 | 说明 |
| --- | --- | --- |
| `MetaMachine.getMachine(BlockGetter, BlockPos)` | **public static** | ✅ |
| `IHasCircuitSlot`（接口） | **public** | ✅ 用 `instanceof` 判定 |
| `IHasCircuitSlot#getCircuitInventory()` | **public**（接口方法） | ✅ |
| `IHasCircuitSlot#isCircuitSlotEnabled()` | **public default** | ✅ |
| `NotifiableItemStackHandler#getSlots()` | **public**（`NotifiableItemStackHandler.java:225`） | ✅ 单方块/总线为 1；输出总线/非 IN 总线为 0 |
| `NotifiableItemStackHandler#setStackInSlot(int, ItemStack)` | **public**（第 302 行，覆写 `IItemHandlerModifiable`） | ✅ 直接写 `storage`，**不受 `capabilityIO` 限制** |
| `IntCircuitBehaviour.stack(int)` | **public static**（第 40 行） | ✅ 生成带 `Configuration` 的电路物品 |
| `IntCircuitBehaviour.setCircuitConfiguration(ItemStack,int)` | **public static**（第 51 行） | ✅ 就地改已有电路 |

**这是唯一完全无需反射/OAT/Mixin 的路径，且与 GT 自身 `IntCircuitBehaviour.useOn` 的行为完全一致。强烈推荐。**

补充：
- 若只想改数值而不换物品实例（保留其它 NBT），可用
  `ItemStack s = inv.getStackInSlot(0); if (IntCircuitBehaviour.isIntegratedCircuit(s)) { IntCircuitBehaviour.setCircuitConfiguration(s, n); inv.setStackInSlot(0, s); }` —— 这正是 `CircuitFancyConfigurator` 的按钮逻辑（`api/machine/fancyconfigurator/CircuitFancyConfigurator.java:131-141`）。
- 清除电路：`inv.setStackInSlot(0, ItemStack.EMPTY)`（仅当 `ConfigHolder.INSTANCE.machines.ghostCircuit == true` 时 GT 自己的 GUI 才允许空电路；**但代码层面 `setStackInSlot` 不做这个检查**）。

### 3.2 ❌ 不推荐 / 需注意的路径

| 路径 | 结论 |
| --- | --- |
| `NotifiableItemStackHandler.storage`（`public final CustomItemStackHandler storage;`，`NotifiableItemStackHandler.java:47`） | **public 字段**，可以直接 `storage.setStackInSlot(0, stack)`。但绕过 `NotifiableItemStackHandler` 的变更通知封装不划算；`setStackInSlot` 已经等价。**可用，但无必要。** |
| `NotifiableItemStackHandler#insertItem(int, ItemStack, boolean)` | public，**会先检查 `canCapInput()`**（第 308-313 行）。电路槽 `capabilityIO = IO.NONE` → `canCapInput() == false` → **直接返回原 stack，写入失败**。❌ |
| `NotifiableItemStackHandler#extractItem(int, int, boolean)` | 同理受 `canCapOutput()` 限制；电路槽**取不出来**。❌ |
| `NotifiableItemStackHandler#insertItemInternal(int, ItemStack, boolean)` / `extractItemInternal(...)` | **public**（第 315 / 328 行），绕过 capability 检查。✅ 可用作替代，但 `setStackInSlot` 更直接。 |
| Forge `CapabilityItemHandler.ITEM_HANDLER` 能力 | **无法写电路槽。** `MetaMachine#getItemHandlerCap(Direction, boolean)`（`MetaMachine.java:765-787`）只聚合“实现了 `IItemHandlerModifiable` 且 `hasCapability(side)` 的 trait”，并包成 `IOFilteredInvWrapper`。电路槽是 `NotifiableItemStackHandler`（确实是 `IItemHandlerModifiable`），但它的 `capabilityIO = IO.NONE`，`getItemHandlerCap` 拿到的包装器只会转发 `importItems`/`exportItems`/`shareInventory` 等。**结论：AE2/漏斗/管道无法插入电路槽，必须用代码直写。** |
| `MetaMachineBlockEntity.metaMachine` 字段 | public 字段（`IntCircuitBehaviour.useOn` 第 143 行直接访问），但**需要先拿到 BE**；用 `MetaMachine.getMachine` 更简洁。 |

### 3.3 关于“哪一个槽位是电路槽”

**不存在一个稳定的“NBT 槽位索引”可以从外部推导**：电路槽是**独立的 `NotifiableItemStackHandler` 实例**（1 格），通过 `IHasCircuitSlot#getCircuitInventory()` 暴露，在 Forge capability 聚合视图中**根本不可见**（见上表）。因此：
- 通过 capability 访问时，**没有“电路槽索引”这个概念**；
- 通过代码访问时，索引恒为 `0`（`circuitInventory.setStackInSlot(0, …)`）。

### 3.4 关于 `isHasCircuitSlot()`

- 在 `ItemBusPartMachine` 中声明为 `@Getter(AccessLevel.PROTECTED) private boolean hasCircuitSlot`（第 68 行）→ 生成的 `isHasCircuitSlot()` 是 **protected**，**包外不可见**，且对单方块机器（`SimpleTieredMachine`）根本没有此字段。
- 因此外部判定应使用 **`machine instanceof IHasCircuitSlot`** + **`getCircuitInventory().getSlots() > 0`**，而不是 `isHasCircuitSlot()`。
- `MEPatternBufferPartMachine` 内部自己在用 `isHasCircuitSlot()`（第 293、380 行），因为它在同一继承体系内（`protected` 可见）。

### 3.5 时序/线程注意

- 必须在**服务端**执行（`!level.isClientSide`）。`setStackInSlot` 会触发 `CustomItemStackHandler` 的变更回调 → `notifyListeners()` → `RecipeLogic#updateTickSubscription()`，从而使机器重新寻找配方。
- 若在推物品的同 tick 内先写电路再写物品，配方匹配发生在 `RecipeLogic.serverTick()` 的下一 tick，顺序无碍。
- 若想让机器立刻放弃“上次配方”重新匹配，可调用 **`RecipeLogic#markLastRecipeDirty()`**（`api/machine/trait/RecipeLogic.java:420`，**public**）。

---

## 4. 单方块机器的物品输入（AE2 样板供应器如何送料）

### 4.1 相关字段与创建（`api/machine/WorkableTieredMachine.java`）

```java
public abstract class WorkableTieredMachine extends TieredEnergyMachine
        implements IRecipeLogicMachine, IMachineLife, IMufflableMachine, IOverclockMachine {

    @Getter @Persisted @DescSynced public final RecipeLogic recipeLogic;
    @Getter public final GTRecipeType[] recipeTypes;
    @Getter public final Int2IntFunction tankScalingFunction;

    @Persisted public final NotifiableItemStackHandler importItems;     // <-- 输入物品
    @Persisted public final NotifiableItemStackHandler exportItems;     // <-- 输出物品
    @Persisted public final NotifiableFluidTank importFluids;
    @Persisted public final NotifiableFluidTank exportFluids;
    @Persisted public final NotifiableComputationContainer importComputation;
    @Persisted public final NotifiableComputationContainer exportComputation;
    ...
}

protected NotifiableItemStackHandler createImportItemHandler(Object... args) {
    return new NotifiableItemStackHandler(this, getRecipeType().getMaxInputs(ItemRecipeCapability.CAP), IO.IN);
}
protected NotifiableItemStackHandler createExportItemHandler(Object... args) {
    return new NotifiableItemStackHandler(this, getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP), IO.OUT);
}
```

注意：这三个字段在 `WorkableTieredMachine` 中是 **public 字段**（`public final NotifiableItemStackHandler importItems;`），因此 `machine.importItems` / `machine.exportItems` 直接可访问；`recipeLogic` 是 private+`@Getter`（→ `getRecipeLogic()`）。

### 4.2 插入走哪条能力？IO 侧与默认配置

- `NotifiableItemStackHandler(this, slots, IO.IN)` → 三参构造器把 `capabilityIO` 设为与 `handlerIO` 相同，即 **`capabilityIO = IO.IN`**（`api/machine/trait/NotifiableItemStackHandler.java:67-69`）。
- 因此 `canCapInput()` 为 `true`（`ICapabilityTrait#canCapInput()` → `getCapabilityIO().support(IO.IN)`，`api/machine/trait/ICapabilityTrait.java:9`），`insertItem(...)` 会真正写入。
- `MetaMachine#getItemHandlerCap(Direction side, boolean useCoverCapability)`（`MetaMachine.java:765`）把机器上所有满足条件的物品 handler trait 合成一个 **`IOFilteredInvWrapper`**：

```java
var list = getTraits().stream()
        .filter(IItemHandlerModifiable.class::isInstance)
        .filter(t -> t.hasCapability(side))
        .map(IItemHandlerModifiable.class::cast)
        .toList();
if (list.isEmpty()) return null;

var io = IO.BOTH;
if (side != null && this instanceof IAutoOutputItem autoOutput
        && autoOutput.getOutputFacingItems() == side
        && !autoOutput.isAllowInputFromOutputSideItems()) {
    io = IO.OUT;                       // 该面被设为“物品输出面”且未允许输入 → 只允许抽出
}

IOFilteredInvWrapper handlerList = new IOFilteredInvWrapper(list, io,
        getItemCapFilter(side, IO.IN), getItemCapFilter(side, IO.OUT));
```

结论（**对 addon 直接可用**）：
- 暴露给外部（AE2 样板供应器、漏斗、管道）的是 Forge `CapabilityItemHandler.ITEM_HANDLER`，由 `MetaMachineBlockEntity` 转发到 `getItemHandlerCap`。
- **默认状态下任意一面都可以插入**（`IO.BOTH`）。
- **例外**：`SimpleTieredMachine` 实现了 `IAutoOutputBoth`。其 `outputFacingItems` 默认是**机器正面的反面**（`SimpleTieredMachine` 构造器：`this.outputFacingItems = hasFrontFacing() ? getFrontFacing().getOpposite() : Direction.UP;`），且 `allowInputFromOutputSideItems` 默认 `false`。也就是说：**“物品输出面”那一面默认只出不进**。AE2 样板供应器应贴在**输出的反方向面**（即一般意义上的“输入侧”）上。
- 还有一个正交限制：`IOFilteredInvWrapper` 的 per-side 过滤器来自安装在该面的 `ItemFilterCover`（`MetaMachine#getItemCapFilter`）。默认无 cover → 全通过。**不构成默认阻碍**。
- 玩家可以用螺丝刀在输出面切换 `allowInputFromOutputSideItems`，addon 侧无需关心（若需兼容，可读 `m.isAllowInputFromOutputSideItems()`，**public getter，来自 `SimpleTieredMachine` 的 `@Getter`**）。

### 4.3 是否有 `importItems` 暴露为 `IItemHandler`？

- **是。** `NotifiableItemStackHandler implements ICapabilityTrait, IItemHandlerModifiable`（`NotifiableItemStackHandler.java:36-37`），并且因为 `capabilityIO = IO.IN`，它会出现在 `getItemHandlerCap` 的聚合列表里。
- 从外面看**只有一个聚合 `IItemHandler`**（import + export 合并），起止索引 = 各 trait 按 `getTraits()` 顺序排列的槽位。**槽位索引映射不是公开契约**，addon 若要精确定位“第 N 个输入槽”，正确做法是**直接用 `machine.importItems` 字段**（public）或 `((WorkableTieredMachine) machine).importItems.storage`，而**不是**猜 capability 里的索引。

### 4.4 多方块部件（总线）的自动 IO

`ItemBusPartMachine#autoIO()`（第 231 行）每 5 tick 执行：

```java
protected void autoIO() {
    if (getOffsetTimer() % 5 == 0) {
        if (isWorkingEnabled()) {
            if (io == IO.OUT) {
                getInventory().exportToNearby(getFrontFacing());
            } else if (io == IO.IN) {
                getInventory().importFromNearby(getFrontFacing());
            } else if (io == IO.BOTH) {
                getInventory().importFromNearby(getFrontFacing());
                getInventory().exportToNearby(getFrontFacing().getOpposite());
            }
        }
        updateInventorySubscription();
    }
}
```

- 输入总线**只从 `getFrontFacing()` 那一面拉取**（`importFromNearby(getFrontFacing())`）；`NotifiableItemStackHandler#importFromNearby/Direction...` 用 `GTTransferUtils.getAdjacentItemHandler` 取相邻的 Forge 物品能力再 `transferItemsFiltered`。
- 所以 AE2 样板供应器贴在输入总线的**正面**即可自动送料（每 5 tick 一次）。
- `MEBusPartMachine#shouldSubscribe()`（`integration/ae2/machine/MEBusPartMachine.java`）：`isWorkingEnabled() && isOnline()`；且 `swapIO()` 固定返回 `false`（不允许螺丝刀切换 ME 总线的输入输出方向）。

---

## 5. 从外部读取机器状态（空闲 / 运行 / 刚完成一次合成）

### 5.1 `RecipeLogic.Status` 枚举（完整、精确）

`api/machine/trait/RecipeLogic.java:51-64`：

```java
public enum Status implements StringRepresentable {
    IDLE("idle"),
    WORKING("working"),
    WAITING("waiting"),
    SUSPEND("suspend");
    ...
}
```

四个枚举常量就是全部：**`IDLE`、`WORKING`、`WAITING`、`SUSPEND`**。

### 5.2 获取 `RecipeLogic` 与状态

```java
MetaMachine machine = MetaMachine.getMachine(level, pos);
if (machine instanceof IRecipeLogicMachine rlm) {
    RecipeLogic logic = rlm.getRecipeLogic();     // @NotNull
    RecipeLogic.Status st = logic.getStatus();    // @Getter，public
    boolean working = logic.isWorking();          // == (status == WORKING)
    boolean idle    = logic.isIdle();             // == (status == IDLE)
    boolean waiting = logic.isWaiting();
    boolean suspend = logic.isSuspend();
    boolean active  = logic.isActive();           // isWorking() || isWaiting() || (isSuspend() && isActive)
    int progress    = logic.getProgress();
    int duration    = logic.getMaxProgress();
    double pct      = logic.getProgressPercent();
}
```

| 成员 | 签名 / 行号 | 可见性 |
| --- | --- | --- |
| `machine` 字段 | `public final IRecipeLogicMachine machine;`（第 69 行） | public |
| `lastFailedMatches` | `public List<GTRecipe> lastFailedMatches;`（第 70 行） | public |
| `getStatus()` | `@Getter private Status status`（第 76 行） | **public getter** |
| `isActive()` | `public boolean isActive()`（第 465 行） | public |
| `isWorking()` | `public boolean isWorking()`（第 424 行） | public |
| `isIdle()` | `public boolean isIdle()`（第 428 行） | public |
| `isWaiting()` | `public boolean isWaiting()`（第 432 行） | public |
| `isSuspend()` | `public boolean isSuspend()`（第 436 行） | public |
| `progress` | `@Getter @Setter protected int progress;`（第 112 行） | **getter/setter public** |
| `duration` | `@Getter protected int duration;`（第 116 行） | **getter public** |
| `getMaxProgress()` | `public int getMaxProgress()`（第 461 行） | public |
| `getProgressPercent()` | `public double getProgressPercent()`（第 184 行） | public |
| `getLastRecipe()` | `@Getter protected GTRecipe lastRecipe;`（第 94 行） | **getter public**，`@Nullable` |
| `getLastOriginRecipe()` | `@Getter protected GTRecipe lastOriginRecipe;`（第 107 行） | **getter public**，`@Nullable` |
| `getConsecutiveRecipes()` | `@Getter protected int consecutiveRecipes;`（第 98 行） | **getter public** |
| `getChanceCaches()` | `@Getter protected final Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches;`（第 129 行） | **getter public** |
| `markLastRecipeDirty()` | `public void markLastRecipeDirty()`（第 420 行） | public |
| `resetRecipeLogic()` | `public void resetRecipeLogic()`（第 152 行） | public |
| `getRecipeManager()` | `public RecipeManager getRecipeManager()`（第 191 行） | public（**注意：这是原版 RecipeManager，见第 6.4 节**） |

> ⚠️ **`RecipeLogic` 上没有 `getRecipeType()`**。配方类型在机器侧：`IRecipeLogicMachine#getRecipeType()`（`api/machine/feature/IRecipeLogicMachine.java:28`，`@NotNull GTRecipeType getRecipeType();`），以及 `getRecipeTypes()` / `getActiveRecipeType()` / `setActiveRecipeType(int)`。

### 5.3 “完成了一次合成”的时刻

`RecipeLogic#onRecipeFinish()`（第 484 行，**public**）是合成结算点：

```java
public void onRecipeFinish() {
    machine.afterWorking();
    if (lastRecipe != null) {
        runAttempt = 0; runDelay = 0; consecutiveRecipes++;
        handleRecipeIO(lastRecipe, IO.OUT);       // <-- 产出在此写入输出总线/槽
        ...
    }
}
```

`serverTick()` 中 `if (progress >= duration) { onRecipeFinish(); }`（第 205-207 行）。

**外部检测方案（按可靠性排序）：**

1. **最快、最可靠：Mixin `@Inject` 到 `RecipeLogic#onRecipeFinish()V` 的 TAIL**（或 `HEAD` 以取到“本次产出前的 lastRecipe”）。这是唯一的**精确边沿**。
2. **轮询方案（无 Mixin）**：每 tick / 每 N tick 观察
   `logic.getConsecutiveRecipes()` 递增，或 `status` 从 `WORKING` 变为 `IDLE`/`WORKING`（`WORKING → WORKING` 且 `progress` 回绕说明连做）。
   - 单方块机器默认 `keepSubscribing() == false`（`WorkableTieredMachine#keepSubscribing()` 返回 `false`），机器会在无配方时**退订 tick**；此时 `onRecipeFinish` 之后状态会稳定在 `IDLE`，用 `progress==0 && duration==0 && !isActive()` 判断即可。
   - 更稳的组合：记录上一 tick 的 `getConsecutiveRecipes()` + `getLastRecipe()` 引用是否改变。
3. **无需 Mixin 的“产出后”钩子**：给输出槽/输出总线注册 `addChangedListener`（`NotifiableRecipeHandlerTrait#addChangedListener(Runnable)`，public，返回 `ISubscription`），在产出写入时立即被回调。适合“产出后立刻回收”的场景。

`IRecipeLogicMachine` 还提供了一组**可被定义回调用**的钩子（都是 `default` 方法，public）： `beforeWorking(@Nullable GTRecipe)`（第 80 行）、`onWorking()`（第 87 行）、`onWaiting()`（第 94 行）、`afterWorking()`（第 101 行）、`notifyStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus)`（第 37 行）。

---

## 6. 概率产出（chanced outputs）

### 6.1 数据结构：`Content`

`api/recipe/content/Content.java:32-37`：

```java
public class Content {
    @Getter
    public final Object content;
    public final int chance;
    public final int maxChance;
    public final int tierChanceBoost;
```

- **全是 public final 字段**；只有 `content` 有 Lombok `@Getter`（→ `getContent()`）。**没有 `getChance()` / `getMaxChance()` 方法**——直接读字段。
- 唯一判定方法：`public boolean isChanced()`（第 70 行）：

```java
public boolean isChanced() {
    return chance > 0 && chance < maxChance;
}
```

- 构造器：`public Content(Object content, int chance, int maxChance, int tierChanceBoost)`（第 39 行）。注意构造器会对 `tierChanceBoost` 做归一化：`this.tierChanceBoost = fixBoost(tierChanceBoost);`（`fixBoost` 为 private，第 78 行），换算依据 `ChanceLogic.getMaxChancedValue() / maxChance`。

### 6.2 100% 是多少？

**`ChanceLogic.getMaxChancedValue() == 10_000`**（`api/recipe/chance/logic/ChanceLogic.java:293-294`）：

```java
public static int getMaxChancedValue() {
    return 10_000;
}
```

- 因此 **`chance == maxChance`（通常即 `chance == 10000` 且 `maxChance == 10000`）表示必定产出**。
- **`chance == 0` 在输入侧表示“不消耗”（notConsumable）**，不是“0% 概率”。
- 一个 content 是否“真正的概率产出”用 `content.isChanced()`（0 < chance < maxChance）。
- 配方 builder 的默认值：`@Setter public int chance = ChanceLogic.getMaxChancedValue();`、`@Setter public int maxChance = ChanceLogic.getMaxChancedValue();`、`@Setter public int tierChanceBoost = 0;`（`data/recipe/builder/GTRecipeBuilder.java`）。
- `maxChance` 允许不是 10000（例如用 `chancedOutput(ItemStack, String fraction, int tierChanceBoost)` 写分数），所以**永远用 `chance / (float) maxChance` 算百分比**，不要假设分母是 10000。GT 自己的 GUI 就是 `float chance = (float) getBoostedChance(...) / content.maxChance;`（`ItemRecipeCapability.applyWidgetInfo`）。

对应 builder 方法（`data/recipe/builder/GTRecipeBuilder.java`）：`chancedInput(ItemStack, int, int)` L725、`chancedInput(FluidStack, int, int)` L739、**`chancedOutput(ItemStack, int chance, int tierChanceBoost)` L753**、`chancedOutput(FluidStack, int, int)` L767、`chancedOutput(TagPrefix, Material, int, int)` L781、`chancedOutput(ItemStack, String fraction, int)` L789。它们一律采用“保存旧 chance → 设置 → 调用 outputXXX → 还原”的模式，并由 `protected boolean checkChanceAndPrintError(int chance)` 校验（chance 必须 `> 0` 且 `<= 10000`）。 **注意：不存在 `chancedOutputs(...)` 方法。**

### 6.3 概率逻辑与 boost

- 每种 capability 可独立指定 `ChanceLogic`：`GTRecipe#getChanceLogicForCapability(RecipeCapability<?> cap, IO io, boolean isTick)`（`api/recipe/GTRecipe.java:201`），默认 `ChanceLogic.OR`。
- 可用的 `ChanceLogic` 常量（`ChanceLogic.java`）：**`OR`、`AND`、`FIRST`（@Deprecated）、`XOR`、`NONE`**。
- boost：`ChanceBoostFunction`（`api/recipe/chance/boost/ChanceBoostFunction.java`），由配方类型持有：`GTRecipeType#getChanceFunction()`（`api/recipe/GTRecipeType.java:50-52`，`@Getter private ChanceBoostFunction chanceFunction = ChanceBoostFunction.NONE;`）。
- 实际 roll 在 `RecipeRunner.fillContentMatchList` 中：

```java
ChanceBoostFunction function = recipe.getType().getChanceFunction();
int recipeTier = RecipeHelper.getPreOCRecipeEuTier(recipe);
int chanceTier = recipeTier + recipe.ocLevel;
...
chancedContents = logic.roll(chancedContents, function, recipeTier, chanceTier, cache, recipe.getTotalRuns());
```

- roll 结果**带缓存**（`chanceCaches`，按 `RecipeCapability` → `Object2IntMap` 映射），用于跨多次合成累计概率（`ChanceLogic.OR` 中 `guaranteed = totalChance / maxChance` 的确定性部分 + 余数概率）。缓存持久化于 NBT 键 `chance_cache`（`RecipeLogic.saveCustomPersistedData`，第 617 行）。

### 6.4 枚举一个配方的物品产出及其概率

```java
// recipe 为 GTRecipe
List<Content> itemOut = recipe.getOutputContents(ItemRecipeCapability.CAP);  // 普通产出
List<Content> tickOut = recipe.getTickOutputContents(ItemRecipeCapability.CAP); // per-tick 产出

for (Content c : itemOut) {
    Ingredient ing = ItemRecipeCapability.CAP.of(c.getContent());   // 还原为 Ingredient
    int amount;
    if (ing instanceof SizedIngredient si)          amount = si.getAmount();       // @Getter，public
    else if (ing instanceof IntProviderIngredient ip) amount = ip.getCountProvider().getMaxValue();
    else                                            amount = 1;

    boolean guaranteed = !c.isChanced() && c.chance >= c.maxChance;
    float pct = c.chance / (float) c.maxChance;     // 1.0f == 100%
    ItemStack stack = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
}
```

辅助 API（`api/recipe/RecipeHelper.java`，全 public static）：
- `getOutputItems(GTRecipe)` → `List<ItemStack>`（第 ~119 行；注意它会取 `ingredient.getItems()[0]`，**丢失 chance 信息**，做概率判断时不要用它）。
- `getOutputItems(GTRecipeBuilder)`、`getInputItems(GTRecipe)`、`getInputFluids(...)`、`getOutputFluids(...)`。
- `getOutputContents(GTRecipe, RecipeCapability<T>)` / `getInputContents(...)`。

`GTRecipe` 字段（`api/recipe/GTRecipe.java:36-39`）：

```java
public final Map<RecipeCapability<?>, List<Content>> inputs;        // 第 36 行
public final Map<RecipeCapability<?>, List<Content>> outputs;       // 第 37 行
public final Map<RecipeCapability<?>, List<Content>> tickInputs;    // 第 38 行
public final Map<RecipeCapability<?>, List<Content>> tickOutputs;   // 第 39 行
```

> ⚠️ **7.3.0 的 `GTRecipe` 上不存在 `itemInputs` / `itemOutputs` 字段**（那些名字只出现在 `GTRecipeBuilder`）。请用 `getOutputContents(ItemRecipeCapability.CAP)`。

### 6.5 给定机器 → 找到它能跑的 `GTRecipe`

**关键事实：GT 配方不在原版 `RecipeManager` 里。** 它们存在每个 `GTRecipeType` 自己的 `GTRecipeLookup`（一棵按原料索引的 Trie）。

```java
// 1) 拿到机器的配方类型
GTRecipeType type = ((IRecipeLogicMachine) machine).getRecipeType();

// 2) 方式 A：让 type 按 holder 当前内容搜索（最贴近机器实际行为）
Iterator<GTRecipe> it = type.searchRecipe((IRecipeCapabilityHolder) machine,
                                          r -> RecipeHelper.matchContents((IRecipeCapabilityHolder) machine, r).isSuccess());
```

| API | 签名 | 位置 | 说明 |
| --- | --- | --- | --- |
| `GTRecipeType#searchRecipe` | `public @NotNull Iterator<GTRecipe> searchRecipe(IRecipeCapabilityHolder holder, Predicate<GTRecipe> canHandle)` | `api/recipe/GTRecipeType.java:196` | 找不到时还会尝试 `customRecipeLogicRunners` |
| `GTRecipeType#getLookup()` | `@Getter private final GTRecipeLookup lookup` → `public GTRecipeLookup getLookup()` | 同上 第 82-83 行 | |
| `GTRecipeType#getMaxInputs/getMaxOutputs` | `public int getMaxInputs(RecipeCapability<?> cap)` / `getMaxOutputs(...)` | 第 219 / 223 行 | |
| `GTRecipeType#registryName` | `public final ResourceLocation registryName;` | 第 42 行 | |
| `GTRecipeType#getChanceFunction()` | `@Getter private ChanceBoostFunction chanceFunction` | 第 50-52 行 | |
| `GTRecipeLookup#find` | `public GTRecipe find(IRecipeCapabilityHolder holder, Predicate<GTRecipe> canHandle)` | `api/recipe/lookup/GTRecipeLookup.java:105` | Trie 递归查找单个配方 |
| `GTRecipeLookup#findRecipe` | `public GTRecipe findRecipe(IRecipeCapabilityHolder holder)` | 第 50 行 | 等价 `find(holder, r -> RecipeHelper.matchRecipe(holder, r).isSuccess())` |
| `GTRecipeLookup#getRecipeIterator` | `public RecipeIterator getRecipeIterator(IRecipeCapabilityHolder holder, Predicate<GTRecipe> canHandle)` | 第 120 行 | 遍历所有候选 |
| `GTRecipeLookup#addRecipe` | `public boolean addRecipe(GTRecipe recipe)` | 第 469 行 | 动态注入配方 |
| `GTRecipeLookup#removeAllRecipes` | `public void removeAllRecipes()` | 第 457 行 | |
| `GTRecipeLookup#getLookup()` | `@Getter private final Branch lookup` | 第 39-40 行 | |
| `GTRecipeLookup#recurseIngredientTreeFindRecipe` | `public GTRecipe recurseIngredientTreeFindRecipe(List<List<AbstractMapIngredient>>, Branch, Predicate<GTRecipe>, int, int, BitSet)`（两个重载） | 第 172 / 200 行 | 底层递归 |
| `RecipeHelper.matchContents` | `public static ActionResult matchContents(IRecipeCapabilityHolder holder, GTRecipe recipe)` | `api/recipe/RecipeHelper.java` | = `matchRecipe` + `matchTickRecipe` |
| `RecipeHelper.matchRecipe` | `public static ActionResult matchRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe)` | 同上 | 纯模拟，不改动内容 |

**注意**：
- `holder` 必须是 **`IRecipeCapabilityHolder`**（`WorkableTieredMachine` / `WorkableMultiblockMachine` 都实现了它）。**对单方块机器**，`IRecipeLogicMachine extends IRecipeCapabilityHolder`，所以直接强转即可。
- `GTRecipeLookup.find/getRecipeIterator` 是**基于 holder 当前库存**的查找（`prepareRecipeFind` 从 `holder.getCapabilitiesForIO(IO.IN)` 收集原料）。若只想“枚举该配方类型下所有配方”，7.3.0 **没有**公开的 `getRecipesFor(...)` / `getAllRecipes()` 之类方法（**这点已核实：`GTRecipeLookup` 只有上表所列 public 方法，没有返回全量集合的公开 API**）。可行的替代方案：
  1. 用 `GTRecipeType#getRecipesInCategory(GTRecipeCategory)`（`GTRecipeType.java:320`，public）——但只覆盖已加入分类的配方；
  2. 用 `searchRecipe(...)` 传入一个自定义的伪 `IRecipeCapabilityHolder`，其 `getCapabilitiesProxy()` 里放一个“包含所有可能的物品”的 handler（GT 自己在 JEI/EMI 插件中就是这么做的思路）；
  3. **对 addon 而言最实用的做法**：不要主动枚举，而是**在机器实际匹配到配方后读取 `logic.getLastRecipe()` / `getLastOriginRecipe()`**，再检查它的 `getOutputContents(ItemRecipeCapability.CAP)` 里有哪些 `isChanced()` 的产出，以及这些产出的物品是否**实际出现在输出库存里**。这直接满足需求 (d)/(e)，且完全避开 Trie 枚举难题。

---

## 7. 不消耗输入（notConsumable）

### 7.1 表示方式：`Content.chance == 0` 约定（**不是**特殊 Ingredient 子类，也**不是**独立 flag）

`GTRecipeBuilder`（`data/recipe/builder/GTRecipeBuilder.java`）的所有重载实现都是一样的模式：

```java
public GTRecipeBuilder notConsumable(ItemStack itemStack) {   // 第 657 行
    int lastChance = this.chance;
    this.chance = 0;
    inputItems(itemStack);
    this.chance = lastChance;
    return this;
}
// 同类重载：notConsumable(Ingredient) :665, (Item) :673, (Supplier<? extends Item>) :681,
//          (TagPrefix, Material) :689, (TagPrefix, Material, int) :697
//          notConsumableFluid(FluidStack) :705, notConsumableFluid(FluidIngredient) :710
//          circuitMeta(int) :718  → notConsumable(IntCircuitIngredient.of(configuration))
```

所以**“不消耗”== 该输入 `Content` 的 `chance == 0`**。`RecipeRunner` 靠这一条把内容排除在待消耗列表之外（见 2.2 节原文注释）。

### 7.2 机器在合成后会把该物品留在输入库存里吗？—— **会**

- `simulated == false` 时 `chance == 0` 的输入根本不进入 `recipeContents`，因此不会有任何 handler 对它调用 `extractItem`。物品**原地不动**。
- 唯一会移除它的情况：玩家/管道手动取出，或机器被拆除时 `MetaMachine#clearInventory(...)`（`MetaMachine.java:503`）把它掉落到地上。

### 7.3 addon 如何从 `GTRecipe` 检测 notConsumable

```java
for (Content c : recipe.getInputContents(ItemRecipeCapability.CAP)) {
    if (c.chance == 0) {
        // 这是 notConsumable（含编程电路）
        Ingredient ing = ItemRecipeCapability.CAP.of(c.getContent());
        boolean isCircuit = ing instanceof IntCircuitIngredient
                || (ing instanceof SizedIngredient si && si.getInner() instanceof IntCircuitIngredient);
    }
}
```

参考 GT 自身的判定（`api/capability/recipe/ItemRecipeCapability.java`）：
- `getMaxParallelByInput`：`if (ing instanceof IntCircuitIngredient) continue;`（第 218 行）；`if (content.chance == 0) { nonConsumables.addTo(ing, count); } else { consumables... }`（第 225-226 行）。
- `applyWidgetInfo`：`if (io == IO.IN && (content.chance == 0 || this.of(content.content) instanceof IntCircuitIngredient)) slot.setIngredientIO(IngredientIO.CATALYST);`（第 492-493 行）。

**坑**：`IntCircuitIngredient` 有时会被包在 `SizedIngredient` / `IntProviderIngredient` 里面（见 `ItemRecipeCapability.compressIngredients` 三种分支都做了 `instanceof` 检查，第 101 / 104 / 107 行），所以判定“是不是电路”时要**解包**：

```java
static boolean isCircuitIngredient(Ingredient ing) {
    if (ing instanceof IntCircuitIngredient) return true;
    if (ing instanceof SizedIngredient si) return si.getInner() instanceof IntCircuitIngredient;
    if (ing instanceof IntProviderIngredient ip) return ip.getInner() instanceof IntCircuitIngredient;
    return false;
}
```
（`SizedIngredient#getInner()` 为 `@Getter protected final Ingredient inner;` → **public getter**，`api/recipe/ingredient/SizedIngredient.java:33-34`；`SizedIngredient#getAmount()` 同样由 `@Getter` 生成，第 31-32 行。）

---

## 8. ME 样板总成（ME Pattern Buffer）内部机制

文件：`integration/ae2/machine/MEPatternBufferPartMachine.java`（本地副本共 707 行）、`integration/ae2/machine/trait/InternalSlotRecipeHandler.java`、`integration/ae2/machine/trait/ProxySlotRecipeHandler.java`。

### 8.1 类声明与字段（全部核实）

```java
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MEPatternBufferPartMachine extends MEBusPartMachine
        implements ICraftingProvider, PatternContainer, IDataStickInteractable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = ...;
    protected static final int MAX_PATTERN_COUNT = 27;

    private final InternalInventory internalPatternInventory = new InternalInventory() { ... };  // 包装 patternInventory
    @Getter @Persisted @DescSynced
    private final CustomItemStackHandler patternInventory = new CustomItemStackHandler(MAX_PATTERN_COUNT);

    @Getter @Persisted protected final NotifiableItemStackHandler shareInventory;   // 9 格, IO.IN/IO.NONE
    @Getter @Persisted protected final NotifiableFluidTank shareTank;                // 9 罐, IO.IN/IO.NONE
    @Getter @Persisted protected final InternalSlot[] internalInventory;             // 长度 27
    private final BiMap<IPatternDetails, InternalSlot> detailsSlotMap = HashBiMap.create(MAX_PATTERN_COUNT);
    @DescSynced @Persisted @Setter private String customName = "";
    private boolean needPatternSync;
    @Persisted private final Set<BlockPos> proxies = new ObjectOpenHashSet<>();
    private final Set<MEPatternBufferProxyPartMachine> proxyMachines = new ReferenceOpenHashSet<>();
    @Getter protected final InternalSlotRecipeHandler internalRecipeHandler;
    @Nullable protected TickableSubscription updateSubs;

    // 继承自 ItemBusPartMachine：
    // @Getter @Persisted protected final NotifiableItemStackHandler circuitInventory;   <-- 共享电路槽（1 格）
    // @Getter @Persisted private final NotifiableItemStackHandler inventory;            <-- 继承的普通库存（ME 总线里被换成 ExportOnlyAEItemList）
}
```

构造器要点（第 137 行起）：

```java
public MEPatternBufferPartMachine(IMachineBlockEntity holder, Object... args) {
    super(holder, IO.IN, args);
    this.patternInventory.setFilter(stack -> stack.getItem() instanceof ProcessingPatternItem);
    for (int i = 0; i < this.internalInventory.length; i++) {
        this.internalInventory[i] = new InternalSlot();
    }
    getMainNode().addService(ICraftingProvider.class, this);
    this.shareInventory = new NotifiableItemStackHandler(this, 9, IO.IN, IO.NONE);
    this.shareTank = new NotifiableFluidTank(this, 9, 8 * FluidType.BUCKET_VOLUME, IO.IN, IO.NONE);
    this.internalRecipeHandler = new InternalSlotRecipeHandler(this, internalInventory);
}
```

### 8.2 `detailsSlotMap`：样板 → `InternalSlot` 的映射

- 类型：`private final BiMap<IPatternDetails, InternalSlot> detailsSlotMap = HashBiMap.create(27);`（**private，无 getter**）。
- 填充时机 1 —— `onLoad()`（第 168 行起）：服务器 tick 1 时遍历 `patternInventory`，`PatternDetailsHelper.decodePattern(pattern, getLevel())` 成功则 `detailsSlotMap.put(details, internalInventory[i])`，然后 `needPatternSync = true`。
- 填充时机 2 —— GUI 改槽：`AEPatternViewSlotWidget.setChangeListener(() -> onPatternChange(finalI))`（`createUIWidget`，第 290 行附近）。
- 更新逻辑 `onPatternChange(int index)`（**private**，第 250 行起）：

```java
private void onPatternChange(int index) {
    if (isRemote()) return;
    var internalInv = internalInventory[index];
    var newPattern = patternInventory.getStackInSlot(index);
    var newPatternDetails = PatternDetailsHelper.decodePattern(newPattern, getLevel());
    var oldPatternDetails = detailsSlotMap.inverse().get(internalInv);
    detailsSlotMap.forcePut(newPatternDetails, internalInv);       // 注意：可能 put(null, inv)
    if (oldPatternDetails != null && !oldPatternDetails.equals(newPatternDetails)) {
        internalInv.refund();                                       // <-- 换样板时把槽内残留退回 ME
    }
    needPatternSync = true;
}
```

- `getAvailablePatterns()`（**public**，第 333 行附近）：`detailsSlotMap.keySet().stream().filter(Objects::nonNull).toList()`。

### 8.3 `pushPattern` —— AE 把样板原料推进 buffer

```java
@Override
public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {   // public
    if (!isFormed() || !getMainNode().isActive() || !detailsSlotMap.containsKey(patternDetails) ||
            !checkInput(inputHolder)) {
        return false;
    }
    var slot = detailsSlotMap.get(patternDetails);
    if (slot != null) {
        slot.pushPattern(patternDetails, inputHolder);
        return true;
    }
    return false;
}

private boolean checkInput(KeyCounter[] inputHolder) {   // private：只接受 item / fluid
    for (KeyCounter input : inputHolder) {
        var illegal = input.keySet().stream()
                .map(AEKey::getType)
                .map(AEKeyType::getId)
                .anyMatch(id -> !id.equals(AEKeyType.items().getId()) && !id.equals(AEKeyType.fluids().getId()));
        if (illegal) return false;
    }
    return true;
}

@Override public boolean isBusy() { return false; }
```

`InternalSlot#pushPattern`（**public 内部类方法**）：

```java
public void pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
    patternDetails.pushInputsToExternalInventory(inputHolder, this::add);   // 逐 key 调 private void add(AEKey, long)
    onContentsChanged();
}
```

**关键点**：
- `!isFormed()` 是最外层短路条件 —— **样板总成必须已成型（作为多方块部件被控制器接纳）才能推样板**。
- 原料被写入 `InternalSlot` 的 `itemInventory` / `fluidInventory`（`Object2LongOpenCustomHashMap<ItemStack>` / `Object2LongOpenHashMap<FluidStack>`，**private 字段**），而不是普通物品槽。
- 若某个 key 已存在，`addTo` 会**累加**数量。
- **不会**因为重复推送同一样板而拒绝（`isBusy()` 恒为 `false`），因此 addon 可以自由重推。

### 8.4 多方块如何消费 buffer 内容 —— `InternalSlotRecipeHandler`

`InternalSlotRecipeHandler.java`（全文 129 行）：

```java
public final class InternalSlotRecipeHandler {
    @Getter private final List<RecipeHandlerList> slotHandlers;

    public InternalSlotRecipeHandler(MEPatternBufferPartMachine buffer, InternalSlot[] slots) {
        this.slotHandlers = new ArrayList<>(slots.length);
        for (int i = 0; i < slots.length; i++) {
            slotHandlers.add(new SlotRHL(buffer, slots[i], i));      // 27 个 SlotRHL
        }
    }

    @Getter
    protected static class SlotRHL extends RecipeHandlerList {
        private final SlotItemRecipeHandler itemRecipeHandler;
        private final SlotFluidRecipeHandler fluidRecipeHandler;

        public SlotRHL(MEPatternBufferPartMachine buffer, InternalSlot slot, int idx) {
            super(IO.IN);
            itemRecipeHandler  = new SlotItemRecipeHandler(buffer, slot, idx);
            fluidRecipeHandler = new SlotFluidRecipeHandler(buffer, slot, idx);
            addHandlers(buffer.getCircuitInventory(),      // <-- ★ 共享电路槽被加进每一个 SlotRHL
                        buffer.getShareInventory(),
                        buffer.getShareTank(),
                        itemRecipeHandler, fluidRecipeHandler);
            this.setGroup(RecipeHandlerGroupDistinctness.BUS_DISTINCT);
        }

        @Override public boolean isDistinct() { return true; }
        @Override public void setDistinct(boolean ignored, boolean notify) {}
    }

    @Getter
    private static class SlotItemRecipeHandler extends NotifiableRecipeHandlerTrait<Ingredient> {
        private final InternalSlot slot;
        private final int priority;
        private final int size = 81;
        private final RecipeCapability<Ingredient> capability = ItemRecipeCapability.CAP;
        private final IO handlerIO = IO.IN;
        private final boolean isDistinct = true;

        private SlotItemRecipeHandler(MEPatternBufferPartMachine buffer, InternalSlot slot, int index) {
            super(buffer);
            this.slot = slot;
            this.priority = IFilteredHandler.HIGH + index + 1;       // <-- 优先级随槽位递增
            slot.setOnContentsChanged(this::notifyListeners);
        }

        @Override
        public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
            if (io != IO.IN || slot.isItemEmpty()) return left;
            return slot.handleItemInternal(left, simulate);          // 从该槽的虚拟库存里扣料
        }
        ...
    }
    // SlotFluidRecipeHandler 结构完全对称
}
```

`InternalSlot#handleItemInternal(List<Ingredient> left, boolean simulate)`（**public**）逐条原料在该槽的 `itemInventory` 中查找并扣除；`simulate == true` 时不改数据。

**多方块侧的接入点**（`api/machine/multiblock/WorkableMultiblockMachine.java`，`onStructureFormed()`）：

```java
@Override
public void onStructureFormed() {
    super.onStructureFormed();
    ...
    capabilitiesProxy.clear();
    capabilitiesFlat.clear();
    ...
    Long2ObjectMap<IO> ioMap = getMultiblockState().getMatchContext().getOrCreate("ioMap", ...);
    for (IMultiPart part : getParts()) {
        IO io = ioMap.getOrDefault(part.self().getPos().asLong(), IO.BOTH);
        if (io == IO.NONE) continue;

        var handlerLists = part.getRecipeHandlers();       // MEPatternBufferPartMachine → internalRecipeHandler.getSlotHandlers()
        for (var handlerList : handlerLists) {
            if (!handlerList.isValid(io)) continue;
            this.addHandlerList(handlerList);
            traitSubscriptions.add(handlerList.subscribe(recipeLogic::updateTickSubscription));
        }
    }
    // 再接 self traits...
    recipeLogic.updateTickSubscription();
}
```

`MEPatternBufferPartMachine#getRecipeHandlers()`（**public @Override**）：

```java
@Override
public List<RecipeHandlerList> getRecipeHandlers() {
    return internalRecipeHandler.getSlotHandlers();
}
```

**由此得到完整数据流**：

```
AE pushPattern → detailsSlotMap 找到 InternalSlot → Slot.pushPattern → slot.itemInventory 累加
                                                            ↓
多方块 onStructureFormed → 把 27 个 SlotRHL 全部 join 进 capabilitiesProxy[IO.IN]
                                                            ↓
控制器 RecipeLogic.searchRecipe → RecipeRunner（模拟匹配 / 实际消耗）
   → 因为每个 SlotRHL 都是 BUS_DISTINCT，RecipeRunner 会【逐个槽】尝试
   → SlotItemRecipeHandler.handleRecipeInner → InternalSlot.handleItemInternal → 真扣料
```

**重要推论**：
1. 配方必须**完整地由“某一个槽 + 共享电路槽 + shareInventory/shareTank”满足**（BUS_DISTINCT 语义：一个 distinct 组必须能独立满足整个配方）。
2. **27 个槽共用同一个 `circuitInventory` 对象**（`buffer.getCircuitInventory()` 被重复 addHandlers 到每个 SlotRHL）。这意味着 **7.3.0 原生状态下所有样板槽共用同一个电路号**——这正是需求 (c) 要解决的问题。

### 8.5 `refund()` 的定义与所有调用点

`InternalSlot#refund()`（**public**，第 506 行起）：

```java
public void refund() {
    var network = getMainNode().getGrid();
    if (network != null) {
        MEStorage networkInv = network.getStorageService().getInventory();
        var energy = network.getEnergyService();
        for (var it = itemInventory.object2LongEntrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            var stack = entry.getKey();
            var count = entry.getLongValue();
            if (stack.isEmpty() || count == 0) { it.remove(); continue; }
            var key = AEItemKey.of(stack);
            if (key == null) continue;
            long inserted = StorageHelper.poweredInsert(energy, networkInv, key, count, actionSource);
            if (inserted > 0) {
                count -= inserted;
                if (count == 0) it.remove();
                else entry.setValue(count);
            }
        }
        // 流体同理（AEFluidKey + poweredInsert）
        onContentsChanged();
    }
}
```

**全部调用点（`grep` 全仓库的结果，只有两处）：**

| 调用者 | 签名 / 可见性 | 触发条件 |
| --- | --- | --- |
| `MEPatternBufferPartMachine#refundAll(ClickData clickData)` | **private**（第 245 行） | GUI 按钮（`ButtonConfigurator` 绑定 `this::refundAll`，第 275 行），遍历 27 个槽全部 `refund()` |
| `MEPatternBufferPartMachine#onPatternChange(int index)` | **private**（第 250 行） | 某槽的样板被替换/清空时，对该槽 `internalInv.refund()` |

**结论（对 addon 极其关键）：7.3.0 中没有任何“合成结束后自动退回残留”的逻辑。** 残留物（例如 notConsumable 输入、概率产出未命中时对应的未消耗部分、以及已推入但本配方根本没用到的多余原料）会一直留在 `InternalSlot.itemInventory` 里，直到玩家按 GUI 的“refund all”按钮或换样板。 → 需求 (b)/(d) 必须由 addon 自己调用 `refund()`（它是 **public**，可直接调用；`InternalSlot` 也是 public 内部类，见 8.7）。

### 8.6 共享电路槽在配方匹配中的参与方式（需求 (c) 的核心）

- `InternalSlotRecipeHandler.SlotRHL` 构造器第 45 行：`addHandlers(buffer.getCircuitInventory(), ...)`。
- `buffer.getCircuitInventory()` 来自 `ItemBusPartMachine`，是**一个 1 格 `NotifiableItemStackHandler`，`handlerIO = IO.IN`、`capabilityIO = IO.NONE`、filter = `IntCircuitBehaviour::isIntegratedCircuit`**。
- 因为它在 `RecipeHandlerList` 的 `handlerMap` 里被登记为 `ItemRecipeCapability.CAP` 的 handler，`RecipeRunner` 在匹配 `IntCircuitIngredient` 时会在这个共享槽里找到电路。
- **所以原生行为是：整个样板总成（无论多少个样板槽）只有一个电路号。** 若同一个多方块里有 3 个样板需要电路 1/2/3，原生无法区分。

### 8.7 最干净的 Hook 点：Mixin 目标与方法签名（需求 (c)/(d)）

#### (i) 让“每个样板槽拥有独立电路”

有两条可行路线，**推荐路线 A**。

**路线 A（推荐）：替换每个 `SlotRHL` 里的电路 handler —— 不碰原类字段，只改 handler 组成**

- 目标：`com.gregtechceu.gtceu.integration.ae2.machine.trait.InternalSlotRecipeHandler$SlotRHL`
  - 类签名：`protected static class SlotRHL extends RecipeHandlerList`（**static 嵌套类，非 final** → Mixin 容易处理；但**不是 public**，Java 侧无法直接引用，Mixin 需用 `@Mixin(targets = "…InternalSlotRecipeHandler$SlotRHL")` 字符串形式）。
- 注入点：构造器 `<init>(Lcom/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine;Lcom/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine$InternalSlot;I)V` 的 `RETURN`。
  - 在 `@Inject(at = @At("RETURN"))` 中，用 `buffer` 与 `idx`（`@Inject` 的 `CallbackInfo` 不能给参数——用 `@ModifyVariable` 或 `@Redirect` 拿到参数，或更简单：**另开一个接口 + `@Unique` 字段**记录 `buffer`/`idx`）。
  - 用 Mixin `@Accessor`/`@Invoker` 拿到 `RecipeHandlerList#getHandlerMap()`（**public `@Getter`**，`api/machine/trait/RecipeHandlerList.java:30-31`）后，把其中的 `buffer.getCircuitInventory()` 替换为**每槽一个的代理 handler**（仿照 GT 自己的 `ProxySlotRecipeHandler.ProxyItemRecipeHandler`，见下）。
  - 代理 handler 只需实现 `IRecipeHandlerTrait<Ingredient>`，`handleRecipeInner` 委托给一个 `List<Ingredient>` / 虚拟库存；`getContents()` 返回 `IntCircuitIngredient.of(perSlotCircuit)` 对应的 `ItemStack`（`IntCircuitBehaviour.stack(n)`，并可在 `simulate == true` 时保留、`false` 时不清除，保持“不消耗”语义）。
- 数据来源：每个样板槽的电路号可以从**样板本身**推导（例如解析 `IPatternDetails` 的输出/输入里是否含电路，或按槽位索引从外部配置读取）。7.3.0 的 `MEPatternBufferPartMachine` **没有**存储“每槽电路号”的字段，addon 必须自己新增存储（见路线 B）。

**路线 B：在 `MEPatternBufferPartMachine` 上新增 per-slot 电路存储（更彻底，但要 Mixin 加字段）**

- 目标：`com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine`（**public，非 final**）。
- 用 `@Mixin(MEPatternBufferPartMachine.class)` 添加
  `@Unique @Persisted`（LDLib2 SyncData 注解可与 Mixin 共存，但需自行注册 `ManagedFieldHolder`，风险较高）或更安全：用 `@Unique` 普通字段 + 自行在 `saveCustomPersistedData/loadCustomPersistedData`（`MachineTrait` 层）或 `MEPatternBufferPartMachine` 的 `serializeNBT` 路径里持久化。
- 再在 `getRecipeHandlers()`（**public**，签名 `()Ljava/util/List;`）`RETURN` 处，把返回的 27 个 `SlotRHL` 逐一“打补丁”。

> **可行性提示**：`InternalSlotRecipeHandler` 本身是 **`public final class`** → **不能继承，但可以 Mixin**。`SlotRHL` 是 **protected static、非 final** → 可 Mixin。`SlotItemRecipeHandler` / `SlotFluidRecipeHandler` 是 **`private static class`** → 只能通过 `@Mixin(targets = "...$SlotItemRecipeHandler")` 字符串定位，且引用其类型时需要用 `Object`/`IRecipeHandlerTrait` 等公共父类型。

**避免 Mixin 的替代方案（值得优先评估）**：
- `MEPatternBufferProxyPartMachine` 已经有现成的“代理 handler”模式：`ProxySlotRecipeHandler`（`integration/ae2/machine/trait/ProxySlotRecipeHandler.java`，**public final class**）内部用 `ProxyItemRecipeHandler#setProxy(IRecipeHandlerTrait<Ingredient>)` 把 `buffer.getCircuitInventory()` 等“代理”出去。
  该类的具体行为对 addon 有三点价值：
  1. 证明可以**用一个自定义 `IRecipeHandlerTrait` 冒充某个槽的电路来源**；
  2. `ProxyItemRecipeHandler` 是 **private static**，不能直接复用，但它的实现只有 ~30 行，addon 完全可以照抄成自己的 public 类；
  3. `MEPatternBufferProxyPartMachine#getRecipeHandlers()`（public）返回 `proxySlotRecipeHandler.getProxySlotHandlers()`，addon 也可以**自己注册一个自定义的 `IMultiPart`**（继承 `ItemBusPartMachine`/`TieredIOPartMachine`）并在其 `getRecipeHandlers()` 里返回**自己的 27 个 RHL**（每个 RHL 里放自己的 per-slot 电路 handler + 原有的 `InternalSlot` item/fluid handler）。这条路线**零 Mixin**，但需要把 buffer 的 `internalInventory` 与 `detailsSlotMap` 暴露出来（二者分别是 `@Getter protected final InternalSlot[] internalInventory` → **`getInternalInventory()` 是 public**；`detailsSlotMap` 是**无 getter 的 private**，只能通过 `getAvailablePatterns()` 反推，或用 `internalInventory[i]` 与 `patternInventory.getStackInSlot(i)` 的**同索引对应关系**——见 8.2，`detailsSlotMap` 就是按索引映射的）。

#### (ii) 概率产出缺失时重推样板

- **推荐注入点：`MEPatternBufferPartMachine#pushPattern`**
  描述符：`(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z` 用途：记录“最近一次推送的样板 + 原料 + 目标槽”，供后续补偿逻辑使用。
  - `@Inject(method = "pushPattern", at = @At("RETURN"), cancellable = false)` —— 读参数即可（`IPatternDetails` 与 `KeyCounter[]` 都是方法参数，可直接拿）。
- **触发补偿的地方（二选一）**：
  - ① `RecipeLogic#onRecipeFinish()V`（`api/machine/trait/RecipeLogic.java:484`，**public**）——多方的 `recipeLogic` 是 `WorkableMultiblockMachine` 的 `public final RecipeLogic recipeLogic`（`@Getter`），可直接访问。注入 `TAIL` 后检查：本次 `logic.getLastRecipe()` 的 `ItemRecipeCapability.CAP` 输出中，哪些 `isChanced()` 的产出**没有**出现在输出总线里 → 对该样板调用 `slot.pushPattern(...)` 重推（或直接再次触发 AE 的 `ICraftingProvider` 流程）。
  - ② 目标改为 `MEPatternBufferPartMachine$InternalSlot#handleItemInternal(Ljava/util/List;Z)Ljava/util/List;`（**public**）——在扣料后记录“本次实际扣了哪些原料”，与产出比对。
- **注意**：`pushPattern` 需要 `KeyCounter[]`（AE2 的原料计数数组）。addon 若要在**不重新向 AE 请求**的前提下重推，必须在首次 `pushPattern` 时就把 `KeyCounter[]` 缓存下来（注意 `KeyCounter` 是否可变——建议深拷贝/重新构造）。

**推荐的“重推”判定（结合需求 (e)）**：
1. 记录 `IPatternDetails` → 期望产出（`details.getOutputs()`，AE2 侧）与其在 GT 配方中的 chance；
2. 合成结束时统计输出总线/槽的实际产出；
3. 若 `实际 < 期望`，且缺失项对应 GT 配方中的 chanced 输出，则按 AE 合成任务的剩余需求量决定重推次数。

### 8.8 `MEPatternBufferPartMachine` 其他 public 成员（便于 addon 使用）

| 成员 | 签名 | 备注 |
| --- | --- | --- |
| `getPatternInventory()` | `CustomItemStackHandler getPatternInventory()` | `@Getter`，27 格 |
| `getInternalInventory()` | `InternalSlot[] getInternalInventory()` | `@Getter @Persisted protected final` → **public getter** |
| `getInternalRecipeHandler()` | `InternalSlotRecipeHandler getInternalRecipeHandler()` | `@Getter` |
| `getShareInventory()` / `getShareTank()` | `NotifiableItemStackHandler` / `NotifiableFluidTank` | `@Getter` |
| `getCircuitInventory()` | 继承自 `ItemBusPartMachine` | `@Getter` |
| `getRecipeHandlers()` | `public List<RecipeHandlerList> getRecipeHandlers()` | `@Override` |
| `getAvailablePatterns()` | `public List<IPatternDetails> getAvailablePatterns()` | 过滤 null |
| `pushPattern(...)` | `public boolean pushPattern(IPatternDetails, KeyCounter[])` | `@Override` |
| `isBusy()` | `public boolean isBusy()` → `false` | `@Override` |
| `addProxy` / `removeProxy` / `getProxies` | `public void addProxy(MEPatternBufferProxyPartMachine)` / `public void removeProxy(...)` / `@UnmodifiableView public Set<...> getProxies()` | |
| `mergeInternalSlots()` | `public BufferData mergeInternalSlots()` | `public record BufferData(Object2LongMap<ItemStack> items, Object2LongMap<FluidStack> fluids)` |
| `getTerminalPatternInventory()` | `public InternalInventory getTerminalPatternInventory()` | AE2 `PatternContainer` |
| `getTerminalGroup()` | `public PatternContainerGroup getTerminalGroup()` | 已会根据共享电路号给终端分组命名（第 380 行附近） |
| `onDataStickShiftUse` | `public InteractionResult onDataStickShiftUse(Player, ItemStack)` | 存 `pos` int 数组 |
| `isWorkingEnabled()` / `setWorkingEnabled(boolean)` | 恒 `true` / 空实现 | ME 总线不参与启停 |
| `isDistinct()` / `setDistinct(boolean)` | 恒 `true` / 空实现 | 强制 distinct |
| `update()` | `protected void update()` | 需要同步样板时调用 `ICraftingProvider.requestUpdate(getMainNode())` |

`InternalSlot` 的 public 成员：

| 成员 | 签名 |
| --- | --- |
| `isItemEmpty()` | `public boolean isItemEmpty()` |
| `isFluidEmpty()` | `public boolean isFluidEmpty()` |
| `onContentsChanged()` | `public void onContentsChanged()` |
| `getItems()` | `public List<ItemStack> getItems()`（按 `GTMath.splitStacks` 展开成 ≤64 的多个 stack） |
| `getFluids()` | `public List<FluidStack> getFluids()` |
| `refund()` | `public void refund()` |
| `pushPattern(...)` | `public void pushPattern(IPatternDetails, KeyCounter[])` |
| `handleItemInternal(...)` | `public @Nullable List<Ingredient> handleItemInternal(List<Ingredient> left, boolean simulate)` |
| `handleFluidInternal(...)` | `public @Nullable List<FluidIngredient> handleFluidInternal(List<FluidIngredient> left, boolean simulate)` |
| `serializeNBT` / `deserializeNBT` | `public CompoundTag serializeNBT()` / `public void deserializeNBT(CompoundTag)` |
| `setOnContentsChanged` | `@Setter private Runnable onContentsChanged` → `public void setOnContentsChanged(Runnable)` |

---

## 9. `NotifiableItemStackHandler` / `RecipeHandlerList` / distinctness 与优先级

### 9.1 `NotifiableItemStackHandler`（`api/machine/trait/NotifiableItemStackHandler.java`）

```java
public class NotifiableItemStackHandler extends NotifiableRecipeHandlerTrait<Ingredient>
        implements ICapabilityTrait, IItemHandlerModifiable {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = ...;
    @Getter public final IO handlerIO;                     // 第 41-42 行
    @Getter public final IO capabilityIO;                  // 第 43-44 行
    @Persisted @DescSynced public final CustomItemStackHandler storage;   // 第 45-47 行
    @Accessors(fluent = true) @Getter @Setter
    private boolean shouldSearchContent = true;            // 第 48-51 行
    private Boolean isEmpty;
```

**三个构造器**：

```java
public NotifiableItemStackHandler(MetaMachine machine, int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO,
                                  IntFunction<CustomItemStackHandler> storageFactory)   // 第 54 行
public NotifiableItemStackHandler(MetaMachine machine, int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO) // 第 63 行
public NotifiableItemStackHandler(MetaMachine machine, int slots, @NotNull IO handlerIO)  // 第 67 行
//                                                   → this(machine, slots, handlerIO, handlerIO)
```

**addon 最常用的 public 成员**：

| 成员 | 签名 | 行号 |
| --- | --- | --- |
| `setFilter` | `public NotifiableItemStackHandler setFilter(Predicate<ItemStack> filter)` | 71 |
| `onContentsChanged` | `public void onContentsChanged()`（清空 isEmpty 缓存 + `notifyListeners()`） | 76 |
| `handleRecipeInner` | `public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate)` | 87 |
| `handleRecipe`（**static**） | `public static List<Ingredient> handleRecipe(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate, IO handlerIO, CustomItemStackHandler storage)` | 93 |
| `getCapability` | `public RecipeCapability<Ingredient> getCapability()` → `ItemRecipeCapability.CAP` | 221 |
| `getSlots` | `public int getSlots()` | 225 |
| `getSize` | `public int getSize()` | 230 |
| `getContents` | `public @NotNull List<Object> getContents()` | 235 |
| `getTotalContentAmount` | `public double getTotalContentAmount()` | 247 |
| `isEmpty` | `public boolean isEmpty()`（带缓存） | 258 |
| `exportToNearby` / `importFromNearby` | `public void exportToNearby(@NotNull Direction... facings)` / `public void importFromNearby(@NotNull Direction... facings)` | 271 / 282 |
| `getStackInSlot` / `setStackInSlot` | `public ItemStack getStackInSlot(int slot)` / `public void setStackInSlot(int index, @NotNull ItemStack stack)` | 297 / 302 |
| `insertItem` / `extractItem` | `public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate)` / `public ItemStack extractItem(int slot, int amount, boolean simulate)`（**受 capabilityIO 限制**） | 308 / 321 |
| `insertItemInternal` / `extractItemInternal` | `public ItemStack insertItemInternal(...)` / `public ItemStack extractItemInternal(...)`（**绕过 capabilityIO**） | 315 / 328 |
| `getSlotLimit` / `isItemValid` | `public int getSlotLimit(int slot)` / `public boolean isItemValid(int slot, @NotNull ItemStack stack)` | 333 / 338 |

`NotifiableRecipeHandlerTrait<T>`（`api/machine/trait/NotifiableRecipeHandlerTrait.java`）：

```java
public abstract class NotifiableRecipeHandlerTrait<T> extends MachineTrait implements IRecipeHandlerTrait<T> {
    protected List<Runnable> listeners = new ArrayList<>();
    @Persisted @DescSynced @Getter @Setter protected boolean isDistinct;
    public NotifiableRecipeHandlerTrait(MetaMachine machine) { super(machine); }
    @Override public ISubscription addChangedListener(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public void notifyListeners() { listeners.forEach(Runnable::run); }
}
```

**注意**：`MachineTrait` 的构造器会 `machine.attachTraits(this)`，且 `MetaMachine#attachTraits` 的注释明确写着 “All traits should be initialized while MetaMachine is creating. **you cannot add them on the fly.**”（`MetaMachine.java:496-501`）。addon 若要给机器添加自定义 handler，必须在**机器的构造器/工厂里**创建。

### 9.2 `RecipeHandlerList`（`api/machine/trait/RecipeHandlerList.java`）

```java
public class RecipeHandlerList {                                          // public，非 final
    public static final RecipeHandlerList NO_DATA = new RecipeHandlerList(IO.NONE);
    public static final Comparator<RecipeHandlerList> COMPARATOR = ...;    // 第 22 行

    @Getter private final Map<RecipeCapability<?>, List<IRecipeHandler<?>>> handlerMap = new Reference2ObjectOpenHashMap<>();
    private final List<IRecipeHandler<?>> allHandlers = new ArrayList<>();
    private final List<NotifiableRecipeHandlerTrait<?>> allHandlerTraits = new ArrayList<>();
    @Getter private final IO handlerIO;                                    // 第 35-36 行
    @Getter private int color = -1;
    @Setter @Getter @NotNull private RecipeHandlerGroup group = RecipeHandlerGroupColor.UNDYED;

    protected RecipeHandlerList(IO handlerIO) { this.handlerIO = handlerIO; }   // 第 45 行：protected 构造器
```

**静态工厂（addon 应该用这些）**：

```java
public static RecipeHandlerList of(IO io, int color, IRecipeHandler<?>... handlers)      // 第 49 行
public static RecipeHandlerList of(IO io, IRecipeHandler<?>... handlers)                 // 第 56 行
public static RecipeHandlerList of(IO io, Iterable<IRecipeHandler<?>> handlers)          // 第 62 行
public static RecipeHandlerList of(IO io, int color, Iterable<IRecipeHandler<?>> handlers) // 第 68 行
```

**其他 public 方法**：`addHandler`(75) / `addHandlers`(79,83) / `setDistinctAndNotify(boolean)`(98) / `setDistinct(boolean)`(102) / `isDistinct()`(118) / `setColor(int)`(122) / `setColor(int,boolean)`(126) / `hasCapability(RecipeCapability<?>)`(138) / `getCapability(RecipeCapability<?>)`(142) / `getCapabilities()`(146) / `doesCapabilityBypassDistinct()`(153) / `isValid(IO)`(160) / `getPriority()`(165) / `getTotalContentAmount()`(171) / `handleRecipe(...)`(178) / `getHandlersFlat()`(199) / `subscribe(Runnable)`(215) / `subscribe(Runnable, RecipeCapability<?>)`(221)。

**`setDistinct(boolean, boolean)` 是 protected**（第 106 行）；外部只能调用 `final` 的 `setDistinct(boolean)` 或 `setDistinctAndNotify(boolean)`。

### 9.3 distinctness（`RecipeHandlerGroupDistinctness`）与分组

`api/machine/trait/RecipeHandlerGroupDistinctness.java`（全文）：

```java
public enum RecipeHandlerGroupDistinctness implements RecipeHandlerGroup {
    BUS_DISTINCT,
    BYPASS_DISTINCT
}
```

- `RecipeHandlerGroup` 是标记接口（`api/machine/trait/RecipeHandlerGroup.java`）；`RecipeHandlerGroupColor` 是 `record`/枚举式的“染色组”（`api/machine/trait/RecipeHandlerGroupColor.java`，含 `UNDYED`）。
- 语义：
  - **`BUS_DISTINCT`**：该组必须**单独**满足整个配方（互不混合）。这是样板总成 27 个槽的核心语义（以及被喷涂成不同颜色的总线）。
  - **`BYPASS_DISTINCT`**：绕过 distinct 检查，对**所有**组都参与（例如能量仓、soul hatch 等“全局”部件）。判定依据是 `RecipeHandlerList#doesCapabilityBypassDistinct()` → `RecipeCapability#shouldBypassDistinct()`。
    - `RecipeCapability#shouldBypassDistinct()` 默认 **`true`**（`api/capability/recipe/RecipeCapability.java:222`）；
    - `ItemRecipeCapability` **覆写为 `false`**（“Items should be respected for distinct checks”，`ItemRecipeCapability.java` 末尾）。
  - `RecipeHelper.addToRecipeHandlerMap(RecipeHandlerGroup key, RecipeHandlerList handler, Map<...> map)`（`api/recipe/RecipeHelper.java`）负责分组，其中 `RecipeHandlerGroupColor.UNDYED` 的 RHL 会被**复制进所有非 distinct、非 bypass 的组**——这就是“未染色总线对所有染色总线都可见”的实现。
- `RecipeRunner.handleContents()` 的执行顺序：**先跑所有 `BUS_DISTINCT` 组**（逐个，且先 `simulate` 验证再实际执行），跑不通再尝试其余分组。

### 9.4 优先级（`IFilteredHandler`）

`api/capability/recipe/IFilteredHandler.java`（全文 29 行）：

```java
public interface IFilteredHandler<K> extends Predicate<K> {
    Comparator<IFilteredHandler<?>> PRIORITY_COMPARATOR =
            Comparator.<IFilteredHandler<?>>comparingInt(IFilteredHandler::getPriority).reversed();
    int HIGHEST = Integer.MAX_VALUE;
    int HIGH      = Integer.MAX_VALUE / 2;
    int NORMAL    = 0;
    int LOW       = Integer.MIN_VALUE / 2;
    int LOWEST    = Integer.MIN_VALUE;

    @Override default boolean test(K ingredient) { return true; }
    default int getPriority() { return NORMAL; }
}
```

- **数值越大优先级越高**（比较器 `reversed()`）。
- `IRecipeHandler#ENTRY_COMPARATOR` 用于同 capability 内 handler 的排序；`RecipeHandlerList#addHandlers` 仅在 `handlerIO.support(IO.OUT)` 时 `sort()`（第 89-96 行）——即**输入 handler 不排序**。
- `RecipeHandlerList#getPriority()` = 所有 handler `getPriority()` 之和（第 165 行）；`COMPARATOR` 先比 priority（`Long.compare`，**升序**，第 23 行），再比“是否有内容”。
- 样板总成给每个槽的 item/fluid handler 设了 `IFilteredHandler.HIGH + index + 1`（`InternalSlotRecipeHandler`），即 **槽位索引越大优先级越高**，用于让后加入的样板优先被尝试。
- **addon 自定义 handler 时**：只需 `extends NotifiableRecipeHandlerTrait<Ingredient>` 并覆写 `getPriority()`（**public default，可覆写**）即可参与优先级排序。若要实现 `IFilteredHandler` 的“过滤 + 优先级”双语义，直接 `implements IFilteredHandler<Ingredient>` 并覆写 `test(K)` 与 `getPriority()`。

---

## 10. 注册：从 addon 注册新机器 / 部件 / 物品（7.3.0）

### 10.1 `GTRegistrate`

文件：`api/registry/registrate/GTRegistrate.java`（本地 233 行）。

```java
public class GTRegistrate extends AbstractRegistrate<GTRegistrate> {       // 第 65 行

    protected GTRegistrate(String modId) { super(modId); }                  // 第 69 行：protected

    @NotNull
    public static GTRegistrate create(String modId) {                       // 第 82 行
        return new GTRegistrate(modId);
    }

    public void registerRegistrate() {                                      // 第 86 行
        registerEventListeners(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // 机器（最简版）
    public MachineBuilder<MachineDefinition> machine(String name,
            Function<IMachineBlockEntity, MetaMachine> metaMachine) {       // 第 139 行
        return new MachineBuilder<>(this, name, MachineDefinition::new, metaMachine,
                MetaMachineBlock::new, MetaMachineItem::new, MetaMachineBlockEntity::new);
    }

    // 多方块（最简版）
    public MultiblockMachineBuilder multiblock(String name,
            Function<IMachineBlockEntity, ? extends MultiblockControllerMachine> metaMachine) {  // 第 154 行
        return new MultiblockMachineBuilder(this, name, metaMachine,
                MetaMachineBlock::new, MetaMachineItem::new, MetaMachineBlockEntity::new);
    }

    // 物品：继承自 AbstractRegistrate → item(String name, ...)
    public <T extends Block> GTBlockBuilder<T, GTRegistrate> block(String name, ...)   // 第 175 行

    public static IGTFluidBuilder fluid(GTRegistrate parent, Material material, String name, String langKey,
            ResourceLocation stillTexture, ResourceLocation flowingTexture)          // 第 73 行
    public IGTFluidBuilder createFluid(String name, String langKey, Material material, ...)  // 第 124 行
    public SoundEntryBuilder sound(String name) / sound(ResourceLocation name)        // 第 160/164 行
    public RegistryEntry<CreativeModeTab> creativeModeTab()                           // 第 196 行
    public void creativeModeTab(Supplier<RegistryEntry<CreativeModeTab>> currentTab)  // 第 200 行
}
```

- `machine` 还有 6 参数的“全量版”（第 129 行），可自定义 `definitionFactory` / `blockFactory` / `itemFactory` / `blockEntityFactory`。
- **GT 自己的 `REGISTRATE` 单例位置**：`com.gregtechceu.gtceu.common.registry.GTRegistration`（**不是** `common/data/GTRegistration.java`，也不是 `api/registry/GTRegistration.java`——这两条路径在 7.3.0 都不存在）。该类全文（17 行）为：

```java
package com.gregtechceu.gtceu.common.registry;

public class GTRegistration {
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GTCEu.MOD_ID);
    static {
        GTRegistration.REGISTRATE.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
    }
    private GTRegistration() {/**/}
}
```

- **addon 不应复用 GT 的 `REGISTRATE`**（它的 modId 是 `gtceu`），必须 `GTRegistrate.create("your_mod_id")`，否则注册会落到 `gtceu` 命名空间。
- `GTRegistrate` **没有覆写 `item(...)`**：物品注册用的是 tterrag Registrate 的 `AbstractRegistrate#item(...)`（因此 `.item("my_item", Item::new).lang(...).register()` 这种写法可用，但与 GT 的 `machine(...)` 不是同一套 builder）。

### 10.2 `MachineBuilder`（`api/registry/registrate/MachineBuilder.java`，本地 623 行）

链式 API（全部 public，返回 `MachineBuilder<DEFINITION>`）：

| 方法 | 行号 |
| --- | --- |
| `MachineBuilder(GTRegistrate registrate, String name, ...)` | 188 |
| `recipeType(GTRecipeType)` / `recipeTypes(GTRecipeType...)` | 204 / 218 |
| `simpleModel(ResourceLocation)` / `defaultModel()` | 246 / 250 |
| `tieredHullModel(ResourceLocation)` | 254 |
| `overlayTieredHullModel(String)` / `(ResourceLocation)` | 258 / 263 |
| `colorOverlayTieredHullModel(String)` / `(String, ...)` / `(ResourceLocation)` | 267 / 271 / 283 |
| `overlaySteamHullModel(String)` / `colorOverlaySteamHullModel(String, ...)` | 295 / 305 |
| `workableTieredHullModel(ResourceLocation)` | 344 |
| `simpleGeneratorModel(ResourceLocation)` | 349 |
| `workableSteamHullModel(boolean isHighPressure, ResourceLocation)` | 354 |
| `workableCasingModel(ResourceLocation baseCasing, ResourceLocation workableModel)` | 359 |
| `sidedOverlayCasingModel(...)` / `sidedWorkableCasingModel(...)` | 364 / 369 |
| `appearanceBlock(Supplier<? extends Block>)` | 375 |
| `tooltips(Component...)` / `tooltips(List<...>)` / `conditionalTooltip(...)` | 380 / 384 / 389 |
| `abilities(PartAbility...)` | 399 |
| `modelProperty(Property<?>)` / `modelProperties(...)` / `removeModelProperty(...)` / `clearModelProperties()` | 404 / 431 / 449 / 454 |
| `recipeModifier(RecipeModifier)` / `(RecipeModifier, boolean alwaysTryModifyRecipe)` / `recipeModifiers(...)` / `noRecipeModifier()` | 459 / 465 / 470 / 480 |
| `addOutputLimit(RecipeCapability<?>, int)` | 486 |
| `multiblockPreviewRenderer(boolean, ...)` | 491 |
| **`register()`** | **533** —— 终端方法，`public DEFINITION register()`，内部把机器注册进 `GTRegistries.MACHINES` |

**重要**：`MachineBuilder` **没有任何静态工厂方法**（没有 `create` / `createMultiblock`）。唯一入口是 **public 构造器（第 188 行）** 与 **`GTRegistrate#machine(...)` / `#multiblock(...)`**。多方块用 **`MultiblockMachineBuilder`**（`api/registry/registrate/MultiblockMachineBuilder.java`，509 行），额外提供 `.pattern(...)` / `.shapeInfo(...)` / `.appearanceBlock(...)` / `.partAppearance(...)` / `.allowFlip(...)`；若未设置 pattern，`register()` 会抛 `IllegalStateException("missing pattern while creating multiblock ")`。

### 10.3 最小可用片段（**直接从 7.3.0 源码改写，已验证 API 存在**）

参照 `common/data/machines/GTAEMachines.java:18-30` 与 `GTMachineUtils.java:169-181`：

```java
// ---- 你的 addon 主类/注册类 ----
public static final GTRegistrate ADDON_REGISTRATE = GTRegistrate.create("my_addon");
// 在 mod 构造器里：ADDON_REGISTRATE.registerRegistrate();

// ---- 注册一个单方块机器（自定义机器类）----
public static final MachineDefinition[] MY_MACHINE = GTMachineUtils.registerSimpleMachine(
        ADDON_REGISTRATE, "my_machine", "My Machine", MY_RECIPE_TYPE, ...);
// registerSimpleMachine 内部实际是：
//   (holder, tier) -> new SimpleTieredMachine(holder, tier, tankScalingFunction)
//   然后 MachineBuilder.recipeType(...).editableUI(...).tier(tier).register()

// ---- 注册一个多方块部件（最贴近 ME 总线部件的写法）----
public static final MachineDefinition MY_PART = ADDON_REGISTRATE
        .machine("my_part", MyPartMachine::new)          // MyPartMachine(IMachineBlockEntity, Object...)
        .langValue("My Part")
        .tier(GTValues.LuV)
        .rotationState(RotationState.ALL)
        .abilities(PartAbility.IMPORT_ITEMS)
        .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
        .tooltips(Component.translatable("block.my_addon.my_part.tooltip"))
        .register();

// ---- 注册一个多方块控制器 ----
public static final MultiblockMachineDefinition MY_MULTI = ADDON_REGISTRATE
        .multiblock("my_multi", MyMultiMachine::new)
        .rotationState(RotationState.ALL)
        .recipeType(MY_RECIPE_TYPE)
        .register();

// ---- 注册一个物品（沿用 Registrate 的 item builder）----
public static final ItemEntry<Item> MY_ITEM = ADDON_REGISTRATE
        .item("my_item", Item::new)
        .lang("My Item")
        .register();
```

佐证（逐字来自 `GTAEMachines.java`）：

```java
public final static MachineDefinition ITEM_IMPORT_BUS_ME = REGISTRATE
        .machine("me_input_bus", MEInputBusPartMachine::new)
        .langValue("ME Input Bus")
        .tier(EV)
        .rotationState(RotationState.ALL)
        .abilities(PartAbility.IMPORT_ITEMS)
        .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
        .tooltips(...)
        .register();
```

以及 `GTMachineUtils.java:169-181`（单方块机器的真实注册路径）：

```java
(holder, tier) -> new SimpleTieredMachine(holder, tier, tankScalingFunction), (tier, builder) -> {
    builder.langValue(...)
           .recipeType(recipeType)
           .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(GTCEu.id(name), recipeType))
           ...
}
```

**关于注册时机（GT 自身的做法，addon 可照抄）**：`GTAEMachines` 的所有 `MachineDefinition` 都是 **static 字段初始化时**完成 `REGISTRATE.machine(...).register()` 的（`GTAEMachines.java:18-134`），而它的 `public static void init() {}` 是**空方法**（第 136 行）——用来强制类加载。`GTMachines.init()` 只在 **AE2 已加载**时才调用 `GTAEMachines.init()`。addon 若注册依赖其它 mod 的机器，也应采用同样的“静态字段 + 空 `init()` 触发类加载 + 条件调用”模式。

### 10.4 文档不一致警告

官方 1.20.1 文档 <https://gregtechceu.github.io/GregTech-Modern/1.20.1/Modpacks/Examples/Custom-Machines/> 给出的示例是：

```java
public static final MachineDefinition[] TEST_ELECTRIC = new SimpleMachineBuilder(ADDON_REGISTRATE, "extractor",
        AddonRecipeTypes.TEST_RECIPE_TYPE)
        .tiers(GTValues.LV, GTValues.MV, GTValues.HV)
        .tankScalingFunction(tier -> tier * 3200)
        .register();
```

**`SimpleMachineBuilder` 在 7.3.0 标签下不存在**（`api/registry/registrate/` 目录中只有 `GTRegistrate`、`MachineBuilder`、`MultiblockMachineBuilder` 等，没有 `SimpleMachineBuilder`；单方块机器统一用 `GTMachineUtils.registerSimpleMachine/registerSimpleSteamMachines/registerSimpleGenerator`）。该文档对应的是**更新的 1.20.1 分支**，请勿照抄到 7.3.0。**【已核实】**

---

## 11. Mixin 可行性

### 11.1 基本信息

| 项目 | 值 |
| --- | --- |
| mod id | `gtceu` |
| 根包 | `com.gregtechceu.gtceu` |
| GTM 自身 Mixin 配置 | `gtceu.mixins.json`（`"package": "com.gregtechceu.gtceu.core.mixins"`，`"plugin": "com.gregtechceu.gtceu.core.mixins.GTMixinPlugin"`，`compatibilityLevel: JAVA_17`，`injectors.defaultRequire = 1`，`maxShiftBy = 5`） |
| GTM 的 Mixin 类存放位置 | `com/gregtechceu/gtceu/core/mixins/**`（含 `client`、`emi`、`forge`、`jei`、`registrate`、`rei`、`ldlib`、`top` 等子包） |
| `required` | `true`（即 GTM 的 Mixin 强制生效） |
| AT 文件 | `src/main/resources/META-INF/accesstransformer.cfg`（内容仅涉及原版类，**没有任何 GT 自身类的 AT**——addon 不能指望 GT 类被放宽可见性） |

### 11.2 目标类的修饰符与可 Mixin 性

| 类 | 完整名 | 修饰符 | Java 继承 | Mixin | 备注 |
| --- | --- | --- | --- | --- | --- |
| `MEPatternBufferPartMachine` | `com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine` | **`public`，非 `final`** | ✅ 可继承 | ✅ | 有 `public MEPatternBufferPartMachine(IMachineBlockEntity holder, Object... args)` 构造器 |
| `MEPatternBufferPartMachine.InternalSlot` | `…MEPatternBufferPartMachine$InternalSlot` | **`public`，非 `final`，非 `static`（内部类！）** | ✅ 可继承（但需 outer 实例） | ⚠️ 需用 `@Mixin(targets = "…MEPatternBufferPartMachine$InternalSlot")` | **非静态内部类**：访问外部实例要当心 `this$0` 合成字段；建议优先 Mixin 外层类 |
| `MEBusPartMachine` | `…integration.ae2.machine.MEBusPartMachine` | **`public abstract`** | ✅ 可继承 | ✅ | |
| `ItemBusPartMachine` | `…common.machine.multiblock.part.ItemBusPartMachine` | **`public`，非 `final`** | ✅ | ✅ | |
| `InternalSlotRecipeHandler` | `…integration.ae2.machine.trait.InternalSlotRecipeHandler` | **`public final`** | ❌ 不能继承 | ✅ | 只能 Mixin 或整体替换 |
| `InternalSlotRecipeHandler.SlotRHL` | `…InternalSlotRecipeHandler$SlotRHL` | **`protected static`，非 `final`** | ⚠️ 同包/子类可见 | ✅（推荐） | **static 嵌套 → Mixin 最省事** |
| `InternalSlotRecipeHandler.SlotItemRecipeHandler` | `…InternalSlotRecipeHandler$SlotItemRecipeHandler` | **`private static`，非 `final`** | ❌ | ✅（需 `targets` 字符串，且只能按公共父类型引用） | |
| `InternalSlotRecipeHandler.SlotFluidRecipeHandler` | 同上 | **`private static`** | ❌ | ✅ | |
| `ProxySlotRecipeHandler` | `…integration.ae2.machine.trait.ProxySlotRecipeHandler` | **`public final`** | ❌ | ✅ | 其内部 `ProxyRHL` / `ProxyItemRecipeHandler` / `ProxyFluidRecipeHandler` 都是 **private static** |
| `RecipeLogic` | `…api.machine.trait.RecipeLogic` | **`public`，非 `final`** | ✅ | ✅ | |
| `RecipeHandlerList` | `…api.machine.trait.RecipeHandlerList` | **`public`，非 `final`** | ✅ | ✅ | 构造器 `protected` |
| `NotifiableItemStackHandler` | `…api.machine.trait.NotifiableItemStackHandler` | **`public`，非 `final`** | ✅ | ✅ | |
| `WorkableMultiblockMachine` | `…api.machine.multiblock.WorkableMultiblockMachine` | **`public abstract`** | ✅ | ✅ | |
| `SimpleTieredMachine` / `WorkableTieredMachine` | `…api.machine.SimpleTieredMachine` / `…api.machine.WorkableTieredMachine` | **`public` / `public abstract`** | ✅ | ✅ | |

### 11.3 推荐 Mixin 目标与精确描述符

> 描述符中的类型全部按 `Mixin` 的 `method = "..."`（名称 + 参数类型列表，JVM 内部名以 `/` 分隔）书写。

#### 目标 1：`MEPatternBufferPartMachine.pushPattern`（记录/拦截推送）
```
method = "pushPattern"
descriptor = "(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"
```
- `public boolean pushPattern(IPatternDetails, KeyCounter[])`
- 用途：需求 (d) 需要缓存 `KeyCounter[]`；或在 push 时按样板写入 per-slot 电路状态。

#### 目标 2：`RecipeLogic.onRecipeFinish`（合成结算边沿 —— **最佳单点**）
```java
@Mixin(RecipeLogic.class)
public abstract class RecipeLogicMixin {
    @Inject(method = "onRecipeFinish", at = @At("TAIL"))
    private void gtam$afterFinish(CallbackInfo ci) { ... }
}
```
```
method = "onRecipeFinish"
descriptor = "()V"
```
- `public void onRecipeFinish()`（`api/machine/trait/RecipeLogic.java:484`）
- 在 `TAIL` 处，本次产出已由 `handleRecipeIO(lastRecipe, IO.OUT)` 写入，可立即读取 `getLastRecipe()`（`@Getter public`）判断 chanched 产出是否齐备。
- 若想在**产出前**做判定，用 `at = @At("HEAD")`。

#### 目标 3：`InternalSlotRecipeHandler$SlotRHL` 构造器（per-slot 电路注入）
```
method = "<init>"
descriptor = "(Lcom/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine;Lcom/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine$InternalSlot;I)V"
```
- `protected SlotRHL(MEPatternBufferPartMachine buffer, InternalSlot slot, int idx)`
- 建议 `@Inject(at = @At("RETURN"))` + `@ModifyVariable`/`@Redirect` 拿参数；或改为**整体 `@Redirect` 掉 `RecipeHandlerList#addHandlers` 调用**（`(Lcom/gregtechceu/gtceu/api/machine/trait/RecipeHandlerList;Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeHandler;[Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeHandler;)V` 之类）以替换电路 handler。

#### 目标 4：`InternalSlotRecipeHandler.SlotItemRecipeHandler.handleRecipeInner`（感知“本槽本次扣了哪些原料”）
```
method = "handleRecipeInner"
descriptor = "(Lcom/gregtechceu/gtceu/api/capability/recipe/IO;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;Ljava/util/List;Z)Ljava/util/List;"
```
- `public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate)`

#### 目标 5：`MEPatternBufferPartMachine.onPatternChange`（换样板时的清理，可选）
```
method = "onPatternChange"
descriptor = "(I)V"
```
- **`private void onPatternChange(int index)`** —— private 方法仍可被 Mixin 注入（`@Inject` 可用），但不能被 `@Override`。

#### 目标 6：`MEPatternBufferPartMachine.getRecipeHandlers`（替换整个 RHL 列表 —— 风险最低的“大改”入口）
```
method = "getRecipeHandlers"
descriptor = "()Ljava/util/List;"
```
- `public List<RecipeHandlerList> getRecipeHandlers()`
- 用 `@Inject(at = @At("RETURN"), cancellable = true)` + `cir.setReturnValue(myList)`，可以**完全接管** 27 个槽的 handler 组成，而不必理解 `SlotRHL` 内部结构。**对需求 (c) 来说这是最容易实现、最不容易随版本崩的方案。**

#### 目标 7：`MEPatternBufferPartMachine$InternalSlot.refund` / `pushPattern` / `handleItemInternal`（可选）
```
refund            = "()V"
pushPattern       = "(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)V"
handleItemInternal= "(Ljava/util/List;Z)Ljava/util/List;"
```

### 11.4 其他 Mixin 注意事项

1. **GTM 的 mixin 配置 `"required": true` 且 `defaultRequire = 1`** —— 你的 addon 需要**自己的** mixin 配置（`your_mod.mixins.json`），不要试图往 `gtceu.mixins.json` 里加东西。
2. **不要 Mixin `gtceu` 的 `core.mixins` 包内已存在的类**（例如 `RecipeManagerMixin`、`IngredientAccessor`）；如需扩展，请在 GTM 的 mixin 之后加载（Mixin 优先级默认：你的 mod 的 mixin 在 `gtceu` 之后应用，`@Inject` 默认插在 GTM 注入点之后——若需要更早，显式设置 `priority`）。
3. **AE2 的类型在描述符中不可省略**：`pushPattern` 的参数是 AE2 的 `appeng.api.crafting.IPatternDetails` 与 `appeng.api.stacks.KeyCounter`，其 JVM 内部名为 `appeng/api/crafting/IPatternDetails` 与 `appeng/api/stacks/KeyCounter`。addon 需要 compileOnly 依赖 AE2（GTM 本身在 `integration.ae2` 包中直接依赖 AE2 API）。
4. **非静态内部类 `InternalSlot`**：Mixin 到一个非静态内部类时，Mixin 类本身应声明为 `public`（无需 `static` 匹配）；若需要访问外部实例，可用 `@Accessor` 取合成字段 `this$0`，或直接在 Mixin 里 `(MEPatternBufferPartMachine) (Object) this` **不行**，必须通过 `this$0`。**这也是推荐改用目标 3 / 目标 6 的原因。**
5. **`@Unique` 新字段与 LDLib2 SyncData**：GT 的机器字段用 `@Persisted`/`@DescSynced`（LDLib2）管理，`ManagedFieldHolder` 是在类初始化时静态构造的。Mixin 新增字段**不会**自动进入 sync/persist 系统；如果需要持久化 per-slot 电路，建议把数据放在：
   - 一个独立的、由 addon 自己用 Forge `Capability` / `SavedData` / `LevelChunk` NBT 管理的存储里；或
   - 追加到 `MEPatternBufferPartMachine` 已有的 `patternInventory`（`CustomItemStackHandler`）之外的**你自己新增的** `CustomItemStackHandler`（通过 Mixin 构造器创建 —— 但 `MachineTrait` 只能在机器创建期挂载，可行）。
6. **`InternalSlot.serializeNBT/deserializeNBT` 已存在**（public），per-slot 数据也可借道它的 NBT 扩展（用 `@Inject` 在 `serializeNBT` 的 `RETURN` 处往返回的 `CompoundTag` 里写自定义键，在 `deserializeNBT` 的 `HEAD` 处读回）。这是**不动 `ManagedFieldHolder` 的最小侵入持久化方案**。

---

## 附录 A：本次调研逐字保存的源码清单

根目录：`refsrc\gtceu\`。**全部文件均为 GitHub `v7.3.0-1.20.1` 标签下的原文。**

### A.1 任务要求的 23 个文件（全部完成）

| # | 相对路径（`refsrc\gtceu\` 之下） | 状态 |
| --- | --- | --- |
| 1 | `integration/ae2/machine/MEBusPartMachine.java` | ✅ |
| 2 | `integration/ae2/machine/MEInputBusPartMachine.java` | ✅ |
| 3 | `integration/ae2/machine/MEOutputBusPartMachine.java` | ✅ |
| 4 | `integration/ae2/machine/MEPatternBufferProxyPartMachine.java` | ✅ |
| 5 | `integration/ae2/machine/trait/InternalSlotRecipeHandler.java` | ✅ |
| 6 | `integration/ae2/machine/trait/GridNodeHostTrait.java` | ✅ |
| 7 | `integration/ae2/machine/feature/IGridConnectedMachine.java` | ✅ |
| 8 | `integration/ae2/utils/AEUtil.java` | ✅ |
| 9 | `api/recipe/ingredient/IntCircuitIngredient.java` | ✅ |
| 10 | `common/item/IntCircuitBehaviour.java` | ✅ |
| 11 | `api/recipe/GTRecipe.java` | ✅ |
| 12 | `api/recipe/content/Content.java` | ✅ |
| 13 | `api/machine/trait/RecipeLogic.java` | ✅ |
| 14 | `api/machine/trait/NotifiableItemStackHandler.java` | ✅ |
| 15 | `api/machine/trait/RecipeHandlerList.java` | ✅（原任务猜测的 `api/machine/trait/recipe/` 是错的，正确路径就是这个） |
| 16 | `common/data/machines/GTAEMachines.java` | ✅ |
| 17 | `api/capability/recipe/ItemRecipeCapability.java` | ✅ |
| 18 | `api/recipe/RecipeHelper.java` | ✅ |
| 19 | `common/machine/multiblock/part/ItemBusPartMachine.java` | ✅ |
| 20 | `api/machine/MetaMachine.java` | ✅ |
| 21 | `api/machine/feature/IRecipeLogicMachine.java` | ✅ |
| 22 | `api/machine/feature/multiblock/IMultiController.java` | ✅ |
| 23 | `common/data/GTItems.java` | ⚠️ **源码未能完整取得**，仅存 `common/data/GTItems.partial.txt`（100001 字节 / 1637 行，约全文 61.5%；全文 162503 字节 / blob sha `759188ce…`）。因 `web_fetch` 传输层 ≈100 KB 硬上限而被截断，`PROGRAMMED_CIRCUIT` 声明位于不可达尾部。**但该物品的注册 id 已用同版本 jar 的 `GTItems.class` 常量池独立核实为 `gtceu:programmed_circuit`**（见第 1.1 节 / 第 0 节）。 |

### A.2 额外抓取的支撑文件（用于保证第 2-11 节结论正确）

`integration/ae2/machine/MEPatternBufferPartMachine.java`、`integration/ae2/machine/trait/GridNodeHolder.java`、`integration/ae2/machine/trait/ProxySlotRecipeHandler.java`、`integration/ae2/machine/feature/multiblock/IAutoPullPart.java`、`integration/ae2/machine/feature/multiblock/IMEStockingPart.java`、 `api/machine/trait/{NotifiableFluidTank,NotifiableRecipeHandlerTrait,IRecipeHandlerTrait,MachineTrait,ICapabilityTrait,RecipeHandlerGroup,RecipeHandlerGroupColor,RecipeHandlerGroupDistinctness}.java`、 `api/machine/feature/{IHasCircuitSlot,IRecipeLogicMachine}.java`、`api/machine/feature/multiblock/{IDistinctPart,IMultiPart,IWorkableMultiController,IDisplayUIMachine,IFluidRenderMulti,IMaintenanceMachine,IMufflerMachine,IMufflerMechanic,IRotorHolderMachine}.java`、 `api/machine/{MetaMachine,MachineDefinition,MultiblockMachineDefinition,WorkableTieredMachine,SimpleTieredMachine}.java`、`api/machine/multiblock/WorkableMultiblockMachine.java`、`api/machine/fancyconfigurator/CircuitFancyConfigurator.java`、`api/machine/trait/ICapabilityTrait.java`、 `api/recipe/{GTRecipeType,RecipeHelper,RecipeRunner,ActionResult}.java`、`api/recipe/lookup/GTRecipeLookup.java`、`api/recipe/content/*`（12 个）、`api/recipe/ingredient/*`（7 个：`EnergyStack`、`FluidContainerIngredient`、`FluidIngredient`、`IntCircuitIngredient`、`IntProviderFluidIngredient`、`IntProviderIngredient`、`SizedIngredient`）、`api/recipe/chance/logic/ChanceLogic.java`、`api/recipe/chance/boost/ChanceBoostFunction.java`、 `api/capability/recipe/{IO,RecipeCapability,ItemRecipeCapability,EURecipeCapability,IRecipeCapabilityHolder,IRecipeHandler,IFilteredHandler}.java`、 `api/registry/GTRegistries.java`、`api/registry/GTRegistry.java`、`api/registry/registrate/{GTRegistrate,MachineBuilder,MultiblockMachineBuilder,BuilderBase,IGTFluidBuilder}.java`、`api/transfer/item/CustomItemStackHandler.java`、`api/GTCEuAPI.java`、 `common/registry/GTRegistration.java`、`common/data/GTMachines.java`、`common/data/machines/GTMachineUtils.java`、`common/machine/multiblock/electric/{FluidDrillMachine,MultiblockTankMachine}.java`、`data/recipe/builder/GTRecipeBuilder.java`。

> **完整性校验（重要）**：除 `GTItems.java` 外，所有文件均通过**字节数比对**（本地字节数 == GitHub API `size`，例如 `MEPatternBufferPartMachine.java` = 28110、`MEBusPartMachine.java` = 3043、`GTMachines.java` = 70957、`MachineBuilder.java` = 33033、`GTRegistrate.java` = 11739、`GTMachineUtils.java` = 60776）与**git blob SHA1 比对**（本地按 `"blob " + len + "\0" + content` 计算并与 GitHub trees API 的 sha 比较，抽查 27/27 完全一致）确认既未截断也未重排；另已复核关键文件**无 UTF-8 BOM、无 CRLF**。
>
> ⚠️ 本次抓取过程中曾发生一次**并发写入事故**：`data/recipe/builder/GTRecipeBuilder.java` 一度被写成带 UTF-8 BOM + CRLF 的 69589 字节版本，`api/machine/feature/IRecipeLogicMachine.java` 一度丢失 2 个缩进空格。二者**均已修复回上游 blob SHA**（`GTRecipeBuilder.java` 现为 67862 字节、无 BOM、无 CRLF）。若后续还有人往同一目录写入，请对本文档引用的行号**重新校验一次**（尤其 `GTRecipeBuilder.java`：本报告的行号已按修复后的 67862 字节版本校正）。
>
> `common/data/GTItems.java.partial.txt` 是**连续、未修改的前缀**（已用中段地标交叉验证：`createFluidCell` L398、`ELECTRIC_PUMP_LV` L724、`CONVEYOR_MODULE_LV` L1012、`ROBOT_ARM` L1203+、`FIELD_GENERATOR` L1321+、`EMITTER` L1373+、`SENSOR` L1413+、`VACUUM_TUBE` L1627），可安全用于查证该文件前 1637 行的内容。

同时抓取（未落盘，仅用于本报告）：
- `src/main/resources/gtceu.mixins.json`（Mixin 包名 / 插件 / injectors）。
- `src/main/resources/META-INF/accesstransformer.cfg`（确认 **GT 没有为自己任何类开 AT**）。
- `gradle.properties`（mod id / 版本 / group）。
- 官方文档站 3 个页面（Home、Development 索引、`Recipe-Logic/Recipe-Searching`、`Modpacks/Examples/Custom-Machines`）——**已标注其滞后/超前于 7.3.0 之处**。
- MC 百科编程电路词条（仅用于交叉验证 `gtceu:programmed_circuit` 这一 id，属外部佐证）。

---

## 附录 B：明确的“不存在 / 不要依赖”清单（7.3.0 负向结论）

以下名字在本报告中被提及过，但在 **7.3.0 标签下不存在**，请勿写进代码：

| 名称 | 结论 |
| --- | --- |
| `GTRecipe#itemInputs` / `#itemOutputs` | ❌ 不存在。只有 `inputs` / `outputs` / `tickInputs` / `tickOutputs`（`Map<RecipeCapability<?>, List<Content>>`）。`itemInputs`/`itemOutputs` 只存在于 `GTRecipeBuilder`。 |
| `Content#getChance()` / `#getMaxChance()` | ❌ 不存在。`chance` / `maxChance` / `tierChanceBoost` 是 public final 字段；只有 `getContent()` 是 getter（`@Getter` 只标注在 `content` 字段上）。 |
| `RecipeCapability#getChance(...)` / `#getMaxChance()` | ❌ 不存在。概率数据只在 `Content` 上；逻辑经 `GTRecipe#getChanceLogicForCapability(cap, io, isTick)` + `ChanceBoostFunction` / `ChanceLogic` 解析。 |
| `RecipeCapability#doMatch(...)` | ❌ 不存在。接近的是 `public boolean doMatchInRecipe()`（`RecipeCapability.java:130`，默认 `true`，`ItemRecipeCapability` 未覆写）。 |
| `ItemRecipeCapability#handle(...)` | ❌ 不存在。内容处理委托给 `IRecipeHandler#handleRecipe(IO, GTRecipe, List, boolean)`。 |
| `RecipeLogic#getRecipeType()` | ❌ 不存在。请用 `IRecipeLogicMachine#getRecipeType()`。 |
| `IRecipeLogicMultiblock` | ❌ 不存在于 7.3.0。**它的实际对应物是 `api/machine/feature/multiblock/IWorkableMultiController`**，而该类全文只有 5 行、自身不声明任何方法：`public interface IWorkableMultiController extends IMultiController, IRecipeLogicMachine {}` —— 即多方块控制器通过**多继承这两个接口**获得配方逻辑能力（`WorkableMultiblockMachine implements IWorkableMultiController`）。`api/machine/feature/multiblock/` 下实际只有：`IDisplayUIMachine`、`IDistinctPart`、`IFluidRenderMulti`、`IMaintenanceMachine`、`IMufflerMachine`（+`IMufflerMechanic`）、`IMultiController`、`IMultiPart`、`IRotorHolderMachine`、`IWorkableMultiController`。多方块的配方逻辑实现在 `WorkableMultiblockMachine`（`api/machine/multiblock/`）。 |
| `IPartAbility` / `IMultiblockPartMachine` | ❌ 二者在 7.3.0 都不存在。部件抽象是 `api/machine/feature/multiblock/IMultiPart`；部件“能力”通过 `api/machine/multiblock/PartAbility` 枚举 + `MachineBuilder#abilities(PartAbility...)` 表达。 |
| `api/machine/WorkableMachine` / `common/machine/tiered/WorkableTieredMachine` | ❌ 都不存在；**没有 `common/machine/tiered/` 目录**（`common/machine` 下只有 `electric/`、`multiblock/`、`owner/`、`steam/`、`storage/`、`trait/`）。正确路径是 `api/machine/WorkableTieredMachine.java`。 |
| `api/machine/feature/IWorkableMachine` | ❌ 不存在。 |
| `api/machine/feature/IDistinctPart` | ❌ 路径错误，正确为 `api/machine/feature/multiblock/IDistinctPart.java`。 |
| `api/capability/recipe/ICapabilityTrait` | ❌ 路径错误，正确为 `api/machine/trait/ICapabilityTrait.java`。 |
| `GTRecipeBuilder#circuit(int)` | ❌ 不存在。正确方法是 **`circuitMeta(int configuration)`**（第 718 行）。 |
| `GTRecipe#getChance(...)` / `#getMaxChance(...)` | ❌ 不存在（`GTRecipe` 上没有任何概率 getter）。最大值在 `Content.maxChance` 与 `ChanceLogic.getMaxChancedValue()`（10000）。 |
| `GTRecipeLookup#removeRecipe(...)` | ❌ 不存在，只有 `removeAllRecipes()`（第 457 行）。 |
| `GTRecipeType#getRecipeManager()` / `#find(...)` / `#getRecipesFor(...)` | ❌ 都不存在。`getRecipeManager()` 只在 `RecipeLogic:191`；查找走 `GTRecipeType#getLookup()` → `GTRecipeLookup#find/findRecipe/getRecipeIterator`。 |
| `api/recipe/GTRecipeLookup.java` | ❌ 路径错误。正确为 `api/recipe/lookup/GTRecipeLookup.java`。 |
| `api/machine/trait/recipe/RecipeHandlerList.java` | ❌ 路径错误。正确为 `api/machine/trait/RecipeHandlerList.java`。 |
| `api/recipe/ingredient/CircuitIngredient` / `ItemIngredient` / `TagIngredient` | ❌ 不存在（是旧版 GTCEu 的命名）。7.3.0 的 `api/recipe/ingredient/` 只有 7 个类，见附录 A.2。 |
| `SimpleMachineBuilder`（文档中的类） | ❌ 7.3.0 不存在。用 `GTMachineUtils.registerSimpleMachine(...)` 或 `GTRegistrate#machine(...)` + `MachineBuilder`。 |
| `GTRecipeLookup#getRecipesFor(...)` / 任何“返回全部配方”的公开方法 | ❌ 不存在。可用 `GTRecipeType#searchRecipe(holder, canHandle)` / `getRecipesInCategory(GTRecipeCategory)`，或改为读取 `RecipeLogic#getLastRecipe()`。 |
| `GTRecipeType#getRecipeManager()` | ❌ 不存在。`RecipeLogic#getRecipeManager()` 返回的是**原版** `net.minecraft.world.recipe.RecipeManager`，**里面没有 GT 配方**。 |

---

## 附录 C：面向 5 条需求的实现建议速查

| 需求 | 建议落点 | 关键 API |
| --- | --- | --- |
| (a) 推物品 + 按样板设电路 | AE2 样板供应器贴到机器（非输出面）；设电路用 `IHasCircuitSlot#getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(n))`；推物品用 Forge `CapabilityItemHandler.ITEM_HANDLER`（或直接 `((WorkableTieredMachine) m).importItems`） | `MetaMachine.getMachine`、`IHasCircuitSlot`、`IntCircuitBehaviour.stack`、`NotifiableItemStackHandler#setStackInSlot/insertItem` |
| (b) 回收未消耗残留 | 轮询/监听合成结束（`RecipeLogic#onRecipeFinish` Mixin 或输出槽 `addChangedListener`），从机器的 `importItems`/`exportItems` 或 ME 输出总线取残留，再插回 ME（`MEStorage#insert` / `StorageHelper.poweredInsert`）；样板总成侧直接 `InternalSlot#refund()` | `RecipeLogic#getStatus/isActive/getLastRecipe`、`InternalSlot#refund()`、`MEBusPartMachine#actionSource` |
| (c) 每个样板槽独立电路 | **方案 1（推荐）**：Mixin `MEPatternBufferPartMachine#getRecipeHandlers()Ljava/util/List;` 的 `RETURN`，返回自己构造的 27 个 `RecipeHandlerList`，每个内嵌一个 per-slot 电路 handler。**方案 2**：Mixin `SlotRHL` 构造器替换 `buffer.getCircuitInventory()`。**方案 3**：完全自建一个部件实现 `IMultiPart`（零 Mixin，复用 `getInternalInventory()`） | `RecipeHandlerList.of(IO, IRecipeHandler...)`、`NotifiableRecipeHandlerTrait<Ingredient>`、`IFilteredHandler`、`InternalSlotRecipeHandler#getSlotHandlers()` |
| (d) 概率产出缺失时重推 | Mixin `MEPatternBufferPartMachine#pushPattern` 缓存 `KeyCounter[]`；在 `RecipeLogic#onRecipeFinish` 后比对 `getLastRecipe().getOutputContents(ItemRecipeCapability.CAP)` 的 chanced 项与实际产出，缺失则重新 `slot.pushPattern(...)` 或再次走 AE 请求 | `Content#isChanced()`、`GTRecipe#getOutputContents`、`InternalSlot#pushPattern`、`InternalSlot#refund()` |
| (e) 按 AE 任务需求处理概率产出 | 从 `IPatternDetails#getOutputs()` 取期望产出；结合 `Content.chance / Content.maxChance` 计算期望数量；用 `RecipeLogic#getChanceCaches()`（public getter，`Map<RecipeCapability<?>, Object2IntMap<?>>`）读取 GT 的概率累积缓存，判断“已经累计了多少概率”以决定补偿 | `Content.chance/maxChance`、`ChanceLogic#getMaxChancedValue()`（10000）、`RecipeLogic#getChanceCaches()`、`GTRecipe#getChanceLogicForCapability` |
