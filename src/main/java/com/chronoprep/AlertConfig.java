package com.chronoprep;

/**
 * Configuration for a single alert, loaded once at engine startup.
 */
public class AlertConfig {
    private final String id;
    private final String query;
    private final double criticalThreshold;
    private final double warningThreshold;
    private final long checkIntervalMs;
    private final long repeatIntervalMs;

    public AlertConfig(String id, String query, double criticalThreshold, double warningThreshold,
                       long checkIntervalMs, long repeatIntervalMs) {
        this.id = id;
        this.query = query;
        this.criticalThreshold = criticalThreshold;
        this.warningThreshold = warningThreshold;
        this.checkIntervalMs = checkIntervalMs;
        this.repeatIntervalMs = repeatIntervalMs;
    }

    public String getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public double getCriticalThreshold() {
        return criticalThreshold;
    }

    public double getWarningThreshold() {
        return warningThreshold;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public long getRepeatIntervalMs() {
        return repeatIntervalMs;
    }

    @Override
    public String toString() {
        return "AlertConfig{id='" + id + "', threshold=" + criticalThreshold + "}";
    }
}
