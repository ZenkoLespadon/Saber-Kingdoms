// services/AutoclaimManager.java
package com.kingdomspvp.kingdoms.services;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoClaimManager {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    private AutoClaimManager() {}
    public static boolean isEnabled(UUID id) { return ENABLED.contains(id); }
    public static void enable(UUID id) { ENABLED.add(id); }
    public static void disable(UUID id) { ENABLED.remove(id); }
    public static boolean toggle(UUID id) { return ENABLED.contains(id) ? !ENABLED.remove(id) : ENABLED.add(id); }
}
