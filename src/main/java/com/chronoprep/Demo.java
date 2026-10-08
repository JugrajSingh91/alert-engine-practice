package com.chronoprep;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Demo {
    public static void main(String args[]) throws Exception {
        double[] values = {110, 1234, 45, 60};
        AtomicInteger index = new AtomicInteger();
        MetricQuerier querier = query -> values[index.incrementAndGet() % values.length];
        Notifier notifier = new Notifier() {
            @Override
            public void notify(AlertConfig alert, double value) {
                System.out.println("NOTIFY " + alert.getId() + " value=" + value);
            }

            @Override
            public void resolve(AlertConfig alert) {
                System.out.println("RESOLVE " + alert.getId());
            }
        };
        AlertStore alertStore = () -> List.of(new AlertConfig("cpu-high", "cpu_usage", 100.0, 5000, 10000));
        AlertEngine alertEngine = new AlertEngine(alertStore, querier, notifier, 2);
        alertEngine.start();
        Thread.sleep(22000);
        alertEngine.stop();
    }
}
