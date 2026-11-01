package com.mattercontrol.net;

import com.mattercontrol.MatterMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/** Server -> owner: everything the HUD shows. matter < 0 means "not awakened". */
public record SyncPayload(int matter, int max, int selected, int material, int cooldown, int cooldownMax,
                          List<String> palette) implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(MatterMod.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncPayload::matter,
            PacketCodecs.VAR_INT, SyncPayload::max,
            PacketCodecs.VAR_INT, SyncPayload::selected,
            PacketCodecs.VAR_INT, SyncPayload::material,
            PacketCodecs.VAR_INT, SyncPayload::cooldown,
            PacketCodecs.VAR_INT, SyncPayload::cooldownMax,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), SyncPayload::palette,
            SyncPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
