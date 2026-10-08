package com.chronoprep;

public class AlertRuntimeState {
    public AlertState getLastState() {
        return lastState;
    }

    public void setLastState(AlertState lastState) {
        this.lastState = lastState;
    }

    public int getConsecutiveBreaches() {
        return consecutiveBreaches;
    }

    public void setConsecutiveBreaches(int consecutiveBreaches) {
        this.consecutiveBreaches = consecutiveBreaches;
    }

    public long getLastNotifyTimeMs() {
        return lastNotifyTimeMs;
    }

    public void setLastNotifyTimeMs(long lastNotifyTimeMs) {
        this.lastNotifyTimeMs = lastNotifyTimeMs;
    }

    AlertState lastState;
    int consecutiveBreaches;
    long lastNotifyTimeMs;

    public AlertRuntimeState () {
        this.lastState = AlertState.PASS;
        this.consecutiveBreaches = 0;
        this.lastNotifyTimeMs = 0;
    }
}
