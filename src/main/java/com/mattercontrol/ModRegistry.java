package com.mattercontrol;

import com.mattercontrol.item.MatterCoreItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModRegistry {
    private ModRegistry() {}

    public static final Item MATTER_CORE = Registry.register(Registries.ITEM, MatterMod.id("matter_core"),
            new MatterCoreItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(MATTER_CORE));
    }
}
