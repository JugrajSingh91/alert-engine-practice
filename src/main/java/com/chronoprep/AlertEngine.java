package com.chronoprep;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alert execution engine — Part I: basic engine.
 * <p>
 * Required behavior:
 * 1. Load alert configs ONCE at startup via AlertStore.getAlerts().
 * 2. Poll each alert's query on its check interval via MetricQuerier.executeQuery().
 * 3. Evaluate: value > criticalThreshold -> CRITICAL, otherwise PASS.
 * 4. On EVERY breaching check -> Notifier.notify(alert, value).
 * 5. On transition back to PASS -> Notifier.resolve(alert).
 * 6. start() begins evaluation; stop() shuts everything down cleanly.
 * <p>
 * Concurrency is expected: multiple alerts evaluate on their own schedules,
 * so per-alert state must be thread-safe and stop() must not leak threads.
 * <p>
 * Edge cases worth handling: executeQuery throws, empty alert list,
 * stop() called before start().
 */

public class AlertEngine {
    private final AlertStore store;
    private final MetricQuerier querier;
    private final Notifier notifier;
    private final int numThreads;
    private ScheduledExecutorService scheduler;


    public AlertEngine(AlertStore store, MetricQuerier querier, Notifier notifier,
                       int threads) {
        this.store = store;
        this.querier = querier;
        this.notifier = notifier;
        this.numThreads = threads;
    }

    public void start() {
        if (this.scheduler != null) {
            throw new IllegalStateException("engine already started");
        }
        List<AlertConfig> alerts = store.getAlerts();

        ThreadFactory threadFactory = new ThreadFactory() {
            AtomicInteger atomicInteger = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread t = new Thread(runnable, "alert-checker-" + atomicInteger.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };

        this.scheduler = Executors.newScheduledThreadPool(this.numThreads, threadFactory);

        for (AlertConfig alert : alerts) {
            AlertRuntimeState alertRunTimeState = new AlertRuntimeState();
            scheduler.scheduleAtFixedRate(
                    () -> evaluate(alert, alertRunTimeState),
                    0, alert.getCheckIntervalMs(),
                    TimeUnit.MILLISECONDS);
        }
    }

    void evaluate(AlertConfig alert, AlertRuntimeState alertRunTimeState) {
        double metric;
        try {
            metric = querier.executeQuery(alert.getQuery());
        } catch (Exception e) {
            System.out.println("Metric querier threw an exception: " + e);
            return;
        }
        boolean shouldNotify = false;
        boolean shouldResolve = false;
        synchronized (alertRunTimeState) {
            if (metric > alert.getCriticalThreshold()) {
                alertRunTimeState.setLastState(AlertState.CRITICAL);
                shouldNotify = true;
            } else {
                if (alertRunTimeState.getLastState() == AlertState.CRITICAL) {
                    alertRunTimeState.setLastState(AlertState.PASS);
                    shouldResolve = true;
                }
            }
        }
        System.out.printf("[t=%d] %s value=%.1f -> %s%n",
                System.currentTimeMillis(), alert.getId(), metric, alertRunTimeState.getLastState());

        if (shouldNotify) notifier.notify(alert, metric);
        if (shouldResolve) notifier.resolve(alert);
    }

    public void stop() {
        //stop before start
        if (this.scheduler == null) return;

        this.scheduler.shutdown();

        try {
            if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) this.scheduler.shutdownNow();
        } catch (InterruptedException e) {
            this.scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
