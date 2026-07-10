# NEX-224 Deep-Clone Verification Design

## Context

NEX-224 reports that simulation trace snapshots share nested maps and slices because `cloneState` performs only a top-level copy. The production fix already landed in commit `d4fb5ff`: `cloneState` now delegates every value to recursive `deepCloneValue`, and `TestTraceSnapshotsDeepCloneNestedState` covers nested map isolation.

Two acceptance criteria still lack direct evidence:

- deterministic runs must include nested state fixtures;
- representative deep-clone overhead must remain below 2x the pre-fix shallow-clone implementation.

This work completes those verification gaps. It does not change production behavior unless measurement exposes an unacceptable regression.

## Scope

The implementation will modify `apps/worker/internal/engine/run_test.go` and add a focused `apps/worker/internal/engine/run_benchmark_test.go` benchmark file.

It will:

1. Strengthen `TestDeterministicWithSameSeed` with JSON-shaped nested maps and slices and compare complete `RunResult` values.
2. Add `BenchmarkRunTypicalNestedState`, which runs a representative multi-step simulation containing scalar, nested-map, and nested-slice state.
3. Apply the identical benchmark test temporarily to commit `d4fb5ff^` and compare it with the benchmark on the NEX-224 branch under the same Go version, machine, command, and benchmark duration.
4. Record the benchmark outputs and computed ratio in the Linear issue or pull request.

The implementation will not add clone-strategy injection, timing assertions, or production-only performance machinery.

## Benchmark Design

The benchmark measures the full `Run` path, not `cloneState` in isolation. This matches the acceptance criterion's practical concern: whether correct trace snapshots make typical simulations materially slower.

The fixture will use:

- a deterministic multi-step decision graph;
- representative JSON-decoded state shapes (`map[string]any` and `[]any`);
- a mixture of scalar fields, nested objects, and nested arrays;
- a reusable bundle and policy setup so benchmark iterations measure simulation work rather than fixture construction.

Both revisions will be measured with the same command and multiple samples. The comparison will use the median `ns/op` for each revision:

```text
overhead ratio = current median ns/op / pre-fix median ns/op
```

The acceptance criterion passes when the ratio is below `2.0`. The benchmark itself will not fail on wall-clock timing because CI timing assertions are noisy and machine-dependent.

## Determinism Coverage

`TestDeterministicWithSameSeed` will initialize nested inventory/profile data and include nested effect values. It will run the same bundle twice with the same seed and compare the complete results using `reflect.DeepEqual`.

This proves that outcome, trace snapshots, selected options, metrics, and nested final state remain deterministic together. The existing trace-isolation regression test remains focused on aliasing and is not duplicated.

## Failure Handling

If the measured ratio is below 2x, the benchmark evidence completes the remaining acceptance criterion.

If the ratio is 2x or greater, implementation stops before changing production code. The measured profiles and likely allocation hotspots will be reviewed, and any optimization will receive a separate design because snapshot correctness must not be weakened to satisfy the performance target.

## Verification

Completion requires:

- the existing trace-isolation regression test to fail against a deliberately shallow clone and pass against the current deep clone;
- the strengthened same-seed test to compare complete results containing nested map and slice fixtures;
- `go test ./internal/engine -count=1` to pass;
- the worker golden-corpus and full worker test suites to pass;
- benchmark samples from `d4fb5ff^` and the current branch using identical settings;
- a documented median overhead ratio below 2x.
