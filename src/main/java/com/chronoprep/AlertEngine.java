package com.chronoprep;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alert execution engine — Part III: warning threshold.
 * <p>
 * Three-state evaluation: value > criticalThreshold is CRITICAL,
 * value > warningThreshold is WARN, otherwise PASS. Notifies on
 * severity promotion (tracked by a max-notified latch, so demotions
 * within an open incident don't re-page); re-notifies with the
 * current state once repeatIntervalMs elapses. Resolves on PASS.
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

    void evaluate(AlertConfig alert, AlertRuntimeState alertRuntimeState) {
        double metric;
        try {
            metric = querier.executeQuery(alert.getQuery());
        } catch (Exception e) {
            System.out.println("Metric querier threw an exception: " + e);
            return;
        }
        boolean shouldNotify = false;
        boolean shouldResolve = false;
        synchronized (alertRuntimeState) {
            long currentMs = System.currentTimeMillis();
            AlertState currentState = metric > alert.getCriticalThreshold()?
                        AlertState.CRITICAL : metric > alert.getWarningThreshold()? AlertState.WARN: AlertState.PASS;
            if (currentState == AlertState.PASS) {
                if (alertRuntimeState.getLastState() != AlertState.PASS) shouldResolve = true;
                alertRuntimeState.setLastState(AlertState.PASS);
                alertRuntimeState.setMaxNotified(AlertState.PASS);
            } else {
                if (currentState.severity > alertRuntimeState.getMaxNotified().severity) {
                    shouldNotify = true;
                    alertRuntimeState.setMaxNotified(currentState);
                    alertRuntimeState.setLastNotifyTimeMs(currentMs);
                } else if (currentMs - alertRuntimeState.getLastNotifyTimeMs() >= alert.getRepeatIntervalMs()) {
                    shouldNotify = true;
                    alertRuntimeState.setLastNotifyTimeMs(currentMs);
                }
                alertRuntimeState.setLastState(currentState);
            }
        }
        System.out.printf("[t=%d] %s value=%.1f -> %s%n",
                System.currentTimeMillis(), alert.getId(), metric, alertRuntimeState.getLastState());

        if (shouldNotify) notifier.notify(alert, alertRuntimeState.getLastState(), metric);
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
