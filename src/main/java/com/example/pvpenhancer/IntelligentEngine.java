package com.example.pvpenhancer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Locale;

public class IntelligentEngine {

    public enum Mode { AUTO, HIKABRAIN, ARENA }

    private final PvPEnhancerPlugin plugin;
    private final PresetManager presets;
    private PresetManager.Profile current;   // target preset
    private PresetManager.Profile smooth;    // smoothed active profile

    // detector
    private int belowAirCheck = 4;
    private double hitWeightNearVoid = 2.0;
    private double wallProximityWeight = 0.6;
    private double switchThreshold = 8.0;
    private double decayPerSecond = 1.0;
    private double emaAlpha = 0.30;

    private double scoreHika = 0.0;
    private double scoreArena = 0.0;
    private long lastTickDecay = System.currentTimeMillis();

    private Mode mode = Mode.AUTO;
    private String resolvedMode = "ARENA";
    private long lastHitMs = 0L;
    private long resampleMs = 20000L;

    public IntelligentEngine(PvPEnhancerPlugin plugin, PresetManager presets) {
        this.plugin = plugin;
        this.presets = presets;
    }

    public void load(FileConfiguration c) {
        this.emaAlpha = c.getDouble("intelligent-kb.ema-alpha", 0.30);
        this.resampleMs = Math.max(5000L, c.getLong("intelligent-kb.resample-seconds", 20) * 1000L);

        this.belowAirCheck = c.getInt("intelligent-kb.detector.below-air-check", 4);
        this.hitWeightNearVoid = c.getDouble("intelligent-kb.detector.hit-weight-near-void", 2.0);
        this.wallProximityWeight = c.getDouble("intelligent-kb.detector.wall-proximity-weight", 0.6);
        this.switchThreshold = c.getDouble("intelligent-kb.detector.switch-threshold", 8.0);
        this.decayPerSecond = c.getDouble("intelligent-kb.detector.decay-per-second", 1.0);

        String m = c.getString("intelligent-kb.mode", "AUTO").toUpperCase(Locale.ROOT);
        switch (m) {
            case "HIKABRAIN" -> mode = Mode.HIKABRAIN;
            case "ARENA" -> mode = Mode.ARENA;
            default -> mode = Mode.AUTO;
        }

        try {
            presets.reload(plugin);
        } catch (Exception ex) {
            plugin.getLogger().warning("Preset load error: " + ex.getMessage());
        }

        // initial mode guess from world names
        double hint = 0;
        try {
            for (World w : Bukkit.getWorlds()) {
                String n = w.getName().toLowerCase(Locale.ROOT);
                if (n.contains("hika") || n.contains("bridge")) hint += 2.0;
            }
        } catch (Throwable ignored) {}
        resolvedMode = (mode == Mode.HIKABRAIN || (mode == Mode.AUTO && hint >= 2.0)) ? "HIKABRAIN" : "ARENA";
        pickNewPreset();
    }

    private void pickNewPreset() {
        PresetManager.Profile p = presets.pick(resolvedMode);
        if (p == null) return;
        current = p;
        if (smooth == null) {
            // first assign
            smooth = new PresetManager.Profile();
            applyEma(1.0);
        }
    }

    private void applyEma(double alpha) {
        if (current == null) return;
        if (smooth == null) smooth = new PresetManager.Profile();
        smooth.baseH    = smooth.baseH    + alpha*(current.baseH - smooth.baseH);
        smooth.baseV    = smooth.baseV    + alpha*(current.baseV - smooth.baseV);
        smooth.minY     = smooth.minY     + alpha*(current.minY  - smooth.minY);
        smooth.clampY   = smooth.clampY   + alpha*(current.clampY- smooth.clampY);
        smooth.airMultH = smooth.airMultH + alpha*(current.airMultH - smooth.airMultH);
        smooth.airMultV = smooth.airMultV + alpha*(current.airMultV - smooth.airMultV);
        smooth.sideBoost= smooth.sideBoost+ alpha*(current.sideBoost - smooth.sideBoost);
        smooth.backBoost= smooth.backBoost+ alpha*(current.backBoost - smooth.backBoost);
    }

    public void setMode(String m) {
        switch (m.toLowerCase(Locale.ROOT)) {
            case "hikabrain" -> { mode = Mode.HIKABRAIN; resolvedMode = "HIKABRAIN"; }
            case "arena"     -> { mode = Mode.ARENA;     resolvedMode = "ARENA"; }
            default          -> { mode = Mode.AUTO; } // resolved will be updated by hits
        }
        pickNewPreset();
    }
    public String getMode() { return mode.toString(); }

    public void decay() {
        long now = System.currentTimeMillis();
        double secs = (now - lastTickDecay) / 1000.0;
        if (secs > 0.2) {
            scoreHika = Math.max(0, scoreHika - decayPerSecond*secs);
            scoreArena = Math.max(0, scoreArena - decayPerSecond*secs);
            lastTickDecay = now;
            if (now - lastHitMs > resampleMs) { pickNewPreset(); lastHitMs = now; }
            applyEma(emaAlpha);
        }
    }

    public void onHitContext(LivingEntity attacker, LivingEntity victim, Vector dir) {
        lastHitMs = System.currentTimeMillis();

        if (mode == Mode.AUTO) {
            if (airBelow(victim.getLocation(), belowAirCheck) >= 2 ||
                airBelow(attacker.getLocation(), belowAirCheck) >= 2) {
                scoreHika += hitWeightNearVoid;
            } else scoreArena += 1.0;

            try {
                org.bukkit.util.RayTraceResult rt = victim.getWorld().rayTraceBlocks(
                        victim.getLocation().add(0,1,0),
                        dir.clone().normalize(),
                        2.5);
                if (rt != null) scoreArena += wallProximityWeight;
            } catch (Throwable ignored) {}

            double diff = scoreHika - scoreArena;
            String newResolved = resolvedMode;
            if (diff > switchThreshold) newResolved = "HIKABRAIN";
            else if (-diff > switchThreshold) newResolved = "ARENA";
            if (!newResolved.equals(resolvedMode)) {
                resolvedMode = newResolved;
                pickNewPreset();
            }
        }
    }

    private int airBelow(Location loc, int max) {
        int air = 0;
        Location c = loc.clone();
        for (int i = 0; i < max; i++) {
            c.subtract(0,1,0);
            if (c.getBlock().getType() == Material.AIR) air++; else break;
        }
        return air;
    }

    public PresetManager.Profile active() { return smooth != null ? smooth : current; }
    public String resolvedMode() { return resolvedMode; }
}
