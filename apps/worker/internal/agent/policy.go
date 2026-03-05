package agent

type Policy interface {
	Version() string
	Choose(optionCount int) int
}

type RandomPolicy struct{}

func (p RandomPolicy) Version() string { return "random-v0-scaffold" }

func (p RandomPolicy) Choose(optionCount int) int {
	if optionCount <= 0 {
		return -1
	}
	return 0
}

type ScriptedPolicy struct {
	Choices []int
	index   int
}

func (p *ScriptedPolicy) Version() string { return "scripted-v0-scaffold" }

func (p *ScriptedPolicy) Choose(optionCount int) int {
	if optionCount <= 0 {
		return -1
	}
	if p.index >= len(p.Choices) {
		return 0
	}
	choice := p.Choices[p.index]
	p.index++
	if choice < 0 || choice >= optionCount {
		return 0
	}
	return choice
}
