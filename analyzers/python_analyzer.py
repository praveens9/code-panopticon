#!/usr/bin/env python3
"""
Panopticon Python Analyzer
Analyzes Python files using the AST module.

Usage: python3 python_analyzer.py <file_path> [--pretty]
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
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_Assert(self, node):
        self.complexity += 1
        self.generic_visit(node)
        
    def visit_comprehension(self, node):
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
        self._handle_function(node)
        
    def visit_AsyncFunctionDef(self, node):
        self._handle_function(node)
        
    def _handle_function(self, node):
        old_method = self.current_method
        self.current_method = node.name
        self.methods.add(node.name)
        self.method_vars[node.name] = set()
        self.generic_visit(node)
        self.current_method = old_method
        
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
            return 1.0
            
        total_possible = len(self.methods) * len(self.instance_vars)
        actual_uses = sum(len(vars_) for vars_ in self.method_vars.values())
        
        return actual_uses / total_possible if total_possible > 0 else 1.0


def analyze_file(file_path: str) -> dict:
    """Analyze a Python file and return metrics."""
    path = Path(file_path)
    
    if not path.exists():
        return {"error": f"File not found: {file_path}"}
    
    try:
        source = path.read_text(encoding='utf-8', errors='ignore')
    except Exception as e:
        return {"error": f"Cannot read file: {e}"}
    
    try:
        tree = ast.parse(source)
    except SyntaxError as e:
        return {
            "filePath": file_path,
            "error": f"Syntax error: {e}",
            "language": "python",
            "loc": len(source.splitlines()),
            "functionCount": 0,
            "totalComplexity": 0,
            "maxComplexity": 0,
            "cohesion": 1.0,
            "fanOut": 0,
            "complexFunctions": []
        }
    
    # Collect functions and classes
    functions = []
    classes = []
    imports = []
    
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            # Skip nested functions in first pass
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
            "cohesion": round(analyzer.cohesion_score(), 3),
            "methods": len(analyzer.methods),
            "fields": len(analyzer.instance_vars)
        })
    
    # Count imports (fan-out approximation)
    fan_out = 0
    for imp in imports:
        if isinstance(imp, ast.Import):
            fan_out += len(imp.names)
        else:
            fan_out += 1
    
    # Line counts
    lines = source.splitlines()
    loc = len([l for l in lines if l.strip() and not l.strip().startswith('#')])
    
    total_cc = sum(f["cc"] for f in function_metrics)
    max_cc = max((f["cc"] for f in function_metrics), default=0)
    
    # Complex functions threshold (default 10 for Python)
    threshold = 10
    complex_funcs = [f["name"] for f in function_metrics if f["cc"] > threshold]
    
    # Average cohesion across classes
    avg_cohesion = (sum(c["cohesion"] for c in class_cohesion) / len(class_cohesion)) if class_cohesion else 1.0
    
    return {
        "filePath": file_path,
        "language": "python",
        "loc": loc,
        "functionCount": len(functions),
        "classCount": len(classes),
        "totalComplexity": total_cc,
        "maxComplexity": max_cc,
        "avgComplexity": round(total_cc / len(functions), 2) if functions else 0,
        "cohesion": round(avg_cohesion, 3),
        "fanOut": fan_out,
        "complexFunctions": complex_funcs,
        "functions": function_metrics,
        "classes": class_cohesion
    }


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: python_analyzer.py <file_path> [--pretty]"}))
        sys.exit(1)
        
    file_path = sys.argv[1]
    pretty = '--pretty' in sys.argv
    
    result = analyze_file(file_path)
    print(json.dumps(result, indent=2 if pretty else None))


if __name__ == "__main__":
    main()
