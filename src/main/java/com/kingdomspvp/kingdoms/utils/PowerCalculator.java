package com.kingdomspvp.kingdoms.utils;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PowerCalculator {

    public static int compute(Player p) {
        int kda = computeKDA(p);       // max 50 pts
        int jobs = computeJobs(p);     // max 50 pts (implémentation plus tard)

        return kda + jobs;             // /100 total
    }

    public static int computeKDA(Player p) {
        String killsStr = PlaceholderAPI.setPlaceholders(p, "%ndplayerstats_kills%");
        String deathsStr = PlaceholderAPI.setPlaceholders(p, "%ndplayerstats_deaths%");

        int kills = parse(killsStr);
        int deaths = parse(deathsStr);

        double score = (kills * 3.0) - (deaths * 1.5);
        if (score < 0) score = 0;

        if (score > 50) score = 50;

        return (int) score;
    }

    public static int computeJobs(Player p) {
        JobsPlayer jp = Jobs.getPlayerManager().getJobsPlayer(p);
        if (jp == null) return 0;

        int totalLevels = 0;

        for (JobProgression prog : jp.getJobProgression()) {
            totalLevels += prog.getLevel();
        }

        double power = totalLevels / 2.0;

        return (int) Math.min(50, power);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }
}