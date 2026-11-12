package com.gamblehub;

import java.util.Locale;

public final class Money {
    private Money() {}

    /** Parses 500, 1,500, 2.5k, 10M, 1.2B, 3T. Returns NaN if invalid. */
    public static double parse(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.replace(",", "").replace("$", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return Double.NaN;
        double mult = 1;
        switch (s.charAt(s.length() - 1)) {
            case 'K' -> mult = 1e3;
            case 'M' -> mult = 1e6;
            case 'B' -> mult = 1e9;
            case 'T' -> mult = 1e12;
            default -> { }
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try { return Double.parseDouble(s) * mult; } catch (NumberFormatException e) { return Double.NaN; }
    }

    public static String fmt(double v) {
        String sign = v < 0 ? "-" : "";
        double a = Math.abs(v), d = a;
        String suf = "";
        if (a >= 1e12) { d = a / 1e12; suf = "T"; }
        else if (a >= 1e9) { d = a / 1e9; suf = "B"; }
        else if (a >= 1e6) { d = a / 1e6; suf = "M"; }
        else if (a >= 1e3) { d = a / 1e3; suf = "K"; }
        String n = String.format(Locale.ROOT, "%.2f", d);
        if (n.contains(".")) n = n.replaceAll("0+$", "").replaceAll("\\.$", "");
        return sign + "$" + n + suf;
    }

    public static String signed(double v) { return v >= 0 ? "+" + fmt(v) : fmt(v); }
}
