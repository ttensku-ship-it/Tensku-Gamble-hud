package com.gamblehub;

import java.util.List;

public record Theme(String name, int top, int bottom, int hill, int accent, int orb) {
    public static final List<Theme> ALL = List.of(
        new Theme("Night Sky",    0xFF0B1030, 0xFF1D2B6B, 0xFF0A0F26, 0xFF7FA6FF, 0xFFFFFFFF),
        new Theme("City Lights",  0xFF1A0B2E, 0xFFB0206F, 0xFF12061F, 0xFFFF4FB0, 0),
        new Theme("Nature Green", 0xFF0E2A1A, 0xFF2E7D4F, 0xFF081A10, 0xFF59E08A, 0),
        new Theme("Sunset",       0xFF3A1A3A, 0xFFFF8A3D, 0xFF2A1226, 0xFFFFB04A, 0xFFFFF2B0),
        new Theme("Ocean",        0xFF062C4A, 0xFF1E9BD1, 0xFF04243D, 0xFF4FD0FF, 0),
        new Theme("Forest",       0xFF07140B, 0xFF16341D, 0xFF040C07, 0xFF7ACB5A, 0),
        new Theme("Winter",       0xFF1C2A44, 0xFF8FB4E8, 0xFF223552, 0xFFBFDFFF, 0),
        new Theme("Desert",       0xFF2B1608, 0xFFE8892B, 0xFF1F1209, 0xFFFFC94D, 0)
    );

    public static Theme get() { return ALL.get(Math.floorMod(Hub.cfg.theme, ALL.size())); }
}
