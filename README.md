# Alert Execution Engine

Polls metric queries on a schedule, evaluates them against thresholds, and delivers notifications. Built in three increments. Needs JDK 17+ and Maven.

## Behavior

- Loads alert configs once at startup; each alert polls on its own check interval, concurrently.
- Three-state evaluation per poll: value > critical → CRITICAL, value > warning → WARN, otherwise PASS.
- Notifies on severity promotion; a max-notified latch suppresses demotions within an open incident. Re-notifies with the current state once the repeat interval elapses. Resolves on return to PASS.
- Delivery goes through a `Notifier` interface (severity-aware routing lives downstream); `stop()` shuts the scheduler down cleanly.

## Build increments

1. **Basic engine.** Notify on every breaching check (strict `>`); resolve on recovery.
2. **Repeat interval.** Notify on transition into CRITICAL; re-notify only after the interval elapses.
3. **Warning threshold.** The three-state machine plus the promotion latch.

## Run

```
mvn -q compile exec:java -Dexec.mainClass=com.chronoprep.Demo
```

`Demo` wires the engine to printing stubs and drives it with a scripted metric timeline, so the trace can be read by eye.

## Design notes

- Per-alert state is confined to the alert's own scheduled task and guarded by its own lock; the query runs outside the lock.
- The notify/resolve decision is made inside the lock; the notifier itself is called outside it.
- Daemon threads from a named factory; `stop()` does a bounded graceful shutdown, then forces.
- A throwing query is "no evidence": the poll is skipped, state untouched.
- `AlertConfig` is provided boilerplate — read, never modified.

## Open design questions

Things that came up during the build. Each has more than one defensible answer — the point is to have one ready, stated like someone who's been paged at 3am.

**Delivery correctness**

**What happens if the notifier throws halfway through?**

Mark notified only after the call succeeds — at-least-once. A throw means retry with bounded backoff, off the evaluation path: unbounded retry against a down pager is its own outage (queue explosion, starved threads). The receiver dedupes on an idempotency key (alert ID + incident start). I'd rather risk a duplicate page than lose one silently; a dupe is embarrassing, a lost page is an outage nobody knows about.

**What if a resolve overtakes its notify?**

Give every notification a per-incident sequence number. The receiver orders events by it — a resolve for an incident it never saw gets held or flagged, not displayed as a phantom "resolved." Without ordering, concurrent delivery lies to the operator. Some systems go one step further and only resolve incidents that were actually notified — no point telling the pager about an incident it never heard of.

**What if a retried notify double-pages?**

Same answer as the throw: idempotency key per notification, receiver drops dupes. A retry without idempotency isn't a retry, it's a duplicate.

**One channel fails — pager works, Slack doesn't. Resend everything?**

No. Track delivery per channel, not per notification, and retry only the failed one. Resending all three spams the channels that worked, and that's how you train people to ignore pages.

**An acked alert keeps firing — suppress re-notifies forever?**

Suppress while acked, but acks must expire. A forgotten ack is a silenced incident — the worst kind of quiet. Ack expiry is just another timer with the same lifecycle rules as the repeat interval: it needs a default, and it needs to be visible.

**The metric goes silent because the agent died. Fire or stay quiet?**

Neither, blindly. Silence-as-PASS hides outages; silence-as-firing pages on every deploy hiccup. Resolve as UNKNOWN on a staleness timeout — it's honest about what you know, and it keeps "no data" on a separate tuning track from threshold breaches.

**Time**

**Two instances disagree on "now" — one sends a reminder the other considers not due. Who's right?**

Neither — distributed "now" is a fiction. Elapsed time comes from a monotonic clock per instance, never wall time. If two instances can both notify, give scheduling authority to one leader and stop building correctness on synchronized clocks.

**A GC pause swallows 30 seconds of poll ticks. Catch up or skip?**

Pick deliberately and say so. Catch-up pages on stale data; skipping leaves a coverage gap. I'd usually skip and record the gap — a page on 30-second-old data at 3am is worse than a known blind window. What's unacceptable is accidental catch-up that nobody chose.

**How do "page only 9–5" rules survive DST?**

They don't, unless windows are stored in UTC with explicit zone rules and both transitions are tested. Implicit local-time assumptions are how "don't page at night" becomes "page everyone at 2am twice" on fall-back Sunday.

**An evaluation overruns its check interval — pile up or drift?**

Drift, deliberately. Fixed-rate pileup turns one slow query into a backlog that pages on increasingly stale data; fixed-delay lets the phase slide but every evaluation is fresh. Fresh-and-late beats stale-and-punctual.

**NTP jumps the clock forward five minutes and every repeat interval fires at once. How was this allowed to happen?**

Because elapsed time was measured on a wall clock. Use `System.nanoTime` for all timers; wall time is for human-readable timestamps only. Any timer built on wall time is a page storm waiting for an NTP correction.

**State and crash recovery**

**The engine restarts mid-incident. Re-notify everything or stay quiet?**

Neither. Persist per-alert state plus last-notified timestamps, reload on start, resume the schedule. Without that you're choosing between a restart page storm and missed resolves — and both will happen at the worst possible time.

**The persisted state is corrupt on restart. Fail open or fail closed?**

Fail open — but stagger it. Re-notify everything, because noisy-but-safe beats quiet-but-blind, but spread the burst so the recovery doesn't become its own page storm. And log it like the building is on fire, because your visibility just was.

**After failover, the new primary re-sends a notify the old one already sent. Preventable?**

Yes — fencing plus idempotent receivers. One writer at a time prevents split-brain sends; idempotency keys let the receiver drop the dupes fencing can't prevent. You need both; either alone leaves a hole.

**What breaks when alert state moves to Redis?**

The store becomes a hard dependency of the alerting path. If it's down, the engine must degrade deliberately — run stateless, accept duplicate notifies — not crash. An alerting system that goes blind when its cache is down has its priorities backwards.

**Scale**

**Alert count grows 100x. Does thread-per-alert survive?**

No — it falls over in the low thousands. Shard alerts across a bounded pool by alert ID, or go tick-based: one scheduler, one priority queue of due evaluations. Threads are not a scaling strategy.

**Ten alerts watch the same metric. Ten queries?**

Query once per distinct query string per tick and fan the result out. Ten identical queries waste backend capacity and — worse — can read inconsistent values, so two alerts disagree about the same metric at the same moment.

**A thousand alerts share one expensive query every five seconds. What stops the self-DDoS?**

A per-tenant query budget with load shedding: skip low-priority polls when over budget, never crash. The metrics backend is shared infrastructure — your alerting system doesn't get to take it down.

**One rule templated over ten thousand instances overnight. Then what?**

Cap expansion per rule and fire an alert when the cap is hit. Otherwise the first bad deploy is a ten-thousand-page storm, and the storm is your fault, not the deploy's.

**Hashing by alert ID gives you hot shards. Now what?**

Consistent hashing with bounded load, or a work-stealing queue. Naive hash sharding is fine until one shard draws every expensive query — and it will, right after you stop watching.

**Every alert polls at t=0 after a restart. What hits the metrics backend?**

A thundering herd. Stagger initial delays with jitter spread across the check interval. The restart path is the one path nobody load-tests until it pages everyone.

**Config safety**

**Someone sets a threshold of zero and everything fires. How do you stop that?**

Validate at load — ranges, sanity checks — canary rule changes against a subset of alerts, and a circuit breaker that pauses notifications when the firing rate spikes an order of magnitude. No single layer is enough; the breaker is what saves you at 3am when validation missed something. And when the breaker trips, it must page someone itself — a safety mechanism that fails silently is just another blind spot.

**"Who changed this threshold on Tuesday?" — can you answer?**

Tag every notification with the config version that produced it. Then the incident timeline points at the rule change, not at someone's memory. If you can't trace a page to the config that caused it, you can't do incident review honestly.

**One tenant's malformed regex takes down evaluation for everyone. Acceptable?**

No. Parse and validate each tenant's config in isolation — a bad config disables that tenant's alerts, not the engine. Blast-radius discipline: one tenant's mistake costs that tenant their alerting, nothing else.

**A new alert arrives without a restart. How?**

Add/remove paths: cancel the old scheduled task, build fresh state, schedule the new one — without disturbing what's running. If adding an alert requires a restart, you don't have an alerting system, you have a batch job.

**Notification pipeline**

**A metric hovers at the threshold and pages every few minutes. Fix?**

Hysteresis: N consecutive breaches before the first notify, M consecutive passes before resolve. N=1, M=1 is exactly the naive behavior, so it's a strict generalization — and it names the flapping problem instead of hiding it.

**Several alerts share a dedupe key. When does the group resolve?**

Only when every member has recovered. Track membership as a set of firing alert IDs, not a counter — adds must be idempotent across polls. A counter double-counts a re-poll and the group either never resolves or resolves early. Both are bad at 3am.

**Pure dedupe would suppress re-notifies forever. How does that reconcile with repeat?**

Give dedupe entries a TTL equal to the repeat interval. Inside the window it's a duplicate; after the window it's a scheduled reminder. The repeat rule always wins — dedupe is about identity, repeat is about time, and time moves on.

**The pager API throttles mid-incident. What queues, what drops?**

Queue with priority — CRITICAL ahead of WARN — and define the overflow policy up front: merge, drop lowest priority, or page the operator that the queue itself is full. Deciding this during the incident is how the critical page gets lost behind forty warnings.

**Nobody acks for fifteen minutes. Then what?**

Escalation chains — primary → secondary → manager — which are state machines in their own right, with timers, acks, and hand-offs. "Somebody should do something" is not an escalation policy.

**Deploys need quiet. How do silences not become blind spots?**

Silences must expire, be scoped to specific alerts, and leave an audit trail of who silenced what and when. A forgotten global silence is indistinguishable from a dead alerting system — until the outage.

**"Datacenter-down is firing, so suppress host-down for that DC." Safe?**

Only with the dependency graph available at evaluation time — and one wrong edge silences real pages. Inhibition is powerful and dangerous in exactly equal measure; it deserves the same review rigor as the alerting rules themselves.

**The query returns nothing. Is missing zero?**

No — missing is not zero. A dead agent looks identical to a healthy zero, and treating them the same either hides outages or pages on deploys. Absent-data detection is a separate rule type with its own false-positive profile; tune it independently.

**Watching the watcher**

**How do you know the engine itself is healthy?**

Export its vitals — evaluation latency percentiles, missed/deferred polls, notification success/failure counts, query error rates — and alert on them through a separate, simpler path than the engine itself. The monitor must not depend on the thing it monitors, or the first thing you lose in an outage is visibility into the outage.

**What tells you the whole pipeline is down, as opposed to just quiet?**

A dead-man's switch: a heartbeat alert that fires constantly. If the heartbeat stops arriving downstream, something upstream is dead. It's the one alert that must not depend on the engine it watches — that's the entire point.

**Real rules are "average over five minutes above X," not point comparisons. Where does windowing live?**

In the query (the backend aggregates) or in the engine (a ring buffer per alert). Backend-side is simpler and survives restarts; engine-side gives control but costs memory per alert and complicates backfill. Most teams start backend-side and move only when forced — choose knowing what you give up.
