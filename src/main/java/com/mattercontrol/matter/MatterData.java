package com.mattercontrol.matter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Saved with the player and kept after death.
 * palette = ids of absorbed blocks (oldest first, max 8); material = index of the palette block powers are built from;
 * selected = index of the chosen power.
 */
public record MatterData(boolean awakened, int matter, int selected, int material, List<String> palette) {
    public static final MatterData DEFAULT = new MatterData(false, 0, 0, 0, List.of());

    public static final Codec<MatterData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("awakened").forGetter(MatterData::awakened),
            Codec.INT.fieldOf("matter").forGetter(MatterData::matter),
            Codec.INT.fieldOf("selected").forGetter(MatterData::selected),
            Codec.INT.fieldOf("material").forGetter(MatterData::material),
            Codec.STRING.listOf().fieldOf("palette").forGetter(MatterData::palette)
    ).apply(i, MatterData::new));

    public MatterData withAwakened(boolean v) { return new MatterData(v, matter, selected, material, palette); }
    public MatterData withMatter(int v) { return new MatterData(awakened, v, selected, material, palette); }
    public MatterData withSelected(int v) { return new MatterData(awakened, matter, v, material, palette); }
    public MatterData withMaterial(int v) { return new MatterData(awakened, matter, selected, v, palette); }
    public MatterData withPalette(List<String> v) { return new MatterData(awakened, matter, selected, material, v); }
}
