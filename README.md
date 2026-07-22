# Saber-Kingdoms

**Saber-Kingdoms** is a Minecraft PvP territorial warfare plugin built as a fork of **Saber-Factions**.

This project keeps the original Factions plugin as its foundation and adds my own **Kingdoms** system on top of it. I did not rewrite the Factions core; my work focuses on the additional gameplay layer that connects factions, territory control, and organized wars.

Kingdoms allow multiple factions to unite under a single entity, control territory, and fight structured wars while preserving the persistent nature of the Minecraft world.

---

## Overview

The custom Kingdoms layer introduces:

- a political system above factions, where several factions can belong to the same kingdom
- grid-based territorial claims linked to kingdom ownership
- scheduled wars between kingdoms, with registration and battle phases
- territory capture mechanics during battles
- temporary world modifications during war, restored afterward to avoid permanent damage
- commands, listeners, managers, JSON persistence, and configurable gameplay settings

The system is integrated into the existing Bukkit/Spigot event model. Commands and listeners react to player actions, while service classes manage kingdom state, claims, wars, and runtime battle logic.

## Requirements

- Java 17+
- Spigot or Paper Minecraft server
- Vault
- ProtocolLib
- LuckPerms

Optional integrations:

- WorldGuard
- PlaceholderAPI
- PlayerVaults
- dynmap
