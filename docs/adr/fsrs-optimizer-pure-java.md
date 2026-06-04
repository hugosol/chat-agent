# ADR: Pure Java FSRS Optimizer (Not Rust fsrs-rs)

**Date:** 2026-06-05
**Status:** Accepted

## Context

The FSRS-6 optimizer computes optimal W[21] parameters from a Learner's ReviewLog history by minimizing a log-loss cost function over multidimensional space. The official Anki implementation uses a Rust library (`fsrs-rs`), which ts-fsrs packages as a WASM binary (`@open-spaced-repetition/binding`). py-fsrs uses `scipy.optimize.minimize` with Nelder-Mead.

For our Java backend, we had two paths: (1) call the official Rust binary via CLI subprocess or JNI, or (2) implement the optimizer in pure Java using a numerical optimization library.

## Decision

**Implement the FSRS optimizer in pure Java using Apache Commons Math3 (`SimplexOptimizer` for Nelder-Mead, `BrentOptimizer` for 1D search).** No external Rust/WASM dependency.

## Alternatives Considered

### Rust fsrs-rs via CLI/JNI
- **Pro**: guaranteed mathematical precision matching Anki and ts-fsrs (same Cargo crate)
- **Pro**: no need to port optimization logic
- **Con**: platform binary dependency — introduces native code that must be compiled per-OS (Windows/Linux/macOS), breaking the current single-JAR deployment model
- **Con**: JNI adds memory management risk and debugging complexity
- **Con**: CLI subprocess approach has startup latency per optimization run and fragile error handling
- **Rejected because**: the platform dependency and deployment complexity outweigh the precision guarantee for a single-user English learning app where sub-percent parameter differences are invisible to the Learner

### Pure Java — manual Nelder-Mead
- **Pro**: zero external dependencies
- **Con**: implementing a numerically stable multidimensional optimizer from scratch is ~500 lines of delicate math with corner cases (simplex collapse, convergence criteria, boundary handling)
- **Rejected because**: Commons Math3 is a mature, well-tested library and adds negligible JAR size

### Pure Java — grid/random search only
- **Pro**: trivial implementation, no convergence issues
- **Con**: 21-dimensional grid search has exponential complexity; random search converges too slowly for usable results
- **Rejected because**: not a real optimizer — would produce worse parameters than the defaults for any practical dataset

## Consequences

### Positive
- Single-JAR deployment preserved — no native binaries, no WASM runtime, no platform-specific builds
- Commons Math3 is a stable Apache library with permissive licensing and zero native dependencies
- Full control over optimization lifecycle (progress callbacks, timeout, early termination)
- Optimizer runs on the same `optimizerExecutor` thread pool as other async tasks

### Negative
- Optimization results may differ slightly from `fsrs-rs`/Anki for the same input (different optimizer implementation, different convergence thresholds) — though both minimize the same cost function, Nelder-Mead implementations vary in subtle ways
- Must port the cost function (log-loss) and its wrapper from py-fsrs's Python code, introducing risk of implementation drift
- If fsrs-rs later introduces algorithmic improvements (e.g., FSRS-7 optimizer), we must port them manually

## Mitigation

- Cross-validate initial results against py-fsrs by feeding identical ReviewLog CSV to both and comparing W[21] output within tolerance
- Document the cost function formula explicitly so future readers can audit against upstream FSRS specs
