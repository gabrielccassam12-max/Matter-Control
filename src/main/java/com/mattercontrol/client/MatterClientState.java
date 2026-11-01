package com.mattercontrol.client;

import com.mattercontrol.net.SyncPayload;

public final class MatterClientState {
    private MatterClientState() {}

    public static volatile SyncPayload last = null;
    public static float shownMatter = 0f;
    public static long lastCastFlash = 0L;
    public static long cdEnd = 0L;

    public static void set(SyncPayload p) {
        last = p;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (p != null && mc.world != null) cdEnd = mc.world.getTime() + p.cooldown();
    }
}
