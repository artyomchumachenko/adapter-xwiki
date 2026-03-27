from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REPORT_PATH = Path("reports/sast/spotbugs-report.xml")
BLOCKING_PRIORITIES = {"1", "2"}


def main() -> int:
    if not REPORT_PATH.exists():
        print(f"Security gate error: report not found at {REPORT_PATH}")
        return 1

    root = ET.parse(REPORT_PATH).getroot()
    findings = []

    for bug in root.findall("BugInstance"):
        priority = bug.attrib.get("priority", "")
        category = bug.attrib.get("category", "")
        if category != "SECURITY" or priority not in BLOCKING_PRIORITIES:
            continue

        bug_type = bug.attrib.get("type", "UNKNOWN")
        cwe = bug.attrib.get("cweid", "N/A")
        source_line = bug.find("SourceLine")
        source_file = source_line.attrib.get("sourcefile", "unknown") if source_line is not None else "unknown"
        line = source_line.attrib.get("start", "?") if source_line is not None else "?"
        long_message = bug.findtext("LongMessage", default="No description")

        findings.append(
            {
                "priority": priority,
                "type": bug_type,
                "cwe": cwe,
                "source_file": source_file,
                "line": line,
                "message": long_message,
            }
        )

    if not findings:
        print("Security gate passed: no SECURITY findings with priority 1 or 2 were found.")
        return 0

    print("Security gate failed: blocking SECURITY findings were detected.")
    for finding in findings:
        print(
            f"- priority={finding['priority']} type={finding['type']} "
            f"cwe={finding['cwe']} location={finding['source_file']}:{finding['line']}"
        )
        print(f"  {finding['message']}")

    return 1


if __name__ == "__main__":
    sys.exit(main())
