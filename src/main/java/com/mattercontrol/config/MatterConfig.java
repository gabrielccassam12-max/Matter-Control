package com.mattercontrol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** config/mattercontrol.json, created on first launch. Gameplay values are read by the server. */
public class MatterConfig {
    public static MatterConfig INSTANCE = new MatterConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int maxMatter = 1000;
    /** Ticks between two absorbed blocks while the absorb key is held. */
    public int absorbIntervalTicks = 3;
    public double absorbReach = 6.0;
    /** If true you must use the Matter Core item once to awaken the power. */
    public boolean requireCore = true;
    public boolean allowAbsorb = true;
    /** The Excavate power removes terrain for a while (and turns it into matter). */
    public boolean allowExcavate = true;
    /** Ticks the blocks created by powers stay before the terrain returns to normal. */
    public int blockLifetimeTicks = 400;
    public int maxTempBlocks = 6000;
    public double costMultiplier = 1.0;
    public double damageMultiplier = 1.0;
    public Map<String, Boolean> powers = new LinkedHashMap<>();

    public boolean enabled(String key) {
        return powers.getOrDefault(key, true);
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("mattercontrol.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    MatterConfig c = GSON.fromJson(r, MatterConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            if (INSTANCE.powers == null) INSTANCE.powers = new LinkedHashMap<>();
            for (String k : new String[]{"spear", "wall", "pillar", "bridge", "boulder", "armor", "quake", "sinkhole",
                    "cage", "blades", "shatter", "surf", "magnet", "golem", "excavate"}) {
                INSTANCE.powers.putIfAbsent(k, true);
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[mattercontrol] could not read config, using defaults: " + e);
            INSTANCE = new MatterConfig();
        }
    }
}
