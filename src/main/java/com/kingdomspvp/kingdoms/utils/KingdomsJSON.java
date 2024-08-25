package com.kingdomspvp.kingdoms.utils;

import com.google.gson.reflect.TypeToken;
import com.kingdomspvp.kingdoms.model.Kingdom;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.zcore.util.DiscUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class KingdomsJSON {
    // Path vers le fichier JSON
    private final Path path;
    private final Map<String, Kingdom> kingdoms;

    public KingdomsJSON() {
        this.path = FactionsPlugin.getInstance().getDataFolder().toPath().resolve("kingdoms.json");
        this.kingdoms = new HashMap<>();
    }

    // Sauvegarde des royaumes dans le fichier JSON
    public void forceSave() {
        forceSave(true);
    }

    public void forceSave(boolean sync) {
        saveCore(path, kingdoms, sync);
    }

    private boolean saveCore(Path target, Map<String, Kingdom> entities, boolean sync) {
        return DiscUtil.writeCatch(target, FactionsPlugin.getInstance().getGson().toJson(entities), sync);
    }

    // Chargement des royaumes à partir du fichier JSON
    public void load(Callback<Boolean> success) {
        loadCore(data -> {
            if (data == null) {
                Logger.print("Aucun royaume chargé. Démarrage frais ?", Logger.PrefixType.DEFAULT);
                success.onFinish(true);
                return;
            }
            this.kingdoms.putAll(data);
            Logger.print("Chargé " + kingdoms.size() + " royaumes", Logger.PrefixType.DEFAULT);
            success.onFinish(true);
        });
    }

    private void loadCore(Callback<Map<String, Kingdom>> finish) {
        if (Files.notExists(this.path)) {
            finish.onFinish(new HashMap<>());
            return;
        }
        String content = DiscUtil.readCatch(this.path);
        if (content == null) {
            finish.onFinish(null);
            return;
        }

        Map<String, Kingdom> data = FactionsPlugin.getInstance().getGson().fromJson(content, new TypeToken<Map<String, Kingdom>>() {}.getType());
        if (data == null) {
            finish.onFinish(null);
            return;
        }

        finish.onFinish(data);
    }

    // Gestion des royaumes
    public void addKingdom(Kingdom kingdom) {
        this.kingdoms.put(kingdom.getName(), kingdom);
        forceSave();
    }

    public void removeKingdom(String name) {
        this.kingdoms.remove(name);
        forceSave();
    }

    public Kingdom getKingdom(String name) {
        return this.kingdoms.get(name);
    }

    public Map<String, Kingdom> getAllKingdoms() {
        return new HashMap<>(this.kingdoms);
    }
}

