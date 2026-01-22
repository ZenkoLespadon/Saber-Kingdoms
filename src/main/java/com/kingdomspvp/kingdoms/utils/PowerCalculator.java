package com.kingdomspvp.kingdoms.utils;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.kingdomspvp.kingdoms.model.Claim;
import com.kingdomspvp.kingdoms.model.War;
import com.kingdomspvp.kingdoms.services.ClaimManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

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

        double score = (kills * 5.0) - (deaths * 1.5);
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

        double power = totalLevels * 2.5;

        return (int) Math.min(50, power);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static double effectivePower(Collection<Integer> powers) {
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