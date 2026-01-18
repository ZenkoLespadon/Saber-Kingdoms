package com.kingdomspvp.kingdoms.utils.data;

import com.google.gson.reflect.TypeToken;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.utils.Callback;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.zcore.util.DiscUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class WarsJSON {
    // Chemin vers le fichier JSON
    private final Path path;
    private final Map<String, War> wars;

    public WarsJSON() {
        this.path = FactionsPlugin.getInstance()
                .getDataFolder().toPath()
                .resolve("wars.json");
        this.wars = new HashMap<>();
    }

    /** Force la sauvegarde synchrone dans le fichier */
    public void forceSave() {
        forceSave(true);
    }

    /** Sauvegarde (sync ou async) */
    public void forceSave(boolean sync) {
        saveCore(path, wars, sync);
    }

    private boolean saveCore(Path target, Map<String, War> entities, boolean sync) {
        String json = FactionsPlugin.getInstance()
                .getGson()
                .toJson(entities);
        return DiscUtil.writeCatch(target, json, sync);
    }

    /**
     * Charge les guerres depuis le JSON.
     * @param success callback avec true si chargé (même si vide).
     */
    public void load(Callback<Boolean> success) {
        loadCore(data -> {
            if (data == null) {
                Logger.print("Aucune guerre chargée. Démarrage frais ?", Logger.PrefixType.DEFAULT);
                success.onFinish(true);
                return;
            }
            this.wars.putAll(data);
            Logger.print("Chargé " + wars.size() + " guerres", Logger.PrefixType.DEFAULT);
            success.onFinish(true);
        });
    }

    private void loadCore(Callback<Map<String, War>> finish) {
        if (Files.notExists(this.path)) {
            finish.onFinish(new HashMap<>());
            return;
        }

        String content = DiscUtil.readCatch(this.path);
        if (content == null) {
            finish.onFinish(null);
            return;
        }

        Map<String, War> data = FactionsPlugin.getInstance()
                .getGson()
                .fromJson(content, new TypeToken<Map<String, War>>() {}.getType());

        finish.onFinish(data != null ? data : new HashMap<>());
    }

    /** Ajoute et sauvegarde immédiatement une nouvelle guerre */
    public void addWar(War war) {
        this.wars.put(war.getId(), war);
        forceSave();
    }

    /** Supprime une guerre terminée */
    public void removeWar(String warId) {
        this.wars.remove(warId);
        forceSave();
    }

    /** Récupère une guerre par son ID */
    public War getWar(String warId) {
        return this.wars.get(warId);
    }

    /** Retourne une copie de toutes les guerres */
    public Map<String, War> getAllWars() {
        return new HashMap<>(this.wars);
    }
}
