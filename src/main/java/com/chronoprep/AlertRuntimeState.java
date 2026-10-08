package com.chronoprep;

public class AlertRuntimeState {
    public AlertState getLastState() {
        return lastState;
    }

    public void setLastState(AlertState lastState) {
        this.lastState = lastState;
    }

    AlertState lastState;

    public long getLastNotifyTimeMs() {
        return lastNotifyTimeMs;
    }

    public void setLastNotifyTimeMs(long lastNotifyTimeMs) {
        this.lastNotifyTimeMs = lastNotifyTimeMs;
    }

    long lastNotifyTimeMs;

    public AlertRuntimeState () {
        this.lastState = AlertState.PASS;
    }
}
