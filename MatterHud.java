package com.mattercontrol.client;

import com.mattercontrol.matter.Power;
import com.mattercontrol.net.SyncPayload;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** The matter gauge (bottom-left): tinted by the selected block, with palette, selected power and recharge bar. */
public final class MatterHud {
    private MatterHud() {}

    private static int tint(String id) {
        try {
            Block b = Registries.BLOCK.get(Identifier.of(id));
            return b.getDefaultMapColor().color;
        } catch (Throwable t) {
            return 0x8899AA;
        }
    }

    private static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int shade(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    public static void render(DrawContext g, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SyncPayload s = MatterClientState.last;
        if (mc.player == null || mc.options.hudHidden || s == null || s.matter() < 0) return;

        // smooth gauge
        MatterClientState.shownMatter += (s.matter() - MatterClientState.shownMatter) * 0.18f;
        float shown = MatterClientState.shownMatter;
        float pct = Math.max(0f, Math.min(1f, shown / Math.max(1, s.max())));

        List<String> pal = s.palette();
        int color = pal.isEmpty() ? 0x6FA8DC : tint(pal.get(Math.floorMod(s.material(), pal.size())));
        if ((color & 0xFFFFFF) < 0x303030) color = 0x7A8A99; // very dark blocks (obsidian, deepslate) stay readable

        int h = g.getScaledWindowHeight();
        int x = 10;
        int y = h - 64;
        int w = 110;
        long t = mc.world != null ? mc.world.getTime() : 0;

        // frame
        g.fill(x - 3, y - 3, x + w + 3, y + 13, argb(0x0B0F14, 0xB0));
        g.drawBorder(x - 3, y - 3, w + 6, 16, argb(shade(color, 1.1), 0xFF));
        // fill
        int fillW = (int) (w * pct);
        if (fillW > 0) {
            g.fillGradient(x, y, x + fillW, y + 5, argb(shade(color, 1.35), 0xFF), argb(shade(color, 0.75), 0xFF));
            g.fillGradient(x, y + 5, x + fillW, y + 10, argb(shade(color, 0.75), 0xFF), argb(shade(color, 0.5), 0xFF));
            // moving glint
            int gx = x + (int) ((t * 2) % Math.max(1, fillW));
            g.fill(gx, y, Math.min(gx + 3, x + fillW), y + 10, argb(0xFFFFFF, 0x40));
        }
        // ticks every 25%
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + 10, argb(0x000000, 0x90));

        g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.mattercontrol.matter", s.matter(), s.max()),
                x, y - 13, 0xFFE8EEF4);

        // palette
        int py = y + 17;
        for (int i = 0; i < 8; i++) {
            int px = x + i * 14;
            boolean sel = i == Math.floorMod(s.material(), Math.max(1, pal.size())) && i < pal.size();
            g.fill(px - 1, py - 1, px + 13, py + 13, argb(sel ? 0xFFFFFF : 0x0B0F14, sel ? 0xC0 : 0x80));
            if (i < pal.size()) {
                try {
                    Block b = Registries.BLOCK.get(Identifier.of(pal.get(i)));
                    ItemStack st = b.asItem().getDefaultStack();
                    g.getMatrices().push();
                    g.getMatrices().translate(px, py, 0);
                    g.getMatrices().scale(0.7f, 0.7f, 1f);
                    g.drawItem(st, 0, 0);
                    g.getMatrices().pop();
                } catch (Throwable ignored) {
                }
            }
        }

        // selected power
        Power[] ps = Power.values();
        Power pw = ps[Math.floorMod(s.selected(), ps.length)];
        boolean afford = s.matter() >= pw.cost;
        int ty = py + 17;
        g.drawTextWithShadow(mc.textRenderer, Text.literal("< ").append(pw.displayName()).append(" >"), x, ty,
                afford ? 0xFFFFFFFF : 0xFFFF7777);
        g.drawTextWithShadow(mc.textRenderer, Text.literal(String.valueOf(pw.cost)), x + w - 22, ty - 0, afford ? 0xFF9FE6FF : 0xFFFF7777);

        // cooldown bar
        long left = Math.max(0L, MatterClientState.cdEnd - t);
        if (left > 0 && s.cooldownMax() > 0) {
            int cw = (int) (w * (1f - (float) left / s.cooldownMax()));
            g.fill(x, ty + 11, x + w, ty + 13, argb(0x0B0F14, 0xB0));
            g.fill(x, ty + 11, x + cw, ty + 13, argb(0xFFD966, 0xFF));
        }
    }
}
