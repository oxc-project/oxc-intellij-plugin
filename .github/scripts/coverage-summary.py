"""Prints a Markdown table of line and branch coverage for each module, from Kover's XML report.

Each module's code lives under its own package, such as com.github.oxc.project.oxcintellijplugin.backend,
so a package's module is the segment after the plugin's base package.
"""

import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

BASE_PACKAGE = "com/github/oxc/project/oxcintellijplugin/"
COUNTERS = ("LINE", "BRANCH")


def percent(covered: int, missed: int) -> str:
    total = covered + missed
    return f"{covered / total:.1%} ({covered}/{total})" if total else "n/a"


def main(report_path: str) -> None:
    root = ET.parse(report_path).getroot()
    modules: dict[str, dict[str, list[int]]] = defaultdict(lambda: {c: [0, 0] for c in COUNTERS})

    for package in root.iter("package"):
        name = package.get("name", "")
        module = name.removeprefix(BASE_PACKAGE).split("/")[0] if name.startswith(BASE_PACKAGE) else name
        for counter in package.findall("counter"):
            kind = counter.get("type")
            if kind in COUNTERS:
                modules[module][kind][0] += int(counter.get("covered", 0))
                modules[module][kind][1] += int(counter.get("missed", 0))

    totals = {c: [0, 0] for c in COUNTERS}
    for counter in root.findall("counter"):
        kind = counter.get("type")
        if kind in COUNTERS:
            totals[kind] = [int(counter.get("covered", 0)), int(counter.get("missed", 0))]

    print("## Code coverage")
    print()
    print("| Module | Lines | Branches |")
    print("| --- | --- | --- |")
    for module in sorted(modules):
        counts = modules[module]
        print(f"| {module} | {percent(*counts['LINE'])} | {percent(*counts['BRANCH'])} |")
    print(f"| **Total** | **{percent(*totals['LINE'])}** | **{percent(*totals['BRANCH'])}** |")


if __name__ == "__main__":
    main(sys.argv[1])
