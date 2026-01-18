package com.kingdomspvp.kingdoms.utils.data;

import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.utils.Callback;
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
        ClaimsFile file = new ClaimsFile();
        file.meta = loadExistingMeta();
        file.claims = new HashMap<>(this.claims);

        DiscUtil.writeCatch(
                path,
                FactionsPlugin.getInstance().getGson().toJson(file),
                sync
        );
    }

    private ClaimsFile.Meta loadExistingMeta() {
        try {
            if (Files.notExists(this.path)) return null;

            String content = DiscUtil.readCatch(this.path);
            if (content == null || content.isEmpty()) return null;

            ClaimsFile file = FactionsPlugin.getInstance().getGson()
                    .fromJson(content, ClaimsFile.class);

            return file != null ? file.meta : null;
        } catch (Exception e) {
            return null;
        }
    }


    private boolean saveCore(Path target, Map<String, Claim> entities, boolean sync) {
        return DiscUtil.writeCatch(target, FactionsPlugin.getInstance().getGson().toJson(entities), sync);
    }

    public Integer getMetaClaimSize() {
        if (Files.notExists(this.path)) return null;

        try {
            String content = DiscUtil.readCatch(this.path);
            if (content == null || content.isEmpty()) return null;

            ClaimsFile file = FactionsPlugin.getInstance().getGson()
                    .fromJson(content, ClaimsFile.class);

            if (file == null || file.meta == null) return null;
            return file.meta.claimSize;

        } catch (Exception e) {
            return null;
        }
    }

    public Integer getMetaMapSize() {
        if (Files.notExists(this.path)) return null;

        try {
            String content = DiscUtil.readCatch(this.path);
            if (content == null || content.isEmpty()) return null;

            ClaimsFile file = FactionsPlugin.getInstance().getGson()
                    .fromJson(content, ClaimsFile.class);

            if (file == null || file.meta == null) return null;
            return file.meta.mapSize;

        } catch (Exception e) {
            return null;
        }
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
        if (content == null || content.isEmpty()) {
            finish.onFinish(null);
            return;
        }

        try {
            ClaimsFile file = FactionsPlugin.getInstance().getGson()
                    .fromJson(content, ClaimsFile.class);

            if (file == null || file.claims == null) {
                finish.onFinish(new HashMap<>());
                return;
            }

            finish.onFinish(file.claims);

        } catch (Exception e) {
            Logger.print("Erreur parsing claims.json, fichier invalide", Logger.PrefixType.WARNING);
            e.printStackTrace();
            finish.onFinish(null);
        }
    }
    // dans ClaimsJSON
    public void clearNoSave() {
        this.claims.clear();
    }

    public void addClaimNoSave(Claim claim) {
        this.claims.put(key(claim), claim);
    }

    // expose path si besoin pour /k reload hard
    public java.nio.file.Path getPath() {
        return this.path;
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

    public void setMeta(int mapSize, int claimSize) {
        ClaimsFile file = new ClaimsFile();
        file.meta = new ClaimsFile.Meta();
        file.meta.mapSize = mapSize;
        file.meta.claimSize = claimSize;
        file.claims = new HashMap<>(this.claims);

        DiscUtil.writeCatch(
                this.path,
                FactionsPlugin.getInstance().getGson().toJson(file),
                true
        );
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

    public class ClaimsFile {
        public Meta meta;
        public Map<String, Claim> claims;

        public static class Meta {
            public int claimSize;
            public int mapSize;
        }
    }
}

