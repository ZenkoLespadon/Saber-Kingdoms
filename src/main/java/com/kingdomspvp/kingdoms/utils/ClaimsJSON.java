package com.kingdomspvp.kingdoms.utils;

import com.google.gson.reflect.TypeToken;
import com.kingdomspvp.kingdoms.model.Claim;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.zcore.util.DiscUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ClaimsJSON {

    private final Path path;
    private final Map<String, Claim> claims;

    public ClaimsJSON() {
        this.path = FactionsPlugin.getInstance().getDataFolder().toPath().resolve("claims.json");
        this.claims = new HashMap<>();
    }

    // Sauvegarde
    public void forceSave() {
        forceSave(true);
    }

    public void forceSave(boolean sync) {
        saveCore(path, claims, sync);
    }

    private boolean saveCore(Path target, Map<String, Claim> entities, boolean sync) {
        return DiscUtil.writeCatch(target, FactionsPlugin.getInstance().getGson().toJson(entities), sync);
    }

    // Chargement
    public void load(Callback<Boolean> success) {
        loadCore(data -> {
            if (data == null) {
                Logger.print("Aucun claim chargé. Démarrage frais ?", Logger.PrefixType.DEFAULT);
                success.onFinish(true);
                return;
            }
            this.claims.putAll(data);
            Logger.print("Chargé " + claims.size() + " claims", Logger.PrefixType.DEFAULT);
            success.onFinish(true);
        });
    }

    private void loadCore(Callback<Map<String, Claim>> finish) {
        if (Files.notExists(this.path)) {
            finish.onFinish(new HashMap<>());
            return;
        }

        String content = DiscUtil.readCatch(this.path);
        if (content == null) {
            finish.onFinish(null);
            return;
        }

        Map<String, Claim> data = FactionsPlugin.getInstance().getGson().fromJson(content, new TypeToken<Map<String, Claim>>() {}.getType());
        if (data == null) {
            finish.onFinish(null);
            return;
        }

        finish.onFinish(data);
    }

    // Gestion des claims
    public void addClaim(Claim claim) {
        this.claims.put(key(claim), claim);
        forceSave();
    }

    public void removeClaim(Claim claim) {
        this.claims.remove(key(claim));
        forceSave();
    }

    public Claim getClaim(int gridX, int gridZ) {
        return this.claims.get(key(gridX, gridZ));
    }

    public Map<String, Claim> getAllClaims() {
        return new HashMap<>(this.claims);
    }

    private String key(Claim claim) {
        return key(claim.getGridX(), claim.getGridZ());
    }

    private String key(int gridX, int gridZ) {
        return gridX + "," + gridZ;
    }

    public void clear() {
        this.claims.clear();
        forceSave();
    }

}

