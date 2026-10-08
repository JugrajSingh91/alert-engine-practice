package com.chronoprep;

/**
 * Delivers alert notifications (pager, Slack, etc.).
 */
public interface Notifier {
    void notify(AlertConfig alert, AlertState state, double value);

    void resolve(AlertConfig alert);
}
