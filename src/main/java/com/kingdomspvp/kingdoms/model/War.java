package com.kingdomspvp.kingdoms.model;

import com.kingdomspvp.kingdoms.model.Kingdom;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class War {
    private final String id; // MM_dd_HH_mm
    private final Kingdom attackerKingdom;
    private final Kingdom defenderKingdom;
    private final LocalDateTime startTime;
    private WarStatus status;
    private final Set<String> registeredFactions = new HashSet<>();

    public War(String id, Kingdom attacker, Kingdom defender, LocalDateTime start) {
        this.id = id;
        this.attackerKingdom = attacker;
        this.defenderKingdom = defender;
        this.startTime = start;
        this.status = WarStatus.REGISTRATION;
    }

    // ------------------
    // Getters & Setters
    // ------------------

    /** format "MM_dd_HH_mm" **/
    public String getId() {
        return id;
    }

    /** Royaume attaquant **/
    public Kingdom getAttackerKingdom() {
        return attackerKingdom;
    }

    /** Royaume défenseur **/
    public Kingdom getDefenderKingdom() {
        return defenderKingdom;
    }

    /** Date/heure de début **/
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** Statut courant **/
    public WarStatus getStatus() {
        return status;
    }

    public void setStatus(WarStatus status) {
        this.status = status;
    }

    /** Liste immuable des factions inscrites **/
    public Set<String> getRegisteredFactions() {
        return Collections.unmodifiableSet(registeredFactions);
    }

    // ------------------
    // Méthodes métiers
    // ------------------

    /** Vérifie si l'attaquant a assez de membres pour déclarer (>=1 pour l'instant) **/
    public boolean canDeclare() {
        int total = attackerKingdom.getFactions().stream()
                .mapToInt(f -> f.getFPlayers().size())
                .sum();
        return total >= 1;
    }

    /** Inscrire une faction à la guerre **/
    public void registerFaction(String factionName) {
        registeredFactions.add(factionName);
    }

    /** Cette faction est-elle déjà inscrite ? **/
    public boolean isRegistered(String factionName) {
        return registeredFactions.contains(factionName);
    }
}
