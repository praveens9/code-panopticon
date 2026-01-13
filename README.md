# Code Panopticon

A **polyglot code forensic tool** that identifies architectural decay by fusing **Evolutionary History (Git)** with **Structural Analysis**.

Supports: **Java** (bytecode), **Python** (AST), **JavaScript/TypeScript** (regex), and any other language (generic fallback).

---

## 🚀 Purpose

In large-scale projects, standard linters often fail to capture the "Context of Risk." This tool categorizes code based on its **Volatility** (how often it changes) and its **Internal Complexity** (how hard it is to maintain).

The goal is to identify **"Burning Platforms"**—highly active files that are structurally unsound—so teams can prioritize refactoring where it matters most.

---

## 📊 The Metrics

| Metric | Category | Description |
| :--- | :--- | :--- |
| **Churn** | Evolutionary | Number of Git commits touching the file. |
| **Peers** | Evolutionary | Temporal coupling. Files that change together. |
| **Complexity (CC)** | Structural | Cyclomatic complexity. Measures branching. |
| **Max CC** | Structural | Complexity of the worst function. |
| **Cohesion** | Structural | How related methods are to each other. |
| **Fan-Out** | Structural | Number of dependencies (imports). |
| **Risk Score** | Composite | `(Churn × CC × LCOM4) / 100` |

---

## 📋 Verdict Definitions

| Verdict | Meaning | Action |
| :--- | :--- | :--- |
| **OK** | Metrics within healthy thresholds | None |
| **BRAIN_METHOD** | Contains massive, complex methods | Extract Method |
| **SHOTGUN_SURGERY** | Changes ripple to many files | Centralize logic |
| **SPLIT_CANDIDATE** | Multiple unrelated clusters | Split the class |
| **TOTAL_MESS** | Very low cohesion | High priority refactor |
| **GOD_CLASS** | Too complex and too large | Full decomposition |

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

#- **System Map**: Interactive circle packing visualization of codebase structure and risk. Zoom in to exploring folders and files.played files

# System Map visualization
system_map:
  max_files: 100  # Limit displayed files
```

See [`panopticon.yaml`](panopticon.yaml) for all available options.

---

## 📁 Output

- **panopticon-report.html** - Interactive dashboard with quadrant view, treemap, and network graph
- **panopticon-report.csv** - Spreadsheet-friendly data export

---

## 🏗️ Architecture

```
PolyglotApp (CLI)
    │
    ├── GitMiner (evolutionary metrics)
    │
    ├── AnalyzerRegistry (plugin system)
    │   ├── JavaBytecodeAnalyzer (SootUp)
    │   ├── PythonAnalyzer (AST)
    │   ├── JavaScriptAnalyzer (regex)
    │   └── GenericTextAnalyzer (fallback)
    │
    ├── ForensicRuleEngine (configurable verdicts)
    │
    └── Reporters (HTML, CSV)
```

---

## 📚 Documentation

- [Architecture Plan](docs/plan.md) - Detailed design decisions
- [Analyzer Reference](docs/analyzer-reference.md) - Technical implementation details
- [Progress Tracker](docs/progress.md) - Implementation status

---

#static-analysis #code-quality #git-forensics #polyglot #architecture
