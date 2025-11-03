package com.kingdomspvp.kingdoms.services;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.utils.Callback;
import com.kingdomspvp.kingdoms.utils.ClaimsJSON;

import java.util.*;

public class ClaimManager {

    private static final ClaimsJSON claimsJSON = new ClaimsJSON();

    public static final int MAP_SIZE = 1024;
    public static final int CLAIM_SIZE = 64;
    public static final int HALF_MAP = MAP_SIZE / 2;

    /**
     * Chargement des claims
     */
    public static void loadClaims(Callback<Boolean> success) {
        claimsJSON.load(success);
    }

    /**
     * Sauvegarde des claims
     */
    public static void saveClaims() {
        claimsJSON.forceSave();
    }

    /**
     * Génération des claims sur toute la map si la map est vide
     */
    public static void generateClaimsIfEmpty() {
        if (!claimsJSON.getAllClaims().isEmpty()) {
            System.out.println("[Kingdoms] Claims déjà présents, génération ignorée.");
            return;
        }

        int startX = -HALF_MAP;
        int startZ = -HALF_MAP;

        for (int x = startX; x < startX + MAP_SIZE; x += CLAIM_SIZE) {
            for (int z = startZ; z < startZ + MAP_SIZE; z += CLAIM_SIZE) {
                int gridX = (int) Math.floor((double) x / CLAIM_SIZE);
                int gridZ = (int) Math.floor((double) z / CLAIM_SIZE);
                Claim claim = new Claim(gridX, gridZ);
                claimsJSON.addClaim(claim);
            }
        }

        System.out.println("[Kingdoms] Claims générés : " + claimsJSON.getAllClaims().size());
    }

    /**
     * Ajout d'un claim unique
     */
    public static void addClaim(Claim claim) {
        claimsJSON.addClaim(claim);
        saveClaims();
    }

    /**
     * Suppression d'un claim
     */
    public static void removeClaim(int gridX, int gridZ) {
        String key = gridX + "," + gridZ;
        claimsJSON.removeClaim(claimsJSON.getClaim(gridX, gridZ));
        saveClaims();
    }

    /**
     * Retourne tous les claims
     */
    public static Collection<Claim> getClaims() {
        return claimsJSON.getAllClaims().values();
    }

    /**
     * Trouve un claim spécifique
     */
    public static Claim getClaim(int gridX, int gridZ) {
        return claimsJSON.getClaim(gridX, gridZ);
    }

    /**
     * Change le royaume d'un claim
     */
    public static void setKingdomForClaim(int gridX, int gridZ, String kingdomName) {
        Claim claim = getClaim(gridX, gridZ);
        if (claim != null) {
            claim.setKingdomName(kingdomName);
            addClaim(claim);
        }
    }

    public static Claim getClaimByCoordinates(int x, int z) {
        int gridX = (int) Math.floor((double) x / CLAIM_SIZE);
        int gridZ = (int) Math.floor((double) z / CLAIM_SIZE);
        return getClaim(gridX, gridZ);
    }

    public static Integer getNumClaims() {
        return claimsJSON.getAllClaims().size();
    }

    public static void generateClaims() {
        claimsJSON.clear();

        int startX = -HALF_MAP;
        int startZ = -HALF_MAP;

        for (int x = startX; x < startX + MAP_SIZE; x += CLAIM_SIZE) {
            for (int z = startZ; z < startZ + MAP_SIZE; z += CLAIM_SIZE) {
                int gridX = (int) Math.floor((double) x / CLAIM_SIZE);
                int gridZ = (int) Math.floor((double) z / CLAIM_SIZE);
                Claim claim = new Claim(gridX, gridZ);
                claimsJSON.addClaim(claim);
            }
        }
    }

    public static boolean isAdjacentToKingdomClaim(Claim claim, String kingdomName) {
        int x = claim.getGridX();
        int z = claim.getGridZ();

        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
        };

        for (int[] dir : directions) {
            Claim neighbor = ClaimManager.getClaim(x + dir[0], z + dir[1]);
            if (neighbor != null && kingdomName.equalsIgnoreCase(neighbor.getKingdomName())) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasClaimForKingdom(String kingdomName) {
        for (Claim c : getClaims()) {
            if (kingdomName.equalsIgnoreCase(c.getKingdomName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCornerClaim(Claim claim) {
        int gridX = claim.getGridX();
        int gridZ = claim.getGridZ();

        int half = ClaimManager.HALF_MAP / ClaimManager.CLAIM_SIZE;
        int min = -half;
        int max = half - 1;

        return (gridX == min && gridZ == min) ||
               (gridX == min && gridZ == max) ||
               (gridX == max && gridZ == min) ||
               (gridX == max && gridZ == max);
    }

    // services/ClaimManager.java
    public static List<Claim> getDefenderClaimsAdjacentToAttacker(String defenderKingdom, String attackerKingdom) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : getClaims()) {
            if (defenderKingdom.equalsIgnoreCase(c.getKingdomName())) {
                if (isAdjacentToKingdomClaim(c, attackerKingdom)) {
                    result.add(c);
                }
            }
        }
        return result;
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
        if (claim == null || kingdomName == null) return;
        claim.setKingdomName(kingdomName);
        if (factionTag != null && !factionTag.isEmpty()) {
            claim.setFactionName(factionTag);
        }
        addClaim(claim); // addClaim() force la sauvegarde
    }

    /**
     * Surcharge pratique si tu veux juste passer le royaume (faction tag auto: "Paysans_<royaume>")
     */
    public static void transferClaimToKingdom(Claim claim, String kingdomName) {
        String defaultFactionTag = "Paysans_" + kingdomName;
        transferClaimToKingdom(claim, kingdomName, defaultFactionTag);
    }
}
