package com.mattercontrol.matter;

import net.minecraft.text.Text;

/** The 15 powers: key, matter cost and cooldown in ticks. */
public enum Power {
    SPEAR("spear", 40, 14),
    WALL("wall", 120, 60),
    PILLAR("pillar", 80, 40),
    BRIDGE("bridge", 60, 30),
    BOULDER("boulder", 70, 24),
    ARMOR("armor", 150, 240),
    QUAKE("quake", 130, 80),
    SINKHOLE("sinkhole", 140, 120),
    CAGE("cage", 120, 140),
    BLADES("blades", 110, 200),
    SHATTER("shatter", 150, 100),
    SURF("surf", 90, 120),
    MAGNET("magnet", 50, 40),
    GOLEM("golem", 400, 600),
    EXCAVATE("excavate", 0, 30);

    public final String key;
    public final int cost;
    public final int cooldown;

    Power(String key, int cost, int cooldown) {
        this.key = key;
        this.cost = cost;
        this.cooldown = cooldown;
    }

    public Text displayName() {
        return Text.translatable("power.mattercontrol." + key);
    }
}
