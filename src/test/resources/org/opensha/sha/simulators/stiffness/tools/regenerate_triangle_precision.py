"""Keep the exceptional high-precision equations synchronized with the double kernel.

Uses only the Python standard library. Geometry and routing are handwritten Java;
this script translates only AngDisStrainFSC's scalar algebra to decimal and two-double calls.
Run with --check to verify the committed equations without rewriting anything.
Whitespace is ignored so IDE indentation changes do not require regeneration.
"""
import argparse
import collections
import ast
from pathlib import Path
import re

root = next(p for p in Path(__file__).resolve().parents if (p / 'build.gradle').exists())
package = root / 'src/main/java/org/opensha/sha/simulators/stiffness'
source = (package / 'TriangularDislocationCalc.java').read_text()
start = source.index('private static Tensor AngDisStrainFSC(')
stop = source.index('private static Tensor AngSetupFSC_S(', start)
source = source[start:stop]
target = package / 'TriangularDislocationPrecision.java'
original = target.read_text()


def expression(node):
    if isinstance(node, ast.Name):
        return 'M_PI' if node.id == 'PI' else node.id
    if isinstance(node, ast.Constant):
        return f'number("{node.value}")'
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
        return f'negate({expression(node.operand)})'
    if isinstance(node, ast.BinOp):
        operation = {ast.Add: 'add', ast.Sub: 'subtract', ast.Mult: 'multiply', ast.Div: 'divide'}[type(node.op)]
        return f'{operation}({expression(node.left)}, {expression(node.right)})'
    if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
        return f'{node.func.id}({", ".join(expression(arg) for arg in node.args)})'
    raise ValueError(ast.dump(node))


def format_algebra(lines):
    """Tabs and fixed continuation indentation; only wrap at argument boundaries."""
    result = []
    for line in lines:
        leading = len(line) - len(line.lstrip(' '))
        indent = '\t' * (leading // 4)
        text = line.lstrip()
        prefix = indent
        while len(prefix.expandtabs(4)) + len(text) > 120:
            width = 120 - len(prefix.expandtabs(4))
            split = text.rfind(', ', 0, width)
            if split < 0:
                # A deeply nested call can have no earlier argument boundary.
                # Allow that prefix to exceed the target, then resume wrapping.
                split = text.find(', ')
                if split < 0:
                    break
            result.append(prefix + text[:split + 1])
            text = text[split + 2:]
            prefix = indent + '\t\t'
        result.append(prefix + text)
    return '\n'.join(result) + '\n'


lines = []
for name, value in re.findall(r'^\s+(\w+) = ([^;]+);', source, re.M):
    if name in ('sinB', 'cosB'):
        continue  # The geometric wrapper derives these directly from the edge.
    lines.append(f'        BigDecimal {name} = {expression(ast.parse(" ".join(value.split()), mode="eval").body)};')
lines.append('        BigDecimal[] result = new BigDecimal[6];')
components = ('XX', 'YY', 'ZZ', 'XY', 'XZ', 'YZ')
for name, value in re.findall(r'private static double correction(\w+)\(FreeSurfaceTerms t\) \{\s*return ([^;]+);', source):
    value = value.replace('t.', '')
    lines.append(f'        result[{components.index(name)}] = {expression(ast.parse(" ".join(value.split()), mode="eval").body)};')
lines += ['        return result;', '    }', '    // @formatter:on', '']
start = re.search(r'^[ \t]*BigDecimal cotB\s*=', original, re.M).start()
stop = re.search(r'^[ \t]*private static BigDecimal value\(', original[start:], re.M).start() + start
result = original[:start] + format_algebra(lines) + '\n' + original[stop:]
def extended_result(original):
    """Reuse identical subexpressions; never reassociate or simplify the algebra."""
    initial = [(name, ast.parse(" ".join(value.split()), mode="eval").body)
               for name, value in re.findall(r'^\s+(\w+) = ([^;]+);', source, re.M)
               if name not in ('sinB', 'cosB')]
    parameters = ['y1', 'y2', 'y3', 'sinB', 'cosB', 'b1', 'b2', 'b3', 'nu', 'a']
    names = parameters + [name for name, _ in initial]
    roots = [(name, ast.parse(" ".join(value.replace('t.', '').split()), mode="eval").body)
             for name, value in re.findall(
                 r'private static double correction(\w+)\(FreeSurfaceTerms t\) \{\s*return ([^;]+);', source)]
    counts = collections.Counter(ast.dump(n) for _, root in roots for n in ast.walk(root)
                                 if isinstance(n, (ast.BinOp, ast.UnaryOp)))
    shared, common = {}, []

    def emit(node, terms=False):
        key = ast.dump(node)
        if terms and key in shared:
            return f't.common[{shared[key]}]'
        if isinstance(node, ast.Name):
            return 'M_PI' if node.id == 'PI' else 't.' + node.id if terms and node.id in names else node.id
        if isinstance(node, ast.Constant):
            return f'number("{node.value}")'
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
            return f'negate({emit(node.operand, terms)})'
        if isinstance(node, ast.BinOp):
            operation = {ast.Add: 'add', ast.Sub: 'subtract', ast.Mult: 'multiply', ast.Div: 'divide'}[type(node.op)]
            return f'{operation}({emit(node.left, terms)}, {emit(node.right, terms)})'
        if isinstance(node, ast.Call):
            return f'{node.func.id}({", ".join(emit(arg, terms) for arg in node.args)})'
        raise ValueError(ast.dump(node))

    def visit(node):
        for child in ast.iter_child_nodes(node):
            visit(child)
        key = ast.dump(node)
        if counts.get(key, 0) > 1 and key not in shared:
            value = emit(node, True)  # dependencies have already been assigned
            shared[key] = len(common)
            common.append(value)

    for _, node in roots:
        visit(node)

    def summands(node):
        # Split the existing left-associated sum into small methods for HotSpot.
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            return summands(node.left) + [node.right]
        return [node]

    code = ['    private static DoubleDouble[] strain(DoubleDouble y1, DoubleDouble y2, DoubleDouble y3, DoubleDouble sinB, DoubleDouble cosB, DoubleDouble b1, DoubleDouble b2, DoubleDouble b3, DoubleDouble nu, DoubleDouble a) {',
            '        Terms t = new Terms(y1, y2, y3, sinB, cosB, b1, b2, b3, nu, a);',
            '        return new DoubleDouble[] {componentXX(t), componentYY(t), componentZZ(t), componentXY(t), componentXZ(t), componentYZ(t)};',
            '    }', '    private static final class Terms {',
            '        final DoubleDouble ' + ', '.join(names) + ';',
            '        Terms(DoubleDouble y1, DoubleDouble y2, DoubleDouble y3, DoubleDouble sinB, DoubleDouble cosB, DoubleDouble b1, DoubleDouble b2, DoubleDouble b3, DoubleDouble nu, DoubleDouble a) {']
    code += [f'            this.{name} = {name};' for name in parameters]
    code += [f'            {name} = {emit(node)};' for name, node in initial]
    code.append(f'            common = new DoubleDouble[{len(common)}];')
    code += [f'            init{first}(this);' for first in range(0, len(common), 40)]
    code += ['        }', '        final DoubleDouble[] common;', '    }']
    for first in range(0, len(common), 40):
        code.append(f'    private static void init{first}(Terms t) {{')
        code += [f'        t.common[{i}] = {common[i]};' for i in range(first, min(first + 40, len(common)))]
        code.append('    }')
    for name, node in roots:
        parts = summands(node)
        value = f'part{name}0(t)'
        for i in range(1, len(parts)):
            value = f'add({value}, part{name}{i}(t))'
        code += [f'    private static DoubleDouble component{name}(Terms t) {{', f'        return {value};', '    }']
        for i, part in enumerate(parts):
            code += [f'    private static DoubleDouble part{name}{i}(Terms t) {{',
                     f'        return {emit(part, True)};', '    }']
    code.append('    // @formatter:on')
    start = re.search(r'^[ \t]*private static DoubleDouble\[\] strain\(', original, re.M).start()
    stop = re.search(r'^[ \t]*private static DoubleDouble value\(', original[start:], re.M).start() + start
    return original[:start] + format_algebra(code) + "\n" + original[stop:]


extended_target = package / 'TriangularDislocationExtended.java'
extended_original = extended_target.read_text()
extended = extended_result(extended_original)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
for path, before, after in [(target, original, result), (extended_target, extended_original, extended)]:
    if args.check:
        if ''.join(before.split()) != ''.join(after.split()):
            raise SystemExit(f'Precision equations need regeneration: {path.name}')
    else:
        path.write_text(after)
        print(f'Regenerated {path}')
if args.check:
    print('Decimal and two-double equations match the ordinary kernel')
