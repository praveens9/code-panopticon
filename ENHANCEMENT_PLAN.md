# Panopticon Enhancement Plan v2

## Goal
Create a code quality measurement tool that:
1. **Analyzes current architectural health** of a codebase
2. **Serves as a quality gate for AI-generated code** – track if quality degrades over time

---

## Metrics to Add

### Structural Metrics (Bytecode)

| Metric | Description | Value |
|--------|-------------|-------|
| **Afferent Coupling (Ca)** | Classes that depend *on* this class | High Ca = "load-bearing wall", risky to modify |
| **Instability (I)** | `FanOut / (Ca + FanOut)` | 0 = stable (many dependents), 1 = volatile |
| **LOC** | Lines of code per class | Size indicator for treemap visualization |

> **Note on Nesting Depth:** Currently we measure Cyclomatic Complexity (branches), not nesting depth. Adding explicit nesting depth requires additional bytecode analysis but CC already captures most of the cognitive load signal.

### Composite Risk Metric

```
Risk Score = (Churn × TotalCC × LCOM4) / 100
```

**Purpose:** Single number for ranking classes. AI-generated code that increases Risk Score = regression.

### Recent Churn (Explanation)

Recent Churn (last 90 days) helps distinguish:
- **Dead hotspots:** High lifetime churn but dormant now → lower priority
- **Active hotspots:** Still being changed → higher priority for refactoring

For AI quality measurement: Track if **new** AI-generated changes cluster in already-complex areas.

---

## Visualization Enhancements

### 1. Treemap View (New Tab)
- **Size:** Lines of Code
- **Color:** Risk Score (Red = High, Green = Low)
- **Hierarchy:** Package → Class
- **Click:** Opens forensic panel

### 2. Interactive DataTable (New Tab)
- Sortable columns: Churn, CC, LCOM4, Risk Score
- Filters: Package dropdown, Verdict dropdown
- Click row → Forensic panel

### 3. Improved Quadrant Chart (Existing Bubble)
- Add **background color zones** with labels:
  - 🔴 Top-right = "Burning Platform"
  - 🟡 Top-left = "Complex but Stable"
  - 🟢 Bottom = "Healthy"
- Add **threshold lines** (configurable: Churn=10, CC=50)
- Add **legend** explaining zones

### 4. Dependency Network (Future)
**My recommendation:** Start with **temporal coupling graph** (files that change together) rather than static dependencies:
- Already computed in `GitMiner`
- Shows *behavioral* coupling, not just imports
- More actionable: "These files should be in the same package or merged"

Static Fan-Out is useful later for architecture boundaries.

---

## Implementation Phases

### Phase 1: Quick Wins
- [ ] Add Afferent Coupling (Ca) metric
- [ ] Add Instability (I) metric  
- [ ] Add Risk Score composite
- [ ] Add Recent Churn (90-day window)

### Phase 2: Visualization
- [ ] Add DataTable view as new HTML tab
- [ ] Add background colors + legend to quadrant chart
- [ ] Add LOC metric for treemap sizing

### Phase 3: Treemap
- [ ] Implement D3.js Treemap view
- [ ] Package hierarchy extraction
- [ ] Click integration with forensic panel

### Phase 4: Network Graph
- [ ] Temporal coupling network visualization
- [ ] Optional: Static dependency graph

---

## Verification

1. Run analyzer on PickingService
2. Verify new metrics appear in CSV
3. Open HTML, verify all tabs render
4. Click elements, verify forensic panel works
5. Compare Risk Scores before/after a known refactoring
