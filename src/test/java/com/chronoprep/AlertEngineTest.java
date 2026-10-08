package com.chronoprep;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AlertEngine — YOUR TESTS GO HERE.
 *
 * Stub collaborators are provided below. The four TODO tests cover the
 * behaviors interviewers check. Timing-sensitive tests: prefer short
 * intervals (hundreds of ms) and generous assertion windows — flaky
 * tests are a bad look in an interview too.
 */
class AlertEngineTest {

    // ---------- Stub collaborators (use these in your tests) ----------

    static class StubStore implements AlertStore {
        private final List<AlertConfig> alerts;
        private int calls = 0;

        StubStore(AlertConfig... alerts) {
            this.alerts = List.of(alerts);
        }

        @Override
        public List<AlertConfig> getAlerts() {
            calls++;
            return alerts;
        }
    }

    static class StubQuerier implements MetricQuerier {
        private final Queue<Double> values = new ConcurrentLinkedQueue<>();

        void addValues(Double... vs) {
            values.addAll(List.of(vs));
        }

        @Override
        public double executeQuery(String query) {
            Double v = values.poll();
            return v == null ? 0.0 : v;
        }
    }

    static class RecordingNotifier implements Notifier {
        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void notify(AlertConfig alert, double value) {
            events.add("NOTIFY:" + alert.getId() + ":" + value);
        }

        @Override
        public void resolve(AlertConfig alert) {
            events.add("RESOLVE:" + alert.getId());
        }
    }

    // ---------- Your tests ----------

    @Test
    void criticalValueTriggersNotify() {
        // TODO: threshold 90, query returns 95 -> expect exactly one NOTIFY, no RESOLVE.
        // Hint: start the engine, wait ~3 check intervals, stop, assert on events.
    }

    @Test
    void passToCriticalToPassTriggersNotifyThenResolve() {
        // TODO: threshold 90, values 80 -> 95 -> 80 across checks
        // -> expect NOTIFY followed by RESOLVE, in that order.
    }

    @Test
    void sustainedCriticalReNotifiesOnRepeatInterval() throws Exception {
        // TODO: check interval 200ms, repeat interval 400ms, constant critical value.
        // Run ~1 second -> expect NOTIFY at t~0, re-NOTIFY at t~400ms and t~800ms,
        // and NO notify at t~200ms / t~600ms.
    }

    @Test
    void getAlertsCalledOnceAtStartup() throws Exception {
        // TODO: run the engine for several check intervals, then assert
        // StubStore.calls == 1. Configs load once — never per poll.
    }
}
