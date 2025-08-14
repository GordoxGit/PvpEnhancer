package com.example.pvpenhancer;

import org.bukkit.entity.Player;

public class PvpEnhancerAPI {
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
