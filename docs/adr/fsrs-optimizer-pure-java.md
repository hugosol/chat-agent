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
- Numerical gradients introduce approximation error — W[21] output will not be bit-identical to py-fsrs. Validation uses loss-based criteria (see Mitigation), not per-element W[21] comparison
- 42 loss evaluations per gradient step means slower convergence than analytical gradients. For 10,000 ReviewLogs: ~20 seconds; for 100,000: ~3 minutes. Acceptable for async background task
- Must manually port CosineAnnealingLR and Adam update rules from py-fsrs torch code
- If py-fsrs changes its optimizer (e.g., FSRS-7), we must manually re-align

## Mitigation

### Cross-validation strategy

Cross-validate against py-fsrs using the identical `review_logs_josh_1711744352250_to_1728234780857.csv` test dataset:

| Metric | Value |
|--------|-------|
| Total reviews | 12,580 |
| Unique cards | 1,205 |
| Avg reviews/card | 10.4 (min=1, max=54) |
| Rating distribution | Again 29.2%, Good 70.8%, Hard 0.008% (1 entry), Easy 0% |
| Date range | 2024-03-30 ~ 2024-10-07 (6 months) |

**Data limitation**: Hard (1 entry) and Easy (0 entries) ratings are virtually absent. Parameters w[15] (Hard penalty) and w[16] (Easy bonus) receive no meaningful training signal from this dataset — they are excluded from cross-validation assertions for both py-fsrs and Java output.

### Validation criteria (in order of importance)

**Primary (must pass):**
- BCELoss with optimized W[21] must be strictly lower than BCELoss with default W[21] on the same dataset. This proves the optimizer produces parameters that fit the review history better than unoptimized defaults.

**Auxiliary (should pass):**
- Optimized BCELoss must be ≤ py-fsrs optimized BCELoss × 1.01 (within 1% of py-fsrs result). This calibrates that the Java numerical-gradient optimizer achieves comparable optimization quality to py-fsrs's analytical-gradient optimizer. 1% loss difference translates to sub-day interval variance — invisible to the Learner.

**W[21] not compared directly:**
- No per-element tolerance assertion on W[21] values. The loss surface in 21-dimensional parameter space is flat near the optimum — different optimizer paths (numerical vs analytical gradients) converge to different W[21] vectors that produce nearly identical loss. Direct W[21] comparison would reject functionally equivalent parameter sets.

**Parameters w[15] and w[16] excluded:**
- These parameters control Hard penalty and Easy bonus respectively. With 0–1 Hard/Easy ratings in the test dataset, both py-fsrs and Java optimizer produce values driven entirely by initialization + parameter bounds rather than learning signal. Cross-validation ignores these two indices.

### Additional validation tests
- Determinism: same input twice → identical output (fixed RNG seed=42, matching py-fsrs)
- Unordered-input invariance: shuffling ReviewLog order does not change output
- Boundary: empty list → returns default W[21]; <512 reviews → returns defaults
- Edge cases: all-Again ratings do not crash; all-Easy ratings (difficulty → 1.0) do not crash
- Synthetic recovery: generate ReviewLogs from known W[21] via FsrsScheduler simulation, verify optimizer recovers weights within ±0.1 per element

### Implementation review
- Document the complete loss function, gradient computation, and Adam update rules inline so future readers can audit against upstream FSRS specs
- Numeric gradient step h=1e-4 is documented and configurable for future precision tuning
