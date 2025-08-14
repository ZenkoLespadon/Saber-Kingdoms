package com.kingdomspvp.kingdoms.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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

    /** Vérifie si l'attaquant a assez de membres pour déclarer (>=1 pour l'instant, >= 5 plus tard) **/
    public boolean canDeclare() {
        int total = attackerKingdom.getFactions().stream()
                .mapToInt(f -> f.getFPlayers().size())
                .sum();
        return total >= 1;
    }

    private final Set<UUID> attackerPlayers = new HashSet<>();
    private final Set<UUID> defenderPlayers = new HashSet<>();

    public Set<UUID> getAttackerPlayers() {
        return Collections.unmodifiableSet(attackerPlayers);
    }

    public Set<UUID> getDefenderPlayers() {
        return Collections.unmodifiableSet(defenderPlayers);
    }

    /** Retourne true si ajouté, false si déjà présent ou mauvais camp */
    public boolean addParticipant(UUID playerId, Kingdom playerKingdom) {
        if (playerKingdom.equals(attackerKingdom)) {
            return attackerPlayers.add(playerId);
        } else if (playerKingdom.equals(defenderKingdom)) {
            return defenderPlayers.add(playerId);
        }
        return false; // joueur n'appartient à aucun des deux royaumes
    }
}
