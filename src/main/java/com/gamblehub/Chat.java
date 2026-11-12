package com.gamblehub;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat parsing. These patterns are best guesses for the server's message wording.
 * If a payment doesn't register, copy the exact chat line and adjust the matching pattern here.
 */
final class Chat {
    private Chat() {}

    private static final String AMT = "\\$?([\\d.,]+\\s?[KkMmBbTt]?)";
    static final Pattern IN = Pattern.compile("(\\w{3,16}) (?:has )?(?:paid|sent) you " + AMT);
    static final Pattern OUT_A = Pattern.compile("[Yy]ou (?:have )?(?:paid|sent) (\\w{3,16}) " + AMT);
    static final Pattern OUT_B = Pattern.compile("[Yy]ou (?:have )?(?:paid|sent) " + AMT + " to (\\w{3,16})");
    static final Pattern BAL = Pattern.compile("(?i)(?:balance|bal)\\W{0,4}\\$([\\d.,]+\\s?[KMBT]?)");
    static final Pattern PAY_CMD = Pattern.compile("^pay\\s+(\\w{3,16})\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    static void handle(String m) {
        Matcher a = IN.matcher(m);
        if (a.find()) {
            double v = Money.parse(a.group(2));
            if (!Double.isNaN(v) && v > 0) Hub.onPayment(a.group(1), v, true);
            return;
        }
        Matcher o = OUT_A.matcher(m);
        if (o.find()) {
            double v = Money.parse(o.group(2));
            if (!Double.isNaN(v) && v > 0) Hub.onPayment(o.group(1), v, false);
            return;
        }
        Matcher o2 = OUT_B.matcher(m);
        if (o2.find()) {
            double v = Money.parse(o2.group(1));
            if (!Double.isNaN(v) && v > 0) Hub.onPayment(o2.group(2), v, false);
            return;
        }
        Matcher b = BAL.matcher(m);
        if (b.find()) {
            double v = Money.parse(b.group(1));
            if (!Double.isNaN(v)) Hub.cfg.balance = v;
        }
    }

    /** Pay Guard: big /pay commands must be repeated within 10s to go through. */
    static boolean allowCommand(String cmd) {
        if (!Hub.cfg.payConfirmOn) return true;
        Matcher m = PAY_CMD.matcher(cmd.trim());
        if (!m.find()) return true;
        double v = Money.parse(m.group(2));
        if (Double.isNaN(v) || v < Hub.cfg.payGuardAbove) return true;

        String norm = cmd.trim().toLowerCase();
        long now = System.currentTimeMillis();
        if (norm.equals(Hub.pendingCmd) && now < Hub.pendingUntil) {
            Hub.pendingCmd = "";
            return true;
        }
        Hub.pendingCmd = norm;
        Hub.pendingUntil = now + 10_000;
        Minecraft.getInstance().gui.hud.getChat().addMessage(Component.literal("[Pay Guard] Blocked /pay " + m.group(1) + " "
                + Money.fmt(v) + ". Send the same command again within 10s to confirm."));
        return false;
    }
}
