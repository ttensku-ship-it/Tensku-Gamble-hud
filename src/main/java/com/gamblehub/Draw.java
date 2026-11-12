package com.gamblehub;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Every call into Minecraft's drawing API lives here. If a Minecraft update renames
 * a draw method, this is the only file you have to fix.
 */
final class Draw {
    private Draw() {}

    static Font font() { return Minecraft.getInstance().font; }
    static int width(String s) { return font().width(s); }

    static int argb(float a, int rgb) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

    /** Rectangle with the corners cut by 1px (the pixel-rounded look). */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }

    static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) { g.text(font(), s, x, y, color); }

    /** Text centred on (cx, cy), scaled. */
    static void scaled(GuiGraphicsExtractor g, String s, float cx, float cy, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.text(font(), s, -width(s) / 2, -4, color);
        g.pose().popMatrix();
    }

    static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) { g.item(stack, x, y); }
}
