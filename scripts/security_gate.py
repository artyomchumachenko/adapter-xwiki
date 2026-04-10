from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

try:
    import yaml  # type: ignore
except ImportError:  # pragma: no cover - optional dependency in local runs
    yaml = None


DEFAULT_SAST_REPORT_PATH = Path("reports/sast/spotbugs-report.xml")
DEFAULT_SCA_REPORT_PATH = Path("reports/sca/trivy-results.json")
DEFAULT_ZAP_REPORT_PATH = Path("reports/dast/zap-report.json")
DEFAULT_NUCLEI_REPORT_PATH = Path("reports/dast/nuclei-report.json")
DEFAULT_DAST_SUPPRESSIONS_PATH = Path("reports/dast/suppressions.yml")

DEFAULT_SAST_BLOCKING_PRIORITIES = {"1"}
DEFAULT_SCA_BLOCKING_SEVERITIES = {"CRITICAL"}
DEFAULT_DAST_BLOCKING_SEVERITIES = {"CRITICAL"}

ZAP_RISK_CODE_TO_SEVERITY = {
    "0": "INFO",
    "1": "LOW",
    "2": "MEDIUM",
    "3": "HIGH",
}


def parse_csv_set(raw_value: str) -> set[str]:
    return {item.strip().upper() for item in raw_value.split(",") if item.strip()}


def normalize_severity(raw_severity: str) -> str:
    severity = str(raw_severity or "UNKNOWN").strip().upper()
    if severity == "MODERATE":
        return "MEDIUM"
    return severity


def load_dast_suppressions(suppressions_path: Path) -> list[dict[str, str]]:
    if not suppressions_path.exists():
        return []

    raw_text = suppressions_path.read_text(encoding="utf-8")

    if yaml is None:
        suppressions: list[dict] = []
        current: dict[str, str] | None = None
        in_section = False
        for raw_line in raw_text.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line == "suppressions:":
                in_section = True
                continue
            if not in_section:
                continue
            if line.startswith("- "):
                if current:
                    suppressions.append(current)
                current = {}
                line = line[2:]
            if ":" in line:
                key, value = line.split(":", 1)
                value = value.strip().strip("'").strip('"')
                if current is None:
                    current = {}
                current[key.strip()] = value
        if current:
            suppressions.append(current)
    else:
        payload = yaml.safe_load(raw_text) or {}
        suppressions = payload.get("suppressions") or []

    normalized: list[dict[str, str]] = []

    for suppression in suppressions:
        if not isinstance(suppression, dict):
            continue
        normalized.append(
            {
                "tool": str(suppression.get("tool", "")).strip().lower(),
                "id": str(suppression.get("id", "")).strip(),
                "path_contains": str(suppression.get("path_contains", "")).strip(),
            }
        )

    return normalized


def is_suppressed(finding: dict[str, str], suppressions: list[dict[str, str]]) -> bool:
    for suppression in suppressions:
        suppression_tool = suppression.get("tool")
        suppression_id = suppression.get("id")
        path_contains = suppression.get("path_contains")

        tool_match = not suppression_tool or suppression_tool == finding.get("tool", "").lower()
        id_match = not suppression_id or suppression_id == finding.get("id", "")
        path_match = not path_contains or path_contains in finding.get("path", "")

        if tool_match and id_match and path_match:
            return True
    return False


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
            severity = normalize_severity(vulnerability.get("Severity", "UNKNOWN"))
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


def read_nuclei_json_report(report_path: Path) -> list[dict]:
    raw_text = report_path.read_text(encoding="utf-8-sig").strip()
    if not raw_text:
        return []

    # Support both JSONL and regular JSON payloads (array/object).
    if raw_text.startswith("[") or raw_text.startswith("{"):
        payload = json.loads(raw_text)
        if isinstance(payload, list):
            return [item for item in payload if isinstance(item, dict)]
        if isinstance(payload, dict):
            return [payload]
        return []

    findings: list[dict] = []
    for line in raw_text.splitlines():
        line = line.strip()
        if not line:
            continue
        entry = json.loads(line)
        if isinstance(entry, dict):
            findings.append(entry)
    return findings


def scan_dast(
    zap_report_path: Path,
    nuclei_report_path: Path,
    blocking_severities: set[str],
    suppressions_path: Path,
    target_required: bool,
) -> tuple[list[dict[str, str]], str]:
    suppressions = load_dast_suppressions(suppressions_path)
    blocking_findings: list[dict[str, str]] = []
    severity_counter: Counter[str] = Counter()
    seen = set()

    if target_required and (not zap_report_path.exists() or not nuclei_report_path.exists()):
        missing = [str(path) for path in (zap_report_path, nuclei_report_path) if not path.exists()]
        raise FileNotFoundError(f"DAST target required but report(s) not found: {', '.join(missing)}")

    if zap_report_path.exists():
        zap_payload = json.loads(zap_report_path.read_text(encoding="utf-8"))
        for site in zap_payload.get("site", []):
            for alert in site.get("alerts", []):
                severity = ZAP_RISK_CODE_TO_SEVERITY.get(str(alert.get("riskcode", "")), "UNKNOWN")
                severity_counter[severity] += 1
                for instance in alert.get("instances", []):
                    path = str(instance.get("uri", "unknown"))
                    finding_id = str(alert.get("pluginid", "UNKNOWN"))
                    dedupe_key = ("zap", finding_id, path)
                    if dedupe_key in seen:
                        continue
                    seen.add(dedupe_key)

                    finding = {
                        "tool": "zap",
                        "id": finding_id,
                        "severity": severity,
                        "path": path,
                        "title": str(alert.get("alert", "No description")),
                    }

                    if is_suppressed(finding, suppressions):
                        continue
                    if severity in blocking_severities:
                        blocking_findings.append(finding)

    if nuclei_report_path.exists():
        for entry in read_nuclei_json_report(nuclei_report_path):
            info = entry.get("info", {})
            severity = normalize_severity(info.get("severity", "UNKNOWN"))
            severity_counter[severity] += 1
            path = str(entry.get("matched-at") or entry.get("host") or "unknown")
            finding_id = str(entry.get("template-id") or "UNKNOWN")

            finding = {
                "tool": "nuclei",
                "id": finding_id,
                "severity": severity,
                "path": path,
                "title": str(info.get("name") or "No description"),
            }

            if is_suppressed(finding, suppressions):
                continue
            if severity in blocking_severities:
                blocking_findings.append(finding)

    severity_summary = ", ".join(
        f"{severity}={count}" for severity, count in sorted(severity_counter.items())
    ) or "no findings reported"

    summary = (
        f"DAST gate passed: no blocking findings were found. Summary: {severity_summary}."
        if not blocking_findings
        else f"DAST gate failed: found {len(blocking_findings)} blocking finding(s). Summary: {severity_summary}."
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


def print_dast_findings(findings: list[dict[str, str]]) -> None:
    for finding in findings:
        print(
            f"- tool={finding['tool']} severity={finding['severity']} "
            f"id={finding['id']} path={finding['path']}"
        )
        print(f"  {finding['title']}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Security gate for SAST, SCA and DAST reports.")
    parser.add_argument("--mode", choices=("sast", "sca", "dast", "all"), default="all")
    parser.add_argument("--sast-report", default=str(DEFAULT_SAST_REPORT_PATH))
    parser.add_argument("--sca-report", default=str(DEFAULT_SCA_REPORT_PATH))
    parser.add_argument("--zap-report", default=str(DEFAULT_ZAP_REPORT_PATH))
    parser.add_argument("--nuclei-report", default=str(DEFAULT_NUCLEI_REPORT_PATH))
    parser.add_argument("--dast-suppressions", default=str(DEFAULT_DAST_SUPPRESSIONS_PATH))
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
    parser.add_argument(
        "--dast-blocking-severities",
        default=",".join(sorted(DEFAULT_DAST_BLOCKING_SEVERITIES)),
        help="Comma-separated DAST severities that should block the pipeline.",
    )
    parser.add_argument(
        "--target-required",
        action="store_true",
        help="Fail if DAST reports are missing (used for fail-closed mode).",
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

    if args.mode in {"dast", "all"}:
        try:
            dast_findings, dast_summary = scan_dast(
                zap_report_path=Path(args.zap_report),
                nuclei_report_path=Path(args.nuclei_report),
                blocking_severities=parse_csv_set(args.dast_blocking_severities),
                suppressions_path=Path(args.dast_suppressions),
                target_required=args.target_required,
            )
            print(dast_summary)
            if dast_findings:
                print_dast_findings(dast_findings)
                exit_code = 1
        except (FileNotFoundError, RuntimeError, json.JSONDecodeError) as error:
            print(f"Security gate error: {error}")
            return 1

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
