package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.ClaimsJSON;
import com.kingdomspvp.kingdoms.utils.KingdomsConfig;
import org.bukkit.Bukkit;

import java.nio.file.Files;
import java.util.*;

public class ClaimManager {

    private static final ClaimsJSON claimsJSON = new ClaimsJSON();

    private static int ACTIVE_CLAIM_SIZE;
    private static int ACTIVE_MAP_SIZE;

    /** Verrou global si claim-size mismatch */
    private static volatile boolean CLAIMS_LOCKED = false;

    public static int getActiveClaimSize() {
        return ACTIVE_CLAIM_SIZE;
    }

    public static int getActiveMapSize() {
        return ACTIVE_MAP_SIZE;
    }

    /* ===================== */
    /* ====== LOAD ========= */
    /* ===================== */

    public static void loadClaims(Callback<Boolean> success) {
        claimsJSON.load(ok -> {

            Integer metaClaimSize = claimsJSON.getMetaClaimSize();
            Integer metaMapSize   = claimsJSON.getMetaMapSize();

            if (metaClaimSize == null || metaMapSize == null) {
                ACTIVE_CLAIM_SIZE = KingdomsConfig.CONFIG_CLAIM_SIZE;
                ACTIVE_MAP_SIZE   = KingdomsConfig.CONFIG_MAP_SIZE;
                generateClaims(); // écrit la meta
            } else {
                ACTIVE_CLAIM_SIZE = metaClaimSize;
                ACTIVE_MAP_SIZE   = metaMapSize;
                KingdomsConfig.warnClaimSizeMismatch(metaClaimSize);
                KingdomsConfig.warnMapSizeMismatch(metaMapSize);
            }

            success.onFinish(ok);
        });
    }

    /* ===================== */
    /* ====== LOCK ========= */
    /* ===================== */

    public static boolean areClaimsLocked() {
        return CLAIMS_LOCKED;
    }

    public static void setClaimsLocked(boolean locked) {
        CLAIMS_LOCKED = locked;
        if (locked) {
            Bukkit.getLogger().warning("[Kingdoms] Claims LOCKED.");
        }
    }

    /* ===================== */
    /* ====== SAVE ========= */
    /* ===================== */

    public static void saveClaims() {
        claimsJSON.forceSave();
    }

    /* ===================== */
    /* ==== GENERATION ===== */
    /* ===================== */

    public static void generateClaimsIfEmpty() {
        if (!claimsJSON.getAllClaims().isEmpty()) {
            Bukkit.getLogger().info("[Kingdoms] Claims déjà présents, génération ignorée.");
            return;
        }
        generateClaims();
    }

    public static void generateClaims() {
        claimsJSON.clearNoSave();

        int halfMap = ACTIVE_MAP_SIZE / 2;

        for (int x = -halfMap; x < halfMap; x += ACTIVE_CLAIM_SIZE) {
            for (int z = -halfMap; z < halfMap; z += ACTIVE_CLAIM_SIZE) {
                int gx = (int) Math.floor((double) x / ACTIVE_CLAIM_SIZE);
                int gz = (int) Math.floor((double) z / ACTIVE_CLAIM_SIZE);
                claimsJSON.addClaimNoSave(new Claim(gx, gz));
            }
        }

        claimsJSON.setMeta(ACTIVE_MAP_SIZE, ACTIVE_CLAIM_SIZE);
    }

    /** Utilisé par /k reload hard */
    public static void hardRegenerateClaims() {
        ACTIVE_CLAIM_SIZE = KingdomsConfig.CONFIG_CLAIM_SIZE;
        ACTIVE_MAP_SIZE   = KingdomsConfig.CONFIG_MAP_SIZE;
        generateClaims();
    }


    /* ===================== */
    /* ====== CRUD ========= */
    /* ===================== */

    public static void addClaim(Claim claim) {
        if (CLAIMS_LOCKED) return;
        claimsJSON.addClaim(claim);
        saveClaims();
    }

    public static void removeClaim(int gridX, int gridZ) {
        if (CLAIMS_LOCKED) return;
        Claim c = claimsJSON.getClaim(gridX, gridZ);
        if (c != null) {
            claimsJSON.removeClaim(c);
            saveClaims();
        }
    }

    public static Claim getClaim(int gridX, int gridZ) {
        return claimsJSON.getClaim(gridX, gridZ);
    }

    public static Collection<Claim> getClaims() {
        return claimsJSON.getAllClaims().values();
    }

    public static Integer getNumClaims() {
        return claimsJSON.getAllClaims().size();
    }

    /* ===================== */
    /* ==== COORDINATES ==== */
    /* ===================== */

    public static Claim getClaimByCoordinates(int x, int z) {
        int gx = (int) Math.floor((double) x / ACTIVE_CLAIM_SIZE);
        int gz = (int) Math.floor((double) z / ACTIVE_CLAIM_SIZE);
        return getClaim(gx, gz);
    }


    /* ===================== */
    /* ====== KINGDOM ====== */
    /* ===================== */

    public static void setKingdomForClaim(int gridX, int gridZ, String kingdomName) {
        if (CLAIMS_LOCKED) return;
        Claim claim = getClaim(gridX, gridZ);
        if (claim != null) {
            claim.setKingdomName(kingdomName);
            addClaim(claim);
        }
    }

    public static boolean hasClaimForKingdom(String kingdomName) {
        for (Claim c : getClaims()) {
            if (kingdomName.equalsIgnoreCase(c.getKingdomName())) return true;
        }
        return false;
    }

    public static List<Claim> getClaimsByKingdomName(String name) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : getClaims()) {
            if (name.equalsIgnoreCase(c.getKingdomName())) {
                result.add(c);
            }
        }
        return result;
    }

    public static void transferClaimToKingdom(Claim claim, String kingdomName, String factionTag) {
        if (CLAIMS_LOCKED || claim == null || kingdomName == null) return;
        claim.setKingdomName(kingdomName);
        if (factionTag != null && !factionTag.isEmpty()) {
            claim.setFactionName(factionTag);
        }
        addClaim(claim);
    }

    public static void transferClaimToKingdom(Claim claim, String kingdomName) {
        transferClaimToKingdom(claim, kingdomName, "Paysans_" + kingdomName);
    }

    /* ===================== */
    /* ===== ADJACENCY ===== */
    /* ===================== */

    public static boolean isAdjacentToKingdomClaim(Claim claim, String kingdomName) {
        int x = claim.getGridX();
        int z = claim.getGridZ();

        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            Claim n = getClaim(x + d[0], z + d[1]);
            if (n != null && kingdomName.equalsIgnoreCase(n.getKingdomName())) {
                return true;
            }
        }
        return false;
    }

    public static List<Claim> getDefenderClaimsAdjacentToAttacker(String defender, String attacker) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : getClaims()) {
            if (defender.equalsIgnoreCase(c.getKingdomName())
                    && isAdjacentToKingdomClaim(c, attacker)) {
                result.add(c);
            }
        }
        return result;
    }

    /* ===================== */
    /* ====== CORNERS ====== */
    /* ===================== */

    public static boolean isCornerClaim(Claim claim) {
        int mapSize = ACTIVE_MAP_SIZE;
        int claimSize = ACTIVE_CLAIM_SIZE;

        int half = (mapSize / 2) / claimSize;
        int min = -half;
        int max = half - 1;

        int x = claim.getGridX();
        int z = claim.getGridZ();

        return (x == min && z == min)
                || (x == min && z == max)
                || (x == max && z == min)
                || (x == max && z == max);
    }

    /* ===================== */
    /* ====== META ========= */
    /* ===================== */

    public static Integer getStoredClaimSize() {
        return claimsJSON.getMetaClaimSize();
    }

    public static Integer getStoredMapSize() {
        return claimsJSON.getMetaMapSize();
    }
}
