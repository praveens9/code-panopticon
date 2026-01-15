# Code Panopticon

> *"From Passive Visualizer to Active Architectural Advisor"*

A **polyglot code forensic intelligence platform** that identifies architectural decay by fusing **Evolutionary History (Git)**, **Structural Analysis**, **Social Dynamics**, and **Testability Assessment**.

Supports: **Java** (bytecode), **Python** (AST), **JavaScript/TypeScript** (regex), and any other language (generic fallback).

---

## 🚀 Purpose

In large-scale projects, standard linters fail to capture the **Context of Risk**. Code Panopticon analyzes code through four integrated lenses:

| Dimension | Question | Metrics |
|-----------|----------|---------|
| **📐 Structure** | How complex is this code? | Complexity, Cohesion, Coupling |
| **⏱️ Evolution** | How often does it change? | Churn, Temporal Coupling |
| **👥 Social** | Who knows this code? | Bus Factor, Knowledge Islands |
| **🛡️ Safety** | Can I refactor confidently? | Testability Score, Seams |

The goal: Identify **"Burning Platforms"**—highly active files that are structurally unsound, maintained by absent experts, with no safety net—so teams can prioritize refactoring where it matters most.

---

## 📊 The Metrics

### Evolutionary Metrics

| Metric | Description |
|--------|-------------|
| **Churn** | Number of Git commits touching the file |
| **Recent Churn** | Commits in the last 90 days |
| **Temporal Coupling** | Files that change together (hidden dependencies) |
| **Days Since Last Commit** | Code freshness/staleness indicator |

### Structural Metrics

| Metric | Description |
|--------|-------------|
| **Complexity (CC)** | Cyclomatic complexity—measures branching |
| **Max CC** | Complexity of the worst function |
| **Cohesion (LCOM4)** | How related methods are to each other |
| **Fan-Out** | Number of dependencies (imports) |
| **Instability** | Ratio of outbound to total coupling |

### Social Metrics

| Metric | Description |
|--------|-------------|
| **Author Count** | Number of distinct contributors |
| **Primary Author %** | Knowledge concentration |
| **Bus Factor** | Authors needed to cover 50% of code |

### Composite Metrics

| Metric | Formula |
|--------|---------|
| **Risk Score** | `Complexity × Churn × LCOM4` (amplified by social/safety factors) |

---

## 📋 Verdict Definitions

### Structural Verdicts

| Verdict | Meaning | Action |
|---------|---------|--------|
| **OK** | Metrics within healthy thresholds | None |
| **BLOATED** | Large file with many LOC | Consider splitting |
| **BRAIN_METHOD** | Contains massive, complex methods | Extract Method |
| **SPLIT_CANDIDATE** | Multiple unrelated clusters | Split the class |
| **HIGH_COUPLING** | Too many dependencies | Dependency Inversion |

### Behavioral Verdicts

| Verdict | Meaning | Action |
|---------|---------|--------|
| **TOTAL_MESS** | High Complexity + High Churn | **Immediate refactor priority** |
| **GOD_CLASS** | Too complex and too large | Full decomposition |
| **SHOTGUN_SURGERY** | Changes ripple to many files | Centralize logic |
| **HIDDEN_DEPENDENCY** | High temporal coupling, low imports | Make explicit |
| **FRAGILE_HUB** | Central coordinator, frequent changes | Stabilize interface |

### Social Verdicts

| Verdict | Meaning | Action |
|---------|---------|--------|
| **KNOWLEDGE_ISLAND** | Single author + inactive expert | **Knowledge transfer first** |

### Safety Verdicts

| Verdict | Meaning | Action |
|---------|---------|--------|
| **UNTESTED_HOTSPOT** | High risk + No test coverage | **Write tests before refactoring** |

---

## 🏃 How to Run

### Prerequisites
- Java 17+
- Python 3 (for Python analysis)
- Node.js (optional, for ESLint-based JS analysis)

### Analyze a Repository

```bash
# 1. Clone and build
git clone <repo-url>
cd code-panopticon
./gradlew compileJava

# 2. Analyze a local repo with Java bytecode
./gradlew run --args="--repo /path/to/project --classes /path/to/compiled/classes" --console=plain

# 3. Analyze a local repo (Python, JS, etc.)
./gradlew run --args="--repo /path/to/project" --console=plain

# 4. Analyze a remote GitHub repo (auto-clones)
./gradlew run --args="--repo https://github.com/user/repo" --console=plain

# 5. Keep the cloned repo after analysis
./gradlew run --args="--repo https://github.com/user/repo --keep-clone" --console=plain

# 6. Large repo mode (only analyze hotspots)
./gradlew run --args="--repo /path/to/project --hotspots-only --min-churn 5"
```

### CLI Options

| Option | Description |
|--------|-------------|
| `--repo <path\|url>` | Path or URL to Git repository (required) |
| `--classes <path>` | Path to compiled Java classes (optional) |
| `--output <dir>` | Output directory for reports (default: `reports/`) |
| `--hotspots-only` | Only analyze files with Git activity |
| `--min-churn <n>` | Minimum churn to include a file |
| `--keep-clone` | Keep cloned repo (for remote URLs) |

---

## ⚙️ Configuration

Create a `panopticon.yaml` in your project root to customize analysis:

```yaml
# Thresholds for verdict classification
thresholds:
  total_mess:
    churn: 20
    complexity: 50
  brain_method:
    max_cc: 15
  split_candidate:
    lcom4: 3
  bloated:
    loc: 500

# Risk score weights
weights:
  churn: 1.0
  complexity: 1.0
  coupling: 0.1

# Files to exclude from analysis
exclusions:
  - "**/test/**"
  - "**/node_modules/**"

# System Map visualization
system_map:
  max_files: 100
```

See [`panopticon.yaml`](panopticon.yaml) for all available options.

---

## 📁 Output

- **panopticon-report.html** - Interactive dashboard with:
  - **Bubble Chart**: Churn × Complexity visualization
  - **System Map**: Circle-packing codebase explorer
  - **Network Graph**: Temporal coupling visualization
  - **Data Table**: Sortable, filterable file metrics
  - **Side Panel**: Deep dive with forensics, testability, and action plans
  
- **panopticon-report.csv** - Spreadsheet-friendly data export

---

## 🏗️ Architecture

```
PolyglotApp (CLI)
    │
    ├── GitMiner (evolutionary + social metrics)
    │   ├── Churn Analysis
    │   ├── Temporal Coupling
    │   └── Social Forensics (Author Distribution, Bus Factor)
    │
    ├── AnalyzerRegistry (plugin system)
    │   ├── JavaBytecodeAnalyzer (SootUp)
    │   ├── PythonAnalyzer (AST)
    │   ├── JavaScriptAnalyzer (regex)
    │   └── GenericTextAnalyzer (fallback)
    │
    ├── ForensicRuleEngine (configurable verdicts)
    │   ├── Structural Rules
    │   ├── Behavioral Rules
    │   ├── Social Rules
    │   └── Safety Rules
    │
    └── Reporters (HTML, CSV)
```

---

## 🧠 Philosophy

Code Panopticon is built on four core beliefs:

1. **Complexity is only a problem if we have to work with it** — A complex file untouched for years is stable; one changing weekly is a fire.

2. **Code is written by teams, not individuals** — Knowledge islands and absent experts are organizational risks.

3. **Fear without confidence is paralysis** — Show the safety net (tests, seams) before prescribing refactoring.

4. **Diagnosis must lead to action** — Don't just say "God Class"; provide the refactoring pathway.

Read the full [Philosophy Document](philosophy.md) for the complete design rationale.

---

## 📚 Documentation

- [Philosophy](philosophy.md) - Core beliefs and design rationale
- [Architecture Plan](docs/plan.md) - Detailed design decisions
- [Analyzer Reference](docs/analyzer-reference.md) - Technical implementation details
- [Research](docs/research.md) - Product direction and paradigm extensions

---

## 🎯 Roadmap

### Current (v2.x)
- ✅ Bubble Chart visualization
- ✅ System Map (circle-packing)
- ✅ Network Graph (temporal coupling)
- ✅ Configurable verdicts via YAML
- ✅ Polyglot analysis (Java, Python, JS)

### Next (v3.0 — "Active Advisor")
- 🔲 **Social Forensics Panel** — Author distribution, bus factor, knowledge islands
- 🔲 **Testability X-Ray** — Test coverage correlation, seam identification
- 🔲 **Refactoring Workflows** — LCOM4 clusters as named responsibilities

---

## 🏷️ Tags

#static-analysis #code-quality #git-forensics #polyglot #architecture #technical-debt

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
