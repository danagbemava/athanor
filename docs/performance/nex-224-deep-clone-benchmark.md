# NEX-224 Deep-Clone Benchmark Evidence

## Purpose and acceptance threshold

This benchmark measures the full worker simulation `Run` path with representative nested state. It compares the pre-fix shallow-clone implementation with the current deep-clone implementation using identical benchmark code. The predefined acceptance threshold is a current-to-pre-fix median `ns/op` ratio below `2.0`.

## Revisions and benchmark identity

- Pre-fix revision: `71ec2fba31a1dab8c1f1f69e555757b544e1717e`
- Current benchmark commit: `b361a5efa6532e5806ca8ebe9e80b3bca52e92b2`
- Benchmark file: `apps/worker/internal/engine/run_benchmark_test.go`
- Benchmark file blob: `557faa63ed581308b27f7e5ab900215f1530b9b4`

## Command and environment

The following command was run from `apps/worker` in each worktree:

```sh
go test ./internal/engine -run '^$' -bench '^BenchmarkRunTypicalNestedState$' -benchmem -benchtime=1s -count=5
```

- Go: `go version go1.26.0 darwin/arm64`
- GOOS: `darwin`
- GOARCH: `arm64`
- CPU: `Apple M3`

Execution order was sequential: all five pre-fix baseline samples were run first, followed by all five current samples.

## Exact samples

| Order | Revision | ns/op | B/op | allocs/op |
|---:|---|---:|---:|---:|
| 1 | Pre-fix | 225,060 | 1,296,914 | 102 |
| 2 | Pre-fix | 214,695 | 1,296,909 | 102 |
| 3 | Pre-fix | 202,672 | 1,296,907 | 102 |
| 4 | Pre-fix | 118,921 | 1,296,906 | 102 |
| 5 | Pre-fix | 177,189 | 1,296,905 | 102 |
| 6 | Current | 181,101 | 1,313,250 | 264 |
| 7 | Current | 218,819 | 1,313,247 | 264 |
| 8 | Current | 246,693 | 1,313,247 | 264 |
| 9 | Current | 263,279 | 1,313,249 | 264 |
| 10 | Current | 283,665 | 1,313,249 | 264 |

## Results

- Pre-fix median: `202,672 ns/op`, `1,296,907 B/op`, `102 allocs/op`
- Current median: `246,693 ns/op`, `1,313,249 B/op`, `264 allocs/op`
- Current/pre-fix median ratio: `1.217203166`

Observed median ratio 1.217x; passes the predefined <2.0 threshold.

These sequential samples are noisy, and running the complete baseline block before the complete current block may introduce order bias. The ratio is therefore a point estimate, not a precise stable slowdown. As allocation context, the median increased from 102 to 264 allocations per operation, while median bytes increased from 1,296,907 to 1,313,249 B/op.

## Cleanup

The temporary `/tmp/athanor-nex-224-baseline` worktree and `codex/nex-224-baseline` branch were removed after measurement. A final worktree listing confirmed that only the primary repository and NEX-224 feature worktree remained.
