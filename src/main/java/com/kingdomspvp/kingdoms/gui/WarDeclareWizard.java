package com.kingdomspvp.kingdoms.gui;

import com.kingdomspvp.kingdoms.model.Kingdom;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import com.kingdomspvp.kingdoms.services.KingdomsManager;
import com.kingdomspvp.kingdoms.services.WarManager;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class WarDeclareWizard implements Listener {

    private static final String TITLE_KINGDOMS = ChatColor.DARK_GREEN + "Déclare la guerre : Royaume";
    private static final String TITLE_DAY      = ChatColor.DARK_GREEN + "Choix du jour";
    private static final String TITLE_HOUR     = ChatColor.DARK_GREEN + "Choix de l'heure";
    private static final String TITLE_MINUTE   = ChatColor.DARK_GREEN + "Choix des minutes";
    private static final String TITLE_CONFIRM  = ChatColor.RED + "Déclaration de guerre"; // (dernier écran)

    // Slots validation sur les écrans de confirmation
    private static final int SLOT_RED   = 11;
    private static final int SLOT_MID   = 13;
    private static final int SLOT_GREEN = 15;

    private enum Step { KINGDOM, DAY, HOUR, MINUTE, CONFIRM }

    private static final class State {
        final UUID playerId;
        Kingdom attacker, defender;
        LocalDate day;
        Integer hour, minute;
        Step step = Step.KINGDOM;
        int page = 0; // pagination royaumes
        State(UUID id) { this.playerId = id; }
        LocalDateTime when() { return LocalDateTime.of(day, LocalTime.of(hour, minute)); }
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    // ---------- API ----------
    public static void openFor(Player p) {
        if (p == null) return;
        State s = new State(p.getUniqueId());
        FPlayer fp = FPlayers.getInstance().getByPlayer(p);
        Faction fac = (fp != null) ? fp.getFaction() : null;
        if (fac == null || fac.isWilderness()) { p.sendMessage(ChatColor.RED + "Vous devez être dans une faction."); return; }
        Kingdom atk = KingdomsManager.getKingdomByFactionName(fac.getTag());
        if (atk == null) { p.sendMessage(ChatColor.RED + "Votre faction n'appartient à aucun royaume."); return; }
        s.attacker = atk;
        STATES.put(p.getUniqueId(), s);
        openKingdomList(p, s);
    }

    // ---------- RENDERERS ----------
    // Page Royaumes : 2 pages max, inventaire simple (27), laines colorées, centrées
    private static void openKingdomList(Player p, State s) {
        s.step = Step.KINGDOM;

        List<Kingdom> all = KingdomsManager.getAllKingdoms()
                .stream().filter(k -> !k.equals(s.attacker)).collect(Collectors.toList());

        int perPage = 14; // on centrera sur 2 lignes (7+7)
        int pages = Math.max(1, (int)Math.ceil(all.size() / (double)perPage));
        s.page = Math.max(0, Math.min(s.page, pages - 1));

        int from = s.page * perPage;
        int to = Math.min(all.size(), from + perPage);
        List<Kingdom> pageItems = all.subList(from, to);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_KINGDOMS + ChatColor.GRAY + " (page " + (s.page+1) + "/" + pages + ")");

        // Construire les items laines colorées
        List<ItemStack> items = new ArrayList<>();
        for (Kingdom k : pageItems) {
            ItemStack it = new ItemStack(woolOf(k.getColor()));
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(k.getColor() + k.getName());
            m.setLore(Collections.singletonList(ChatColor.GRAY + "Clique pour cibler ce royaume"));
            it.setItemMeta(m);
            items.add(it);
        }

        // Centrer sur 2 lignes (7 slots centrés : colonnes 1..7 → indices [10..16] et [19..25])
        placeCenteredRow(inv, items, 1); // ligne du milieu
        if (items.size() > 7) {
            placeCenteredRow(inv, items.subList(7, items.size()), 2);
        }

        // Pagination (verres pour éviter confusion) en bas
        if (s.page > 0) inv.setItem(18, makeItem(glassRed(), ChatColor.YELLOW + "Page précédente"));
        if (to < all.size()) inv.setItem(26, makeItem(glassGreen(), ChatColor.YELLOW + "Page suivante"));

        // Annuler global (verre rouge)
        inv.setItem(9, makeItem(glassRed(), ChatColor.RED + "Annuler"));

        p.openInventory(inv);
    }

    // Page Jour : inventaire simple (27), éléments centrés, format JJ/MM/AAAA
    private static void openDayList(Player p, State s) {
        s.step = Step.DAY;
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_DAY);

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i <= 3; i++) {
            LocalDate d = LocalDate.now().plusDays(i);
            String label = (i==0 ? "Aujourd'hui" : (i==1 ? "Demain" : fmtDateFR(d)));
            ItemStack it = makeItem(Material.CLOCK, ChatColor.AQUA + label,
                    ChatColor.GRAY + "Date: " + fmtDateFR(d));
            items.add(it);
        }
        placeCenteredRow(inv, items, 1);

        inv.setItem(9, makeItem(glassRed(), ChatColor.RED + "Annuler"));
        p.openInventory(inv);
    }

    // Page Heure : inventaire simple (27), items "Xh", Annuler en case 27 (index 26)
    private static void openHourList(Player p, State s) {
        s.step = Step.HOUR;
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_HOUR);

        List<ItemStack> items = new ArrayList<>();
        for (Integer h = 0; h < 24; h++) {
            items.add(makeItem(Material.OAK_SIGN, ChatColor.AQUA + h.toString() + "h"));
        }
        // On affiche 18 heures (0..17) ligne 1 et 2, puis 6 (18..23) centrées ligne 3
        placeCenteredRow(inv, items.subList(0, 9), 0);
        placeCenteredRow(inv, items.subList(9, 18), 1);
        placeCenteredRow(inv, items.subList(18, 24), 2);

        inv.setItem(26, makeItem(glassRed(), ChatColor.RED + "Annuler")); // case 27
        p.openInventory(inv);
    }

    // Page Minute : inventaire simple (27), sélection toutes les 5 minutes, format hh:mm, items centrés
    private static void openMinuteList(Player p, State s) {
        s.step = Step.MINUTE;
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MINUTE);

        List<ItemStack> minutes = new ArrayList<>();
        for (int m = 0; m < 60; m += 5) {
            minutes.add(makeItem(Material.MAP, ChatColor.AQUA + fmtHHMM(s.hour, m)));
        }
        // 12 items (0..55) → 5-min steps = 12 ; on centre sur 2 lignes
        placeCenteredRow(inv, minutes.subList(0, 6), 1);
        placeCenteredRow(inv, minutes.subList(6, 12), 2);

        inv.setItem(9, makeItem(glassRed(), ChatColor.RED + "Annuler"));
        p.openInventory(inv);
    }

    // Écrans de confirmation (avec verres colorés et choix centré)
    private static void openChoiceConfirm(Player p, State s, ItemStack choice, String title) {
        Inventory inv = Bukkit.createInventory(null, 27, title);
        inv.setItem(SLOT_RED,   makeItem(glassRed(), ChatColor.RED + "Annuler"));
        inv.setItem(SLOT_MID,   choice);
        inv.setItem(SLOT_GREEN, makeItem(glassGreen(), ChatColor.GREEN + "Valider"));
        p.openInventory(inv);
    }

    private static void openConfirmKingdom(Player p, State s) {
        ItemStack choice = new ItemStack(woolOf(s.defender.getColor()));
        ItemMeta m = choice.getItemMeta();
        m.setDisplayName(s.defender.getColor() + s.defender.getName());
        m.setLore(Collections.singletonList(ChatColor.GRAY + "Royaume cible"));
        choice.setItemMeta(m);
        openChoiceConfirm(p, s, choice, ChatColor.DARK_GREEN + "Confirmer le royaume");
    }

    private static void openConfirmDay(Player p, State s, LocalDate d) {
        ItemStack choice = makeItem(Material.CLOCK, ChatColor.AQUA + fmtDateFR(d),
                ChatColor.GRAY + "Jour sélectionné", ChatColor.DARK_GRAY + fmtDateFR(d)); // date en bas
        openChoiceConfirm(p, s, choice, ChatColor.DARK_GREEN + "Confirmer le jour");
    }

    private static void openConfirmHour(Player p, State s) {
        String label = s.hour + "h";
        ItemStack choice = makeItem(Material.OAK_SIGN, ChatColor.AQUA + label,
                ChatColor.GRAY + "Heure sélectionnée");
        openChoiceConfirm(p, s, choice, ChatColor.DARK_GREEN + "Confirmer l'heure — " + ChatColor.AQUA + label);
    }

    private static void openConfirmMinute(Player p, State s) {
        String hhmm = fmtHHMM(s.hour, s.minute);
        ItemStack choice = makeItem(Material.MAP, ChatColor.AQUA + hhmm,
                ChatColor.GRAY + "Minute sélectionnée");
        openChoiceConfirm(p, s, choice, ChatColor.DARK_GREEN + "Confirmer la minute — " + ChatColor.AQUA + hhmm);
    }

    // Dernier écran (titre rouge “Déclaration de guerre”)
    private static void openFinalConfirm(Player p, State s) {
        String sum1 = ChatColor.GOLD + "Royaume: " + ChatColor.AQUA + s.defender.getName();
        String sum2 = ChatColor.GOLD + "Jour: " + ChatColor.AQUA + fmtDateFR(s.day);
        String sum3 = ChatColor.GOLD + "Heure: " + ChatColor.AQUA + fmtHHMM(s.hour, s.minute);
        ItemStack paper = makeItem(Material.PAPER, ChatColor.RED + "Déclaration de guerre", sum1, sum2, sum3);
        openChoiceConfirm(p, s, paper, TITLE_CONFIRM);
    }

    // ---------- EVENTS ----------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        State s = STATES.get(p.getUniqueId());
        if (s == null) return;

        String title = e.getView().getTitle();
        if (!isOurTitle(title)) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;

        // Annuler boutons globaux
        if (isCancel(it)) { cleanup(p); p.closeInventory(); p.sendMessage(ChatColor.RED + "Déclaration annulée."); return; }

        // --- Royaumes ---
        if (title.startsWith(TITLE_KINGDOMS)) {
            // pagination via verres aux bords
            if (it.getType() == glassRed() && e.getSlot() == 18 && s.page > 0) { s.page--; openKingdomList(p, s); return; }
            if (it.getType() == glassGreen() && e.getSlot() == 26) { s.page++; openKingdomList(p, s); return; }

            // Clic sur laine = choix du royaume
            String name = (it.hasItemMeta() && it.getItemMeta().hasDisplayName())
                    ? ChatColor.stripColor(it.getItemMeta().getDisplayName()) : null;
            if (name != null) {
                Kingdom k = KingdomsManager.getKingdomByName(name);
                if (k != null && !k.equals(s.attacker)) {
                    s.defender = k;
                    openConfirmKingdom(p, s);
                    return;
                }
            }
        }

        // Confirm Royaume
        if (title.contains("Confirmer le royaume")) {
            if (e.getSlot() == SLOT_GREEN) { openDayList(p, s); return; }
            if (e.getSlot() == SLOT_RED)   { openKingdomList(p, s); return; }
        }

        // --- Jours ---
        if (title.equals(TITLE_DAY)) {
            if (it.getType() == Material.CLOCK) {
                List<String> lore = it.getItemMeta().getLore();
                // la 2e ligne contient "Date: JJ/MM/AAAA"
                String line = (lore != null && lore.size() >= 1) ? ChatColor.stripColor(lore.get(0)) : null;
                // on retrouve la date depuis le label principal
                String main = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                LocalDate d;
                if (main.contains("Aujourd"))      d = LocalDate.now();
                else if (main.contains("Demain"))  d = LocalDate.now().plusDays(1);
                else                                d = parseFR(main);
                s.day = d;
                openConfirmDay(p, s, d);
                return;
            }
        }
        if (title.contains("Confirmer le jour")) {
            if (e.getSlot() == SLOT_GREEN) { openHourList(p, s); return; }
            if (e.getSlot() == SLOT_RED)   { openDayList(p, s); return; }
        }

        // --- Heures ---
        if (title.equals(TITLE_HOUR)) {
            if (e.getSlot() == 26) { cleanup(p); p.closeInventory(); p.sendMessage(ChatColor.RED + "Déclaration annulée."); return; }
            String label = ChatColor.stripColor(it.getItemMeta().getDisplayName()); // ex "13h"
            if (label.endsWith("h")) label = label.substring(0, label.length()-1);
            try { s.hour = Integer.parseInt(label); openConfirmHour(p, s); } catch (NumberFormatException ignored) {}
            return;
        }
        if (title.contains("Confirmer l'heure")) {
            if (e.getSlot() == SLOT_GREEN) { openMinuteList(p, s); return; }
            if (e.getSlot() == SLOT_RED)   { openHourList(p, s); return; }
        }

        // --- Minutes ---
        if (title.equals(TITLE_MINUTE)) {
            String label = ChatColor.stripColor(it.getItemMeta().getDisplayName()); // "hh:mm"
            int h = Integer.parseInt(label.substring(0, label.indexOf(":")));
            int m = Integer.parseInt(label.substring(label.indexOf(":")+1));
            s.hour = h; s.minute = m;
            openConfirmMinute(p, s);
            return;
        }
        if (title.contains("Confirmer la minute")) {
            if (e.getSlot() == SLOT_GREEN) { openFinalConfirm(p, s); return; }
            if (e.getSlot() == SLOT_RED)   { openMinuteList(p, s); return; }
        }

        // --- Dernière page (titre rouge) ---
        if (title.equals(TITLE_CONFIRM)) {
            if (e.getSlot() == SLOT_GREEN) {
                if (s.defender == null || s.day == null || s.hour == null || s.minute == null) { p.sendMessage(ChatColor.RED + "Sélection incomplète."); return; }
                List<com.kingdomspvp.kingdoms.model.Claim> attackable =
                        ClaimManager.getDefenderClaimsAdjacentToAttacker(s.defender.getName(), s.attacker.getName());
                if (attackable.isEmpty()) { p.sendMessage(ChatColor.RED + "Aucune frontière commune."); return; }
                LocalDateTime when = s.when();
                LocalDateTime now  = LocalDateTime.now();
                if (when.isBefore(now.plus(WarManager.MIN_TIME_BEFORE_WAR))) { p.sendMessage(ChatColor.RED + "Délais insuffisant (" + WarManager.MIN_TIME_BEFORE_WAR.toMinutes() + " min)."); return; }
                if (when.isAfter(now.plus(WarManager.MAX_TIME_BEFORE_WAR)))  { p.sendMessage(ChatColor.RED + "Trop à l'avance (" + WarManager.MAX_TIME_BEFORE_WAR.toMinutes() + " min)."); return; }
                if (WarManager.hasPendingWar(s.attacker, s.defender))         { p.sendMessage(ChatColor.RED + "Une guerre est déjà programmée."); return; }

                War w = WarManager.declareWar(s.defender, s.attacker, when.toString(), attackable);
                WarManager.sendMessagetoPlayersOfKingdoms(w);
                p.sendMessage(ChatColor.GREEN + "Guerre déclarée contre " + ChatColor.AQUA + s.defender.getName()
                        + ChatColor.GREEN + " le " + ChatColor.AQUA + fmtDateFR(when.toLocalDate())
                        + ChatColor.GREEN + " à " + ChatColor.AQUA + fmtHHMM(when.getHour(), when.getMinute()));
                cleanup(p); p.closeInventory(); return;
            }
            if (e.getSlot() == SLOT_RED) { openMinuteList(p, s); return; }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // pas de cleanup forcé ici pour permettre retour si besoin
    }

    // ---------- UTILS ----------
    private static boolean isOurTitle(String t) {
        return t != null && (t.startsWith(TITLE_KINGDOMS) || t.equals(TITLE_DAY) || t.equals(TITLE_HOUR)
                || t.equals(TITLE_MINUTE) || t.contains("Confirmer") || t.equals(TITLE_CONFIRM));
    }

    private static void placeCenteredRow(Inventory inv, List<ItemStack> items, int row) {
        if (items == null || items.isEmpty()) return;
        int width = 9;
        int rowStart = row * width; // 0,9,18
        int count = Math.min(items.size(), width - 2); // éviter bords
        int startCol = (width - count) / 2; // centré
        for (int i = 0; i < count; i++) {
            inv.setItem(rowStart + startCol + i, items.get(i));
        }
    }

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            m.setLore(Arrays.stream(lore).collect(Collectors.toList()));
        }
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack cloneWithLore(ItemStack base, String... lore) {
        ItemStack it = base.clone();
        ItemMeta m = it.getItemMeta();
        m.setLore(Arrays.asList(lore));
        it.setItemMeta(m);
        return it;
    }

    private static String fmtDateFR(LocalDate d) {
        int j = d.getDayOfMonth(), m = d.getMonthValue(), a = d.getYear();
        return String.format("%02d/%02d/%04d", j, m, a);
    }
    private static LocalDate parseFR(String label) {
        // label "JJ/MM/AAAA"
        String[] p = label.split("/");
        return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
    }
    private static String fmtHHMM(Integer h, Integer m) {
        int hh = (h == null ? 0 : h);
        int mm = (m == null ? 0 : m);
        return String.format("%02d:%02d", hh, mm);
    }

    private static Material woolOf(ChatColor c) {
        if (c == null) return Material.WHITE_WOOL;
        switch (c) {
            case RED: return Material.RED_WOOL;
            case GREEN: return Material.GREEN_WOOL;
            case BLUE: return Material.BLUE_WOOL;
            case YELLOW: return Material.YELLOW_WOOL;
            case GOLD: return Material.ORANGE_WOOL;
            case DARK_PURPLE:
            case LIGHT_PURPLE: return Material.MAGENTA_WOOL;
            case DARK_GREEN: return Material.GREEN_WOOL;
            case AQUA: return Material.LIGHT_BLUE_WOOL;
            case DARK_AQUA: return Material.CYAN_WOOL;
            case DARK_BLUE: return Material.BLUE_WOOL;
            case GRAY: return Material.LIGHT_GRAY_WOOL;
            case DARK_GRAY: return Material.GRAY_WOOL;
            case BLACK: return Material.BLACK_WOOL;
            case WHITE: return Material.WHITE_WOOL;
            default: return Material.WHITE_WOOL;
        }
    }
    private static Material glassRed()   { return Material.RED_STAINED_GLASS; }
    private static Material glassGreen() { return Material.GREEN_STAINED_GLASS; }

    private static boolean isCancel(ItemStack it) {
        return (it.getType() == glassRed()) &&
                it.hasItemMeta() && it.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(it.getItemMeta().getDisplayName()).equalsIgnoreCase("Annuler");
    }

    private static void cleanup(Player p) {
        if (p == null) return;
        STATES.remove(p.getUniqueId());
    }
}
