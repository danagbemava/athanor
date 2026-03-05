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

	for steps < maxSteps {
		node, ok := nodeIndex[current]
		if !ok {
			result.Outcome = "error_missing_node"
			result.StepsTaken = steps
			result.Metrics["final_state"] = state
			return result
		}

		applyEffects(state, node.Effects)

		switch node.Type {
		case NodeTypeDecision:
			options := filterDecisionOptions(state, node.DecisionOptions)
			if len(options) == 0 {
				result.Outcome = "error_no_options"
				result.StepsTaken = steps
				result.Metrics["final_state"] = state
				return result
			}
			choice := policy.Choose(len(options), prng.NextIntN)
			if choice < 0 || choice >= len(options) {
				result.Outcome = "error_policy_choice"
				result.StepsTaken = steps
				result.Metrics["final_state"] = state
				return result
			}
			current = options[choice].To
			steps++

		case NodeTypeChance:
			options := filterChanceOptions(state, node.ChanceOptions)
			if len(options) == 0 {
				result.Outcome = "error_no_options"
				result.StepsTaken = steps
				result.Metrics["final_state"] = state
				return result
			}

			total := 0.0
			for _, opt := range options {
				if opt.Weight > 0 {
					total += opt.Weight
				}
			}
			if total <= 0 {
				result.Outcome = "error_probability_weights"
				result.StepsTaken = steps
				result.Metrics["final_state"] = state
				return result
			}

			target := prng.NextFloat64() * total
			acc := 0.0
			selected := options[len(options)-1]
			for _, opt := range options {
				w := opt.Weight
				if w <= 0 {
					continue
				}
				acc += w
				if target < acc {
					selected = opt
					break
				}
			}

			current = selected.To
			steps++

		case NodeTypeTerminal:
			result.Outcome = node.Outcome
			if result.Outcome == "" {
				result.Outcome = "terminal"
			}
			result.StepsTaken = steps
			result.Metrics["final_state"] = state
			return result

		default:
			result.Outcome = "error_invalid_node_type"
			result.StepsTaken = steps
			result.Metrics["final_state"] = state
			return result
		}
	}

	result.Outcome = "error_overflow"
	result.StepsTaken = maxSteps
	result.Metrics["final_state"] = state
	return result
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
