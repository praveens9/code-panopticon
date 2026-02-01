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


### ⚠️ Verdict Priority (Masking Rules)
The system uses a strict priority engine. Even if a file has multiple issues (e.g., it is both a `TOTAL_MESS` and has no tests), only the **highest priority verdict** is displayed to focus your attention on the most critical risk.

| Priority | Verdict | Why? |
|----------|---------|------|
| **0 (Highest)** | **KNOWLEDGE_ISLAND** | 🚨 **Social Risk**: If the only expert leaves, the code becomes unmaintainable. |
| **1** | **SHOTGUN_SURGERY** | 🌊 **Architecture Risk**: Changing this file breaks the system everywhere. |
| **2** | **UNTESTED_HOTSPOT** | 🔥 **Safety Risk**: High complexity/churn with no safety net. |
| **3** | **HIDDEN_DEPENDENCY** | 🕸️ **Hidden Risk**: Invisible coupling. |
| **4** | **GOD_CLASS** / **TOTAL_MESS** | 🏗️ **Local Risk**: Bad design within the file. |
| **5 (Lowest)** | **BLOATED** / **COMPLEX** | ⚠️ **Warning**: Code smell. |

> *Example: A file that is a `TOTAL_MESS` and has no tests (`UNTESTED_HOTSPOT`) but is maintained by a single person will be labeled **KNOWLEDGE_ISLAND**. Fix the knowledge gap first (Code Walkthrough), then add tests, then refactor.*

---

## 🏃 How to Run


### Prerequisites
- Java 17+
- Python 3 (for Python analysis)
- Node.js (optional, for ESLint-based JS analysis)

### Quick Start

```bash
# Clone and build
git clone https://github.com/praveens9/code-panopticon.git
cd code-panopticon
./gradlew compileJava
```

---

## 📖 CLI Reference

All options are passed via `--args="..."` to Gradle. The `--console=plain` flag (Gradle option) provides cleaner output.

### Required Options

| Option | Description |
|--------|-------------|
| `--repo <path\|url>` | Path to local Git repository **or** GitHub URL |

### Optional Options

| Option | Description | Default |
|--------|-------------|---------|
| `--classes <path>` | Path to compiled Java `.class` files (for bytecode analysis) | None |
| `--output <dir>` | Output directory for reports | `reports/` |
| `--name <name>` | Custom project name for report title | Auto-detect |
| `--framework <name>` | Manually specify test framework (e.g. `playwright`, `testng`) | Auto-detect |
| `--test-only` | Treat **all files** as test code (for automation repos) | Off |
| `--hotspots-only` | Only analyze files with Git activity (faster for large repos) | Off |
| `--min-churn <n>` | Minimum commits to include a file (use with `--hotspots-only`) | 1 |
| `--keep-clone` | Keep cloned repo after analysis (for remote URLs) | Off |

---

### Usage Examples

#### 1. Analyze a Local Repository

Analyze the code-panopticon repo itself:

```bash
./gradlew run --args="--repo ." --console=plain
```

**Expected output:**
- `reports/panopticon-report.html` — Interactive HTML report
- `reports/panopticon-report.csv` — CSV export
- `reports/panopticon-data.json` — Raw JSON data

---

#### 2. Analyze a Remote GitHub Repository

Analyze any public GitHub repo (auto-clones to temp directory):

```bash
./gradlew run --args="--repo https://github.com/praveens9/code-panopticon" --console=plain
```

The repo is cloned to a temp folder and deleted after analysis.

---

#### 3. Hotspots-Only Mode (Large Repos)

For large codebases, analyze only files with Git activity:

```bash
./gradlew run --args="--repo . --hotspots-only" --console=plain
```

Combine with `--min-churn` to filter by minimum commits:

```bash
./gradlew run --args="--repo . --hotspots-only --min-churn 5" --console=plain
```

This skips files with fewer than 5 commits—useful for focusing on actively maintained code.

---

#### 4. Java Bytecode Analysis

For deeper Java analysis (cohesion, coupling), provide compiled classes:

```bash
./gradlew run --args="--repo . --classes ./app/build/classes/java/main" --console=plain
```

---

#### 5. Keep Cloned Repository

When analyzing remote repos, keep the clone for inspection:

```bash
./gradlew run --args="--repo https://github.com/praveens9/code-panopticon --keep-clone" --console=plain
```

The repo path is printed at the end for reference.

---

#### 6. Custom Output Directory

Save reports to a specific folder:

```bash
./gradlew run --args="--repo . --output ./my-reports" --console=plain
```

---

#### 7. Custom Project Name

Override the auto-detected project name in the report header:

```bash
./gradlew run --args="--repo . --name 'My Awesome Project'" --console=plain
```

This displays **"My Awesome Project: Risk Analysis"** as the report title.

---

#### 8. Analyze Test Automation Repo

If analyzing a **test-only** repository (e.g., Playwright or Cypress framework), normal analysis might misclassify helper files as production code. Use `--test-only` to treat **everything** as test code:

```bash
./gradlew run --args="--repo . --test-only" --console=plain
```

This applies test-specific metrics (like Assertion count, Test Smells) to all files.

#### 9. Override Test Framework

If the auto-detection gets it wrong (or for custom setups), force a specific framework:

```bash
./gradlew run --args="--repo . --framework playwright" --console=plain
```


---

## � Test Health Metrics

Code Panopticon treats **tests as code too**. When viewing test files, the UI shows test-specific metrics.

| Metric | Description |
|--------|-------------|
| **Framework Detection** | Auto-detects JUnit, TestNG, Playwright, Cypress, WebdriverIO, Jest, etc. |
| **Assertion Count** | Number of assertions in a test file |
| **Hardcoded Waits** | Detects `Thread.sleep`, `cy.wait`, `setTimeout` (flakiness risk) |
| **Selector Quality** | Detects brittle XPath/CSS usage in E2E tests |

### Test Smells Detected

The UI includes dedicated test-specific diagnostics:

| Smell | What It Means | Action |
|-------|---------------|--------|
| **Assertion Roulette** | Too many assertions (>20) in one test | Split into focused tests |
| **Eager Test** | Test is doing too much | One behavior per test |
| **Mystery Guest** | Relies on external data/resources | Inline test data |
| **Hardcoded Waits** | Uses `sleep()`, `cy.wait()`, etc. | Use explicit waits |
| **Brittle Selectors** | Uses fragile XPath/CSS | Use data-testid |

---

## �🤖 AI Agent Integration (MCP)

Code Panopticon includes a **Model Context Protocol (MCP)** server, identifying it as a "Smart Tool" for AI agents (like Claude Desktop).

### Capabilities
- **`analyze_codebase`**: AI triggers the scan, getting immediate access to the full forensic report (JSON) and providing you the HTML link.
- **`get_risk_summary`**: AI identifies "Burning Platforms" and architectural decay instantly.
- **`get_file_insights`**: AI retrieves deep metrics for specific files on demand.

### Quick Setup

1. Run the setup script:
   ```bash
   ./setup-mcp.sh
   ```
2. Copy the generated config into your `claude_desktop_config.json`.
3. Restart Claude Desktop.

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

### Current (v3.0 — "Active Advisor") ✅
- ✅ Bubble Chart visualization
- ✅ System Map (circle-packing)
- ✅ Network Graph (temporal coupling)
- ✅ Configurable verdicts via YAML
- ✅ Polyglot analysis (Java, Python, JS)
- ✅ **Social Forensics Panel** — Author distribution, bus factor, knowledge islands
- ✅ **Testability X-Ray** — Test coverage correlation, seam identification
- ✅ **Refactoring Workflows** — LCOM4 clusters as named responsibilities

### Future Enhancements
- 🔮 Multi-language IDE plugin support
- 🔮 Real-time analysis during development
- 🔮 AI-powered refactoring suggestions

---

## 🏷️ Tags

#static-analysis #code-quality #git-forensics #polyglot #architecture #technical-debt

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
