# Alert Execution Engine — Interview Practice

Reconstructed from candidate reports of Chronosphere's technical phone screen (2021–2026). This is NOT their actual code — but the shape matches what's reported: you're handed interfaces (the "boilerplate") and you design + implement the engine, then validate it by running it.

## The three parts

The interviewer reveals these progressively. Build them in order — one commit per part.

**Part I — basic engine.** Load alert configs once at startup, poll each query on its check interval. Notify on *every* breaching check (threshold is strict `>`), resolve on transition back to PASS. Concurrency expected, clean shutdown required.

**Part II — repeat interval.** Notify on transition into CRITICAL; while it stays critical, re-notify only every repeat interval.

**Part III — warning threshold.** PASS ↔ WARN ↔ CRITICAL state machine: value > critical → CRITICAL, warning < value ≤ critical → WARN, value ≤ warning → PASS. (Open question for the interviewer: does entering WARN notify?)

## Run

Needs JDK 17+ and Maven. Manual validation via the demo main:

```
mvn -q compile exec:java -Dexec.mainClass=com.chronoprep.Demo
```

`Demo.java` wires the engine to stub implementations that print, and drives it with the hint timeline (values 110, 1234, 45, 62). Eyeball the trace against the expected output for the part you're on.

## How to practice (this matters as much as the code)

- Timebox 60–75 minutes, like the real screen.
- **Narrate out loud the entire time.** Their recruiters say the screen "over-indexes on communication" — silent correct code can still fail.
- Implement one part at a time. When a part is done, ask "what's next?" explicitly — never assume you're done (the rubric fails people who stop early).
- Clarifying questions are graded: `>` vs `>=`, does WARN notify, what should `executeQuery` throwing mean.
- Self-review per part:
  - Is per-alert state thread-safe?
  - Does `stop()` actually terminate all threads?
  - What happens when `executeQuery` throws?
  - What happens with zero alerts, or `stop()` before `start()`?
