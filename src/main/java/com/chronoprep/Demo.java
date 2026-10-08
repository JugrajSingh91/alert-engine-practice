package com.chronoprep;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Demo {

    public static void main(String args[]) throws Exception {
        double[] values = {80, 110, 85, 115, 90, 50, 75, 78, 72, 60};
        AtomicInteger index = new AtomicInteger();
        MetricQuerier querier = query -> values[index.getAndIncrement() % values.length];
        Notifier notifier = new Notifier() {
            @Override
            public void notify(AlertConfig alert, AlertState state, double value) {
                System.out.println("NOTIFY " + state + " " + alert.getId() + " value=" + value);
            }

            @Override
            public void resolve(AlertConfig alert) {
                System.out.println("RESOLVE " + alert.getId());
            }
        };
        AlertConfig alert = new AlertConfig("cpu-high", "cpu_usage", 100.0, 70.0, 5000, 10000);
        AlertStore alertStore = () -> List.of(alert);
        AlertEngine engine = new AlertEngine(alertStore, querier, notifier,2);

        // Test Scheduler
        engine.start();
        Thread.sleep(55000);
        engine.stop();
    }
}
