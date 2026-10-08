# Alert Execution Engine — Interview Practice

Reconstructed from candidate reports of Chronosphere's technical phone screen
(2022–2026). This is NOT their actual code — but the shape matches what's
reported: you're handed interfaces (the "boilerplate") and you design +
implement the engine, plus tests.

## The prompt

`AlertEngine.java` documents the required behavior. In short: load alert
configs once at startup, poll each query on its interval, notify on
CRITICAL, resolve on recovery, re-notify on the repeat interval while
critical stays firing. Concurrency expected, clean shutdown required.

## Run

Needs JDK 17+ and Maven:

```
mvn test
```

## How to practice (this matters as much as the code)

- Timebox 60–75 minutes, like the real screen.
- **Narrate out loud the entire time.** Their recruiters say the screen
  "over-indexes on communication" — silent correct code can still fail.
- Implement the engine first, then write the four TODO tests.
- When done, self-review before looking at anything else:
  - Is per-alert state thread-safe?
  - Does `stop()` actually terminate all threads?
  - What happens when `executeQuery` throws?
  - What happens with zero alerts, or `stop()` before `start()`?
