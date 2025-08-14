package com.example.pvpenhancer;

import org.bukkit.entity.Player;

/**
 * Public entry point for other plugins to interact with PvPEnhancer.
 */
public class PvpEnhancerAPI {
    /**
     * Changes the PvP game mode used to adapt the player's knockback profile.
     * Examples of modes include "FACTION", "DUEL" or "TRAINING".
     *
     * @param player   target player
     * @param gamemode name of the desired PvP mode
     */
    public static void setPlayerGamemode(Player player, String gamemode) {
        PvPEnhancerPlugin plugin = PvPEnhancerPlugin.get();
        if (plugin != null) {
            IntelligentEngine engine = plugin.getEngineForPlayer(player);
            if (engine != null) {
                engine.setCurrentGamemode(gamemode.toUpperCase());
            }
        }
    }
}
