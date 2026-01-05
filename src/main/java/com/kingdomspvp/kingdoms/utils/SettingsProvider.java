package com.kingdomspvp.kingdoms.utils;

import java.util.concurrent.atomic.AtomicReference;

public final class SettingsProvider {
    private static final AtomicReference<KingdomsSettings> REF = new AtomicReference<>();

    private SettingsProvider() {}

    public static KingdomsSettings get() {
        KingdomsSettings s = REF.get();
        if (s == null) throw new IllegalStateException("Settings not initialized");
        return s;
    }

    public static void set(KingdomsSettings settings) {
        REF.set(settings);
    }
}
