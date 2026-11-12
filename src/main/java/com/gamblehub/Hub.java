package com.gamblehub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** All shared state + saved config. */
public final class Hub {
    private Hub() {}

    public static final String TITLE = "DONUTSMP GAMBLE HUB";

    public static class Preset {
        public String name;
        public int timer;
        public double min;
        public Map<String, Integer> prizes = new LinkedHashMap<>();
    }

    public static class Config {
        public int theme = 7; // Desert
        public boolean flashingLights = false, clouds = true, particles = true, animSky = true;
        public boolean auctionOn = false, profitOn = false, payConfirmOn = false, chartOn = false;
        public boolean payAnim = true, sound = true;
        public double minAmount = 0, dailyGoal = 0, payGuardAbove = 1_000_000, minPayout = 0;
        public double balance = -1, todayProfit = 0;
        public int bannerTime = 5, resultTime = 20, timer = 30;
        public String todayDate = "";
        public int[] profitPos = {6, 6};
        public int[] auctionPos = {6, 56};
        public Map<String, Integer> prizes = new LinkedHashMap<>();
        public List<Preset> presets = new ArrayList<>();
        public Preset recent = null;
        public List<String> log = new ArrayList<>();
    }

    public record Payment(String name, double amount, long time, boolean incoming) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("gamblehub.json");

    public static Config cfg = new Config();
    public static final List<Payment> recent = new ArrayList<>();
    public static final List<Double> history = new ArrayList<>();

    // auction runtime
    public static boolean aucRunning;
    public static long aucEnd, bannerUntil, resultUntil;
    public static final Map<String, Double> bids = new HashMap<>();
    public static String winner = "";
    public static double winAmount;

    // pay guard runtime
    public static String pendingCmd = "";
    public static long pendingUntil;

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) cfg = c;
            }
        } catch (Exception ignored) { }
        rollDay();
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(cfg)); } catch (Exception ignored) { }
    }

    public static void rollDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(cfg.todayDate)) { cfg.todayDate = today; cfg.todayProfit = 0; }
    }

    public static String host() {
        var u = net.minecraft.client.Minecraft.getInstance().getUser();
        return u == null ? "?" : u.getName();
    }

    public static int prizeTypes() { return cfg.prizes.size(); }
    public static int prizeTotal() { return cfg.prizes.values().stream().mapToInt(Integer::intValue).sum(); }

    public static void onPayment(String name, double amt, boolean incoming) {
        long now = System.currentTimeMillis();
        recent.add(0, new Payment(name, amt, now, incoming));
        if (recent.size() > 30) recent.remove(recent.size() - 1);

        if (cfg.profitOn && (cfg.minAmount <= 0 || amt >= cfg.minAmount)) {
            rollDay();
            double delta = incoming ? amt : -amt;
            cfg.todayProfit += delta;
            history.add(delta);
            if (history.size() > 16) history.remove(0);
            cfg.log.add(0, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  "
                    + Money.signed(delta) + "  " + name);
            while (cfg.log.size() > 200) cfg.log.remove(cfg.log.size() - 1);
            save();
        }

        if (incoming) {
            if (aucRunning && cfg.auctionOn && amt >= cfg.minPayout) bids.merge(name, amt, Double::sum);
            if (cfg.payAnim) PaymentAnimation.trigger(name, amt);
        }
    }

    public static void startAuction() {
        Preset p = new Preset();
        p.name = "recent";
        p.timer = cfg.timer;
        p.min = cfg.minPayout;
        p.prizes = new LinkedHashMap<>(cfg.prizes);
        cfg.recent = p;
        bids.clear();
        winner = "";
        winAmount = 0;
        aucRunning = true;
        cfg.auctionOn = true;
        aucEnd = System.currentTimeMillis() + cfg.timer * 1000L;
        save();
    }

    public static void tick() {
        if (aucRunning && System.currentTimeMillis() >= aucEnd) finishAuction();
    }

    public static void finishAuction() {
        aucRunning = false;
        winner = "";
        winAmount = 0;
        for (var e : bids.entrySet()) {
            if (e.getValue() > winAmount) { winAmount = e.getValue(); winner = e.getKey(); }
        }
        long now = System.currentTimeMillis();
        bannerUntil = now + cfg.bannerTime * 1000L;
        resultUntil = now + cfg.resultTime * 1000L;
    }

    public static String topLine() {
        String best = ""; double v = 0;
        for (var e : bids.entrySet()) if (e.getValue() > v) { v = e.getValue(); best = e.getKey(); }
        return best.isEmpty() ? "No bids yet" : best + " " + Money.fmt(v);
    }
}
