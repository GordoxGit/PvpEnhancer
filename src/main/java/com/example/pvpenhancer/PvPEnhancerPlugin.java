package com.example.pvpenhancer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PvPEnhancerPlugin extends JavaPlugin {

    private static PvPEnhancerPlugin instance;
    private final Set<String> allowedWorlds = new HashSet<>();
    private boolean allowEverywhere = true;
    private boolean debug;

    private PvpListener pvpListener;
    private AdminMenu adminMenu;

    private PresetManager presets;
    private final Map<UUID, IntelligentEngine> playerEngines = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadLocal();

        presets = new PresetManager(getDataFolder());

        pvpListener = new PvpListener(this);
        getServer().getPluginManager().registerEvents(pvpListener, this);

        adminMenu = new AdminMenu(this, pvpListener);
        getServer().getPluginManager().registerEvents(adminMenu, this);

        PvpCommand cmd = new PvpCommand(this, pvpListener, adminMenu);
        if (getCommand("pvp") != null) {
            getCommand("pvp").setExecutor(cmd);
            getCommand("pvp").setTabCompleter(cmd);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isWorldAllowed(p.getWorld())) {
                pvpListener.applyAttackSpeed(p);
                pvpListener.applyMaxNoDamageTicks(p);
                getEngineForPlayer(p);
            }
        }

        getLogger().info("PvPEnhancer v1.4.0 enabled. Scope: " + (allowEverywhere ? "ALL WORLDS" : allowedWorlds));
    }

    public void reloadLocal() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        allowedWorlds.clear();
        List<String> list = cfg.getStringList("allowed-worlds");
        allowEverywhere = true;
        if (list != null) {
            allowEverywhere = false;
            for (String w : list) {
                if (w == null || w.isEmpty()) continue;
                if (w.equals("*")) { allowEverywhere = true; break; }
                allowedWorlds.add(w.toLowerCase(Locale.ROOT));
            }
            if (!allowEverywhere && allowedWorlds.isEmpty()) allowEverywhere = true;
        } else allowEverywhere = true;
        debug = cfg.getBoolean("debug", false);
    }

    public IntelligentEngine getEngineForPlayer(Player player) {
        return playerEngines.computeIfAbsent(player.getUniqueId(), uuid -> {
            IntelligentEngine engine = new IntelligentEngine(this, presets);
            engine.load(getConfig());
            return engine;
        });
    }

    public void removeEngineForPlayer(Player player) {
        playerEngines.remove(player.getUniqueId());
    }

    public IntelligentEngine anyEngine() {
        return playerEngines.values().stream().findFirst().orElse(null);
    }

    public void reloadAllEngines() {
        for (IntelligentEngine engine : playerEngines.values()) {
            engine.load(getConfig());
        }
    }

    public boolean isWorldAllowed(World w) {
        return allowEverywhere || (w != null && allowedWorlds.contains(w.getName().toLowerCase(Locale.ROOT)));
    }
    public boolean debug() { return debug; }
    public static PvPEnhancerPlugin get() { return instance; }
}
