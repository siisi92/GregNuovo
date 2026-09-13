package com.gregnuovo.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

/**
 * 对 GTM 机器的读写：找相邻目标、读写电路槽、读写物品、判断是否在运行。
 *
 * <p>关键点：AE2 样板供应器推料的目标<b>不一定</b>是单方块机器，也可能是多方块零件
 * （输入总线 / ME输入总线 / 样板总成）。零件本身没有配方逻辑，配方在它所属的多方块控制器上。
 * 因此这里把目标拆成两部分：</p>
 * <ul>
 *     <li>{@code itemHost}：真正收到物品的那台机器/零件（退料与补料都在这里）；</li>
 *     <li>{@code workMachine}：真正跑配方的机器（单方块机器本身，或零件所属的多方块控制器）。</li>
 * </ul>
 *
 * <p>1.21.1 变化：物品栏接口从 {@code net.minecraftforge.items} 换成
 * {@code net.neoforged.neoforge.items}；AE 的存储能力从
 * {@code appeng.capabilities.Capabilities.STORAGE}（该类在 AE2 19.x 已删除）
 * 换成 NeoForge 的 {@code AECapabilities.ME_STORAGE}。</p>
 */
public final class MachineAccess {

    /** 一次推料的目标。 */
    public record Target(MetaMachine itemHost, MetaMachine workMachine, Direction side) {}

    /**
     * 找出供应器/样板总成旁边的 GT 目标。
     *
     * @param pushedKeys 已推送的物品键，用于在多个相邻方块中挑出真正收到料的那一个
     */
    @Nullable
    public static Target resolveTarget(Level level, BlockPos partPos, @Nullable Collection<AEKey> pushedKeys) {
        Target fallback = null;
        for (Direction dir : Direction.values()) {
            BlockPos pos = partPos.relative(dir);
            if (!level.isLoaded(pos)) continue;
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine == null) continue;

            MetaMachine work = workMachineOf(machine);
            boolean hasCircuitSlot = machine instanceof IHasCircuitSlot || work instanceof IHasCircuitSlot;
            if (work == null && !hasCircuitSlot) continue;

            Target target = new Target(machine, work == null ? machine : work, dir.getOpposite());
            if (fallback == null) fallback = target;

            if (pushedKeys != null && !pushedKeys.isEmpty()) {
                var handler = itemHandler(machine, target.side());
                if (handler != null && containsAny(handler, pushedKeys)) {
                    return target;
                }
                // 多方块：物品可能已经进了控制器里的某个零件
                if (work != null && work != machine) {
                    for (var part : partsOf(work)) {
                        var partHandler = itemHandler(part, target.side());
                        if (partHandler != null && containsAny(partHandler, pushedKeys)) {
                            return new Target(part, work, target.side());
                        }
                    }
                }
            }
        }
        return fallback;
    }

    /** 目标机器负责“跑配方”的那一台：单方块机器本身，或零件所属的多方块控制器。 */
    @Nullable
    public static MetaMachine workMachineOf(MetaMachine machine) {
        if (machine instanceof IRecipeLogicMachine) return machine;
        MetaMachine controller = controllerOfPart(machine);
        return controller;
    }

    /** 零件所属的多方块控制器（样板总成 / 输入总线 等）。 */
    @Nullable
    public static MetaMachine controllerOfPart(MetaMachine machine) {
        if (!(machine instanceof MultiblockPartMachine part)) return null;
        try {
            if (!part.isFormed()) return null;
            var controllers = part.getControllers();
            if (controllers == null || controllers.isEmpty()) return null;
            IMultiController controller = controllers.first();
            return controller.self();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 样板总成所属的多方块控制器。 */
    @Nullable
    public static MetaMachine controllerOf(MEPatternBufferPartMachine buffer) {
        try {
            if (!buffer.isFormed()) return null;
            MultiblockControllerMachine self = buffer.getControllers().first().self();
            return self;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 多方块控制器的所有零件。 */
    public static List<MetaMachine> partsOf(MetaMachine controller) {
        List<MetaMachine> parts = new ArrayList<>();
        if (!(controller instanceof IMultiController multiController)) return parts;
        try {
            for (var part : multiController.getParts()) {
                if (part instanceof MetaMachine meta) {
                    parts.add(meta);
                }
            }
        } catch (Throwable ignored) {}
        return parts;
    }

    /** 机器是否正在执行配方。 */
    public static boolean isWorking(@Nullable MetaMachine machine) {
        if (!(machine instanceof IRecipeLogicMachine logicMachine)) return false;
        RecipeLogic logic = logicMachine.getRecipeLogic();
        return logic != null && logic.getStatus() == RecipeLogic.Status.WORKING;
    }

    /** 写入（或清除）电路槽：优先写真正收到料的那个方块。 */
    public static boolean setCircuitOn(@Nullable Target target, int circuit) {
        if (target == null) return false;
        if (setCircuit(target.itemHost(), circuit)) return true;
        return target.workMachine() != target.itemHost() && setCircuit(target.workMachine(), circuit);
    }

    /** 写入（或清除）机器电路槽。 */
    public static boolean setCircuit(@Nullable MetaMachine machine, int circuit) {
        if (!(machine instanceof IHasCircuitSlot circuitSlot) || !circuitSlot.isCircuitSlotEnabled()) return false;
        try {
            ItemStack stack = circuit < 0 ? ItemStack.EMPTY
                    : IntCircuitBehaviour.stack(Math.min(circuit, IntCircuitBehaviour.CIRCUIT_MAX));
            circuitSlot.getCircuitInventory().setStackInSlot(0, stack);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 机器当前电路槽里的电路编号；-1 表示无电路。 */
    public static int getCircuit(@Nullable MetaMachine machine) {
        if (!(machine instanceof IHasCircuitSlot circuitSlot) || !circuitSlot.isCircuitSlotEnabled()) return -1;
        try {
            ItemStack stack = circuitSlot.getCircuitInventory().getStackInSlot(0);
            if (PatternAnalyzer.isCircuitStack(stack)) {
                return IntCircuitBehaviour.getCircuitConfiguration(stack);
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** 目标是否有可写电路槽。 */
    public static boolean hasCircuitSlot(@Nullable MetaMachine machine) {
        return machine instanceof IHasCircuitSlot circuitSlot && circuitSlot.isCircuitSlotEnabled();
    }

    /** 目标里当前生效的电路编号（itemHost 优先）。 */
    public static int getCircuitOn(@Nullable Target target) {
        if (target == null) return -1;
        int circuit = getCircuit(target.itemHost());
        if (circuit >= 0) return circuit;
        return target.workMachine() == target.itemHost() ? -1 : getCircuit(target.workMachine());
    }

    @Nullable
    public static IItemHandlerModifiable itemHandler(@Nullable MetaMachine machine, @Nullable Direction side) {
        if (machine == null) return null;
        try {
            var handler = machine.getItemHandlerCap(side, false);
            if (handler != null) return handler;
        } catch (Throwable ignored) {}
        try {
            var handler = machine.getItemHandlerCap(null, false);
            if (handler != null) return handler;
        } catch (Throwable ignored) {}
        for (Direction dir : Direction.values()) {
            try {
                var handler = machine.getItemHandlerCap(dir, false);
                if (handler != null) return handler;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean containsAny(IItemHandlerModifiable handler, Collection<AEKey> keys) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) continue;
            for (AEKey wanted : keys) {
                if (RecipeChanceResolver.sameKey(wanted, key)) return true;
            }
        }
        return false;
    }

    /**
     * 取回“本次推送但未被消耗”的余料。
     *
     * <p>依次尝试三条路径，先成功先算：</p>
     * <ol>
     *     <li><b>GT 内部处理槽</b>：直接读机器/零件的 {@code NotifiableItemStackHandler.storage}，
     *         不受能力与侧面权限限制（GT 的输入槽对外往往是只进不出的）；</li>
     *     <li><b>AE 存储</b>：ME输入总线这类方块的库存挂在 AE 网络上（{@code AECapabilities.ME_STORAGE}）；</li>
     *     <li><b>NeoForge 物品栏</b>：普通容器 / 其它 mod 的机器。</li>
     * </ol>
     *
     * @param pushed          本次推送的内容（键 -> 数量），取走多少就扣多少预算
     * @param notOurKeys      不应取回的键（例如样板的产物）
     * @param leftoverKeys    只取回这些键（GT 配方里的 notConsumable 输入）；为空表示不做限制
     * @param allowUnfiltered 过滤后一无所获时，是否允许退而取回所有已推送的余料
     */
    public static List<GenericStack> extractLeftovers(@Nullable MetaMachine machine, @Nullable ServerLevel level,
                                                      @Nullable BlockPos hostPos, @Nullable Direction side,
                                                      Map<AEKey, Long> pushed, Collection<AEKey> notOurKeys,
                                                      Collection<AEKey> leftoverKeys, boolean allowUnfiltered,
                                                      @Nullable IActionSource src) {
        List<GenericStack> result = new ArrayList<>();
        extractAll(machine, level, hostPos, side, pushed, notOurKeys, leftoverKeys, src, result);
        if (result.isEmpty() && allowUnfiltered && !leftoverKeys.isEmpty()) {
            // 保底：任务已经结束，退而取回所有“本次推送且还在机器里”的东西
            extractAll(machine, level, hostPos, side, pushed, notOurKeys, List.of(), src, result);
        }
        return result;
    }

    private static void extractAll(@Nullable MetaMachine machine, @Nullable ServerLevel level, @Nullable BlockPos hostPos,
                                   @Nullable Direction side, Map<AEKey, Long> pushed, Collection<AEKey> notOurKeys,
                                   Collection<AEKey> leftoverKeys, @Nullable IActionSource src,
                                   List<GenericStack> out) {
        // 1) GT 内部处理槽：直接读 storage，不受能力/侧面权限限制
        if (machine != null) {
            try {
                for (MachineTrait trait : machine.getTraits()) {
                    if (trait instanceof NotifiableItemStackHandler handler) {
                        extractFromStorage(handler.storage, pushed, notOurKeys, leftoverKeys, out);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2) AE 存储（ME输入总线 / 样板总成等把库存挂在 AE 网络上的方块）
        //    1.21.1（AE2 19.x）：appeng.capabilities.Capabilities 已删除，
        //    改用 NeoForge BlockCapability（AECapabilities.ME_STORAGE），也不再有 LazyOptional。
        if (level != null && hostPos != null && src != null) {
            try {
                MEStorage storage = BlockCapabilityCache
                        .create(AECapabilities.ME_STORAGE, level, hostPos, side)
                        .getCapability();
                if (storage != null) {
                    for (var entry : new ArrayList<>(pushed.entrySet())) {
                        if (skip(entry.getKey(), notOurKeys, leftoverKeys)) continue;
                        long budget = entry.getValue();
                        if (budget <= 0) continue;
                        long got;
                        try {
                            got = storage.extract(entry.getKey(), budget, Actionable.MODULATE, src);
                        } catch (Throwable t) {
                            got = 0;
                        }
                        if (got > 0) {
                            out.add(new GenericStack(entry.getKey(), got));
                            entry.setValue(budget - got);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3) NeoForge 物品栏
        if (machine != null) {
            try {
                extractFromStorage(itemHandler(machine, side), pushed, notOurKeys, leftoverKeys, out);
            } catch (Throwable ignored) {}
        }
    }

    private static boolean skip(AEKey key, Collection<AEKey> notOurKeys, Collection<AEKey> leftoverKeys) {
        if (isExcluded(key, notOurKeys)) return true;
        return !leftoverKeys.isEmpty() && !isExcluded(key, leftoverKeys);
    }

    private static void extractFromStorage(@Nullable IItemHandlerModifiable storage, Map<AEKey, Long> pushed,
                                           Collection<AEKey> notOurKeys, Collection<AEKey> leftoverKeys,
                                           List<GenericStack> out) {
        if (storage == null) return;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack inSlot = storage.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(inSlot);
            if (key == null) continue;
            if (skip(key, notOurKeys, leftoverKeys)) continue;

            long budget = 0;
            for (var entry : pushed.entrySet()) {
                if (RecipeChanceResolver.sameKey(entry.getKey(), key)) {
                    budget = entry.getValue();
                    break;
                }
            }
            if (budget <= 0) continue;

            int take = (int) Math.min(budget, inSlot.getCount());
            if (take <= 0) continue;
            ItemStack extracted = storage.extractItem(slot, take, false);
            if (extracted.isEmpty()) continue;

            out.add(new GenericStack(key, extracted.getCount()));
            for (var entry : pushed.entrySet()) {
                if (RecipeChanceResolver.sameKey(entry.getKey(), key)) {
                    entry.setValue(Math.max(0, entry.getValue() - extracted.getCount()));
                    break;
                }
            }
        }
    }

    private static boolean isExcluded(AEKey key, Collection<AEKey> notOurKeys) {
        for (AEKey excluded : notOurKeys) {
            if (RecipeChanceResolver.sameKey(excluded, key)) return true;
        }
        return false;
    }

    /** 往机器里插入物品（用于概率产物的重试补料）。 */
    public static boolean insertItem(@Nullable MetaMachine machine, @Nullable Direction side, ItemStack stack) {
        if (stack.isEmpty()) return true;
        IItemHandlerModifiable handler = itemHandler(machine, side);
        if (handler == null) return false;

        ItemStack remaining = stack;
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining.isEmpty();
    }

    /** 机器位置 -> 朝向供应器的那一面。 */
    @Nullable
    public static Direction sideFacing(BlockPos machinePos, BlockPos partPos) {
        for (Direction dir : Direction.values()) {
            if (machinePos.relative(dir).equals(partPos)) return dir;
        }
        return null;
    }

    private MachineAccess() {}
}
