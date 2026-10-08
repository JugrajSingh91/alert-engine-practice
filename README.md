# Alert Execution Engine

A small Java engine that polls metric queries on a schedule, evaluates them against thresholds, and delivers alert notifications. Built in three increments.

## What it does

- Loads alert configs once at startup.
- Polls each alert's query on its own check interval — one schedule per alert, evaluated concurrently.
- Drives a per-alert state machine (PASS / WARN / CRITICAL) from the returned values.
- Delivers notifications through a `Notifier` interface; `stop()` shuts everything down cleanly.

## Build increments

**1. Basic engine.** Poll each query on its interval. Notify on every breaching check (strict `>` threshold); resolve on transition back to PASS.

**2. Repeat notifications.** Notify on transition into CRITICAL; while the alert stays critical, re-notify only after the repeat interval elapses.

**3. Warning threshold.** Three-state machine — PASS ↔ WARN ↔ CRITICAL: value > critical → CRITICAL, warning < value ≤ critical → WARN, value ≤ warning → PASS.

## Run

Needs JDK 17+ and Maven:

```
mvn -q compile exec:java -Dexec.mainClass=com.chronoprep.Demo
```

`Demo` wires the engine to printing stubs and drives it with a scripted metric timeline, so evaluations can be traced by eye.

## Design notes

- Per-alert runtime state is confined to the alert's own scheduled task and guarded by its own lock. The query runs outside the lock so a slow query doesn't block state updates.
- The notification decision is made inside the lock; the notifier itself is called outside it, so a slow notifier can't stall the next evaluation.
- Threads are daemons from a named factory. `stop()` does a bounded graceful shutdown before forcing.
- A throwing query is treated as "no evidence" — the poll is skipped and state is left untouched.
- `AlertConfig` is treated as provided boilerplate: the engine never modifies it, only reads it.

## Open design questions

Things that came up during the build. Each has more than one defensible answer — the point is to have one ready.

**Delivery correctness**

- *The notifier throws halfway through.* If the engine marks an alert notified before the call returns, a throw loses the page while state says sent. At-least-once means marking notified only after success, plus retries with idempotency keys; at-most-once means marking before and accepting the loss. Most paging paths choose at-least-once and dedupe downstream.
- *A resolve overtakes its notify.* With concurrent delivery, a resolve can reach the pager before the notify, showing "resolved" for an incident the operator never saw. A per-incident sequence number lets the receiver order and reconcile events.
- *A retried notify double-pages.* The call times out and is retried, but the first attempt actually succeeded. An idempotency key per notification (alert ID + incident start) lets the receiver drop the duplicate.
- *One channel fails.* Notify goes to pager, Slack, and email; Slack fails. Retry only the failed channel instead of resending all three — which means tracking delivery per channel, not per notification.
- *An acked alert keeps firing.* Suppress re-notifies while acked, but acks must expire — a forgotten ack silences a real incident forever. Ack expiry is a timer with the same lifecycle questions as the repeat interval.
- *The metric goes silent.* The query stops returning data because the agent died. Treating silence as PASS hides outages; treating it as firing pages on every deploy hiccup. A staleness timeout that resolves as UNKNOWN is the usual middle ground.

**Time**

- *Two instances disagree on "now."* One sends a reminder the other considers not due, causing duplicates or gaps. Measure elapsed time with monotonic clocks per instance, or give scheduling authority to a single leader.
- *A GC pause swallows poll ticks.* Thirty seconds of pause means missed evaluations. Run them late (catch-up, possibly paging on stale data) or skip them (a gap in coverage)? Either is defensible; silent catch-up is not — it must be a deliberate policy.
- *Business-hours windows.* "Page only 9–5" rules break on DST transitions and implicit timezone assumptions. Store windows in UTC with explicit zone rules and test the spring-forward and fall-back edges.
- *An evaluation overruns its interval.* Fixed-rate scheduling piles up backlogged executions; fixed-delay lets the phase drift. For alerting, drift is usually safer than a pileup — but it should be a choice, not an accident.
- *NTP jumps the clock forward five minutes.* Every repeat interval fires at once. Measure elapsed time with a monotonic clock (`System.nanoTime`); reserve wall time for human-readable timestamps.

**State and crash recovery**

- *The engine restarts mid-incident.* Without persisted state it either re-notifies everything (page storm) or misses resolves. Persist per-alert state plus last-notified timestamps, reload on start, and resume the schedule from there.
- *Persisted state is corrupt on restart.* Fail open and re-notify everything (noisy but safe) or fail closed and stay blind (quiet but dangerous)? There is no neutral option — pick one and log loudly.
- *Failover resends.* The old primary sent a notify the new primary never learned about, so it sends again. Fencing (a single writer at a time) plus an idempotent notifier (the receiver drops dupes) covers both directions.
- *State moves to an external store.* Redis or a DB makes failover easy, but that store becomes a hard dependency of the alerting path. If it's down, the engine should degrade deliberately — e.g., run stateless and accept duplicate notifies — not crash.

**Scale**

- *Alert count grows 100x.* Thread-per-alert stops working in the low thousands. Shard alerts across a bounded pool by alert ID, or move to a tick-based design: one scheduler and a priority queue of due evaluations.
- *Ten alerts watch the same metric.* Ten identical queries waste backend capacity and can read inconsistent values. Query once per distinct query string per tick and fan the result out to subscribed alerts, with a short TTL cache to absorb scheduling skew.
- *One expensive query, a thousand alerts, every five seconds.* That's a self-inflicted DDoS on the metrics backend. Give each tenant a query budget and shed load by skipping low-priority polls — never by crashing.
- *Cardinality explosion.* One rule templated over ten thousand instances becomes ten thousand alert instances overnight. Cap expansion per rule and fire an alert when the cap is hit; otherwise the first bad deploy is also a pager storm.
- *Hot shards.* Hashing by alert ID is simple until one shard draws all the expensive queries. Consistent hashing with bounded load, or a work-stealing queue, keeps utilization even.
- *Restart thundering herd.* Every alert schedules its first poll at t=0 after a restart and hammers the metrics backend at once. Stagger initial delays with jitter spread across the check interval.

**Config safety**

- *A threshold of zero.* Someone sets it and everything fires. Validate configs at load (ranges, sanity checks), canary rule changes against a subset of alerts, and trip a circuit breaker that pauses notifications if the firing rate spikes an order of magnitude.
- *Auditability.* "Who changed this threshold on Tuesday?" Tag every notification with the config version that produced it, so incidents trace back to the rule change that caused them.
- *Tenant isolation.* One tenant's malformed regex must not crash evaluation for everyone. Parse and validate each tenant's config in isolation; a bad config disables that tenant's alerts, not the engine.
- *Runtime changes.* A new alert arrives without a restart. The engine needs add/remove paths — cancel the old scheduled task, create fresh state, schedule the new one — without disturbing the alerts already running.

**Notification pipeline**

- *Flapping.* A metric hovers at the threshold and pages every few minutes. Require N consecutive breaches before the first notify and M consecutive passes before resolve (hysteresis); N=1, M=1 reproduces the naive behavior exactly.
- *Cross-alert dedupe.* Several alerts share a dedupe key, e.g. the same service. Keep a shared map from key to last-notified; a group resolve fires only when every member alert has recovered, which needs per-key membership tracking (a set of firing alert IDs, not a counter — adds must be idempotent across polls).
- *Dedupe vs repeat.* Pure dedupe would suppress re-notifications forever. Give dedupe entries a TTL equal to the repeat interval: inside the window it's a duplicate, after the window it's a scheduled reminder. The repeat rule always wins.
- *Pager API throttles mid-incident.* Queue with priority (CRITICAL ahead of WARN) and define the overflow policy up front: merge, drop lowest priority, or page the operator that the queue itself is full.
- *Nobody acks.* Fifteen minutes pass. Escalation chains (primary → secondary → manager) are state machines in their own right, with timers, acks, and hand-offs.
- *Silences.* Deploys need quiet windows. Silences must expire — a forgotten silence is a blind spot — be scoped to specific alerts, and leave an audit trail of who silenced what and when.
- *Inhibition.* "Datacenter-down is firing, so suppress host-down for that DC." Dependency-aware suppression needs the dependency graph at evaluation time, and one wrong edge silences real pages.
- *Absent data.* The query returns nothing. Missing is not zero: a dead agent looks identical to a healthy zero. Absent-data detection is a separate rule type with its own false-positive profile — tune it independently of threshold rules.

**Watching the watcher**

- *Engine health metrics.* Export the engine's own vitals: evaluation latency percentiles, missed or deferred polls, notification success/failure counts, query error rates. Alert on these through a separate, simpler path than the engine itself.
- *Dead-man's switch.* A heartbeat alert that fires constantly. If the heartbeat stops arriving downstream, the engine — or the pipeline behind it — is down. This is the one alert that must not depend on the engine it monitors.
- *Windowed evaluation.* Real rules are "average over five minutes above X," not point comparisons. The windowing can live in the query (the backend aggregates) or in the engine (a ring buffer per alert); the choice changes memory footprint, correctness across restarts, and backfill behavior.
