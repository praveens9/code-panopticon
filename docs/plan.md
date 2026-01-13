# Code Panopticon: Language-Agnostic Refactoring Plan

## Executive Summary

Transform the current Java-specific MVP into a **polyglot code forensic analyzer** that can analyze any repository (Python, TypeScript, Go, Rust, etc.) with:
- Language-agnostic Git-based evolutionary metrics
- Pluggable static analysis backends  
- Highly configurable forensic rule engine
- Intelligent repository scanning for large/small repos

---

## Current State Analysis

### What Exists

```
┌─────────────────────────────────────────────────────────────────┐
│                      Current Architecture                        │
├─────────────────────────────────────────────────────────────────┤
│  GitMiner.java        → Git CLI wrapper (language-agnostic ✓)   │
│  BytecodeAnalyzer.java → SootUp Java bytecode (Java-only ✗)     │
│  LCOM4Analyzer.java   → Cohesion analysis (Java-only ✗)         │
│  MetricCalculator.java → Metric interface (reusable concept ✓)  │
│  App.java             → Main orchestrator (tightly coupled ✗)   │
│  HtmlReporter.java    → Rich visualization (reusable ✓)         │
└─────────────────────────────────────────────────────────────────┘
```

### Core Strengths to Preserve
1. **Evolutionary Metrics** - Git-based churn/coupling analysis is already language-agnostic
2. **Forensic Rule Engine** - Verdict determination logic (BRAIN_METHOD, SHOTGUN_SURGERY, etc.)
3. **Interactive Reports** - HTML dashboard with quadrant view, treemap, network graph
4. **Risk Score Formula** - `(Churn × TotalCC × LCOM4) / 100`

### Key Limitations
1. **SootUp Dependency** - Requires compiled `.class` files, Java-only
2. **Hardcoded Thresholds** - Scattered throughout `BytecodeAnalyzer.java` 
3. **No Text-Based Analysis** - Cannot analyze source files directly
4. **Single Repository Mode** - No batch/incremental scanning

---

## Proposed Architecture

### Design Principles
1. **Git as Universal Truth** - Evolutionary metrics work everywhere
2. **Pluggable Analyzers** - Each language gets its own lightweight analyzer
3. **Text-First Analysis** - Fallback to AST-parsing or even regex for unsupported langs
4. **Configuration Over Code** - All thresholds externalized to YAML/JSON
5. **Progressive Enhancement** - Basic metrics always work; advanced metrics optional

### Component Diagram

```
                         ┌───────────────────────────┐
                         │       CLI Interface       │
                         │  (panopticon analyze)     │
                         └──────────┬────────────────┘
                                    │
                         ┌──────────▼────────────────┐
                         │   Analysis Orchestrator   │
                         │  (handles large repos)    │
                         └──────────┬────────────────┘
                                    │
            ┌───────────────────────┼───────────────────────┐
            │                       │                       │
   ┌────────▼───────┐    ┌──────────▼──────────┐    ┌───────▼───────┐
   │  Git Miner     │    │  Language Detector  │    │   Config      │
   │  (unchanged)   │    │  (new)              │    │   Loader      │
   └────────┬───────┘    └──────────┬──────────┘    └───────┬───────┘
            │                       │                       │
            │            ┌──────────▼──────────┐            │
            │            │  Analyzer Registry  │◄───────────┘
            │            │  (plugin system)    │
            │            └──────────┬──────────┘
            │                       │
            │       ┌───────────────┼───────────────┐
            │       │               │               │
            │  ┌────▼────┐    ┌─────▼─────┐   ┌─────▼─────┐
            │  │  Java   │    │  Python   │   │  Generic  │
            │  │ Analyzer│    │ Analyzer  │   │ Analyzer  │
            │  └─────────┘    └───────────┘   └───────────┘
            │
   ┌────────▼────────────────────────────────────────────┐
   │                   Data Fusion Layer                  │
   │   (merges Git metrics + Structural metrics)          │
   └────────────────────────┬────────────────────────────┘
                            │
                 ┌──────────▼──────────┐
                 │  Forensic Verdicts  │
                 │  (configurable)     │
                 └──────────┬──────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
       ┌────▼────┐    ┌─────▼─────┐   ┌─────▼─────┐
       │  HTML   │    │   JSON    │   │    CSV    │
       │ Report  │    │  Report   │   │  Report   │
       └─────────┘    └───────────┘   └───────────┘
```

---

## Phase 1: Core Refactoring (Lightweight Foundation)

### 1.1 Externalize Configuration

Create `panopticon.yaml` for all thresholds:

```yaml
# panopticon.yaml
version: 1

# Git Analysis Settings
git:
  recent_churn_days: 90
  min_shared_commits: 5
  coupling_threshold_percent: 30
  exclude_patterns:
    - "**/test/**"
    - "**/*Test.*"
    - "**/node_modules/**"
    - "**/vendor/**"

# Structural Thresholds (per language or global)
thresholds:
  defaults:
    max_cc: 15
    max_lcom4: 4
    max_fan_out: 30
    max_loc: 500
    max_methods: 20
    brain_method_cc: 15
    brain_method_instructions: 50
    
  # Override for specific languages
  python:
    max_cc: 10  # Python tends to be more readable
    max_loc: 300
    
  javascript:
    max_fan_out: 50  # JS modules often have many imports

# Forensic Rule Engine Configuration
verdicts:
  SHOTGUN_SURGERY:
    condition: "peers > 10"
    priority: 1
    
  HIDDEN_DEPENDENCY:
    condition: "peers > 3 AND fanOut < 5 AND verdict == 'OK'"
    priority: 2
    
  BRAIN_METHOD:
    condition: "maxCC > ${thresholds.max_cc} AND instructions > ${thresholds.brain_method_instructions}"
    priority: 3
    
  SPLIT_CANDIDATE:
    condition: "substantialComponents > 1"
    priority: 4
    
  TOTAL_MESS:
    condition: "lcom4 > ${thresholds.max_lcom4}"
    priority: 5
    
  GOD_CLASS:
    condition: "totalCC > 200 OR (methods > 50 AND cohesion < 0.5)"
    priority: 6
    
  FRAGILE_HUB:
    condition: "fanOut > ${thresholds.max_fan_out} AND churn > 10"
    priority: 7

# Output Settings
output:
  formats: [html, csv, json]
  directory: "./panopticon-output"
```

### 1.2 Create Analyzer Plugin Interface

```java
// LanguageAnalyzer.java (NEW)
public interface LanguageAnalyzer {
    
    /** Unique language identifier (e.g., "java", "python", "go") */
    String getLanguageId();
    
    /** File extensions this analyzer handles */
    Set<String> getSupportedExtensions();
    
    /** Whether this analyzer can run (dependencies available) */
    boolean isAvailable();
    
    /** Analyze a single file and return metrics */
    Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config);
    
    /** Batch analyze multiple files (for efficiency) */
    default List<FileMetrics> analyzeBatch(List<Path> files, AnalyzerConfig config) {
        return files.stream()
            .map(f -> analyze(f, config))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
}

// FileMetrics.java (NEW - language-agnostic)
public record FileMetrics(
    String filePath,
    String language,
    int loc,
    int functionCount,
    double totalComplexity,
    double maxComplexity,
    double cohesion,           // If calculable
    int fanOut,               // Import/dependency count
    List<String> complexFunctions,  // Brain method candidates
    Map<String, Object> extra       // Language-specific metrics
) {}
```

### 1.3 Implement Generic Text Analyzer (Fallback)

A regex/heuristic-based analyzer providing minimal metrics for any file:

```java
// GenericTextAnalyzer.java
public class GenericTextAnalyzer implements LanguageAnalyzer {
    
    @Override
    public String getLanguageId() { return "generic"; }
    
    @Override
    public Set<String> getSupportedExtensions() { return Set.of("*"); }
    
    @Override
    public boolean isAvailable() { return true; }  // Always available
    
    @Override
    public Optional<FileMetrics> analyze(Path file, AnalyzerConfig config) {
        List<String> lines = Files.readAllLines(file);
        
        int loc = countNonBlankLines(lines);
        int functionCount = countFunctionPatterns(lines);  // Heuristic
        double complexity = estimateComplexity(lines);     // Branching keywords
        int fanOut = countImportPatterns(lines);           // Heuristic
        
        return Optional.of(new FileMetrics(
            file.toString(), "generic", loc, functionCount,
            complexity, complexity, 0.5, fanOut,
            List.of(), Map.of()
        ));
    }
    
    // Simplified heuristics
    private int countFunctionPatterns(List<String> lines) {
        // Match: "def ", "function ", "fn ", "func ", "fun ", etc.
        Pattern funcPattern = Pattern.compile("^\\s*(def|function|fn|func|fun|pub fn|async def)\\s+\\w+");
        return (int) lines.stream().filter(l -> funcPattern.matcher(l).find()).count();
    }
    
    private double estimateComplexity(List<String> lines) {
        // Count branching keywords
        Pattern branchPattern = Pattern.compile("\\b(if|else|elif|for|while|switch|case|catch|&&|\\|\\|)\\b");
        return lines.stream().mapToInt(l -> {
            Matcher m = branchPattern.matcher(l);
            int count = 0; while (m.find()) count++;
            return count;
        }).sum();
    }
}
```

---

## Phase 2: Language-Specific Analyzers

### 2.1 Python Analyzer (Tree-sitter or AST)

Use Python's `ast` module via subprocess or integrate Tree-sitter-java:

```java
public class PythonAnalyzer implements LanguageAnalyzer {
    
    @Override
    public String getLanguageId() { return "python"; }
    
    @Override
    public Set<String> getSupportedExtensions() { return Set.of(".py"); }
    
    @Override
    public boolean isAvailable() {
        return isPythonInstalled();  // Check `python3 --version`
    }
    
    @Override
    public Optional<FileMetrics> analyze(Path file, AnalyzerConfig config) {
        // Call external Python script: panopticon_python_analyzer.py
        String json = executeCommand("python3", 
            "analyzers/python_analyzer.py", 
            file.toString());
        return parseJsonToFileMetrics(json);
    }
}
```

External Python script (`analyzers/python_analyzer.py`):

```python
#!/usr/bin/env python3
import ast
import sys
import json
from collections import defaultdict

class ComplexityVisitor(ast.NodeVisitor):
    def __init__(self):
        self.complexity = 1  # Base complexity
        
    def visit_If(self, node): self.complexity += 1; self.generic_visit(node)
    def visit_For(self, node): self.complexity += 1; self.generic_visit(node)
    def visit_While(self, node): self.complexity += 1; self.generic_visit(node)
    def visit_ExceptHandler(self, node): self.complexity += 1; self.generic_visit(node)
    def visit_BoolOp(self, node): self.complexity += len(node.values) - 1; self.generic_visit(node)

def analyze_file(path):
    with open(path) as f:
        tree = ast.parse(f.read())
    
    functions = [n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))]
    classes = [n for n in ast.walk(tree) if isinstance(n, ast.ClassDef)]
    imports = [n for n in ast.walk(tree) if isinstance(n, (ast.Import, ast.ImportFrom))]
    
    complexities = []
    for func in functions:
        visitor = ComplexityVisitor()
        visitor.visit(func)
        complexities.append({"name": func.name, "cc": visitor.complexity})
    
    return {
        "filePath": path,
        "language": "python",
        "loc": sum(1 for _ in open(path)),
        "functionCount": len(functions),
        "totalComplexity": sum(c["cc"] for c in complexities),
        "maxComplexity": max((c["cc"] for c in complexities), default=0),
        "fanOut": len(imports),
        "complexFunctions": [c["name"] for c in complexities if c["cc"] > 10]
    }

if __name__ == "__main__":
    print(json.dumps(analyze_file(sys.argv[1])))
```

### 2.2 JavaScript/TypeScript Analyzer

Use `eslint` with complexity rules or Tree-sitter:

```java
public class JavaScriptAnalyzer implements LanguageAnalyzer {
    
    private static final Set<String> EXTENSIONS = Set.of(".js", ".jsx", ".ts", ".tsx");
    
    @Override
    public Optional<FileMetrics> analyze(Path file, AnalyzerConfig config) {
        // Use eslint with complexity rules
        String json = executeCommand("npx", "eslint", 
            "--format=json", 
            "--rule", "complexity: [error, {max: 1}]",
            file.toString());
        return parseEslintOutput(json);
    }
}
```

### 2.3 Go Analyzer

Use `gocyclo` and `go vet`:

```java
public class GoAnalyzer implements LanguageAnalyzer {
    
    @Override
    public Optional<FileMetrics> analyze(Path file, AnalyzerConfig config) {
        String complexity = executeCommand("gocyclo", "-avg", file.toString());
        String imports = executeCommand("go", "list", "-f", "{{.Imports}}", file.getParent().toString());
        // Parse and return
    }
}
```

### 2.4 Keep Java Analyzer (SootUp)

Wrap existing functionality into the new interface:

```java
public class JavaBytecodeAnalyzer implements LanguageAnalyzer {
    
    private final Path compiledClassesPath;  // Requires compiled classes
    
    @Override
    public boolean isAvailable() {
        return compiledClassesPath != null && Files.exists(compiledClassesPath);
    }
    
    @Override
    public Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config) {
        // Convert source path to class name
        String className = convertPathToClass(sourceFile);
        // Reuse existing BytecodeAnalyzer logic
        return existingAnalyzer.analyze(className).map(this::toFileMetrics);
    }
}
```

---

## Phase 3: Intelligent Repository Scanning

### 3.1 Large Repo Handling

```java
public class RepositoryScanner {
    
    private final Config config;
    private final int BATCH_SIZE = 100;
    
    public ScanResult scan(Path repoRoot) {
        // 1. Estimate repo size
        long fileCount = countSourceFiles(repoRoot);
        
        if (fileCount > 10000) {
            log.info("Large repo detected ({} files). Using hotspot-first strategy.", fileCount);
            return scanHotspotFirst(repoRoot);
        } else {
            return scanAll(repoRoot);
        }
    }
    
    /** For large repos: Only analyze files with Git churn */
    private ScanResult scanHotspotFirst(Path repoRoot) {
        // 1. Get Git churn data first
        GitAnalysisResult gitData = gitMiner.scanHistory(repoRoot);
        Set<String> hotspots = gitData.churnMap().keySet()
            .stream()
            .filter(f -> gitData.churnMap().get(f) >= config.minChurnForAnalysis())
            .collect(Collectors.toSet());
        
        log.info("Focusing on {} hotspot files", hotspots.size());
        
        // 2. Only analyze hotspots
        return analyzeFiles(hotspots.stream()
            .map(f -> repoRoot.resolve(f))
            .filter(Files::exists)
            .toList());
    }
    
    /** Stream processing in batches */
    private ScanResult analyzeFiles(List<Path> files) {
        return files.stream()
            .collect(Collectors.groupingBy(this::detectLanguage))
            .entrySet()
            .parallelStream()
            .flatMap(entry -> {
                LanguageAnalyzer analyzer = registry.getAnalyzer(entry.getKey());
                return entry.getValue().stream()
                    .collect(batching(BATCH_SIZE))
                    .flatMap(batch -> analyzer.analyzeBatch(batch).stream());
            })
            .collect(toScanResult());
    }
}
```

### 3.2 Incremental Analysis

```java
public class IncrementalScanner {
    
    private static final String CACHE_FILE = ".panopticon-cache.json";
    
    public ScanResult scanIncremental(Path repoRoot) {
        CacheData cache = loadCache(repoRoot);
        
        // Get files changed since last scan
        String lastCommit = cache.lastCommitHash();
        List<String> changedFiles = getChangedFiles(repoRoot, lastCommit);
        
        if (changedFiles.isEmpty()) {
            log.info("No changes since last scan");
            return cache.toScanResult();
        }
        
        // Re-analyze only changed files
        ScanResult delta = analyzeFiles(changedFiles);
        
        // Merge with cache
        return cache.merge(delta);
    }
}
```

---

## Phase 4: Enhanced Forensic Rule Engine

### 4.1 Expression-Based Rules

Replace hardcoded if-else chains with a rule DSL:

```java
public class ForensicRuleEngine {
    
    private final List<VerdictRule> rules;
    
    public String evaluateVerdict(FileMetrics metrics, GitMetrics gitMetrics) {
        Map<String, Object> context = buildContext(metrics, gitMetrics);
        
        return rules.stream()
            .sorted(Comparator.comparing(VerdictRule::priority))
            .filter(rule -> rule.evaluate(context))
            .findFirst()
            .map(VerdictRule::verdict)
            .orElse("OK");
    }
    
    private Map<String, Object> buildContext(FileMetrics m, GitMetrics g) {
        return Map.of(
            "churn", g.churn(),
            "recentChurn", g.recentChurn(),
            "peers", g.coupledPeers(),
            "totalCC", m.totalComplexity(),
            "maxCC", m.maxComplexity(),
            "lcom4", m.cohesion(),
            "fanOut", m.fanOut(),
            "loc", m.loc(),
            "methods", m.functionCount()
        );
    }
}

// Rule parser using Spring Expression Language (SpEL) or similar
public class VerdictRule {
    private final String verdict;
    private final String condition;  // e.g., "peers > 10"
    private final int priority;
    
    public boolean evaluate(Map<String, Object> context) {
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(condition);
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        context.forEach(ctx::setVariable);
        return Boolean.TRUE.equals(exp.getValue(ctx, Boolean.class));
    }
}
```

### 4.2 Custom User Rules

Allow users to add project-specific rules:

```yaml
# In panopticon.yaml
verdicts:
  # Built-in rules...
  
  custom:
    LEGACY_CODE:
      condition: "recentChurn == 0 AND churn > 50"
      action: "Consider if this code is still needed"
      
    MICROSERVICE_COUPLING:
      condition: "fanOut > 20 AND path.contains('service')"
      action: "This service may have too many dependencies"
```

---

## Phase 5: Output & Reporting

### 5.1 Structured JSON Output

For CI/CD integration:

```json
{
  "meta": {
    "repository": "/path/to/repo",
    "analyzedAt": "2025-01-13T12:00:00Z",
    "filesAnalyzed": 150,
    "languages": ["java", "python", "javascript"]
  },
  "summary": {
    "totalRiskScore": 4523.5,
    "verdictCounts": {
      "OK": 120,
      "BRAIN_METHOD": 15,
      "SHOTGUN_SURGERY": 5,
      "TOTAL_MESS": 3
    },
    "topRisks": [...]
  },
  "files": [...],
  "couplingGraph": {...}
}
```

### 5.2 Exit Codes for CI

```java
public class ExitCodes {
    public static final int SUCCESS = 0;
    public static final int HAS_WARNINGS = 1;     // Found verdicts but within threshold
    public static final int THRESHOLD_EXCEEDED = 2;  // Failed quality gate
    public static final int ERROR = 3;
}
```

### 5.3 Quality Gate Configuration

```yaml
quality_gate:
  fail_on:
    - verdict: GOD_CLASS
      max_count: 0
    - verdict: TOTAL_MESS
      max_count: 2
    - risk_score_above: 500
```

---

## Implementation Priority

### MVP (1-2 weeks)
1. ✅ Externalize configuration to `panopticon.yaml`
2. ✅ Create `LanguageAnalyzer` interface
3. ✅ Implement `GenericTextAnalyzer` (works on any file)
4. ✅ Refactor `App.java` to use plugin registry
5. ✅ Add Python analyzer script

### V1.0 (2-4 weeks)
6. Implement JavaScript/TypeScript analyzer
7. Add Go analyzer
8. Implement intelligent large-repo scanning
9. Expression-based rule engine
10. JSON output for CI/CD

### V1.5 (Future)
11. Incremental scanning with cache
12. Language Server Protocol (LSP) integration
13. VS Code extension
14. GitHub Action

---

## File Changes Summary

| File | Action | Description |
|------|--------|-------------|
| `panopticon.yaml` | NEW | Externalized configuration |
| `LanguageAnalyzer.java` | NEW | Plugin interface |
| `FileMetrics.java` | NEW | Language-agnostic metrics record |
| `AnalyzerRegistry.java` | NEW | Plugin discovery and loading |
| `GenericTextAnalyzer.java` | NEW | Fallback text-based analyzer |
| `PythonAnalyzer.java` | NEW | Python-specific analyzer |
| `JavaBytecodeAnalyzer.java` | NEW | Wraps existing SootUp logic |
| `RepositoryScanner.java` | NEW | Large repo handling |
| `ForensicRuleEngine.java` | NEW | Configurable verdict evaluation |
| `App.java` | MODIFY | Use new plugin architecture |
| `BytecodeAnalyzer.java` | MODIFY | Extract shared logic |
| `GitMiner.java` | MODIFY | Minor: add exclude patterns |
| `HtmlReporter.java` | KEEP | Visualization works as-is |
| `analyzers/python_analyzer.py` | NEW | External Python script |
| `analyzers/js_analyzer.js` | NEW | External JS script (optional) |

---

## Non-Goals (Keep It Lightweight)

❌ **No GUI application** - CLI-only  
❌ **No database** - File-based caching only  
❌ **No complex plugin system** - Simple interface + classpath scanning  
❌ **No custom DSL parser** - Use existing expression libraries  
❌ **No microservices detection** - Out of scope for now  

---

## Verification Plan

### Automated Tests
1. **Unit tests for GenericTextAnalyzer** - Run on sample files
2. **Integration test** - Run on this repository (`code-panopticon`) itself
3. **Python analyzer test** - Run on a sample Python project

### Manual Verification
1. Run on a real Python project (e.g., Flask, Django app)
2. Run on a JavaScript/Node.js project
3. Run on a large monorepo (>1000 files) to test performance
4. Verify HTML report works correctly with multi-language data

---

## Questions for User

Before proceeding to implementation:

1. **Technology preference** - Should we stay with Java or rewrite in a more portable language (Go, Rust, Python)?
   - Java: Keep existing SootUp integration, mature ecosystem
   - Go: Single binary distribution, fast, good CLI support
   - Python: Easier external analyzer integration, but slower

2. **Analyzer priority** - Which languages should we support first after the generic analyzer?
   - Python, JavaScript/TypeScript, Go, Rust, etc.

3. **Rule engine complexity** - Do you want a simple threshold-based system or full expression support (SpEL-like)?

4. **Distribution method** - JAR file, Docker image, native binary (GraalVM)?
