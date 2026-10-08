package com.chronoprep;

import java.util.List;

/**
 * Source of alert configurations. Called ONCE at engine startup.
 */
public interface AlertStore {
    List<AlertConfig> getAlerts();
}
