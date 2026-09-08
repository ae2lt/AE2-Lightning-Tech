#!/usr/bin/env python3
"""Run sequential, fresh-JVM wireless I/O A/B measurements on macOS/Linux/Windows.

One round is a pilot; five rounds enable the unchanged Gradle acceptance check.
Failed runs retain their logs and reports and never become successful samples.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time


def identity(project):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(project), *args], text=True).strip()
    digest = hashlib.sha256()
    files = sorted(project.glob("src/**/*.java")) + [project / "build.gradle", project / "gradle.properties"]
    for path in files:
        digest.update(str(path.relative_to(project)).encode())
        digest.update(path.read_bytes())
    return {"head": git("rev-parse", "HEAD"), "dirty": bool(git("status", "--porcelain")),
            "sourceSha256": digest.hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profiles", nargs="+", default=["export-continuous-256x27"])
    parser.add_argument("--runs", type=int, choices=range(1, 6), default=5)
    parser.add_argument("--warmup", type=int, default=200)
    parser.add_argument("--samples", type=int, default=1200)
    parser.add_argument("--max-io-p99-ms", type=float, default=4.0)
    args = parser.parse_args()
    if args.warmup < 40 or args.samples < 200 or args.warmup + args.samples > 1420:
        parser.error("require warmup >= 40, samples >= 200, total <= 1420")
    allowed = {"1024x27", "import-period60-1024x27", "import-buffered-normal-1024x27",
               "export-empty-1024", "export-mismatch-1024"} | {
        f"export-continuous-{targets}x{keys}" for targets in (64, 256, 1024) for keys in (1, 27)}
    if not set(args.profiles) <= allowed:
        parser.error("unknown wireless I/O profile")
    projects = {"baseline": args.baseline.resolve(), "candidate": args.candidate.resolve()}
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    init = Path(__file__).resolve().with_name("wireless-io-benchmark.init.gradle")
    init_hash = hashlib.sha256(init.read_bytes()).hexdigest()
    snapshots = {name: identity(project) for name, project in projects.items()}
    manifest = {"schema": 1, "projects": snapshots, "settings": vars(args).copy(), "runs": []}
    manifest["initScriptSha256"] = init_hash
    manifest["settings"] = {k: str(v) if isinstance(v, Path) else v for k, v in manifest["settings"].items()}

    def save():
        (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

    def gradle(project):
        wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
        return [str(project / wrapper), "-p", str(project), "--offline", "--no-configuration-cache",
                "-I", str(init)]

    failed = False
    save()
    for profile in args.profiles:
        for run in range(1, args.runs + 1):
            # Alternate which version goes first without running competing servers.
            order = list(projects) if run % 2 else list(reversed(projects))
            for role in ("control", "stress"):
                for name in order:
                    project = projects[name]
                    if identity(project) != snapshots[name] or hashlib.sha256(init.read_bytes()).hexdigest() != init_hash:
                        raise RuntimeError(f"{name} changed during sampling")
                    destination = output / profile / name
                    destination.mkdir(parents=True, exist_ok=True)
                    scenario = f"gametest-{role}-{profile}-run{run}"
                    command = gradle(project) + ["runWirelessIoGameTestServer",
                        f"-Pae2ltBenchmarkScenario={scenario}", f"-Pae2ltBenchmarkCommit={name}",
                        f"-Pae2ltBenchmarkGitHead={snapshots[name]['head']}",
                        f"-Pae2ltBenchmarkWorktreeDirty={str(snapshots[name]['dirty']).lower()}",
                        f"-Pae2ltBenchmarkControl={str(role == 'control').lower()}",
                        "-Pae2ltBenchmarkIncludeEligibility=true", "-Pae2ltBenchmarkDiagnostics=false",
                        f"-Pae2ltBenchmarkWarmupTicks={args.warmup}", f"-Pae2ltBenchmarkSampleTicks={args.samples}"]
                    print(f"{name} {scenario}: starting", flush=True)
                    started = time.time()
                    with (destination / f"{role}-run{run}.log").open("w") as log:
                        result = subprocess.run(command, cwd=project, stdout=log, stderr=subprocess.STDOUT)
                    reports = project / "run-wireless-io-gametest/benchmark-reports/wireless-interface-io"
                    fresh = [p for p in reports.glob(f"*{scenario}.json") if p.stat().st_mtime >= started]
                    report_ok = False
                    if len(fresh) == 1:
                        report = fresh[0]
                        csv = report.with_name(report.stem + "-ticks.csv")
                        shutil.copyfile(report, destination / f"{role}-run{run}.json")
                        if csv.exists():
                            shutil.copyfile(csv, destination / f"{role}-run{run}-ticks.csv")
                            data = json.loads(report.read_text())
                            report_ok = not data["partial"] and data["samples"] == args.samples
                    log_text = (destination / f"{role}-run{run}.log").read_text(errors="replace")
                    registered = re.search(r"(\d+) GAME TESTS COMPLETE", log_text)
                    game_tests = int(registered.group(1)) if registered else None
                    previous_counts = {r["gameTests"] for r in manifest["runs"]}
                    same_registration = game_tests is not None and (not previous_counts or previous_counts == {game_tests})
                    ok = result.returncode == 0 and report_ok and same_registration
                    failed |= not ok
                    manifest["runs"].append({"version": name, "scenario": scenario,
                        "exitCode": result.returncode, "completeReport": report_ok, "gameTests": game_tests,
                        "sameRegistration": same_registration,
                        "elapsedSeconds": round(time.time() - started, 2)})
                    save()
                    print(f"{name} {scenario}: {'PASS' if ok else 'FAIL'}", flush=True)
        if args.runs == 5:
            command = gradle(projects["candidate"]) + ["checkWirelessIoBenchmarkRegression",
                f"-Pae2ltBenchmarkMaxIoP99Ms={args.max_io_p99_ms}",
                f"-Pae2ltBenchmarkLiveComparisonDir={output / profile / 'comparison'}"]
            for name in projects:
                for role in ("stress", "control"):
                    reports = ";".join(str(output / profile / name / f"{role}-run{i}.json") for i in range(1, 6))
                    command.append(f"-Pae2ltBenchmark{name.title()}{role.title()}Reports={reports}")
            with (output / profile / "comparison.log").open("w") as log:
                result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
            failed |= result.returncode != 0
    manifest["result"] = "FAIL" if failed else ("PASS" if args.runs == 5 else "PILOT_COMPLETE")
    save()
    print(f"{manifest['result']}: {output}", flush=True)
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
