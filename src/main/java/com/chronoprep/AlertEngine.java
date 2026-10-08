package com.chronoprep;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alert execution engine — Part II: repeat interval.
 * <p>
 * Polls each alert's query on its check interval. Value > criticalThreshold
 * is CRITICAL, otherwise PASS. Notifies on transition into CRITICAL and
 * re-notifies while critical only after repeatIntervalMs elapses since the
 * last notify. Resolves on transition back to PASS.
 * <p>
 * Per-alert state is confined to the alert's own scheduled task and guarded
 * by its own lock; stop() shuts the scheduler down cleanly.
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
            long currentMs = System.currentTimeMillis();
            if (metric > alert.getCriticalThreshold()) {
                if (alertRunTimeState.lastState == AlertState.PASS
                    || (alertRunTimeState.lastState == AlertState.CRITICAL
                        && currentMs - alertRunTimeState.getLastNotifyTimeMs() >= alert.getRepeatIntervalMs())) {
                    alertRunTimeState.setLastState(AlertState.CRITICAL);
                    shouldNotify = true;
                    alertRunTimeState.setLastNotifyTimeMs(currentMs);
                }
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
