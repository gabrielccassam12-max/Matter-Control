package com.mattercontrol.matter;

import com.mattercontrol.config.MatterConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Every block a power creates (or removes) is registered here with the terrain it replaced, and put back later.
 * Constructions only overwrite air, water and replaceable plants; digging powers only touch blocks
 * without block entities and never unbreakable ones.
 */
public final class TempBlocks {
    private TempBlocks() {}

    private static final class Entry {
        ServerWorld world;
        BlockPos pos;
        BlockState original;
        BlockState placed;
        long expire;
    }

    private static final Map<String, Entry> MAP = new HashMap<>();

    private static String key(ServerWorld w, BlockPos p) {
        return w.getRegistryKey().getValue() + "@" + p.asLong();
    }

    public static boolean replaceable(BlockState s) {
        return s.isAir() || s.isLiquid() || s.isReplaceable();
    }

    public static boolean diggable(ServerWorld w, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity()) return false;
        float h = s.getHardness(w, pos);
        return h >= 0 && h <= 55;
    }

    public static boolean isTemp(ServerWorld w, BlockPos pos) {
        return MAP.containsKey(key(w, pos));
    }

    private static boolean entityInside(ServerWorld w, BlockPos pos) {
        return !w.getEntitiesByClass(LivingEntity.class, new Box(pos).contract(0.05), e -> true).isEmpty();
    }

    /** Places a construction block. Returns false if it could not (solid terrain, entity in the way, limit reached). */
    public static boolean place(ServerWorld w, BlockPos pos, BlockState state, int life) {
        if (MAP.size() >= MatterConfig.INSTANCE.maxTempBlocks || w.isOutOfHeightLimit(pos)) return false;
        BlockState cur = w.getBlockState(pos);
        Entry e = MAP.get(key(w, pos));
        if (e != null) {
            if (cur.equals(e.placed)) {
                e.expire = Math.max(e.expire, w.getTime() + life);
                return true;
            }
            MAP.remove(key(w, pos));
        }
        if (!replaceable(cur) || entityInside(w, pos)) return false;
        w.setBlockState(pos, state, Block.NOTIFY_ALL);
        Entry n = new Entry();
        n.world = w;
        n.pos = pos.toImmutable();
        n.original = cur;
        n.placed = state;
        n.expire = w.getTime() + life;
        MAP.put(key(w, pos), n);
        return true;
    }

    /** Temporarily removes a solid block (a pit). The original comes back when the time is up. */
    public static boolean dig(ServerWorld w, BlockPos pos, int life) {
        BlockState cur = w.getBlockState(pos);
        Entry e = MAP.get(key(w, pos));
        if (e != null && cur.equals(e.placed)) { // digging our own construction: remove it early
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            e.placed = Blocks.AIR.getDefaultState();
            e.expire = w.getTime() + life;
            return true;
        }
        if (MAP.size() >= MatterConfig.INSTANCE.maxTempBlocks || !diggable(w, pos, cur)) return false;
        w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        Entry n = new Entry();
        n.world = w;
        n.pos = pos.toImmutable();
        n.original = cur;
        n.placed = Blocks.AIR.getDefaultState();
        n.expire = w.getTime() + life;
        MAP.put(key(w, pos), n);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (MAP.isEmpty() || server.getTicks() % 4 != 0) return;
        Iterator<Map.Entry<String, Entry>> it = MAP.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            if (e.world.getTime() < e.expire) continue;
            if (!e.world.getChunkManager().isChunkLoaded(e.pos.getX() >> 4, e.pos.getZ() >> 4)) continue; // retry later
            restore(e);
            it.remove();
        }
    }

    private static void restore(Entry e) {
        BlockState cur = e.world.getBlockState(e.pos);
        boolean stillOurs = cur.equals(e.placed) || (e.placed.isAir() && replaceable(cur));
        if (stillOurs) e.world.setBlockState(e.pos, e.original, Block.NOTIFY_ALL);
    }

    public static void restoreAll() {
        for (Entry e : MAP.values()) {
            if (e.world.getChunkManager().isChunkLoaded(e.pos.getX() >> 4, e.pos.getZ() >> 4)) restore(e);
        }
        MAP.clear();
    }
}
