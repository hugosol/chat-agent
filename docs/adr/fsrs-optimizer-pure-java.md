# ADR: Pure Java FSRS Optimizer — Manual Adam + Numerical Gradients (Not Rust fsrs-rs)

**Date:** 2026-06-05
**Status:** Accepted

## Context

The FSRS-6 optimizer computes optimal W[21] parameters from a Learner's ReviewLog history by minimizing a BCELoss (Binary Cross Entropy) cost function via gradient descent. The official Anki implementation uses a Rust library (`fsrs-rs`), which ts-fsrs packages as a WASM binary (`@open-spaced-repetition/binding`). py-fsrs uses PyTorch Adam with analytical gradients and CosineAnnealingLR scheduling — NOT scipy Nelder-Mead (the `scipy.optimize` import in py-fsrs code is vestigial; actual optimization is done entirely in torch).

For our Java backend, we had two paths: (1) call the official Rust binary via CLI subprocess or JNI, or (2) implement the optimizer in pure Java.

## Decision

**Implement the FSRS optimizer in pure Java using a manually coded Adam optimizer with finite-difference numerical gradients (h=1e-4).** No PyTorch, no Commons Math3, no Rust/WASM dependency. The optimizer is a pure-Java class (`FsrsOptimizer`) accepting `List<ReviewLog>` + `FsrsSchedulerConfig` and returning `OptimizeResult(double[] weights, double finalLoss, int iterations, long durationMs)`.

## Alternatives Considered

### Rust fsrs-rs via CLI/JNI
- **Pro**: guaranteed mathematical precision matching Anki and ts-fsrs (same Cargo crate)
- **Pro**: no need to port optimization logic
- **Con**: platform binary dependency — introduces native code that must be compiled per-OS (Windows/Linux/macOS), breaking the current single-JAR deployment model
- **Con**: JNI adds memory management risk and debugging complexity
- **Con**: CLI subprocess approach has startup latency per optimization run and fragile error handling
- **Rejected because**: platform dependency and deployment complexity outweigh precision guarantee

### Manual Adam + Numerical Gradients (chosen)
- **Pro**: same algorithm as py-fsrs (Adam + BCELoss + CosineAnnealingLR), enabling direct cross-validation against py-fsrs test vectors
- **Pro**: zero external dependencies — Adam is ~20 lines of Java, numerical gradients are ~30 lines
- **Pro**: full control over convergence, early stopping, progress reporting
- **Pro**: parameter clamping to py-fsrs bounds (LOWER_BOUNDS/UPPER_BOUNDS) at each gradient step
- **Con**: numerical gradients (finite differences) require 42 loss evaluations per gradient step (21 parameters × 2 perturbations), slower than analytical gradients
- **Con**: numerical stability depends on step size h; h=1e-4 chosen to match py-fsrs's effective precision

### Commons Math3 SimplexOptimizer (Nelder-Mead) — rejected
- **Pro**: mature library, derivative-free
- **Con**: algorithm differs from py-fsrs (which uses Adam), making cross-validation of W[21] output impossible — different optimizers converge to different local minima even with the same loss function
- **Con**: adds external dependency (`commons-math3`) that is not needed for any other module
- **Con**: Nelder-Mead on 21-dimensional bounded space converges slowly and unreliably without careful simplex initialization
- **Rejected because**: algorithm divergence from py-fsrs breaks the primary validation strategy (byte-identical test data → comparable W[21] output)

### DeepLearning4J / ND4J — rejected
- **Pro**: closest to PyTorch experience, analytical gradients via autodiff
- **Con**: heavy dependency (~100MB), overkill for a 21-parameter optimization
- **Rejected because**: weight-to-dependency ratio is unreasonable

### Pure Java — grid/random search only — rejected
- **Pro**: trivial implementation, no convergence issues
- **Con**: 21-dimensional grid search has exponential complexity; random search converges too slowly for usable results
- **Rejected because**: not a real optimizer

## Consequences

### Positive
- Single-JAR deployment preserved — no native binaries, no DL framework, no WASM runtime
- Algorithm matches py-fsrs: Adam + BCELoss + CosineAnnealingLR + same parameter bounds → direct cross-validation possible
- Full control over optimization lifecycle (progress callbacks via `ProgressCallback` functional interface, timeout, early stopping)
- Optimizer runs on the same `optimizerExecutor` thread pool as other async tasks
- Deterministic output (fixed RNG seed = 42, matching py-fsrs)

### Negative
- Numerical gradients introduce approximation error — W[21] output will not be bit-identical to py-fsrs. Tolerance target: each element within ±0.05 of py-fsrs output, and optimized loss must be strictly lower than default loss
- 42 loss evaluations per gradient step means slower convergence than analytical gradients. For 10,000 ReviewLogs: ~20 seconds; for 100,000: ~3 minutes. Acceptable for async background task
- Must manually port CosineAnnealingLR and Adam update rules from py-fsrs torch code
- If py-fsrs changes its optimizer (e.g., FSRS-7), we must manually re-align

## Mitigation

- Cross-validate against py-fsrs using the identical `review_logs_josh_1711744352250_to_1728234780857.csv` test dataset (12,580 real Anki review logs) — same data fed to both optimizers, W[21] output compared with tolerance 0.05 per element
- Additional validation: optimized BCELoss must be strictly lower than BCELoss with default W[21]
- Determinism test: same input twice → identical output
- Unordered-input test: shuffling ReviewLog order does not change output
- Document the complete loss function, gradient computation, and Adam update rules inline so future readers can audit against upstream FSRS specs
- Numeric gradient step h=1e-4 is documented and configurable for future precision tuning
