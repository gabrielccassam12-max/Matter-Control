package com.mattercontrol.client;

import com.mattercontrol.net.ActionPayload;
import com.mattercontrol.net.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MatterClient implements ClientModInitializer {
    private static final String CAT = "key.categories.mattercontrol";
    public static KeyBinding ABSORB, CAST, NEXT, PREV, MATERIAL;

    @Override
    public void onInitializeClient() {
        ABSORB = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mattercontrol.absorb", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CAT));
        CAST = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mattercontrol.cast", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CAT));
        NEXT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mattercontrol.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, CAT));
        PREV = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mattercontrol.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, CAT));
        MATERIAL = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mattercontrol.material", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CAT));

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> MatterClientState.set(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MatterClientState.set(null));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen != null) return;
            if (ABSORB.isPressed()) ClientPlayNetworking.send(new ActionPayload(ActionPayload.ABSORB));
            while (CAST.wasPressed()) {
                ClientPlayNetworking.send(new ActionPayload(ActionPayload.CAST));
                MatterClientState.lastCastFlash = client.world != null ? client.world.getTime() : 0L;
            }
            while (NEXT.wasPressed()) ClientPlayNetworking.send(new ActionPayload(ActionPayload.NEXT));
            while (PREV.wasPressed()) ClientPlayNetworking.send(new ActionPayload(ActionPayload.PREV));
            while (MATERIAL.wasPressed()) ClientPlayNetworking.send(new ActionPayload(ActionPayload.MATERIAL));
        });

        HudRenderCallback.EVENT.register(MatterHud::render);
    }
}
