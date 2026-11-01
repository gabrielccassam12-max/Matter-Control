package com.mattercontrol;

import com.mattercontrol.config.MatterConfig;
import com.mattercontrol.matter.MatterManager;
import com.mattercontrol.matter.Scheduler;
import com.mattercontrol.net.ActionPayload;
import com.mattercontrol.net.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatterMod implements ModInitializer {
    public static final String ID = "mattercontrol";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        MatterConfig.load();
        ModRegistry.init();
        Scheduler.init();
        MatterManager.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            var p = context.player();
            if (p.isSpectator() || !p.isAlive()) return;
            switch (payload.action()) {
                case ActionPayload.ABSORB -> MatterManager.absorb(p);
                case ActionPayload.CAST -> MatterManager.cast(p);
                case ActionPayload.NEXT -> MatterManager.cycle(p, 1);
                case ActionPayload.PREV -> MatterManager.cycle(p, -1);
                case ActionPayload.MATERIAL -> MatterManager.cycleMaterial(p);
                default -> { }
            }
        });
        LOG.info("Matter Control loaded.");
    }
}
