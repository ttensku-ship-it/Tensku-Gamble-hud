package com.gamblehub;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Animated themed sky: gradient, sun/moon, clouds, rolling hills, city lights, falling particles. */
final class Backdrop {
    private Backdrop() {}

    static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, Theme t) {
        Hub.Config c = Hub.cfg;
        long ms = System.currentTimeMillis();
        float time = c.animSky ? ms / 1000f : 0f;

        Draw.gradient(g, x, y, w, h, t.top(), t.bottom());

        if (t.orb() != 0) Draw.round(g, x + (int) (w * 0.78), y + (int) (h * 0.22), 10, 10, t.orb());

        if (c.clouds) {
            for (int i = 0; i < 4; i++) {
                float cx = ((i * 140f + time * 6f) % (w + 80)) - 40;
                int cw = 40;
                if (cx < 0 || cx + cw > w) continue;
                int cy = y + 10 + (i * 23) % Math.max(20, h / 3);
                Draw.round(g, x + (int) cx, cy, cw, 6, 0x33FFFFFF);
                Draw.round(g, x + (int) cx + 8, cy - 4, 22, 5, 0x33FFFFFF);
            }
        }

        for (int col = 0; col < w; col += 2) {
            double hy = h * 0.64 + Math.sin(col * 0.018) * h * 0.06 + Math.sin(col * 0.047 + 1) * h * 0.02;
            int top = y + ((int) hy & ~1);
            g.fill(x + col, top, Math.min(x + col + 2, x + w), y + h, t.hill());
        }

        if (t.name().equals("City Lights")) {
            for (int i = 0; i < 34; i++) {
                boolean on = !c.flashingLights || ((ms / 280 + i * 7) % 4 != 0);
                if (!on) continue;
                int px = x + (i * 53) % w, py = y + (int) (h * 0.72) + (i * 29) % Math.max(8, h / 4);
                g.fill(px, py, px + 2, py + 2, 0xFFFFE27A);
            }
        }

        if (c.particles) {
            for (int i = 0; i < 36; i++) {
                float px = (i * 67f + (c.animSky ? time * (8 + i % 5 * 3) : 0)) % w;
                float py = (i * 41f + time * (10 + i % 4 * 4)) % h;
                g.fill(x + (int) px, y + (int) py, x + (int) px + 2, y + (int) py + 2, 0x99FFFFFF);
            }
        }
    }
}
