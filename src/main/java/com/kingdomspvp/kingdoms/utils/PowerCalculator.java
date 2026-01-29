package com.kingdomspvp.kingdoms.utils;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PowerCalculator {

    public static int compute(Player p) {
        int kda = computeKDA(p);   // max 50
        int jobs = computeJobs(p); // max 50
        return Math.min(100, kda + jobs);
    }

    public static int computeKDA(Player p) {
        String killsStr = PlaceholderAPI.setPlaceholders(p, "%ndplayerstats_kills%");
        String deathsStr = PlaceholderAPI.setPlaceholders(p, "%ndplayerstats_deaths%");

        int kills = parse(killsStr);
        int deaths = parse(deathsStr);

        double score = 25 + (kills * 2.5) - (deaths * 1.5);
        if (score < 0) score = 0;
        if (score > 50) score = 50;

        return (int) score;
    }

    public static int computeJobs(Player p) {
        JobsPlayer jp = Jobs.getPlayerManager().getJobsPlayer(p);
        if (jp == null) return 0;

        double totalPower = 0;

        for (JobProgression prog : jp.getJobProgression()) {
            int lvl = prog.getLevel();
            double jobPower = Math.pow(Math.min(lvl, 15) / 15.0, 2.0) * 50.0;
            totalPower += jobPower;
        }

        if (totalPower > 50) totalPower = 50;
        return (int) totalPower;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static double effectivePower(java.util.Collection<Integer> powers) {
        if (powers == null || powers.isEmpty()) return 0.0;

        double a = 1.3;
        double sum = 0.0;

        for (int p : powers) {
            if (p < 0) p = 0;
            sum += Math.pow(p, a);
        }

        return Math.pow(sum, 1.0 / a);
    }
}
