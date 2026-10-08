package com.chronoprep;

public class AlertRuntimeState {
    public AlertState getLastState() {
        return lastState;
    }

    public void setLastState(AlertState lastState) {
        this.lastState = lastState;
    }

    AlertState lastState;

    public AlertRuntimeState () {
        this.lastState = AlertState.PASS;
    }
}
