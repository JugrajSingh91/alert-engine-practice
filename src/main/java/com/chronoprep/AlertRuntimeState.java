package com.chronoprep;

public class AlertRuntimeState {
    AlertState lastState;
    AlertState maxNotified;
    long lastNotifyTimeMs;

    public AlertRuntimeState () {
        this.lastState = AlertState.PASS;
        this.maxNotified = AlertState.PASS;
    }

    public AlertState getLastState() {
        return lastState;
    }

    public void setLastState(AlertState lastState) {
        this.lastState = lastState;
    }

    public long getLastNotifyTimeMs() {
        return lastNotifyTimeMs;
    }

    public void setLastNotifyTimeMs(long lastNotifyTimeMs) {
        this.lastNotifyTimeMs = lastNotifyTimeMs;
    }

    public AlertState getMaxNotified() {
        return maxNotified;
    }

    public void setMaxNotified(AlertState maxNotified) {
        this.maxNotified = maxNotified;
    }
}
