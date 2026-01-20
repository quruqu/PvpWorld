package me.ujun.pvpWorld.util;

import me.ujun.pvpWorld.PvpWorld;
import me.ujun.pvpWorld.config.ConfigHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;

public class ResetUtil {
    public static void resetPlayerState(Player player) {
        resetAllAttributes(player);
        player.clearActivePotionEffects();
        player.setHealth(player.getMaxHealth());
        player.setFireTicks(0);
        player.setFallDistance(0);

        //food reset
        player.setFoodLevel(20);
        player.setSaturation(0);
        player.setExhaustion(0);
    }


    public static void resetAllAttributes(Player player) {
        player.getAttribute(Attribute.GENERIC_LUCK).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_OXYGEN_BONUS).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_MAX_ABSORPTION).setBaseValue(0);
        player.getAttribute(Attribute.PLAYER_MINING_EFFICIENCY).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_ATTACK_KNOCKBACK).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0);
        player.getAttribute(Attribute.PLAYER_SWEEPING_DAMAGE_RATIO).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_WATER_MOVEMENT_EFFICIENCY).setBaseValue(0);
        player.getAttribute(Attribute.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(0);

        player.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(1);
        player.getAttribute(Attribute.GENERIC_BURNING_TIME).setBaseValue(1);
        player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(1);
        player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED).setBaseValue(1);
        player.getAttribute(Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER).setBaseValue(1);


        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4);
        player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE).setBaseValue(4.5);
        player.getAttribute(Attribute.PLAYER_ENTITY_INTERACTION_RANGE).setBaseValue(3);
        player.getAttribute(Attribute.GENERIC_GRAVITY).setBaseValue(0.08);
        player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH).setBaseValue(0.42);
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20);
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
        player.getAttribute(Attribute.GENERIC_SAFE_FALL_DISTANCE).setBaseValue(3);
        player.getAttribute(Attribute.PLAYER_SNEAKING_SPEED).setBaseValue(0.3);
        player.getAttribute(Attribute.GENERIC_STEP_HEIGHT).setBaseValue(0.6);
        player.getAttribute(Attribute.PLAYER_SUBMERGED_MINING_SPEED).setBaseValue(0.2);

    }

    public static void joinLobby(Player player) {
        resetPlayerState(player);
        player.getInventory().clear();
        player.removeScoreboardTag(ConfigHandler.ffaTag);
        PvpWorld.playerKits.remove(player.getUniqueId());

        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 10000000, 255, true, false, false));

        if (!PvpWorld.devPlayers.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (ConfigHandler.lobby != null) {
            player.teleport(ConfigHandler.lobby);
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

    }
}
