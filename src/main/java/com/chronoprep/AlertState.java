package com.chronoprep;

/**
 * Evaluation result for a single alert check.
 */
public enum AlertState {
    PASS(0),
    WARN(1),
    CRITICAL(2);

    int severity;
    AlertState(int severity) {
        this.severity = severity;
    }
}
