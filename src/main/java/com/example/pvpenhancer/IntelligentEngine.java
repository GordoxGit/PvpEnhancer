package com.example.pvpenhancer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Computes knockback behavior for a single player.
 * Instances of this engine are managed per-player by the plugin.
 */
public class IntelligentEngine {

    public static class Profile {
        public double baseH, baseV, minY, clampY, airMultH, airMultV, sideBoost, backBoost;
    }

    private static final double NEUTRAL_BASE_H = 0.40;
    private static final double NEUTRAL_BASE_V = 0.25;
    private static final double NEUTRAL_MIN_Y = 0.10;
    private static final double NEUTRAL_CLAMP_Y = 0.30;
    private static final double NEUTRAL_AIR_MULT_H = 1.02;
    private static final double NEUTRAL_AIR_MULT_V = 0.80;
    private static final double NEUTRAL_SIDE_BOOST = 0.03;
    private static final double NEUTRAL_BACK_BOOST = 0.05;

    private final PvPEnhancerPlugin plugin;
    private Profile smooth;

    // detector (unused for now but kept for future balancing)
    private int belowAirCheck = 4;
    private double hitWeightNearVoid = 2.0;
    private double wallProximityWeight = 0.6;
    private double switchThreshold = 8.0;
    private double decayPerSecond = 1.0;
    private double emaAlpha = 0.30;

    private long lastTickDecay = System.currentTimeMillis();

    // precision metrics
    private int swingCount = 0;
    private int hitCount = 0;
    private double accuracyRatio = 0.0; // percentage

    // combat metrics
    private int currentCombo = 0;
    private int highestCombo = 0;
    private long lastHitTime = 0;

    // mobility metrics (future use)
    private double averageDistancePerSecond = 0.0;

    public IntelligentEngine(PvPEnhancerPlugin plugin) {
        this.plugin = plugin;
        this.smooth = new Profile();
        this.smooth.baseH = NEUTRAL_BASE_H;
        this.smooth.baseV = NEUTRAL_BASE_V;
        this.smooth.minY = NEUTRAL_MIN_Y;
        this.smooth.clampY = NEUTRAL_CLAMP_Y;
        this.smooth.airMultH = NEUTRAL_AIR_MULT_H;
        this.smooth.airMultV = NEUTRAL_AIR_MULT_V;
        this.smooth.sideBoost = NEUTRAL_SIDE_BOOST;
        this.smooth.backBoost = NEUTRAL_BACK_BOOST;
    }

    public void load(FileConfiguration c) {
        this.emaAlpha = c.getDouble("intelligent-kb.ema-alpha", 0.30);

        this.belowAirCheck = c.getInt("intelligent-kb.detector.below-air-check", 4);
        this.hitWeightNearVoid = c.getDouble("intelligent-kb.detector.hit-weight-near-void", 2.0);
        this.wallProximityWeight = c.getDouble("intelligent-kb.detector.wall-proximity-weight", 0.6);
        this.switchThreshold = c.getDouble("intelligent-kb.detector.switch-threshold", 8.0);
        this.decayPerSecond = c.getDouble("intelligent-kb.detector.decay-per-second", 1.0);
    }

    private void applyEma(Profile target, double alpha) {
        if (smooth == null) smooth = new Profile();
        smooth.baseH    = smooth.baseH    + alpha * (target.baseH    - smooth.baseH);
        smooth.baseV    = smooth.baseV    + alpha * (target.baseV    - smooth.baseV);
        smooth.minY     = smooth.minY     + alpha * (target.minY     - smooth.minY);
        smooth.clampY   = smooth.clampY   + alpha * (target.clampY   - smooth.clampY);
        smooth.airMultH = smooth.airMultH + alpha * (target.airMultH - smooth.airMultH);
        smooth.airMultV = smooth.airMultV + alpha * (target.airMultV - smooth.airMultV);
        smooth.sideBoost= smooth.sideBoost+ alpha * (target.sideBoost- smooth.sideBoost);
        smooth.backBoost= smooth.backBoost+ alpha * (target.backBoost- smooth.backBoost);
    }

    private void updateKnockbackProfile() {
        Profile target = new Profile();

        double acc = accuracyRatio / 100.0;
        double baseHModifier = 1.0 - ((acc - 0.5) * 0.1); // -5% to +5%
        target.baseH = NEUTRAL_BASE_H * baseHModifier;

        double baseVModifier = 1.0 + (Math.min(currentCombo, 10) * 0.01); // +0% to +10%
        target.baseV = NEUTRAL_BASE_V * baseVModifier;

        target.minY = NEUTRAL_MIN_Y;
        target.clampY = NEUTRAL_CLAMP_Y;
        target.airMultH = NEUTRAL_AIR_MULT_H;
        target.airMultV = NEUTRAL_AIR_MULT_V;
        target.sideBoost = NEUTRAL_SIDE_BOOST;
        target.backBoost = NEUTRAL_BACK_BOOST;

        applyEma(target, this.emaAlpha);
    }

    public void decay() {
        long now = System.currentTimeMillis();
        double secs = (now - lastTickDecay) / 1000.0;
        if (secs > 0.2) {
            lastTickDecay = now;
            updateMetrics(secs);
            updateKnockbackProfile();
        }
    }

    public void onHitContext(LivingEntity attacker, LivingEntity victim, Vector dir) {
        // future use for more advanced context-based adjustments
    }

    private void updateMetrics(double secs) {
        double factor = Math.pow(0.9, secs / 30.0); // 10% decay every 30s
        swingCount = (int) Math.round(swingCount * factor);
        hitCount = (int) Math.round(hitCount * factor);
        updateAccuracy();
    }

    public void recordSwing() {
        decay();
        swingCount++;
        updateAccuracy();
    }

    public void recordHit() {
        decay();
        hitCount++;
        updateAccuracy();
        updateCombo();
    }

    private void updateAccuracy() {
        this.accuracyRatio = swingCount > 0 ? (hitCount * 100.0) / swingCount : 0.0;
    }

    private void updateCombo() {
        long now = System.currentTimeMillis();
        if (now - lastHitTime <= 3000) {
            currentCombo++;
        } else {
            currentCombo = 1;
        }
        if (currentCombo > highestCombo) highestCombo = currentCombo;
        lastHitTime = now;
    }

    public String settingsSummary(String player) {
        Profile p = active();
        String kb = "";
        if (p != null) {
            kb = "§fProfil KB: §abaseH=" + String.format(Locale.US, "%.3f", p.baseH) +
                 " §abaseV=" + String.format(Locale.US, "%.3f", p.baseV) +
                 " §aairH=" + String.format(Locale.US, "%.3f", p.airMultH) +
                 " §aairV=" + String.format(Locale.US, "%.3f", p.airMultV) + "\n";
        }
        return "§e=== Stats PvP de " + player + " ===\n" +
                "§fPrécision: §a" + String.format(Locale.US, "%.1f", accuracyRatio) + "%\n" +
                "§fMeilleur Combo: §a" + highestCombo + "\n" +
                kb +
                "§f(Debug) Hits/Swings: §7" + hitCount + "/" + swingCount;
    }

    public Profile active() { return smooth; }
}
