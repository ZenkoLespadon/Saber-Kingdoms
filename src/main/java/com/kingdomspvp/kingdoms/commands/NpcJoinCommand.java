package com.kingdomspvp.kingdoms.commands;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class NpcJoinCommand extends KingdomCommand implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey npcKey;

    public NpcJoinCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.aliases = List.of("npcjoin");
        this.helpShort = ChatColor.GRAY + "Place un PNJ pour rejoindre un royaume";
        this.permission = "kingdoms.admin.npcjoin";
        this.npcKey = new NamespacedKey(plugin, "kingdom_join_npc");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void perform(KingdomCommandContext context) {
        if (context.player == null) {
            context.msg(ChatColor.RED + "Commande réservée aux joueurs.");
            return;
        }
        Player p = context.player;

        Location loc = p.getLocation();
        Villager v = (Villager) p.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName(ChatColor.GOLD + "Rejoindre un Royaume");
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setCollidable(false);

        // Marquage pour le reconnaître plus tard (protection + interaction)
        v.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);

        context.msg(ChatColor.GREEN + "Villageois de sélection créé.");
    }

    @Override
    public String getUsageTranslation() {
        return "/k npcjoin";
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        Byte marker = v.getPersistentDataContainer().get(npcKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return;
        e.setCancelled(true);
        openKingdomMenu(e.getPlayer());
    }

    @EventHandler
    public void onVillagerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Villager v)) return;
        Byte marker = v.getPersistentDataContainer().get(npcKey, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) e.setCancelled(true);
    }

    private void openKingdomMenu(Player p) {
        List<Kingdom> kingdoms = KingdomsManager.getAllKingdoms();
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Choisis ton Royaume");

        int start = 13 - kingdoms.size() / 2;
        for (int i = 0; i < kingdoms.size(); i++) {
            Kingdom k = kingdoms.get(i);
            Material mat;
            try {
                mat = Material.valueOf(k.getColor().name() + "_WOOL");
            } catch (IllegalArgumentException ex) {
                mat = Material.WHITE_WOOL;
            }
            ItemStack wool = new ItemStack(mat);
            ItemMeta meta = wool.getItemMeta();
            meta.setDisplayName(k.getColor() + k.getName());
            wool.setItemMeta(meta);
            inv.setItem(start + i, wool);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Menu principal
        if (e.getView().getTitle().equals(ChatColor.DARK_GREEN + "Choisis ton Royaume")) {
            e.setCancelled(true);
            String kingdomName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            Kingdom k = KingdomsManager.getKingdomByName(kingdomName);
            if (k == null) return;
            openConfirmMenu(p, k);
            return;
        }

        // Confirmation
        if (e.getView().getTitle().startsWith(ChatColor.DARK_RED + "Confirmer ")) {
            e.setCancelled(true);
            String kingdomName = ChatColor.stripColor(e.getView().getTitle().replace("Confirmer ", ""));
            Kingdom k = KingdomsManager.getKingdomByName(kingdomName);
            if (k == null) return;

            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (name.equalsIgnoreCase("Oui")) {
                if (KingdomsManager.getKingdomOfPlayer(p) != null) {
                    p.sendMessage(ChatColor.RED + "Vous avez déjà un royaume.");
                    p.closeInventory();
                    return;
                }
                KingdomsManager.addPlayerToDefaultFactionOfKingdom(p, k);
                p.sendMessage(ChatColor.GREEN + "Vous avez rejoint le royaume " + k.getColor() + k.getName());
                p.closeInventory();
            } else if (name.equalsIgnoreCase("Non")) {
                p.closeInventory();
            }
        }
    }

    private void openConfirmMenu(Player p, Kingdom k) {
        Inventory confirm = Bukkit.createInventory(null, 9, ChatColor.DARK_RED + "Confirmer " + k.getName());

        ItemStack yes = new ItemStack(Material.LIME_WOOL);
        ItemMeta yMeta = yes.getItemMeta();
        yMeta.setDisplayName(ChatColor.GREEN + "Oui");
        yes.setItemMeta(yMeta);

        ItemStack no = new ItemStack(Material.RED_WOOL);
        ItemMeta nMeta = no.getItemMeta();
        nMeta.setDisplayName(ChatColor.RED + "Non");
        no.setItemMeta(nMeta);

        confirm.setItem(3, yes);
        confirm.setItem(5, no);
        p.openInventory(confirm);
    }
}
