package com.kingdomspvp.kingdoms.utils;

import java.util.List;

public record KingdomsSettings(
        Claims claims,
        Visualization visualization,
        Commands commands,
        War war,
        Blocks blocks,
        Death death
) {
    public record Claims(int mapSize, int claimSize) {}

    public record Visualization(Particles particles) {
        public record Particles(float dustSize, int step, int countPerSpawn, int periodTicks,
                                int maxSpawnsPerTick, int maxDistanceBlocks) {}
    }

    public record Commands(VizClaims vizclaims) {
        public record VizClaims(int defaultSeconds, int minSeconds, int maxSeconds, int periodTicks) {}
    }

    public record War(boolean testMode, Planning planning, Detection detection, Combat combat, Teleport teleport, Kda kda) {
        public record Planning(int minTimeBeforeWarMinutes, int maxTimeBeforeWarHours, int joinPromptLeadSeconds) {}
        public record Detection(int windowSeconds) {}
        public record Combat(int durationSeconds, int maxRounds, double roundGainFactor, int targetPoints,
                             double targetFraction, double ratioMin, double ratioMax) {}
        public record Teleport(int offsetFromBorder, int yOffset) {}
        public record Kda(int assistWindowSeconds, boolean countSelfDamage) {}
    }

    public record Blocks(int revertAfterSeconds, List<String> forbidden) {}

    public record Death(int tpMessageDelaySeconds) {}
}
