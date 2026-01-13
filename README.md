# Code Panopticon

A holistic Java code forensic tool that identifies architectural decay by fusing **Evolutionary History (Git)** with **Structural Semantics (Bytecode Analysis)**.

---

## 🚀 Purpose

In large-scale Java projects, standard linters often fail to capture the "Context of Risk." This tool categorizes code based on its **Volatility** (how often it changes) and its **Internal Complexity** (how hard it is to maintain).

The goal is to identify **"Burning Platforms"**—highly active classes that are structurally unsound—so teams can prioritize refactoring where it matters most.

---

## 📊 The Metrics (The Legend)

The tool generates a report with the following forensic dimensions:

| Metric | Category | Description |
| :--- | :--- | :--- |
| **Churn** | Evolutionary | Number of Git commits touching the file. High churn indicates a "Hotspot". |
| **Peers** | Evolutionary | **Temporal Coupling.** Number of other files that change *with* this file. High peers = Shotgun Surgery. |
| **LCOM4** | Structural | **Cohesion.** Number of disjoint logic clusters. 1 = Cohesive. >1 = Fragmented. |
| **Total CC** | Structural | **Class Mass.** Total logic branches in the class. Measures the "Testing Tax". |
| **Max CC** | Structural | **Toxicity.** The complexity of the *single worst method* in the class. Identifies "Brain Methods". |
| **FanOut** | Structural | **Coupling.** Number of unique domain types referenced. Measures fragility. |

---

## 📋 Verdict Definitions

We use a **Forensic Rule Engine** to categorize risk based on the combination of these metrics.

| Verdict | Meaning | Action |
| :--- | :--- | :--- |
| **OK** | Metrics are within healthy thresholds. | None. |
| **BRAIN_METHOD** | **Max CC > 15** + High Code Mass. The class contains a massive, dense algorithm. | **Extract Method** or Strategy Pattern. |
| **SHOTGUN_SURGERY** | **Peers > 10**. Changing this file requires editing >10 other files simultaneously. | Identify copy-pasted logic or shared config leakage. |
| **HIDDEN_DEPENDENCY** | **Peers > 3** + Low Fan-Out. The file is coupled to things it doesn't import (e.g., config, tests). | Check for implicit dependencies. |
| **FRAGILE_HUB** | **FanOut > 30** + High Churn. A central coordinator that changes frequently. | Apply Interface Segregation. |
| **SPLIT_CANDIDATE** | **LCOM4 > 1**. The class contains multiple disconnected clusters of logic. | Split into two classes. |
| **TOTAL_MESS** | **LCOM4 > 4**. The class is a "Bag of Functions". | **High Priority Refactor.** |
| **COMPLEX (Low Risk)** | **Max CC > 15** (Low Mass). High complexity due to boilerplate (e.g., String Switch). | Low priority. |
| **GOD_CLASS** | High Complexity + High Size + Low Cohesion. | **Immediate Refactor.** |

---

## 🎨 Visual Dashboard & "Virtual Detective"

The tool generates a **`panopticon-report.html`** file featuring a **Bubble Chart**:

1.  **Risk Quadrants:** Visualizing **Churn** (X-Axis) vs. **Complexity** (Y-Axis).
    -   **Bubble Size:** Class Size (Method Count)
    -   **Color:** Cohesion (🔴 Red = Low Cohesion, 🟢 Green = High Cohesion)
2.  **Virtual Detective:** Click any bubble to open a **Forensic Profile**. The tool acts as an automated consultant, explaining *why* a file is risky (e.g., "This is a Stable Coordinator" vs "This is a Fragile Hub") and recommending specific fixes.

It also generates **`panopticon-report.csv`** for spreadsheet analysis.

---

## 🏃 How to Run

1.  Ensure the target repository is **compiled** (e.g., `./gradlew compileJava`).
2.  Run the full scan by providing the path to the **Git Repository** and the **Compiled Classes**:

```bash
./gradlew run --args="<path_to_git_repo> <path_to_compiled_classes>" --console=plain
```

---

#static-analysis #sootup #git-forensics #java #clean-code #architecture
