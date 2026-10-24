package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.wraithhawit.rsapg.RSAPG;
import com.wraithhawit.rsapg.compat.Rsmbac;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Every place on a network a new pattern could go, with its free slots and what it feeds.
 *
 * <p>Refined Storage's autocrafter and Cable Tiers' tiered ones share a shape (private
 * {@code getChainingRoot()} / {@code getConnectedMachine()}, package-private
 * {@code getPatternContainer()}), so one reflective adapter covers both without depending on
 * Cable Tiers. rsmbac's structure goes through its own API ({@link Rsmbac}).
 */
public final class PatternTargets {
    private PatternTargets() {
    }

    public enum Kind {
        /** rsmbac multiblock autocrafter: internal patterns only, first choice for them. */
        RSMBAC,
        /** An autocrafter neither facing a machine nor chained to one that does. */
        FREE,
        /** An autocrafter feeding a machine (directly or through its chain). */
        MACHINE
    }

    /** A pattern destination. {@link #free} counts down as a plan assigns slots. */
    public static final class Target {
        public final Kind kind;
        public final ServerLevel level;
        public final BlockPos pos;
        /** Item id of the machine fed (MACHINE only). */
        @Nullable
        public final ResourceLocation machine;
        public final String name;
        public int free;

        Target(final Kind kind, final ServerLevel level, final BlockPos pos, @Nullable final ResourceLocation machine,
               final String name, final int free) {
            this.kind = kind;
            this.level = level;
            this.pos = pos;
            this.machine = machine;
            this.name = name;
            this.free = free;
        }

        /** Coordinates and dimension, for the preview's hover tooltip. */
        public String location() {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ() + " in " + level.dimension().location();
        }
    }

    public static List<Target> collect(final MinecraftServer server, final Network network) {
        final List<Target> targets = new ArrayList<>();
        final Set<GlobalPos> seen = new HashSet<>();
        for (final NetworkNodeContainer container : network.getComponent(GraphNetworkComponent.class).getContainers()) {
            if (!(container.getNode() instanceof PatternProviderNetworkNode)
                || !(container instanceof InWorldNetworkNodeContainer inWorld)) {
                continue;
            }
            final GlobalPos at = inWorld.getPosition();
            if (!seen.add(at)) {
                continue;
            }
            final ServerLevel level = server.getLevel(at.dimension());
            if (level == null || !level.isLoaded(at.pos())) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(at.pos());
            if (be == null) {
                continue;
            }
            try {
                final Target target = describe(level, be);
                if (target != null) {
                    targets.add(target);
                }
            } catch (final ReflectiveOperationException | RuntimeException e) {
                RSAPG.LOG.warn("tree: could not read pattern provider {} at {}: {}",
                    be.getClass().getName(), at.pos().toShortString(), e.toString());
            }
        }
        return targets;
    }

    @Nullable
    private static Target describe(final ServerLevel level, final BlockEntity be) throws ReflectiveOperationException {
        if (Rsmbac.isController(be)) {
            final String name = be instanceof Nameable nameable ? nameable.getDisplayName().getString()
                : be.getBlockState().getBlock().getName().getString();
            return new Target(Kind.RSMBAC, level, be.getBlockPos(), null, name.strip(),
                Rsmbac.freeSlots(level, be.getBlockPos()));
        }
        final Method patterns = method(be.getClass(), "getPatternContainer");
        final Method root = method(be.getClass(), "getChainingRoot");
        final Method connected = method(be.getClass(), "getConnectedMachine");
        if (patterns == null || root == null || connected == null) {
            return null;
        }
        final Container container = (Container) patterns.invoke(be);
        int free = 0;
        for (int i = 0; i < container.getContainerSize(); ++i) {
            if (container.getItem(i).isEmpty()) {
                free++;
            }
        }
        final Object chainRoot = root.invoke(be);
        final BlockEntity machine = (BlockEntity) connected.invoke(chainRoot);
        final String rawName = be instanceof Nameable nameable ? nameable.getDisplayName().getString()
            : Component.translatable(be.getBlockState().getBlock().getDescriptionId()).getString();
        final String name = rawName.strip();
        if (machine == null || isAutocrafter(machine)) {
            return new Target(Kind.FREE, level, be.getBlockPos(), null, name, free);
        }
        final ResourceLocation machineId = BuiltInRegistries.ITEM.getKey(machine.getBlockState().getBlock().asItem());
        return new Target(Kind.MACHINE, level, be.getBlockPos(), machineId, name, free);
    }

    /**
     * Puts an encoded pattern into the target's first free, accepting slot.
     *
     * @return false if the target is gone or has no room any more
     */
    public static boolean place(final Target target, final ItemStack pattern) {
        if (!target.level.isLoaded(target.pos)) {
            return false;
        }
        final BlockEntity be = target.level.getBlockEntity(target.pos);
        if (be == null) {
            return false;
        }
        if (target.kind == Kind.RSMBAC) {
            return Rsmbac.place(target.level, target.pos, pattern);
        }
        final Method patterns = method(be.getClass(), "getPatternContainer");
        if (patterns == null) {
            return false;
        }
        try {
            final Container container = (Container) patterns.invoke(be);
            for (int i = 0; i < container.getContainerSize(); ++i) {
                if (container.getItem(i).isEmpty() && container.canPlaceItem(i, pattern)) {
                    container.setItem(i, pattern);
                    container.setChanged();
                    return true;
                }
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            RSAPG.LOG.warn("tree: could not place a pattern in {} at {}: {}", be.getClass().getName(),
                target.pos.toShortString(), e.toString());
        }
        return false;
    }

    private static boolean isAutocrafter(final BlockEntity be) {
        return method(be.getClass(), "getChainingRoot") != null;
    }

    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getDeclaredMethod("hashCode");
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A no-arg method declared on the class or a superclass, made accessible; null if absent. */
    @Nullable
    private static Method method(final Class<?> type, final String name) {
        final Method found = METHODS.computeIfAbsent(type.getName() + "#" + name, key -> {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    final Method m = c.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (final NoSuchMethodException ignored) {
                    // keep looking up the hierarchy
                }
            }
            return NONE;
        });
        return found == NONE ? null : found;
    }
}
