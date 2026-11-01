package com.mattercontrol.item;

import com.mattercontrol.matter.MatterManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Right-click once: the power awakens, the core is consumed. The power stays with you, even after death. */
public class MatterCoreItem extends Item {
    public MatterCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack);
        if (MatterManager.get(sp).awakened()) {
            sp.sendMessage(Text.translatable("message.mattercontrol.already").formatted(Formatting.YELLOW), true);
            return TypedActionResult.fail(stack);
        }
        MatterManager.awaken(sp);
        if (!sp.isCreative()) stack.decrement(1);
        return TypedActionResult.success(stack);
    }
}
