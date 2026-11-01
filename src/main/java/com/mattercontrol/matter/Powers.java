package com.mattercontrol.matter;

import com.mattercontrol.config.MatterConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.IntConsumer;

/** The 15 matter powers. Everything built is a TempBlock (restored later); everything dug is restored later. */
public final class Powers {
    private Powers() {}

    private static MatterConfig cfg() { return MatterConfig.INSTANCE; }

    // ------------------------------------------------------------ helpers
    private static void loop(int n, IntConsumer step) {
        for (int i = 0; i < n; i++) {
            final int k = i;
            Scheduler.later(i + 1, () -> step.accept(k));
        }
    }

    private static ParticleEffect chunk(BlockState s) {
        return new BlockStateParticleEffect(ParticleTypes.BLOCK, s);
    }

    private static void sound(ServerWorld w, BlockPos pos, SoundEvent e, float vol, float pitch) {
        w.playSound(null, pos, e, SoundCategory.PLAYERS, vol, pitch);
    }

    private static void sound(ServerWorld w, Vec3d at, BlockState s, float vol, float pitch) {
        w.playSound(null, BlockPos.ofFloored(at), s.getSoundGroup().getPlaceSound(), SoundCategory.PLAYERS, vol, pitch);
    }

    private static float dmg(double base) {
        return (float) (base * cfg().damageMultiplier);
    }

    private static void hurt(ServerPlayerEntity p, ServerWorld w, LivingEntity e, double amount) {
        e.damage(w.getDamageSources().playerAttack(p), dmg(amount));
    }

    private static void push(Entity e, Vec3d v) {
        e.addVelocity(v.x, v.y, v.z);
        e.velocityModified = true;
        if (e instanceof ServerPlayerEntity sp) sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
    }

    private static void setVel(Entity e, Vec3d v) {
        e.setVelocity(v);
        e.velocityModified = true;
        if (e instanceof ServerPlayerEntity sp) sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
    }

    private static List<LivingEntity> near(ServerPlayerEntity p, ServerWorld w, Box box) {
        return w.getEntitiesByClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && !(e instanceof PlayerEntity pl && pl.isSpectator()));
    }

    /** Where the crosshair points (block or entity), within range. Null when nothing. */
    private static Vec3d aim(ServerPlayerEntity p, ServerWorld w, double range) {
        HitResult hit = p.raycast(range, 0f, false);
        Vec3d eye = p.getEyePos();
        LivingEntity t = targetEntity(p, w, range);
        if (t != null && (hit.getType() != HitResult.Type.BLOCK || t.getPos().distanceTo(eye) < hit.getPos().distanceTo(eye)))
            return t.getPos();
        if (hit.getType() == HitResult.Type.BLOCK) return hit.getPos();
        return null;
    }

    private static LivingEntity targetEntity(ServerPlayerEntity p, ServerWorld w, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(range));
        Box box = p.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult r = ProjectileUtil.raycast(p, eye, end, box,
                e -> e instanceof LivingEntity && e != p && e.isAlive() && !e.isSpectator(), range * range);
        return r != null && r.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** Y of the free block resting on solid ground near refY, or Integer.MIN_VALUE. */
    private static int surfaceY(ServerWorld w, int x, int z, int refY) {
        for (int y = refY + 3; y >= refY - 5; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (TempBlocks.replaceable(w.getBlockState(pos)) && !TempBlocks.replaceable(w.getBlockState(pos.down()))) return y;
        }
        return Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------ dispatch
    public static boolean cast(ServerPlayerEntity p, ServerWorld w, Power pw, BlockState mat) {
        return switch (pw) {
            case SPEAR -> spear(p, w, mat);
            case WALL -> wall(p, w, mat);
            case PILLAR -> pillar(p, w, mat);
            case BRIDGE -> bridge(p, w, mat);
            case BOULDER -> boulder(p, w, mat);
            case ARMOR -> armor(p, w, mat);
            case QUAKE -> quake(p, w, mat);
            case SINKHOLE -> sinkhole(p, w, mat);
            case CAGE -> cage(p, w, mat);
            case BLADES -> blades(p, w, mat);
            case SHATTER -> shatter(p, w, mat);
            case SURF -> surf(p, w, mat);
            case MAGNET -> magnet(p, w, mat);
            case GOLEM -> golem(p, w, mat);
            case EXCAVATE -> excavate(p, w, mat);
        };
    }

    // ------------------------------------------------------------ 1. SPEAR: a lance of matter shoots out of the ground/air
    private static boolean spear(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d start = p.getEyePos().add(look.multiply(1.2)).subtract(0, 0.3, 0);
        boolean[] stopped = {false};
        sound(w, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
        loop(14, i -> {
            if (stopped[0]) return;
            Vec3d at = start.add(look.multiply(i * 1.4));
            BlockPos bp = BlockPos.ofFloored(at);
            BlockState cur = w.getBlockState(bp);
            if (!TempBlocks.replaceable(cur) && !TempBlocks.isTemp(w, bp)) {
                stopped[0] = true;
                w.spawnParticles(chunk(cur), at.x, at.y, at.z, 25, 0.4, 0.4, 0.4, 0.15);
                return;
            }
            // thick shaft: the block and a ring of particles
            TempBlocks.place(w, bp, mat, 50);
            w.spawnParticles(chunk(mat), at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.05);
            w.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.1);
            for (LivingEntity e : near(p, w, new Box(at, at).expand(1.1))) {
                hurt(p, w, e, 9);
                push(e, look.multiply(1.3).add(0, 0.35, 0));
                w.spawnParticles(chunk(mat), e.getX(), e.getBodyY(0.5), e.getZ(), 25, 0.3, 0.4, 0.3, 0.2);
                sound(w, e.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 1.2f);
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 2. WALL: a wall rises layer by layer in front of you
    private static boolean wall(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Direction d = p.getHorizontalFacing();
        Direction side = d.rotateYClockwise();
        BlockPos center = p.getBlockPos().offset(d, 3);
        int life = cfg().blockLifetimeTicks;
        for (int h = 0; h < 4; h++) {
            final int hh = h;
            Scheduler.later(h * 2 + 1, () -> {
                for (int s = -2; s <= 2; s++) {
                    BlockPos col = center.offset(side, s);
                    int y = surfaceY(w, col.getX(), col.getZ(), center.getY());
                    if (y == Integer.MIN_VALUE) continue;
                    BlockPos pos = new BlockPos(col.getX(), y + hh, col.getZ());
                    if (TempBlocks.place(w, pos, mat, life)) {
                        w.spawnParticles(chunk(mat), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                    }
                }
                sound(w, center, mat, 0.7f, 0.7f + hh * 0.1f);
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 3. PILLAR: a column lifts you into the sky
    private static boolean pillar(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        int life = cfg().blockLifetimeTicks;
        loop(8, i -> {
            if (p.isRemoved() || !p.isAlive()) return;
            BlockPos feet = p.getBlockPos();
            BlockPos up1 = feet.up();
            BlockPos up2 = feet.up(2);
            if (!w.getBlockState(up1).getCollisionShape(w, up1).isEmpty()
                    || !w.getBlockState(up2).getCollisionShape(w, up2).isEmpty()) return;
            p.requestTeleport(p.getX(), feet.getY() + 1.0, p.getZ());
            p.fallDistance = 0;
            TempBlocks.place(w, feet, mat, life);
            w.spawnParticles(chunk(mat), p.getX(), feet.getY() + 0.2, p.getZ(), 14, 0.5, 0.1, 0.5, 0.1);
            sound(w, feet, mat, 0.6f, 0.8f + i * 0.05f);
            for (LivingEntity e : near(p, w, new Box(feet).expand(1.2, 0.4, 1.2))) {
                push(e, new Vec3d(0, 0.9, 0));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 4. BRIDGE: matter flows forward under your feet
    private static boolean bridge(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        if (dir.lengthSquared() < 1.0E-4) dir = Vec3d.fromPolar(0, p.getYaw());
        final Vec3d dn = dir.normalize();
        final Vec3d side = new Vec3d(-dn.z, 0, dn.x);
        final int y = p.getBlockPos().getY() - 1;
        final Vec3d origin = p.getPos();
        int life = cfg().blockLifetimeTicks;
        loop(16, i -> {
            Vec3d c = origin.add(dn.multiply(i + 1));
            for (int s = -1; s <= 1; s++) {
                Vec3d q = c.add(side.multiply(s));
                BlockPos pos = new BlockPos(MathHelperFloor.f(q.x), y, MathHelperFloor.f(q.z));
                if (TempBlocks.place(w, pos, mat, life)) {
                    w.spawnParticles(chunk(mat), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.03);
                }
            }
            sound(w, BlockPos.ofFloored(c), mat, 0.4f, 1.0f + (i % 4) * 0.1f);
        });
        return true;
    }

    private static final class MathHelperFloor {
        static int f(double v) { return (int) Math.floor(v); }
    }

    // ------------------------------------------------------------ 5. BOULDER: a flying boulder that explodes into a crater
    private static boolean boulder(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d[] pos = {p.getEyePos().add(look.multiply(1.5))};
        Vec3d[] vel = {look.multiply(1.5)};
        boolean[] done = {false};
        sound(w, p.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
        loop(30, i -> {
            if (done[0]) return;
            Vec3d at = pos[0];
            vel[0] = vel[0].add(0, -0.03, 0);
            Vec3d next = at.add(vel[0]);
            boolean hit = !TempBlocks.replaceable(w.getBlockState(BlockPos.ofFloored(next)));
            if (!hit) hit = !near(p, w, new Box(next, next).expand(1.2)).isEmpty();
            // a ball of rubble
            for (int k = 0; k < 4; k++) {
                w.spawnParticles(chunk(mat), at.x, at.y, at.z, 8, 0.7, 0.7, 0.7, 0.05);
            }
            w.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.02);
            if (hit) {
                done[0] = true;
                impact(p, w, at, mat, 3.2, 14, 1.4);
                return;
            }
            pos[0] = next;
        });
        return true;
    }

    /** Crater + shockwave used by the boulder. */
    private static void impact(ServerPlayerEntity p, ServerWorld w, Vec3d at, BlockState mat, double r, double damage, double kb) {
        sound(w, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        w.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.5, 0.5, 0.5, 0.0);
        w.spawnParticles(chunk(mat), at.x, at.y, at.z, 120, r * 0.4, r * 0.4, r * 0.4, 0.35);
        BlockPos c = BlockPos.ofFloored(at);
        int ir = (int) Math.ceil(r - 0.6);
        for (int x = -ir; x <= ir; x++)
            for (int y = -ir; y <= ir; y++)
                for (int z = -ir; z <= ir; z++) {
                    if (x * x + y * y + z * z > (r - 0.4) * (r - 0.4)) continue;
                    TempBlocks.dig(w, c.add(x, y, z), cfg().blockLifetimeTicks);
                }
        for (LivingEntity e : near(p, w, new Box(at, at).expand(r + 1))) {
            Vec3d away = e.getPos().subtract(at).normalize();
            hurt(p, w, e, damage);
            push(e, away.multiply(kb).add(0, 0.5, 0));
        }
    }

    // ------------------------------------------------------------ 6. ARMOR: a shell of matter wraps your body
    private static boolean armor(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, 2));
        sound(w, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
        w.spawnParticles(chunk(mat), p.getX(), p.getBodyY(0.5), p.getZ(), 80, 0.5, 0.8, 0.5, 0.3);
        loop(60, i -> {
            if (p.isRemoved() || !p.isAlive()) return;
            if (i % 5 != 0) return;
            double a = i * 0.7;
            for (int k = 0; k < 6; k++) {
                double ang = a + k * Math.PI / 3;
                double y = 0.2 + (k % 3) * 0.7;
                w.spawnParticles(chunk(mat), p.getX() + Math.cos(ang) * 0.7, p.getY() + y, p.getZ() + Math.sin(ang) * 0.7,
                        2, 0.05, 0.05, 0.05, 0.0);
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 7. QUAKE: a ground shockwave expanding from your feet
    private static boolean quake(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d o = p.getPos();
        sound(w, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        loop(10, i -> {
            double r = 1.5 + i * 1.0;
            int pts = (int) (r * 6);
            for (int k = 0; k < pts; k++) {
                double ang = k * 2 * Math.PI / pts;
                double x = o.x + Math.cos(ang) * r;
                double z = o.z + Math.sin(ang) * r;
                int y = surfaceY(w, (int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(o.y));
                if (y == Integer.MIN_VALUE) continue;
                BlockState ground = w.getBlockState(new BlockPos((int) Math.floor(x), y - 1, (int) Math.floor(z)));
                w.spawnParticles(chunk(ground), x, y + 0.2, z, 3, 0.2, 0.3, 0.2, 0.15);
            }
            Box ring = new Box(o.x - r - 1, o.y - 1, o.z - r - 1, o.x + r + 1, o.y + 3, o.z + r + 1);
            for (LivingEntity e : near(p, w, ring)) {
                double d = Math.hypot(e.getX() - o.x, e.getZ() - o.z);
                if (d > r - 1.3 && d <= r + 0.7 && e.isOnGround()) {
                    hurt(p, w, e, 5);
                    Vec3d away = new Vec3d(e.getX() - o.x, 0, e.getZ() - o.z).normalize();
                    push(e, away.multiply(0.5).add(0, 0.65, 0));
                }
            }
            sound(w, BlockPos.ofFloored(o), mat, 0.4f, 0.5f);
        });
        return true;
    }

    // ------------------------------------------------------------ 8. SINKHOLE: the ground swallows your target
    private static boolean sinkhole(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d at = aim(p, w, 28);
        if (at == null) return false;
        int cx = (int) Math.floor(at.x), cz = (int) Math.floor(at.z);
        int top = (int) Math.floor(at.y);
        int life = cfg().blockLifetimeTicks;
        sound(w, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.4f);
        loop(7, depth -> {
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    if (x * x + z * z > 5.5) continue;
                    BlockPos pos = new BlockPos(cx + x, top - depth, cz + z);
                    BlockState s = w.getBlockState(pos);
                    if (TempBlocks.dig(w, pos, life)) {
                        w.spawnParticles(chunk(s), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.05);
                    }
                }
            w.spawnParticles(ParticleTypes.POOF, cx + 0.5, top + 1.0, cz + 0.5, 6, 1.2, 0.2, 1.2, 0.02);
        });
        return true;
    }

    // ------------------------------------------------------------ 9. CAGE: a hollow cube of matter closes around the target
    private static boolean cage(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        LivingEntity t = targetEntity(p, w, 24);
        BlockPos c;
        if (t != null) {
            c = t.getBlockPos();
        } else {
            Vec3d at = aim(p, w, 24);
            if (at == null) return false;
            c = BlockPos.ofFloored(at).up();
        }
        final BlockPos center = c;
        int life = cfg().blockLifetimeTicks;
        sound(w, center, SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        loop(5, i -> {
            // the cage closes from the floor to the roof
            int dy = i - 1;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    boolean wallRing = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    boolean cap = dy == -1 || dy == 3;
                    if (!wallRing && !cap) continue;
                    BlockPos pos = center.add(dx, dy, dz);
                    if (TempBlocks.place(w, pos, mat, life)) {
                        w.spawnParticles(chunk(mat), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.03);
                    }
                }
            sound(w, center, mat, 0.6f, 0.7f);
        });
        return true;
    }

    // ------------------------------------------------------------ 10. BLADES: four matter blades orbit and slice
    private static boolean blades(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        sound(w, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.8f);
        loop(150, i -> {
            if (p.isRemoved() || !p.isAlive()) return;
            double base = i * 0.45;
            for (int k = 0; k < 4; k++) {
                double ang = base + k * Math.PI / 2;
                double rad = 2.3;
                double x = p.getX() + Math.cos(ang) * rad;
                double z = p.getZ() + Math.sin(ang) * rad;
                double y = p.getY() + 0.9 + Math.sin(i * 0.2 + k) * 0.35;
                // the blade: a short arc of chips plus a sharp glint
                for (int s = 0; s < 3; s++) {
                    double a2 = ang - s * 0.12;
                    w.spawnParticles(chunk(mat), p.getX() + Math.cos(a2) * rad, y, p.getZ() + Math.sin(a2) * rad, 1, 0.03, 0.03, 0.03, 0.0);
                }
                w.spawnParticles(ParticleTypes.CRIT, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
                if (i % 2 == 0) {
                    for (LivingEntity e : near(p, w, new Box(x - 0.9, y - 0.9, z - 0.9, x + 0.9, y + 0.9, z + 0.9))) {
                        hurt(p, w, e, 3.5);
                        Vec3d away = new Vec3d(e.getX() - p.getX(), 0, e.getZ() - p.getZ()).normalize();
                        push(e, away.multiply(0.25).add(0, 0.1, 0));
                        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getBodyY(0.5), e.getZ(), 1, 0, 0, 0, 0);
                    }
                }
            }
            if (i % 12 == 0) sound(w, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.3f);
        });
        return true;
    }

    // ------------------------------------------------------------ 11. SHATTER: the target area explodes into shrapnel
    private static boolean shatter(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d at = aim(p, w, 26);
        if (at == null) return false;
        BlockPos c = BlockPos.ofFloored(at);
        int dug = 0;
        for (int x = -3; x <= 3; x++)
            for (int y = -3; y <= 3; y++)
                for (int z = -3; z <= 3; z++) {
                    if (x * x + y * y + z * z > 10) continue;
                    BlockPos pos = c.add(x, y, z);
                    BlockState s = w.getBlockState(pos);
                    if (TempBlocks.dig(w, pos, cfg().blockLifetimeTicks)) {
                        dug++;
                        w.spawnParticles(chunk(s), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.45);
                    }
                }
        sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.3f, 0.9f);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        double damage = Math.min(20, 6 + dug * 0.25);
        for (LivingEntity e : near(p, w, new Box(at, at).expand(5))) {
            Vec3d away = e.getPos().subtract(at).normalize();
            hurt(p, w, e, damage);
            push(e, away.multiply(1.1).add(0, 0.5, 0));
        }
        return true;
    }

    // ------------------------------------------------------------ 12. SURF: ride a wave of matter
    private static boolean surf(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        if (dir.lengthSquared() < 1.0E-4) dir = Vec3d.fromPolar(0, p.getYaw());
        final Vec3d dn = dir.normalize();
        final int life = 60;
        sound(w, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        loop(45, i -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3d v = p.getVelocity();
            setVel(p, new Vec3d(dn.x * 0.95, Math.max(v.y, 0.0), dn.z * 0.95));
            p.fallDistance = 0;
            BlockPos under = p.getBlockPos().down();
            for (int s = -1; s <= 1; s++) {
                BlockPos q = under.add(MathHelperFloor.f(-dn.z * s), 0, MathHelperFloor.f(dn.x * s));
                TempBlocks.place(w, q, mat, life);
            }
            w.spawnParticles(chunk(mat), p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.6, 0.1, 0.6, 0.2);
            if (i % 8 == 0) sound(w, p.getBlockPos(), mat, 0.4f, 1.2f);
            for (LivingEntity e : near(p, w, p.getBoundingBox().expand(1.0))) {
                push(e, dn.multiply(0.8).add(0, 0.35, 0));
                if (i % 5 == 0) hurt(p, w, e, 2);
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 13. MAGNET: drags everything around you to you
    private static boolean magnet(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        sound(w, p.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        loop(12, i -> {
            if (p.isRemoved() || !p.isAlive()) return;
            Vec3d c = p.getPos().add(0, 1, 0);
            Box box = p.getBoundingBox().expand(12);
            for (LivingEntity e : near(p, w, box)) {
                Vec3d to = c.subtract(e.getPos().add(0, e.getHeight() / 2, 0));
                if (to.length() < 1.6) continue;
                setVel(e, to.normalize().multiply(0.55));
                w.spawnParticles(chunk(mat), e.getX(), e.getBodyY(0.5), e.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
            }
            for (ItemEntity it : w.getEntitiesByClass(ItemEntity.class, box, x -> true)) {
                Vec3d to = c.subtract(it.getPos());
                it.setVelocity(to.normalize().multiply(0.5));
            }
            double ang = i * 0.9;
            for (int k = 0; k < 3; k++) {
                double a = ang + k * 2.1;
                double r = 6 - i * 0.4;
                w.spawnParticles(chunk(mat), p.getX() + Math.cos(a) * r, p.getY() + 0.5 + k * 0.4, p.getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.0);
            }
        });
        Scheduler.later(14, () -> {
            for (LivingEntity e : near(p, w, p.getBoundingBox().expand(3))) {
                hurt(p, w, e, 4);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
            }
            w.spawnParticles(chunk(mat), p.getX(), p.getBodyY(0.5), p.getZ(), 60, 1.5, 0.8, 1.5, 0.2);
            sound(w, p.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
        });
        return true;
    }

    // ------------------------------------------------------------ 14. GOLEM: an ally made of the matter you collected
    private static boolean golem(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        IronGolemEntity g = EntityType.IRON_GOLEM.create(w);
        if (g == null) return false;
        Vec3d spot = p.getPos().add(p.getRotationVec(1f).multiply(2.5));
        g.refreshPositionAndAngles(spot.x, p.getY(), spot.z, p.getYaw(), 0);
        g.setPlayerCreated(true);
        g.setCustomName(Text.translatable("entity.mattercontrol.golem"));
        g.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 900, 1));
        g.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 900, 0));
        if (!w.spawnEntity(g)) return false;
        w.spawnParticles(chunk(mat), spot.x, p.getY() + 1, spot.z, 160, 0.8, 1.2, 0.8, 0.35);
        sound(w, g.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        // it crumbles back into matter when the time is up
        Scheduler.later(900, () -> {
            if (!g.isRemoved() && g.isAlive()) {
                w.spawnParticles(chunk(mat), g.getX(), g.getBodyY(0.5), g.getZ(), 100, 0.6, 1.0, 0.6, 0.25);
                g.discard();
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 15. EXCAVATE: tear matter out of the ground in a wide sweep
    private static boolean excavate(ServerPlayerEntity p, ServerWorld w, BlockState mat) {
        HitResult hit = p.raycast(cfg().absorbReach + 2, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos c = ((BlockHitResult) hit).getBlockPos();
        int total = 0;
        int room = cfg().maxMatter - MatterManager.get(p).matter();
        if (room <= 0) return false;
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -2; z <= 2; z++) {
                    if (x * x + y * y + z * z > 6) continue;
                    BlockPos pos = c.add(x, y, z);
                    BlockState s = w.getBlockState(pos);
                    if (!MatterManager.absorbable(w, pos, s)) continue;
                    if (TempBlocks.dig(w, pos, cfg().blockLifetimeTicks)) {
                        int g = Math.max(2, Math.min(30, Math.round(3 + s.getHardness(w, pos) * 3)));
                        total += MatterManager.gain(p, g, s);
                        w.spawnParticles(chunk(s), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.05);
                    }
                }
        if (total <= 0) return false;
        sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        return true;
    }
}
