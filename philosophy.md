# The Philosophy of Code Panopticon

Code Panopticon is not a linter. It does not care about missing semicolons or indentation style. It is a **Code Forensic** tool designed to identify the "Hotspots" in your codebase—the specific files where high maintenance effort meets poor code quality.

## The Core Belief
Software analysis often focuses on static snapshots (how the code looks *now*). Code Panopticon adds the dimension of **Time** (Evolution).

> **Complexity is only a problem if we have to work with it.**

A complex algorithm that was written 5 years ago and never touched again is **not** a problem. It is stable.
A complex file that is modified every week by 5 different developers is a **massive risk**. This is a Hotspot.

---

## The Metrics

Code Panopticon combines structural analysis (code) with evolutionary analysis (git history) to generate its insights.

### 1. Churn (The Heartbeat of Change)
**What is it?**
The number of times a file has been modified (committed) in the repository's history.

**Why it matters?**
High churn is the single best predictor of bugs. If a file is touched frequently, it indicates:
-   Unstable requirements.
-   The code is a "magnet" for changes (God Class).
-   It is fragile and requires constant fixing.

**The Fact:**
Files with high churn are where you spend your money. Refactoring them yields the highest Return on Investment (ROI).

---

### 2. Cyclomatic Complexity (CC)
**What is it?**
A quantitative measure of the number of linearly independent paths through a program's source code. It counts loops, conditionals (`if`, `else`, `switch`), and decision points.
*For Python/Indented languages, we use Indentation Complexity as a proxy.*

**Why it matters?**
High complexity means the code is hard to reason about, hard to test (you need extensive coverage for all branches), and hard to modify without unintended side effects.

**Interpretation:**
-   **1-10**: Simple, readable.
-   **11-20**: Moderate risk.
-   **21-50**: Complex. Hard to test.
-   **>50**: Untestable. A bug factory.

---

### 3. Temporal Coupling (Hidden Dependencies)
**What is it?**
When two or more files change together in the same commit, repeatedly.

**Why it matters?**
Static analysis tools can't see this. If `Server.java` and `Client.js` always change together, they are logically coupled, even if they share no code variables. This often reveals:
-   Leaky abstractions (implementation details spilling over API boundaries).
-   Copy-pasted logic across languages.
-   Architecture that doesn't support the domain needs.

---

### 4. LCOM4 (Lack of Cohesion of Methods)
**What is it?**
Measures how well the methods of a class belong together. It analyzes which methods use which fields (variables).

**How to read it:**
-   **LCOM4 = 1**: The class is cohesive. All parts act as a single unit.
-   **LCOM4 > 1**: The class is actually multiple different classes glued together.
    -   *Example:* If a class has `connectDB()` and `renderUI()` methods that use completely different sets of variables, LCOM4 will be 2.

**The Fix:**
Identify the subgroups (components) within the class and split them into separate classes.

---

### 5. Architectural Metrics (Coupling & Stability)
Often used to measure the "modularity" of your code.

-   **Fan-Out (Efferent Coupling)**: The number of other classes this class depends on (imports/uses). High fan-out means the class knows too much and is fragile to changes in dependencies.
-   **Afferent Coupling**: The number of *other* classes that depend on *this* class. High afferent coupling means the class is critical/stable (changing it breaks many things).
-   **Instability**: A ratio from 0 to 1.
    -   `0`: Stable (Hard to change, many depend on it).
    -   `1`: Unstable (Easy to change, depends on many, no one depends on it).
    -   *Good architecture mixes stable core components with unstable (flexible) outer layers.*

---

### 6. Risk Score calculation
**What is it?**
The final verdict. It allows you to prioritize technical debt.

**Formula:**
`Risk = Complexity × Churn`

We don't just show you complex code. We show you complex code that you are *actively working on*.

---

## The Verdicts

When analyzing files, Code Panopticon assigns a verdict label:

| Verdict | Meaning | Action |
| :--- | :--- | :--- |
| **OK** | Healthy code. Low risk. | Keep it up. |
| **BLOATED** | Large file with many lines of code, but not necessarily complex. | Consider splitting if it grows. |
| **COMPLEX** | High structural complexity (many loops/ifs). | Write more unit tests. Simplify logic. |
| **TOTAL_MESS** | **Burning Platform.** High Complexity + High Churn. | **Refactor Immediately.** This is where bugs live. |
| **GOD_CLASS** | Does too much (High LCOM4) and changes often. | Decompose into smaller responsibilities. |
| **BRAIN_METHOD** | A single method contains most of the class's logic. | Extract Method refactoring. |
| **SPLIT_CANDIDATE**| Low cohesion (High LCOM4). The class is naturally separable. | Split the class based on variable usage. |

---

## Using This Tool
1.  **Run logic**: `code-panopticon --repo <path>`
2.  **Look at the Treemap**: The size is Lines of Code. The color is Risk.
3.  **Find the Big Red Blocks**: These are your priorities.
4.  **Ignore the Small Green Blocks**: They are fine. Do not waste time optimizing code that works and isn't changing.
