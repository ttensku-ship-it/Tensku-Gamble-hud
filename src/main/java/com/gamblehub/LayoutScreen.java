package com.gamblehub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag the HUD elements where you want them. ESC saves and returns. */
public class LayoutScreen extends Screen {
    private final Screen parent;
    private int drag = -1; // 0 = profit card, 1 = auction card

    public LayoutScreen(Screen parent) {
        super(Component.literal("Layout Editor"));
        this.parent = parent;
    }

    private boolean in(int px, int py, int x, int y, int w, int h) { return px >= x && px < x + w && py >= y && py < y + h; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Theme t = Theme.get();
        Draw.rect(g, 0, 0, width, height, 0x88000000);
        Draw.scaled(g, "LAYOUT EDITOR", width / 2f, 20, 2f, t.accent());
        Draw.scaled(g, "Drag the cards  -  ESC to save", width / 2f, 38, 1f, 0xFFCCCCCC);
        int[] p = Hub.cfg.profitPos, a = Hub.cfg.auctionPos;
        Overlay.profitCard(g, p[0], p[1], t, 12_500, 25_000);
        Overlay.auctionCard(g, a[0], a[1], t, "00:14", "Player 5M");
        if (drag == 0) Draw.border(g, p[0] - 2, p[1] - 2, Overlay.CARD_W + 4, Overlay.CARD_H + 4, 0xFFFFFFFF);
        if (drag == 1) Draw.border(g, a[0] - 2, a[1] - 2, Overlay.AUC_W + 4, Overlay.AUC_H + 4, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int x = (int) e.x(), y = (int) e.y();
        int[] p = Hub.cfg.profitPos, a = Hub.cfg.auctionPos;
        if (in(x, y, a[0], a[1], Overlay.AUC_W, Overlay.AUC_H)) { drag = 1; return true; }
        if (in(x, y, p[0], p[1], Overlay.CARD_W, Overlay.CARD_H)) { drag = 0; return true; }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (drag < 0) return super.mouseDragged(e, dx, dy);
        int[] pos = drag == 0 ? Hub.cfg.profitPos : Hub.cfg.auctionPos;
        int w = drag == 0 ? Overlay.CARD_W : Overlay.AUC_W, h = drag == 0 ? Overlay.CARD_H : Overlay.AUC_H;
        pos[0] = (int) Math.max(0, Math.min(width - w, pos[0] + dx));
        pos[1] = (int) Math.max(0, Math.min(height - h, pos[1] + dy));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) { drag = -1; return super.mouseReleased(e); }

    @Override
    public boolean keyPressed(KeyEvent e) { return super.keyPressed(e); }

    @Override
    public void onClose() { Hub.save(); Gui.open(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
