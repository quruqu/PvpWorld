package me.ujun.pvpWorld.arena;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

public class VoidManager {
    private final String worldName;
    public VoidManager(String worldName) { this.worldName = worldName; }

    public World ensure() {
        World w = Bukkit.getWorld(worldName);
        if (w != null) return w;

        WorldCreator wc = new WorldCreator(worldName);
        ChunkGenerator gen = new VoidGenerator();
        wc.generator(gen);
        w = Bukkit.createWorld(wc);

        if (w != null) {
            w.setDifficulty(Difficulty.HARD);
            w.setPVP(true);
            w.setSpawnLocation(0, 64, 0);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setTime(6000);
        }
        return w;
    }
}
