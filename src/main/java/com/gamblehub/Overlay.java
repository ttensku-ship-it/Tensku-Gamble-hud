package com.gamblehub;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** In-game HUD: profit card, chart, auction card, winner banner. */
final class Overlay {
    private Overlay() {}

    static final int CARD_W = 112, CARD_H = 44, AUC_W = 130, AUC_H = 46;

    static void profitCard(GuiGraphicsExtractor g, int x, int y, Theme t, double profit, double goal) {
        Draw.round(g, x, y, CARD_W, CARD_H, 0xCC10131A);
        Draw.border(g, x, y, CARD_W, CARD_H, t.accent());
        Draw.text(g, "Profit", x + 6, y + 5, 0xFFFFFFFF);
        Draw.text(g, Money.signed(profit), x + 6, y + 17, profit >= 0 ? 0xFF4DFF7A : 0xFFFF5555);
        if (goal > 0) {
            float p = (float) Math.max(0, Math.min(1, profit / goal));
            Draw.rect(g, x + 6, y + 34, CARD_W - 12, 4, 0xFF333844);
            Draw.rect(g, x + 6, y + 34, (int) ((CARD_W - 12) * p), 4, t.accent());
        }
    }

    static void auctionCard(GuiGraphicsExtractor g, int x, int y, Theme t, String timer, String line) {
        Draw.round(g, x, y, AUC_W, AUC_H, 0xCC10131A);
        Draw.border(g, x, y, AUC_W, AUC_H, t.accent());
        Draw.text(g, "Auction", x + 6, y + 5, 0xFFFFFFFF);
        Draw.text(g, timer, x + AUC_W - 6 - Draw.width(timer), y + 5, t.accent());
        Draw.text(g, line, x + 6, y + 20, 0xFFE8E8E8);
        long total = Math.max(1, Hub.cfg.timer * 1000L);
        float p = Hub.aucRunning ? Math.max(0f, Math.min(1f, (Hub.aucEnd - System.currentTimeMillis()) / (float) total)) : 0f;
        Draw.rect(g, x + 6, y + 36, AUC_W - 12, 4, 0xFF333844);
        Draw.rect(g, x + 6, y + 36, (int) ((AUC_W - 12) * p), 4, t.accent());
    }

    private static void chart(GuiGraphicsExtractor g, int x, int y, Theme t) {
        int w = CARD_W, h = 40;
        Draw.round(g, x, y, w, h, 0xCC10131A);
        Draw.border(g, x, y, w, h, t.accent());
        if (Hub.history.isEmpty()) return;
        double max = 1;
        for (double d : Hub.history) max = Math.max(max, Math.abs(d));
        int mid = y + h / 2, n = Hub.history.size(), bw = Math.max(2, (w - 8) / 16);
        for (int i = 0; i < n; i++) {
            double d = Hub.history.get(i);
            int bh = (int) (Math.abs(d) / max * (h / 2 - 3));
            int bx = x + 4 + i * bw;
            if (d >= 0) Draw.rect(g, bx, mid - bh, bw - 1, Math.max(1, bh), 0xFF4DFF7A);
            else Draw.rect(g, bx, mid, bw - 1, Math.max(1, bh), 0xFFFF5555);
        }
    }

    static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Gui.current() instanceof HubScreen || Gui.current() instanceof LayoutScreen) return;
        Hub.Config c = Hub.cfg;
        Theme t = Theme.get();
        long now = System.currentTimeMillis();

        if (c.profitOn) {
            Hub.rollDay();
            profitCard(g, c.profitPos[0], c.profitPos[1], t, c.todayProfit, c.dailyGoal);
            if (c.chartOn) chart(g, c.profitPos[0], c.profitPos[1] + CARD_H + 4, t);
        }

        if (c.auctionOn) {
            if (Hub.aucRunning) {
                long left = Math.max(0, Hub.aucEnd - now);
                String tm = String.format("%02d:%02d", left / 60000, (left / 1000) % 60);
                auctionCard(g, c.auctionPos[0], c.auctionPos[1], t, tm, Hub.topLine());
            } else if (c.resultTime > 0 && now < Hub.resultUntil) {
                auctionCard(g, c.auctionPos[0], c.auctionPos[1], t, "ENDED",
                        Hub.winner.isEmpty() ? "No bids" : Hub.winner + " " + Money.fmt(Hub.winAmount));
            }
        }

        if (c.bannerTime > 0 && now < Hub.bannerUntil) {
            int w = mc.getWindow().getGuiScaledWidth();
            int bw = 240, bx = (w - bw) / 2, by = 36;
            Draw.round(g, bx, by, bw, 50, 0xDD10131A);
            Draw.border(g, bx, by, bw, 50, t.accent());
            Draw.scaled(g, "WINNER", w / 2f, by + 11, 1.5f, t.accent());
            String line = Hub.winner.isEmpty() ? "No bids" : Hub.winner + "  " + Money.fmt(Hub.winAmount);
            Draw.scaled(g, line, w / 2f, by + 32, 2f, 0xFFFFFFFF);
        }
    }
}
