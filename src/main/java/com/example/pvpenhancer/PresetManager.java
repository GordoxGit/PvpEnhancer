package com.example.pvpenhancer;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PresetManager {

    public static class Profile {
        public double baseH, baseV, minY, clampY, airMultH, airMultV, sideBoost, backBoost;
    }

    private final Random rng = new Random();
    private final File dataFolder;
    private final List<Profile> hika = new ArrayList<>();
    private final List<Profile> arena = new ArrayList<>();

    public PresetManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void ensureDefaults(org.bukkit.plugin.java.JavaPlugin plugin) {
        File presetsDir = new File(dataFolder, "presets");
        if (!presetsDir.exists()) presetsDir.mkdirs();
        File hikaFile = new File(presetsDir, "hikabrain.yml");
        File arenaFile = new File(presetsDir, "arena.yml");
        if (!hikaFile.exists()) plugin.saveResource("presets/hikabrain.yml", false);
        if (!arenaFile.exists()) plugin.saveResource("presets/arena.yml", false);
    }

    public void reload(org.bukkit.plugin.java.JavaPlugin plugin) throws IOException, InvalidConfigurationException {
        hika.clear(); arena.clear();
        ensureDefaults(plugin);
        loadFile(new File(new File(dataFolder, "presets"), "hikabrain.yml"), hika);
        loadFile(new File(new File(dataFolder, "presets"), "arena.yml"), arena);
    }

    private void loadFile(File file, List<Profile> out) throws IOException, InvalidConfigurationException {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.load(file);
        if (!cfg.isConfigurationSection("profiles") && cfg.isList("profiles")) {
            for (Object o : cfg.getList("profiles")) {
                if (o instanceof Map) {
                    Map<?,?> m = (Map<?,?>) o;
                    Profile p = new Profile();
                    p.baseH    = toD(m.get("baseH"), 0.40);
                    p.baseV    = toD(m.get("baseV"), 0.24);
                    p.minY     = toD(m.get("minY"), 0.10);
                    p.clampY   = toD(m.get("clampY"), 0.30);
                    p.airMultH = toD(m.get("airMultH"), 1.02);
                    p.airMultV = toD(m.get("airMultV"), 0.80);
                    p.sideBoost= toD(m.get("sideBoost"), 0.03);
                    p.backBoost= toD(m.get("backBoost"), 0.05);
                    out.add(p);
                }
            }
        } else if (cfg.isList("profiles")) {
            for (Object o : cfg.getList("profiles")) {
                if (o instanceof Map) {
                    Map<?,?> m = (Map<?,?>) o;
                    Profile p = new Profile();
                    p.baseH    = toD(m.get("baseH"), 0.40);
                    p.baseV    = toD(m.get("baseV"), 0.24);
                    p.minY     = toD(m.get("minY"), 0.10);
                    p.clampY   = toD(m.get("clampY"), 0.30);
                    p.airMultH = toD(m.get("airMultH"), 1.02);
                    p.airMultV = toD(m.get("airMultV"), 0.80);
                    p.sideBoost= toD(m.get("sideBoost"), 0.03);
                    p.backBoost= toD(m.get("backBoost"), 0.05);
                    out.add(p);
                }
            }
        }
    }

    private double toD(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    public Profile pick(String mode) {
        List<Profile> src = "HIKABRAIN".equalsIgnoreCase(mode) ? hika : arena;
        if (src.isEmpty()) return null;
        return src.get(rng.nextInt(src.size()));
    }
}
