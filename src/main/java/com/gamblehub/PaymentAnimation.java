package com.gamblehub;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/** Full-screen "payment received" celebration: dim, glow, rings, confetti, giant counting amount, shine sweep. */
final class PaymentAnimation {
    private PaymentAnimation() {}

    private record Evt(String name, double amt) {}
    private static final class P { float x, y, vx, vy; int size, color; }

    private static final ArrayDeque<Evt> queue = new ArrayDeque<>();
    private static final List<P> parts = new ArrayList<>();
    private static final Random rng = new Random();
    private static final long DUR = 4200;
    private static Evt cur;
    private static long start, last;
    private static boolean spawned;

    static void trigger(String name, double amt) {
        if (queue.size() < 6) queue.add(new Evt(name, amt));
    }

    private static float easeOutBack(float x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
    }

    private static float easeOut(float x) { return 1f - (1f - x) * (1f - x) * (1f - x); }

    private static void sound() {
        try {
            if (Hub.cfg.sound)
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f));
        } catch (Throwable ignored) { }
    }

    static void render(GuiGraphicsExtractor g) {
        long now = System.currentTimeMillis();
        if (cur == null) {
            cur = queue.poll();
            if (cur == null) return;
            start = now; last = now; spawned = false;
            sound();
        }
        float t = (now - start) / (float) DUR;
        if (t >= 1f) { cur = null; parts.clear(); return; }

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float cx = w / 2f, cy = h / 2f - 8;
        int acc = Theme.get().accent() & 0xFFFFFF;
        float fade = t < 0.08f ? t / 0.08f : (t > 0.82f ? (1f - t) / 0.18f : 1f);

        if (!spawned) {
            spawned = true;
            parts.clear();
            int[] cols = {0xFFD76A, 0xFFFFFF, acc, 0x4DFF7A, 0xFF5FA8, 0xFFB000};
            for (int i = 0; i < 180; i++) {
                P p = new P();
                double ang = rng.nextDouble() * Math.PI * 2;
                double sp = 140 + rng.nextDouble() * 420;
                p.x = cx; p.y = cy;
                p.vx = (float) (Math.cos(ang) * sp);
                p.vy = (float) (Math.sin(ang) * sp) - 160;
                p.size = 2 + rng.nextInt(4);
                p.color = cols[rng.nextInt(cols.length)];
                parts.add(p);
            }
        }

        // dim
        g.fill(0, 0, w, h, Draw.argb(0.45f * fade, 0x000000));

        // pulsing glow
        float pulse = 1f + 0.06f * (float) Math.sin(now / 110.0);
        for (int i = 5; i >= 1; i--) {
            int gw = (int) (i * 90 * pulse), gh = (int) (i * 26 * pulse);
            g.fill((int) cx - gw, (int) cy - gh, (int) cx + gw, (int) cy + gh, Draw.argb(0.05f * fade, acc));
        }

        // shock rings
        for (int k = 0; k < 3; k++) {
            float rt = t * 1.6f - k * 0.18f;
            if (rt <= 0 || rt > 1) continue;
            float r = rt * Math.max(w, h) * 0.55f;
            int col = Draw.argb((1f - rt) * fade, acc);
            for (int a = 0; a < 90; a++) {
                double ang = a * Math.PI * 2 / 90;
                int px = (int) (cx + Math.cos(ang) * r), py = (int) (cy + Math.sin(ang) * r * 0.8);
                g.fill(px - 1, py - 1, px + 2, py + 2, col);
            }
        }

        // confetti
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        for (P p : parts) {
            p.vy += 520f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.x < -10 || p.x > w + 10 || p.y > h + 10) continue;
            g.fill((int) p.x, (int) p.y, (int) p.x + p.size, (int) p.y + p.size, Draw.argb(fade, p.color));
        }

        // amount
        double shown = cur.amt() * easeOut(Math.min(1f, t / 0.4f));
        String amt = "+" + Money.fmt(shown);
        float base = cur.amt() >= 1e9 ? 9f : cur.amt() >= 1e8 ? 8f : cur.amt() >= 1e6 ? 7f : 6f;
        String widest = "+" + Money.fmt(cur.amt());
        float maxS = (w * 0.9f) / Math.max(1, Draw.width(widest));
        float pop = easeOutBack(Math.min(1f, t / 0.2f));
        float s = Math.min(base, maxS) * Math.max(0.01f, pop);
        float th = 8f * s;
        float tw = Draw.width(widest) * s;

        Draw.scaled(g, amt, cx + 3, cy + 3, s, Draw.argb(0.6f * fade, 0x3A2400));
        Draw.scaled(g, amt, cx, cy, s, Draw.argb(fade, 0xFFD76A));

        // shine sweep
        if (t < 0.85f) {
            float sweep = (t * 2.4f) % 1f;
            int sx = (int) (cx - tw / 2f + (tw + 60) * sweep - 30);
            g.fill(sx, (int) (cy - th / 2f), sx + 14, (int) (cy + th / 2f), Draw.argb(0.28f * fade, 0xFFFFFF));
        }

        float tp = Math.max(0.01f, Math.min(1f, t / 0.15f));
        Draw.scaled(g, "PAYMENT RECEIVED", cx, cy - th / 2f - 22, 2.5f * tp, Draw.argb(fade, 0xFFFFFF));
        Draw.scaled(g, "from " + cur.name(), cx, cy + th / 2f + 22, 2f * tp, Draw.argb(fade, acc));
    }
}
