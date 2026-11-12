package com.gamblehub;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/** The Gamble Hub menu. Immediate-mode: every frame rebuilds the clickable regions. */
public class HubScreen extends Screen {
    private enum View { MAIN, SETUP, THEMES }
    private enum Tab {
        OVERVIEW("Overview"), AUCTION("Auction"), PROFIT("Profit"), PAY("Pay Confirm"), GAMES("Games Window");
        final String label;
        Tab(String l) { label = l; }
    }

    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {}

    private static final int BLUE = 0xFF4C5CF0, ROW = 0xFF1D222D, ROW_HOV = 0xFF2C3345;
    private static final int PANEL = 0xE0141821, GRAY = 0xFF8A8F99, PALE = 0xFFB9B2A0;
    private static final int W = 460;

    private View view = View.MAIN;
    private Tab tab = Tab.OVERVIEW;
    private boolean showLogs, manage, onlySelected, sliderDrag;
    private int H = 280, ox, oy, page, sliderX, sliderW, mouseX, mouseY;
    private double lastClickX;
    private String focus = null, search = "", tooltip = null;

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, String> text = new HashMap<>();
    private final Map<String, Consumer<String>> commits = new HashMap<>();
    private final Set<String> live = new HashSet<>();
    private final List<Item> filtered = new ArrayList<>();
    private final Map<String, ItemStack> stacks = new HashMap<>();

    public HubScreen() { super(Component.literal("Gamble Hub")); }

    @Override
    protected void init() {
        Hub.Config c = Hub.cfg;
        text.put("banner", String.valueOf(c.bannerTime));
        text.put("result", String.valueOf(c.resultTime));
        text.put("minAmount", c.minAmount > 0 ? Money.fmt(c.minAmount).substring(1) : "");
        text.put("goal", c.dailyGoal > 0 ? Money.fmt(c.dailyGoal).substring(1) : "");
        text.put("guard", Money.fmt(c.payGuardAbove).substring(1));
        text.put("minPayout", c.minPayout > 0 ? Money.fmt(c.minPayout).substring(1) : "");
        text.put("search", "");
        refilter();
    }

    // ------------------------------------------------------------------ helpers

    private boolean over(int x, int y, int w, int h) { return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h; }

    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean sel, boolean leftAlign, Runnable r) {
        Draw.round(g, x, y, w, h, sel ? BLUE : (over(x, y, w, h) ? ROW_HOV : ROW));
        int tx = leftAlign ? x + 8 : x + (w - Draw.width(label)) / 2;
        Draw.text(g, label, tx, y + (h - 8) / 2, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, r, null));
    }

    private void toggle(GuiGraphicsExtractor g, String label, int x, int y, int w, boolean val, Consumer<Boolean> set) {
        Draw.round(g, x, y, w, 22, over(x, y, w, 22) ? ROW_HOV : ROW);
        Draw.text(g, label, x + 8, y + 7, 0xFFFFFFFF);
        String st = val ? "On" : "Off";
        Draw.text(g, st, x + w - 44 - Draw.width(st), y + 7, val ? 0xFFFFFFFF : GRAY);
        int tx = x + w - 38;
        Draw.round(g, tx, y + 5, 30, 12, val ? BLUE : 0xFF5A5F6A);
        Draw.rect(g, val ? tx + 19 : tx + 1, y + 6, 10, 10, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, 22, () -> { set.accept(!val); Hub.save(); }, null));
    }

    private void label(GuiGraphicsExtractor g, String s, int x, int y) { Draw.text(g, s, x, y, PALE); }

    private void field(GuiGraphicsExtractor g, String id, String hint, int x, int y, int w, boolean isLive, Consumer<String> commit) {
        boolean f = id.equals(focus);
        Draw.rect(g, x, y, w, 20, f ? 0xFF1A1A1A : 0xFF000000);
        if (f) Draw.border(g, x, y, w, 20, 0xFF7F8CFF);
        String v = text.getOrDefault(id, "");
        if (v.isEmpty() && !f) Draw.text(g, hint, x + 6, y + 6, 0xFF666666);
        else Draw.text(g, v + (f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), x + 6, y + 6, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, 20, () -> focus = id, null));
        commits.put(id, commit);
        if (isLive) live.add(id);
    }

    private void fieldSet(GuiGraphicsExtractor g, String id, String hint, int x, int y, int w, Consumer<String> commit) {
        field(g, id, hint, x, y, w - 52, false, commit);
        button(g, "Set", x + w - 48, y, 48, 20, false, false, () -> commit.accept(text.getOrDefault(id, "")));
    }

    private static int clampInt(String s, int lo, int hi, int fallback) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(s.trim()))); } catch (Exception e) { return fallback; }
    }

    private void refilter() {
        String q = search.toLowerCase();
        filtered.clear();
        for (Item it : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            if (id.equals("minecraft:air")) continue;
            if (onlySelected && !Hub.cfg.prizes.containsKey(id)) continue;
            if (q.isEmpty() || id.contains(q)) filtered.add(it);
        }
        page = Math.min(page, Math.max(0, (filtered.size() - 1) / 28));
    }

    private ItemStack stackOf(Item it) {
        return stacks.computeIfAbsent(BuiltInRegistries.ITEM.getKey(it).toString(), k -> new ItemStack(it));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        mouseX = mx; mouseY = my;
        hits.clear(); commits.clear(); live.clear(); tooltip = null;
        H = view == View.MAIN ? 280 : 300;
        ox = (width - W) / 2;
        oy = Math.max(2, (height - H) / 2);
        Theme t = Theme.get();

        Draw.round(g, ox - 2, oy - 2, W + 4, H + 4, 0xFF2B303C);
        Backdrop.draw(g, ox, oy, W, H, t);

        switch (view) {
            case MAIN -> main(g, t);
            case SETUP -> setup(g, t);
            case THEMES -> themes(g, t);
        }

        if (tooltip != null) {
            int tw = Draw.width(tooltip) + 8;
            Draw.rect(g, mx + 8, my - 16, tw, 14, 0xF0100010);
            Draw.border(g, mx + 8, my - 16, tw, 14, 0xFF5000A0);
            Draw.text(g, tooltip, mx + 12, my - 13, 0xFFFFFFFF);
        }
    }

    private void footer(GuiGraphicsExtractor g) {
        Draw.text(g, "ESC - close", ox + 12, oy + H - 14, 0xFF6B7488);
        String r = "Gamble Hub";
        Draw.text(g, r, ox + W - 12 - Draw.width(r), oy + H - 14, 0xFF6B7488);
    }

    // ---- MAIN

    private void main(GuiGraphicsExtractor g, Theme t) {
        Hub.Config c = Hub.cfg;
        Draw.text(g, Hub.TITLE, ox + 14, oy + 12, t.accent());

        int sx = ox + 12, sy = oy + 30;
        for (Tab tb : Tab.values()) {
            button(g, tb.label, sx, sy, 112, 22, tab == tb, true, () -> { tab = tb; focus = null; showLogs = false; });
            sy += 26;
        }
        sy += 6;
        button(g, "Layout Editor >", sx, sy, 112, 22, false, true, () -> Gui.open(new LayoutScreen(this)));
        sy += 26;
        button(g, "Themes >", sx, sy, 112, 22, false, true, () -> { view = View.THEMES; focus = null; });

        int cx = ox + 136, cw = 196, y = oy + 30;
        Draw.text(g, tab.label.toUpperCase(), cx, y, PALE);
        y += 14;

        switch (tab) {
            case OVERVIEW -> {
                toggle(g, "Auction", cx, y, cw, c.auctionOn, v -> c.auctionOn = v); y += 26;
                toggle(g, "Profit Tracker", cx, y, cw, c.profitOn, v -> c.profitOn = v); y += 26;
                toggle(g, "Pay Confirm", cx, y, cw, c.payConfirmOn, v -> c.payConfirmOn = v); y += 30;
                button(g, "Open Auction", cx, y, cw, 22, false, true, () -> view = View.SETUP); y += 26;
                button(g, "Profit Logs", cx, y, cw, 22, false, true, () -> { tab = Tab.PROFIT; showLogs = true; });
            }
            case AUCTION -> {
                toggle(g, "Auction (bids + HUD)", cx, y, cw, c.auctionOn, v -> c.auctionOn = v); y += 26;
                button(g, "Open Auction", cx, y, cw / 2 - 2, 22, false, false, () -> view = View.SETUP);
                button(g, "Favourites", cx + cw / 2 + 2, y, cw / 2 - 2, 22, false, false, () -> view = View.SETUP);
                y += 26;
                Draw.text(g, "HOST: " + Hub.host(), cx, y + 2, GRAY); y += 18;
                label(g, "WINNER BANNER TIME (0-60s)", cx, y); y += 11;
                fieldSet(g, "banner", "5", cx, y, cw, s -> { c.bannerTime = clampInt(s, 0, 60, c.bannerTime); Hub.save(); }); y += 26;
                label(g, "RESULT DISPLAY (0-60s; 0=off)", cx, y); y += 11;
                fieldSet(g, "result", "20", cx, y, cw, s -> { c.resultTime = clampInt(s, 0, 60, c.resultTime); Hub.save(); });
            }
            case PROFIT -> {
                if (showLogs) {
                    button(g, "< Back", cx, y, 60, 18, false, false, () -> showLogs = false);
                    y += 22;
                    if (c.log.isEmpty()) Draw.text(g, "No profit logged yet.", cx, y + 4, GRAY);
                    for (int i = 0; i < Math.min(11, c.log.size()); i++) Draw.text(g, c.log.get(i), cx, y + i * 12, 0xFFE8E8E8);
                } else {
                    toggle(g, "Profit Tracker + Card", cx, y, cw, c.profitOn, v -> c.profitOn = v); y += 26;
                    toggle(g, "Profit Chart", cx, y, cw, c.chartOn, v -> c.chartOn = v); y += 28;
                    label(g, "MIN AMOUNT (0 = count all)", cx, y); y += 11;
                    fieldSet(g, "minAmount", "e.g. 500k", cx, y, cw, s -> {
                        double v = s.isBlank() ? 0 : Money.parse(s);
                        if (!Double.isNaN(v)) c.minAmount = Math.max(0, v);
                        Hub.save();
                    });
                    y += 26;
                    label(g, "DAILY GOAL (bar on the card)", cx, y); y += 11;
                    fieldSet(g, "goal", "e.g. 100M", cx, y, cw, s -> {
                        double v = s.isBlank() ? 0 : Money.parse(s);
                        if (!Double.isNaN(v)) c.dailyGoal = Math.max(0, v);
                        Hub.save();
                    });
                    y += 28;
                    button(g, "Reset Today's Profit", cx, y, cw, 22, false, true, () -> {
                        c.todayProfit = 0; Hub.history.clear(); Hub.save();
                    });
                }
            }
            case PAY -> {
                toggle(g, "Pay Confirm", cx, y, cw, c.payConfirmOn, v -> c.payConfirmOn = v); y += 26;
                label(g, "CONFIRM /pay ABOVE ($)", cx, y); y += 11;
                fieldSet(g, "guard", "e.g. 1M", cx, y, cw, s -> {
                    double v = Money.parse(s);
                    if (!Double.isNaN(v)) c.payGuardAbove = Math.max(0, v);
                    Hub.save();
                });
                y += 28;
                toggle(g, "Payment animation", cx, y, cw, c.payAnim, v -> c.payAnim = v); y += 26;
                toggle(g, "Payment sound", cx, y, cw, c.sound, v -> c.sound = v); y += 30;
                button(g, "Test payment animation", cx, y, cw, 22, false, true, () -> {
                    PaymentAnimation.trigger("TestPlayer", 25_000_000);
                    onClose();
                });
            }
            case GAMES -> {
                Draw.text(g, "Recent payments", cx, y, GRAY); y += 14;
                if (Hub.recent.isEmpty()) Draw.text(g, "Nothing yet.", cx, y, GRAY);
                for (int i = 0; i < Math.min(9, Hub.recent.size()); i++) {
                    Hub.Payment p = Hub.recent.get(i);
                    int col = p.incoming() ? 0xFF4DFF7A : 0xFFFF5555;
                    Draw.text(g, (p.incoming() ? "+" : "-") + Money.fmt(p.amount()).substring(1), cx, y + i * 12, col);
                    Draw.text(g, p.name(), cx + 80, y + i * 12, 0xFFE8E8E8);
                }
            }
        }

        card(g, ox + 342, oy + 30, 106);
        footer(g);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int w) {
        Hub.Config c = Hub.cfg;
        Draw.round(g, x, y, w, 80, PANEL);
        Draw.text(g, "BALANCE", x + 8, y + 8, PALE);
        Draw.text(g, c.balance < 0 ? "-" : Money.fmt(c.balance), x + 8, y + 20, 0xFFFFFFFF);
        Draw.text(g, "TODAY", x + 8, y + 40, PALE);
        Draw.text(g, Money.signed(c.todayProfit), x + 8, y + 52, c.todayProfit >= 0 ? 0xFF4DFF7A : 0xFFFF5555);

        int sy = y + 90;
        Draw.text(g, "STATUS", x + 4, sy, PALE); sy += 13;
        status(g, "Auction", Hub.aucRunning ? "LIVE" : (c.auctionOn ? "ON" : "OFF"), Hub.aucRunning || c.auctionOn, x, sy, w); sy += 12;
        status(g, "Profit", c.profitOn ? "ON" : "OFF", c.profitOn, x, sy, w); sy += 12;
        status(g, "Pay guard", c.payConfirmOn ? "ON" : "OFF", c.payConfirmOn, x, sy, w); sy += 12;
        Draw.text(g, "Prizes", x + 4, sy, 0xFF8A7F68);
        String n = String.valueOf(Hub.prizeTypes());
        Draw.text(g, n, x + w - 4 - Draw.width(n), sy, 0xFFFFFFFF);
    }

    private void status(GuiGraphicsExtractor g, String k, String v, boolean on, int x, int y, int w) {
        Draw.text(g, k, x + 4, y, 0xFF8A7F68);
        Draw.text(g, v, x + w - 4 - Draw.width(v), y, on ? 0xFF4DFF7A : 0xFFFF5555);
    }

    // ---- SETUP (auction setup + favourite presets)

    private void setup(GuiGraphicsExtractor g, Theme t) {
        Hub.Config c = Hub.cfg;
        int lx = ox + 12, lw = 216, y = oy + 10;

        Draw.text(g, "AUCTION SETUP", lx, y + 4, t.accent());
        button(g, "Recent", lx + lw - 70, y, 70, 20, false, false, () -> {
            if (Hub.cfg.recent != null) {
                c.prizes = new LinkedHashMap<>(Hub.cfg.recent.prizes);
                c.timer = Hub.cfg.recent.timer;
                c.minPayout = Hub.cfg.recent.min;
                text.put("minPayout", c.minPayout > 0 ? Money.fmt(c.minPayout).substring(1) : "");
                refilter();
            }
        });
        y += 26;

        label(g, "Auction timer", lx, y);
        String tm = String.format("00:%02d", c.timer);
        Draw.text(g, tm, lx + lw - Draw.width(tm), y, 0xFFFFFFFF);
        y += 12;
        sliderX = lx + 4; sliderW = lw - 8;
        float frac = (c.timer - 15) / 45f;
        Draw.rect(g, sliderX, y + 8, sliderW, 4, 0xFF1A2038);
        Draw.rect(g, sliderX, y + 8, (int) (sliderW * frac), 4, BLUE);
        int kx = sliderX + (int) (sliderW * frac);
        Draw.rect(g, kx - 5, y + 3, 10, 14, 0xFFFFFFFF);
        Draw.rect(g, kx - 3, y + 5, 6, 10, BLUE);
        hits.add(new Hit(sliderX - 6, y, sliderW + 12, 20, () -> { sliderDrag = true; setTimerFromX(lastClickX); }, null));
        y += 22;
        String[] ticks = {"15s", "30s", "45s", "60s"};
        for (int i = 0; i < 4; i++) {
            int tx = sliderX + sliderW * i / 3;
            Draw.text(g, ticks[i], tx - Draw.width(ticks[i]) / 2, y, GRAY);
        }
        y += 14;

        label(g, "Min payout", lx, y); y += 11;
        field(g, "minPayout", "e.g. 500k", lx, y, lw, true, s -> {
            double v = s.isBlank() ? 0 : Money.parse(s);
            if (!Double.isNaN(v)) c.minPayout = Math.max(0, v);
        });
        y += 26;

        label(g, "Prize items (L +1 / R -1, max 64)", lx, y); y += 11;
        field(g, "search", "Search all items...", lx, y, lw - 82, true, s -> { search = s; page = 0; refilter(); });
        button(g, onlySelected ? "Selected" : "Favourites", lx + lw - 78, y, 78, 20, onlySelected, false, () -> { onlySelected = !onlySelected; refilter(); });
        y += 26;

        int start = page * 28;
        for (int i = 0; i < 28; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) break;
            Item it = filtered.get(idx);
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            int sx = lx + (i % 7) * 31, sy = y + (i / 7) * 23;
            int count = c.prizes.getOrDefault(id, 0);
            Draw.round(g, sx, sy, 28, 22, count > 0 ? 0xFF2F3A78 : (over(sx, sy, 28, 22) ? ROW_HOV : ROW));
            Draw.item(g, stackOf(it), sx + 6, sy + 3);
            if (count > 0) {
                String n = String.valueOf(count);
                Draw.text(g, n, sx + 27 - Draw.width(n), sy + 13, 0xFFFFFFFF);
            }
            if (over(sx, sy, 28, 22)) tooltip = stackOf(it).getHoverName().getString();
            hits.add(new Hit(sx, sy, 28, 22,
                    () -> { c.prizes.put(id, Math.min(64, c.prizes.getOrDefault(id, 0) + 1)); },
                    () -> {
                        int n = c.prizes.getOrDefault(id, 0) - 1;
                        if (n <= 0) c.prizes.remove(id); else c.prizes.put(id, n);
                    }));
        }
        y += 4 * 23 + 4;

        Draw.text(g, Hub.prizeTypes() + " item type(s), " + Hub.prizeTotal() + " total", lx, y + 4, 0xFF4DFF7A);
        int pages = Math.max(1, (filtered.size() + 27) / 28);
        button(g, "<", lx + lw - 70, y, 18, 16, false, false, () -> page = Math.max(0, page - 1));
        Draw.text(g, (page + 1) + "/" + pages, lx + lw - 48, y + 4, GRAY);
        button(g, ">", lx + lw - 18, y, 18, 16, false, false, () -> page = Math.min(pages - 1, page + 1));
        y += 22;

        button(g, "Start", lx, oy + H - 30, lw / 2 - 3, 22, false, false, () -> { Hub.startAuction(); onClose(); });
        button(g, "Clear items", lx + lw / 2 + 3, oy + H - 30, lw / 2 - 3, 22, false, false, () -> { c.prizes.clear(); refilter(); });

        // favourites panel
        int rx = ox + 240, rw = 208;
        Draw.round(g, rx, oy + 6, rw, H - 12, PANEL);
        Draw.text(g, "FAVOURITE PRESETS", rx + 8, oy + 14, PALE);
        Draw.text(g, "Saved in the Favourites menu.", rx + 8, oy + 27, 0xFF6B7488);

        if (c.presets.isEmpty()) {
            Draw.scaled(g, "No saved auction presets", rx + rw / 2f, oy + 120, 1f, GRAY);
            Draw.scaled(g, "Add one in Favourites.", rx + rw / 2f, oy + 134, 1f, GRAY);
        }
        int py = oy + 44;
        for (int i = 0; i < Math.min(8, c.presets.size()); i++) {
            Hub.Preset p = c.presets.get(i);
            int ii = i;
            button(g, p.name + "  (" + p.prizes.size() + ")", rx + 8, py, manage ? rw - 40 : rw - 16, 20, false, true, () -> {
                c.prizes = new LinkedHashMap<>(p.prizes); c.timer = p.timer; c.minPayout = p.min;
                text.put("minPayout", c.minPayout > 0 ? Money.fmt(c.minPayout).substring(1) : "");
                refilter();
            });
            if (manage) button(g, "X", rx + rw - 28, py, 20, 20, false, false, () -> { c.presets.remove(ii); Hub.save(); });
            py += 24;
        }
        button(g, "Manage", rx + 8, oy + H - 30, 90, 22, manage, false, () -> manage = !manage);
        button(g, "Save current", rx + rw - 98, oy + H - 30, 90, 22, true, false, () -> {
            Hub.Preset p = new Hub.Preset();
            p.name = "Preset " + (c.presets.size() + 1);
            p.timer = c.timer; p.min = c.minPayout;
            p.prizes = new LinkedHashMap<>(c.prizes);
            c.presets.add(p); Hub.save();
        });
    }

    private void setTimerFromX(double x) {
        double f = Math.max(0, Math.min(1, (x - sliderX) / (double) sliderW));
        Hub.cfg.timer = 15 + (int) Math.round(f * 45);
    }

    // ---- THEMES

    private void themes(GuiGraphicsExtractor g, Theme cur) {
        Hub.Config c = Hub.cfg;
        Draw.text(g, "GUI THEMES", ox + 14, oy + 10, cur.accent());

        int lx = ox + 12, y = oy + 28;
        for (int i = 0; i < Theme.ALL.size(); i++) {
            Theme th = Theme.ALL.get(i);
            int ii = i;
            boolean sel = c.theme == i;
            boolean hov = over(lx, y, 206, 24);
            Draw.round(g, lx, y, 206, 24, sel ? 0xFF2F3A78 : (hov ? ROW_HOV : ROW));
            Draw.gradient(g, lx + 6, y + 4, 36, 16, th.top(), th.bottom());
            Draw.rect(g, lx + 6, y + 15, 36, 5, th.hill());
            Draw.text(g, th.name(), lx + 52, y + 8, 0xFFFFFFFF);
            hits.add(new Hit(lx, y, 206, 24, () -> { c.theme = ii; Hub.save(); }, null));
            y += 26;
        }
        Draw.text(g, "1-" + Theme.ALL.size() + "/" + Theme.ALL.size(), lx + 150, oy + H - 44, GRAY);
        Draw.text(g, "Selected " + cur.name() + ".", lx, oy + H - 44, GRAY);
        button(g, "Back", lx, oy + H - 30, 90, 22, false, false, () -> view = View.MAIN);

        int rx = ox + 232, rw = 216;
        Draw.round(g, rx, oy + 8, rw, 142, PANEL);
        Draw.text(g, "PREVIEW", rx + 8, oy + 14, PALE);
        Draw.text(g, cur.name(), rx + 8, oy + 27, 0xFFFFFFFF);
        int bx = rx + 8, by = oy + 42, bw = rw - 16, bh = 84;
        Backdrop.draw(g, bx, by, bw, bh, cur);
        Overlay.profitCard(g, bx + 8, by + 8, cur, 12_500, 25_000);
        Draw.text(g, "Auction  00:14", bx + 8, by + bh - 14, 0xFFFFFFFF);
        Draw.border(g, bx, by, bw, bh, cur.accent());
        Draw.text(g, "HUD accents match this theme.", rx + 8, oy + 134, 0xFF6B7488);

        int ty = oy + 160;
        toggle(g, "Flashing City Lights", rx, ty, rw, c.flashingLights, v -> c.flashingLights = v); ty += 26;
        toggle(g, "Moving Clouds", rx, ty, rw, c.clouds, v -> c.clouds = v); ty += 26;
        toggle(g, "Particles", rx, ty, rw, c.particles, v -> c.particles = v); ty += 26;
        toggle(g, "Animated Sky", rx, ty, rw, c.animSky, v -> c.animSky = v);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        lastClickX = e.x();
        int mx = (int) e.x(), my = (int) e.y(), b = e.button();
        focus = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                Runnable r = (b == 1 && h.right() != null) ? h.right() : h.left();
                if (r != null) r.run();
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (sliderDrag) { setTimerFromX(e.x()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        sliderDrag = false;
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (focus != null) {
            if (k == Keys.ENTER || k == Keys.KP_ENTER) {
                Consumer<String> cm = commits.get(focus);
                if (cm != null) cm.accept(text.getOrDefault(focus, ""));
                focus = null;
            } else if (k == Keys.BACKSPACE) {
                String v = text.getOrDefault(focus, "");
                if (!v.isEmpty()) text.put(focus, v.substring(0, v.length() - 1));
                fireLive();
            } else if (k == Keys.ESCAPE) {
                focus = null;
            }
            return true;
        }
        if (k == Keys.ESCAPE && view != View.MAIN) { view = View.MAIN; return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (focus == null) return super.charTyped(e);
        String ch = new String(Character.toChars(e.codepoint()));
        String v = text.getOrDefault(focus, "");
        if (v.length() < 32 && !ch.isBlank() || (ch.equals(" ") && v.length() < 32)) text.put(focus, v + ch);
        fireLive();
        return true;
    }

    private void fireLive() {
        if (focus != null && live.contains(focus)) {
            Consumer<String> cm = commits.get(focus);
            if (cm != null) cm.accept(text.getOrDefault(focus, ""));
        }
    }

    @Override
    public void removed() { Hub.save(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
