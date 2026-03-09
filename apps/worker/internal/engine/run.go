package engine

import (
	"reflect"
	"slices"

	"github.com/athanor/apps/worker/internal/agent"
	"github.com/athanor/apps/worker/internal/rng"
)

const DefaultMaxSteps = 10000

func Run(bundle Bundle, seed uint64, policy agent.Policy) RunResult {
	return RunWithMaxSteps(bundle, seed, policy, DefaultMaxSteps)
}

func RunWithMaxSteps(bundle Bundle, seed uint64, policy agent.Policy, maxSteps int) RunResult {
	result := RunResult{
		BundleHash: bundle.BundleHash,
		Seed:       seed,
		Metrics:    map[string]any{},
	}

	if policy == nil {
		result.Outcome = "error_no_policy"
		return result
	}

	result.AgentVersion = policy.Version()

	nodeIndex := make(map[string]Node, len(bundle.Nodes))
	for _, n := range bundle.Nodes {
		nodeIndex[n.ID] = n
	}

	state := cloneState(bundle.InitialState)
	prng := rng.New(seed)
	current := bundle.EntryNodeID
	steps := 0
	trace := make([]TraceEvent, 0, maxSteps)

	for steps < maxSteps {
		node, ok := nodeIndex[current]
		if !ok {
			outcome := "error_missing_node"
			trace = append(trace, TraceEvent{
				Step:             steps,
				NodeID:           current,
				NodeType:         "missing",
				StateBefore:      cloneState(state),
				StateAfter:       cloneState(state),
				EffectsApplied:   []TraceEffect{},
				AvailableOptions: []TraceOption{},
				Outcome:          &outcome,
			})
			result.Outcome = outcome
			result.StepsTaken = steps
			result.Metrics["final_state"] = cloneState(state)
			result.Trace = trace
			return result
		}

		stateBefore := cloneState(state)
		applyEffects(state, node.Effects)
		stateAfter := cloneState(state)

		switch node.Type {
		case NodeTypeDecision:
			options := filterDecisionOptions(state, node.DecisionOptions)
			if len(options) == 0 {
				outcome := "error_no_options"
				trace = append(trace, traceEvent(
					steps,
					node,
					stateBefore,
					stateAfter,
					traceDecisionOptions(options),
					nil,
					nil,
					&outcome,
				))
				result.Outcome = outcome
				result.StepsTaken = steps
				result.Metrics["final_state"] = cloneState(state)
				result.Trace = trace
				return result
			}

			choice := policy.Choose(len(options), prng.NextIntN)
			if choice < 0 || choice >= len(options) {
				outcome := "error_policy_choice"
				trace = append(trace, traceEvent(
					steps,
					node,
					stateBefore,
					stateAfter,
					traceDecisionOptions(options),
					nil,
					nil,
					&outcome,
				))
				result.Outcome = outcome
				result.StepsTaken = steps
				result.Metrics["final_state"] = cloneState(state)
				result.Trace = trace
				return result
			}

			selected := options[choice]
			nextNodeID := selected.To
			trace = append(trace, traceEvent(
				steps,
				node,
				stateBefore,
				stateAfter,
				traceDecisionOptions(options),
				&TraceSelection{
					Index: choice,
					To:    nextNodeID,
					Guard: traceGuard(selected.Guard),
				},
				&nextNodeID,
				nil,
			))
			current = nextNodeID
			steps++

		case NodeTypeChance:
			options := filterChanceOptions(state, node.ChanceOptions)
			if len(options) == 0 {
				outcome := "error_no_options"
				trace = append(trace, traceEvent(
					steps,
					node,
					stateBefore,
					stateAfter,
					traceChanceOptions(options),
					nil,
					nil,
					&outcome,
				))
				result.Outcome = outcome
				result.StepsTaken = steps
				result.Metrics["final_state"] = cloneState(state)
				result.Trace = trace
				return result
			}

			total := 0.0
			for _, opt := range options {
				if opt.Weight > 0 {
					total += opt.Weight
				}
			}
			if total <= 0 {
				outcome := "error_probability_weights"
				trace = append(trace, traceEvent(
					steps,
					node,
					stateBefore,
					stateAfter,
					traceChanceOptions(options),
					nil,
					nil,
					&outcome,
				))
				result.Outcome = outcome
				result.StepsTaken = steps
				result.Metrics["final_state"] = cloneState(state)
				result.Trace = trace
				return result
			}

			target := prng.NextFloat64() * total
			acc := 0.0
			selected := options[len(options)-1]
			selectedIndex := len(options) - 1
			for index, opt := range options {
				w := opt.Weight
				if w <= 0 {
					continue
				}
				acc += w
				if target < acc {
					selected = opt
					selectedIndex = index
					break
				}
			}

			nextNodeID := selected.To
			weight := selected.Weight
			trace = append(trace, traceEvent(
				steps,
				node,
				stateBefore,
				stateAfter,
				traceChanceOptions(options),
				&TraceSelection{
					Index:  selectedIndex,
					To:     nextNodeID,
					Weight: &weight,
					Guard:  traceGuard(selected.Guard),
				},
				&nextNodeID,
				nil,
			))
			current = nextNodeID
			steps++

		case NodeTypeTerminal:
			outcome := node.Outcome
			if outcome == "" {
				outcome = "terminal"
			}
			trace = append(trace, traceEvent(
				steps,
				node,
				stateBefore,
				stateAfter,
				[]TraceOption{},
				nil,
				nil,
				&outcome,
			))
			result.Outcome = outcome
			result.StepsTaken = steps
			result.Metrics["final_state"] = cloneState(state)
			result.Trace = trace
			return result

		default:
			outcome := "error_invalid_node_type"
			trace = append(trace, traceEvent(
				steps,
				node,
				stateBefore,
				stateAfter,
				[]TraceOption{},
				nil,
				nil,
				&outcome,
			))
			result.Outcome = outcome
			result.StepsTaken = steps
			result.Metrics["final_state"] = cloneState(state)
			result.Trace = trace
			return result
		}
	}

	outcome := "error_overflow"
	trace = append(trace, TraceEvent{
		Step:             maxSteps,
		NodeID:           current,
		NodeType:         "overflow",
		StateBefore:      cloneState(state),
		StateAfter:       cloneState(state),
		EffectsApplied:   []TraceEffect{},
		AvailableOptions: []TraceOption{},
		Outcome:          &outcome,
	})
	result.Outcome = outcome
	result.StepsTaken = maxSteps
	result.Metrics["final_state"] = cloneState(state)
	result.Trace = trace
	return result
}

func traceEvent(
	step int,
	node Node,
	stateBefore map[string]any,
	stateAfter map[string]any,
	availableOptions []TraceOption,
	selectedOption *TraceSelection,
	nextNodeID *string,
	outcome *string,
) TraceEvent {
	return TraceEvent{
		Step:             step,
		NodeID:           node.ID,
		NodeType:         string(node.Type),
		StateBefore:      stateBefore,
		StateAfter:       stateAfter,
		EffectsApplied:   traceEffects(node.Effects),
		AvailableOptions: availableOptions,
		SelectedOption:   selectedOption,
		NextNodeID:       nextNodeID,
		Outcome:          outcome,
	}
}

func traceEffects(effects []Effect) []TraceEffect {
	output := make([]TraceEffect, 0, len(effects))
	for _, effect := range effects {
		output = append(output, TraceEffect{
			Op:    string(effect.Op),
			Path:  effect.Path,
			Value: effect.Value,
		})
	}
	return output
}

func traceDecisionOptions(options []DecisionOption) []TraceOption {
	output := make([]TraceOption, 0, len(options))
	for index, option := range options {
		output = append(output, TraceOption{
			Index: index,
			To:    option.To,
			Guard: traceGuard(option.Guard),
		})
	}
	return output
}

func traceChanceOptions(options []ChanceOption) []TraceOption {
	output := make([]TraceOption, 0, len(options))
	for index, option := range options {
		weight := option.Weight
		output = append(output, TraceOption{
			Index:  index,
			To:     option.To,
			Weight: &weight,
			Guard:  traceGuard(option.Guard),
		})
	}
	return output
}

func traceGuard(guard *Guard) *TraceGuard {
	if guard == nil {
		return nil
	}
	return &TraceGuard{Var: guard.Var, Equals: guard.Equals}
}

func cloneState(in map[string]any) map[string]any {
	if in == nil {
		return map[string]any{}
	}
	out := make(map[string]any, len(in))
	for k, v := range in {
		out[k] = v
	}
	return out
}

func applyEffects(state map[string]any, effects []Effect) {
	for _, e := range effects {
		switch e.Op {
		case EffectSet:
			state[e.Path] = e.Value
		case EffectIncrement:
			state[e.Path] = asFloat(state[e.Path]) + asFloat(e.Value)
		case EffectDecrement:
			state[e.Path] = asFloat(state[e.Path]) - asFloat(e.Value)
		case EffectAddToSet:
			state[e.Path] = addToSet(state[e.Path], e.Value)
		case EffectRemoveFromSet:
			state[e.Path] = removeFromSet(state[e.Path], e.Value)
		}
	}
}

func asFloat(v any) float64 {
	switch n := v.(type) {
	case int:
		return float64(n)
	case int32:
		return float64(n)
	case int64:
		return float64(n)
	case float32:
		return float64(n)
	case float64:
		return n
	default:
		return 0
	}
}

func addToSet(current any, value any) []string {
	set := toStringSet(current)
	v := toString(value)
	if v == "" {
		return set
	}
	if slices.Contains(set, v) {
		return set
	}
	return append(set, v)
}

func removeFromSet(current any, value any) []string {
	set := toStringSet(current)
	v := toString(value)
	if v == "" {
		return set
	}
	out := make([]string, 0, len(set))
	for _, existing := range set {
		if existing != v {
			out = append(out, existing)
		}
	}
	return out
}

func toStringSet(v any) []string {
	switch typed := v.(type) {
	case []string:
		return append([]string{}, typed...)
	case []any:
		out := make([]string, 0, len(typed))
		for _, item := range typed {
			s := toString(item)
			if s != "" {
				out = append(out, s)
			}
		}
		return out
	default:
		return []string{}
	}
}

func toString(v any) string {
	s, ok := v.(string)
	if !ok {
		return ""
	}
	return s
}

func filterDecisionOptions(state map[string]any, options []DecisionOption) []DecisionOption {
	out := make([]DecisionOption, 0, len(options))
	for _, opt := range options {
		if guardPasses(state, opt.Guard) {
			out = append(out, opt)
		}
	}
	return out
}

func filterChanceOptions(state map[string]any, options []ChanceOption) []ChanceOption {
	out := make([]ChanceOption, 0, len(options))
	for _, opt := range options {
		if guardPasses(state, opt.Guard) {
			out = append(out, opt)
		}
	}
	return out
}

func guardPasses(state map[string]any, guard *Guard) bool {
	if guard == nil {
		return true
	}
	value, ok := state[guard.Var]
	if !ok {
		return false
	}
	return reflect.DeepEqual(value, guard.Equals)
}
