package com.mattercontrol.matter;

import com.mattercontrol.MatterMod;
import com.mattercontrol.config.MatterConfig;
import com.mattercontrol.net.SyncPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side brain: awakening, absorbing blocks, the palette, power selection, costs, cooldowns and HUD sync. */
public final class MatterManager {
    private MatterManager() {}

    public static final AttachmentType<MatterData> DATA = AttachmentRegistry.<MatterData>builder()
            .persistent(MatterData.CODEC)
            .copyOnDeath()
            .buildAndRegister(MatterMod.id("matter"));

    private static final Map<UUID, Long> CD_END = new HashMap<>();
    private static final Map<UUID, Integer> CD_MAX = new HashMap<>();
    private static final Map<UUID, Long> LAST_ABSORB = new HashMap<>();

    public static MatterData get(ServerPlayerEntity p) {
        return p.getAttachedOrElse(DATA, MatterData.DEFAULT);
    }

    public static void set(ServerPlayerEntity p, MatterData d) {
        p.setAttached(DATA, d);
        sync(p);
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TempBlocks::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TempBlocks.restoreAll();
            CD_END.clear();
            CD_MAX.clear();
            LAST_ABSORB.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            CD_END.remove(id);
            CD_MAX.remove(id);
            LAST_ABSORB.remove(id);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
    }

    // ------------------------------------------------------------ helpers
    private static void note(ServerPlayerEntity p, String key, Formatting color, Object... args) {
        p.sendMessage(Text.translatable(key, args).formatted(color), true);
    }

    public static void sync(ServerPlayerEntity p) {
        MatterData d = get(p);
        long now = p.getWorld().getTime();
        int cd = (int) Math.max(0, CD_END.getOrDefault(p.getUuid(), 0L) - now);
        ServerPlayNetworking.send(p, new SyncPayload(d.awakened() ? d.matter() : -1, MatterConfig.INSTANCE.maxMatter,
                d.selected(), d.material(), cd, CD_MAX.getOrDefault(p.getUuid(), 1), new ArrayList<>(d.palette())));
    }

    private static boolean awake(ServerPlayerEntity p) {
        MatterData d = get(p);
        if (d.awakened()) return true;
        if (!MatterConfig.INSTANCE.requireCore || p.isCreative()) {
            awaken(p);
            return true;
        }
        note(p, "message.mattercontrol.not_awake", Formatting.RED);
        return false;
    }

    public static void awaken(ServerPlayerEntity p) {
        MatterData d = get(p);
        set(p, d.withAwakened(true).withMatter(Math.max(d.matter(), p.isCreative() ? MatterConfig.INSTANCE.maxMatter : 100)));
        ServerWorld w = (ServerWorld) p.getWorld();
        w.spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.9, 0.6, 0.6);
        w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                p.getX(), p.getY() + 0.3, p.getZ(), 40, 0.8, 0.2, 0.8, 0.2);
        w.playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 0.6f);
        note(p, "message.mattercontrol.awakened", Formatting.GOLD);
    }

    public static BlockState materialOf(MatterData d) {
        if (d.palette().isEmpty()) return Blocks.STONE.getDefaultState();
        String id = d.palette().get(Math.floorMod(d.material(), d.palette().size()));
        Block b = Registries.BLOCK.get(Identifier.of(id));
        if (b == Blocks.AIR || b instanceof net.minecraft.block.FallingBlock) b = Blocks.COBBLESTONE; // sand would fall away
        return b.getDefaultState();
    }

    // ------------------------------------------------------------ matter and palette
    /** Adds matter (capped) and remembers the block in the palette. Returns how much was really added. */
    public static int gain(ServerPlayerEntity p, int amount, BlockState from) {
        MatterData d = get(p);
        int max = MatterConfig.INSTANCE.maxMatter;
        int add = Math.max(0, Math.min(amount, max - d.matter()));
        List<String> pal = new ArrayList<>(d.palette());
        int material = d.material();
        if (from != null) {
            String id = Registries.BLOCK.getId(from.getBlock()).toString();
            if (!pal.contains(id)) {
                pal.add(id);
                if (pal.size() > 8) {
                    pal.remove(0);
                    material = Math.max(0, material - 1);
                }
            }
        }
        set(p, d.withMatter(d.matter() + add).withPalette(pal).withMaterial(material));
        return add;
    }

    public static boolean absorbable(ServerWorld w, BlockPos pos, BlockState s) {
        return TempBlocks.diggable(w, pos, s) && s.isFullCube(w, pos) && s.getHardness(w, pos) <= 25 && !TempBlocks.isTemp(w, pos);
    }

    // ------------------------------------------------------------ actions
    public static void absorb(ServerPlayerEntity p) {
        MatterConfig cfg = MatterConfig.INSTANCE;
        if (!cfg.allowAbsorb || !awake(p)) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        long now = w.getTime();
        if (now - LAST_ABSORB.getOrDefault(p.getUuid(), -100L) < cfg.absorbIntervalTicks) return;
        MatterData d = get(p);
        if (d.matter() >= cfg.maxMatter) {
            note(p, "message.mattercontrol.full", Formatting.YELLOW);
            return;
        }
        HitResult hit = p.raycast(cfg.absorbReach, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState s = w.getBlockState(pos);
        if (!absorbable(w, pos, s)) return;
        int gain = Math.max(3, Math.min(40, Math.round(4 + s.getHardness(w, pos) * 5)));
        if (!TempBlocks.dig(w, pos, cfg.blockLifetimeTicks)) return;
        LAST_ABSORB.put(p.getUuid(), now);
        gain(p, gain, s);

        // the block dissolves into a stream of matter flowing into the player
        BlockStateParticleEffect fx = new BlockStateParticleEffect(ParticleTypes.BLOCK, s);
        Vec3d from = Vec3d.ofCenter(pos);
        Vec3d to = p.getPos().add(0, 1.0, 0);
        for (int i = 0; i < 7; i++) {
            Vec3d q = from.lerp(to, i / 7.0);
            w.spawnParticles(fx, q.x, q.y, q.z, 2, 0.12, 0.12, 0.12, 0.02);
        }
        w.spawnParticles(ParticleTypes.ENCHANT, to.x, to.y, to.z, 3, 0.3, 0.4, 0.3, 0.3);
        w.playSound(null, pos, s.getSoundGroup().getBreakSound(), SoundCategory.PLAYERS, 0.5f, 0.55f);
    }

    public static void cycle(ServerPlayerEntity p, int dir) {
        if (!awake(p)) return;
        MatterData d = get(p);
        int n = Power.values().length;
        MatterData nd = d.withSelected(Math.floorMod(d.selected() + dir, n));
        set(p, nd);
        Power pw = Power.values()[nd.selected()];
        note(p, "message.mattercontrol.power", Formatting.GOLD, pw.displayName(), (int) (pw.cost * MatterConfig.INSTANCE.costMultiplier));
    }

    public static void cycleMaterial(ServerPlayerEntity p) {
        if (!awake(p)) return;
        MatterData d = get(p);
        if (d.palette().isEmpty()) {
            note(p, "message.mattercontrol.empty_palette", Formatting.YELLOW);
            return;
        }
        set(p, d.withMaterial((d.material() + 1) % d.palette().size()));
    }

    public static void cast(ServerPlayerEntity p) {
        MatterConfig cfg = MatterConfig.INSTANCE;
        if (!awake(p)) return;
        MatterData d = get(p);
        ServerWorld w = (ServerWorld) p.getWorld();
        Power pw = Power.values()[Math.floorMod(d.selected(), Power.values().length)];
        if (!cfg.enabled(pw.key) || (pw == Power.EXCAVATE && !cfg.allowExcavate)) {
            note(p, "message.mattercontrol.disabled", Formatting.RED);
            return;
        }
        long now = w.getTime();
        if (now < CD_END.getOrDefault(p.getUuid(), 0L)) return; // the HUD bar shows the recharge
        int cost = (int) Math.round(pw.cost * cfg.costMultiplier);
        if (d.matter() < cost) {
            note(p, "message.mattercontrol.low", Formatting.RED, cost);
            return;
        }
        if (Powers.cast(p, w, pw, materialOf(d))) {
            MatterData after = get(p); // Excavate may already have added matter
            set(p, after.withMatter(Math.max(0, after.matter() - cost)));
            CD_END.put(p.getUuid(), now + pw.cooldown);
            CD_MAX.put(p.getUuid(), Math.max(1, pw.cooldown));
            sync(p);
        }
    }
}
