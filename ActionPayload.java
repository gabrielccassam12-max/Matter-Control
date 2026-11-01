package com.mattercontrol.net;

import com.mattercontrol.MatterMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server key presses. ABSORB is sent every tick while the key is held (the server throttles it). */
public record ActionPayload(int action) implements CustomPayload {
    public static final int ABSORB = 0, CAST = 1, NEXT = 2, PREV = 3, MATERIAL = 4;

    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(MatterMod.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
            PacketCodecs.VAR_INT.xmap(ActionPayload::new, ActionPayload::action).cast();

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
