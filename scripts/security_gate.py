from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


DEFAULT_SAST_REPORT_PATH = Path("reports/sast/spotbugs-report.xml")
DEFAULT_SCA_REPORT_PATH = Path("reports/sca/trivy-results.json")
DEFAULT_SAST_BLOCKING_PRIORITIES = {"1"}
DEFAULT_SCA_BLOCKING_SEVERITIES = {"CRITICAL"}


def parse_csv_set(raw_value: str) -> set[str]:
    return {item.strip().upper() for item in raw_value.split(",") if item.strip()}


def scan_sast(report_path: Path, blocking_priorities: set[str]) -> tuple[list[dict[str, str]], str]:
    if not report_path.exists():
        raise FileNotFoundError(f"SAST report not found at {report_path}")

    root = ET.parse(report_path).getroot()
    findings: list[dict[str, str]] = []

    for bug in root.findall("BugInstance"):
        priority = bug.attrib.get("priority", "")
        category = bug.attrib.get("category", "")
        if category != "SECURITY" or priority not in blocking_priorities:
            continue

        source_line = bug.find("SourceLine")
        findings.append(
            {
                "priority": priority,
                "type": bug.attrib.get("type", "UNKNOWN"),
                "cwe": bug.attrib.get("cweid", "N/A"),
                "source_file": source_line.attrib.get("sourcefile", "unknown") if source_line is not None else "unknown",
                "line": source_line.attrib.get("start", "?") if source_line is not None else "?",
                "message": bug.findtext("LongMessage", default="No description"),
            }
        )

    summary = (
        "SAST gate passed: no SECURITY findings with blocking priorities were found."
        if not findings
        else f"SAST gate failed: found {len(findings)} blocking SECURITY finding(s)."
    )
    return findings, summary


def scan_sca(report_path: Path, blocking_severities: set[str]) -> tuple[list[dict[str, str]], str]:
    if not report_path.exists():
        raise FileNotFoundError(f"SCA report not found at {report_path}")

    payload = json.loads(report_path.read_text(encoding="utf-8"))
    results = payload.get("Results", [])
    blocking_findings: list[dict[str, str]] = []
    severity_counter: Counter[str] = Counter()
    total_vulnerabilities = 0

    for result in results:
        target = result.get("Target", "unknown")
        vulnerabilities = result.get("Vulnerabilities") or []
        total_vulnerabilities += len(vulnerabilities)

        for vulnerability in vulnerabilities:
            severity = str(vulnerability.get("Severity", "UNKNOWN")).upper()
            severity_counter[severity] += 1

            if severity not in blocking_severities:
                continue

            blocking_findings.append(
                {
                    "target": target,
                    "vulnerability_id": vulnerability.get("VulnerabilityID", "UNKNOWN"),
                    "package_name": vulnerability.get("PkgName", "unknown"),
                    "installed_version": vulnerability.get("InstalledVersion", "unknown"),
                    "fixed_version": vulnerability.get("FixedVersion") or "unfixed",
                    "severity": severity,
                    "title": vulnerability.get("Title") or vulnerability.get("Description") or "No description",
                    "primary_url": vulnerability.get("PrimaryURL", "N/A"),
                }
            )

    severity_summary = ", ".join(
        f"{severity}={count}" for severity, count in sorted(severity_counter.items())
    ) or "no vulnerabilities reported"

    summary = (
        f"SCA gate passed: no blocking vulnerabilities were found. Summary: {severity_summary}."
        if not blocking_findings
        else (
            f"SCA gate failed: found {len(blocking_findings)} blocking vulnerability finding(s) "
            f"out of {total_vulnerabilities}. Summary: {severity_summary}."
        )
    )
    return blocking_findings, summary


def print_sast_findings(findings: list[dict[str, str]]) -> None:
    for finding in findings:
        print(
            f"- priority={finding['priority']} type={finding['type']} "
            f"cwe={finding['cwe']} location={finding['source_file']}:{finding['line']}"
        )
        print(f"  {finding['message']}")


def print_sca_findings(findings: list[dict[str, str]]) -> None:
    for finding in findings:
        print(
            f"- severity={finding['severity']} vulnerability={finding['vulnerability_id']} "
            f"package={finding['package_name']} installed={finding['installed_version']} "
            f"fixed={finding['fixed_version']} target={finding['target']}"
        )
        print(f"  {finding['title']}")
        print(f"  {finding['primary_url']}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Security gate for SAST and SCA reports.")
    parser.add_argument("--mode", choices=("sast", "sca", "all"), default="all")
    parser.add_argument("--sast-report", default=str(DEFAULT_SAST_REPORT_PATH))
    parser.add_argument("--sca-report", default=str(DEFAULT_SCA_REPORT_PATH))
    parser.add_argument(
        "--sast-blocking-priorities",
        default=",".join(sorted(DEFAULT_SAST_BLOCKING_PRIORITIES)),
        help="Comma-separated SpotBugs priorities that should block the pipeline.",
    )
    parser.add_argument(
        "--sca-blocking-severities",
        default=",".join(sorted(DEFAULT_SCA_BLOCKING_SEVERITIES)),
        help="Comma-separated Trivy severities that should block the pipeline.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    exit_code = 0

    if args.mode in {"sast", "all"}:
        try:
            sast_findings, sast_summary = scan_sast(
                report_path=Path(args.sast_report),
                blocking_priorities=parse_csv_set(args.sast_blocking_priorities),
            )
            print(sast_summary)
            if sast_findings:
                print_sast_findings(sast_findings)
                exit_code = 1
        except FileNotFoundError as error:
            print(f"Security gate error: {error}")
            return 1

    if args.mode in {"sca", "all"}:
        try:
            sca_findings, sca_summary = scan_sca(
                report_path=Path(args.sca_report),
                blocking_severities=parse_csv_set(args.sca_blocking_severities),
            )
            print(sca_summary)
            if sca_findings:
                print_sca_findings(sca_findings)
                exit_code = 1
        except FileNotFoundError as error:
            print(f"Security gate error: {error}")
            return 1

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
