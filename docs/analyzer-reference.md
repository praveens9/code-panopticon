# Analyzer Implementation Reference

## Quick Reference: Metrics Per Language

| Metric | Java (SootUp) | Python (ast) | JS/TS (ESLint) | Go (gocyclo) | Generic (regex) |
|--------|---------------|--------------|----------------|--------------|-----------------|
| LOC | ✓ Bytecode | ✓ AST | ✓ ESLint | ✓ go list | ✓ Line count |
| Functions | ✓ Methods | ✓ FunctionDef | ✓ ESLint | ✓ go list | ~ Pattern match |
| Cyclomatic Complexity | ✓ Full | ✓ Full | ✓ Full | ✓ Full | ~ Branch keywords |
| LCOM4 (Cohesion) | ✓ Field refs | ~ Limited | ✗ N/A | ✗ N/A | ✗ N/A |
| Fan-Out | ✓ Type refs | ✓ Imports | ✓ Imports | ✓ Imports | ~ Import patterns |
| Afferent Coupling | ✓ Full | ✗ N/A | ✗ N/A | ✗ N/A | ✗ N/A |

Legend: ✓ Full support, ~ Partial/Heuristic, ✗ Not available

---

## Configuration Example: Project-Specific Rules

```yaml
# panopticon.yaml for a Django project

include_patterns:
  - "**/*.py"
  - "**/*.js"
  
exclude_patterns:
  - "**/migrations/**"
  - "**/tests/**"
  - "**/node_modules/**"
  - "manage.py"
  
thresholds:
  python:
    max_cc: 10
    max_loc: 200
    max_fan_out: 20
    
verdicts:
  custom:
    DJANGO_VIEW_BLOAT:
      condition: "path.contains('/views/') AND totalCC > 50"
      action: "Split this view into smaller class-based views"
      
    UNUSED_MIGRATION:
      condition: "churn == 0 AND path.contains('migrations')"
      action: "Squash old migrations"
```

---

## External Analyzer Scripts

### Python Analyzer (Full Version)

Located at: `analyzers/python_analyzer.py`

```python
#!/usr/bin/env python3
"""
Panopticon Python Analyzer
Usage: python3 python_analyzer.py <file_path>
Output: JSON metrics to stdout
"""
import ast
import sys
import json
from pathlib import Path

class ComplexityVisitor(ast.NodeVisitor):
    """Calculate Cyclomatic Complexity for a function/method."""
    
    def __init__(self):
        self.complexity = 1  # Base complexity
        
    def visit_If(self, node):
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_For(self, node):
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_While(self, node):
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_ExceptHandler(self, node):
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_With(self, node):
        # Context managers add implicit exception handling
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_Assert(self, node):
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_comprehension(self, node):
        # List/dict/set comprehensions with conditions
        if node.ifs:
            self.complexity += len(node.ifs)
        self.generic_visit(node)
        
    def visit_BoolOp(self, node):
        # and/or expressions add to complexity
        self.complexity += len(node.values) - 1
        self.generic_visit(node)
        
    def visit_IfExp(self, node):
        # Ternary expressions
        self.complexity += 1
        self.generic_visit(node)


class CohesionAnalyzer(ast.NodeVisitor):
    """Analyze method-field relationships for LCOM-like metric."""
    
    def __init__(self):
        self.current_method = None
        self.methods = set()
        self.instance_vars = set()
        self.method_vars = {}  # method -> set of accessed instance vars
        
    def visit_FunctionDef(self, node):
        self.current_method = node.name
        self.methods.add(node.name)
        self.method_vars[node.name] = set()
        self.generic_visit(node)
        self.current_method = None
        
    def visit_Attribute(self, node):
        if (self.current_method and 
            isinstance(node.value, ast.Name) and 
            node.value.id == 'self'):
            self.instance_vars.add(node.attr)
            self.method_vars[self.current_method].add(node.attr)
        self.generic_visit(node)
        
    def cohesion_score(self):
        """
        Calculate LCOM-like cohesion score.
        Returns 1.0 (fully cohesive) to 0.0 (no cohesion).
        """
        if not self.methods or not self.instance_vars:
            return 1.0  # No methods or no fields = trivially cohesive
            
        total_possible = len(self.methods) * len(self.instance_vars)
        actual_uses = sum(len(vars_) for vars_ in self.method_vars.values())
        
        return actual_uses / total_possible if total_possible > 0 else 1.0


def analyze_file(file_path: str) -> dict:
    """Analyze a Python file and return metrics."""
    path = Path(file_path)
    
    if not path.exists():
        return {"error": f"File not found: {file_path}"}
    
    source = path.read_text(encoding='utf-8', errors='ignore')
    
    try:
        tree = ast.parse(source)
    except SyntaxError as e:
        return {
            "filePath": file_path,
            "error": f"Syntax error: {e}",
            "language": "python"
        }
    
    # Collect functions and classes
    functions = []
    classes = []
    imports = []
    
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            functions.append(node)
        elif isinstance(node, ast.ClassDef):
            classes.append(node)
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            imports.append(node)
    
    # Calculate complexity for each function
    function_metrics = []
    for func in functions:
        visitor = ComplexityVisitor()
        visitor.visit(func)
        function_metrics.append({
            "name": func.name,
            "cc": visitor.complexity,
            "line": func.lineno
        })
    
    # Calculate cohesion for classes
    class_cohesion = []
    for cls in classes:
        analyzer = CohesionAnalyzer()
        analyzer.visit(cls)
        class_cohesion.append({
            "name": cls.name,
            "cohesion": analyzer.cohesion_score(),
            "methods": len(analyzer.methods),
            "fields": len(analyzer.instance_vars)
        })
    
    # Count imports (fan-out approximation)
    fan_out = 0
    for imp in imports:
        if isinstance(imp, ast.Import):
            fan_out += len(imp.names)
        else:
            fan_out += 1  # ImportFrom counts as 1
    
    # Line counts
    lines = source.splitlines()
    loc = len([l for l in lines if l.strip() and not l.strip().startswith('#')])
    
    total_cc = sum(f["cc"] for f in function_metrics)
    max_cc = max((f["cc"] for f in function_metrics), default=0)
    complex_funcs = [f["name"] for f in function_metrics if f["cc"] > 10]
    
    return {
        "filePath": file_path,
        "language": "python",
        "loc": loc,
        "functionCount": len(functions),
        "classCount": len(classes),
        "totalComplexity": total_cc,
        "maxComplexity": max_cc,
        "avgComplexity": total_cc / len(functions) if functions else 0,
        "fanOut": fan_out,
        "complexFunctions": complex_funcs,
        "functions": function_metrics,
        "classes": class_cohesion
    }


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: python_analyzer.py <file_path>"}))
        sys.exit(1)
        
    result = analyze_file(sys.argv[1])
    print(json.dumps(result, indent=2 if '--pretty' in sys.argv else None))


if __name__ == "__main__":
    main()
```

---

## CLI Usage Examples

```bash
# Basic scan (uses detected languages)
panopticon analyze /path/to/repo

# Specify output formats
panopticon analyze /path/to/repo --output html,json,csv

# With custom config
panopticon analyze /path/to/repo --config ./panopticon.yaml

# Large repo mode (only analyze hotspots)
panopticon analyze /path/to/repo --hotspots-only --min-churn 5

# Specific languages only
panopticon analyze /path/to/repo --languages python,javascript

# CI mode (fail on quality gate)
panopticon analyze /path/to/repo --ci --fail-on-verdict GOD_CLASS

# Incremental scan (fast)
panopticon analyze /path/to/repo --incremental

# Verbose logging
panopticon analyze /path/to/repo -v
```

---

## Testing Strategy

### Unit Tests (JUnit)

```java
@Test
void testGenericAnalyzer_countsFunctions() {
    Path testFile = createTempFile("""
        def foo():
            pass
            
        function bar() {}
        
        fn baz() {}
    """);
    
    GenericTextAnalyzer analyzer = new GenericTextAnalyzer();
    FileMetrics metrics = analyzer.analyze(testFile, config).get();
    
    assertEquals(3, metrics.functionCount());
}

@Test  
void testForensicRuleEngine_evaluatesCondition() {
    ForensicRuleEngine engine = new ForensicRuleEngine(List.of(
        new VerdictRule("TEST_VERDICT", "churn > 5 AND totalCC > 10", 1)
    ));
    
    Map<String, Object> context = Map.of("churn", 10, "totalCC", 20);
    
    assertEquals("TEST_VERDICT", engine.evaluate(context));
}
```

### Integration Tests

```bash
# Run on this repository
./gradlew run --args=". ./app/build/classes" --console=plain

# Verify output files exist
ls panopticon-output/
# Should contain: report.html, report.csv, report.json

# Run Python analyzer test
python3 analyzers/python_analyzer.py analyzers/python_analyzer.py
# Should output JSON with metrics for itself
```

### Manual Verification

1. Clone a sample Python project and run analyzer
2. Verify HTML report shows Python classes
3. Check that verdicts make sense for Python code
4. Test large repo performance (>1000 files)
