#!/usr/bin/env python3
"""Write the THIRD-PARTY-NOTICES file of a MORPHEUS distribution.

A distributed archive redistributes every third-party component on the runtime classpath of the launcher, and the
licences of those components ask for their texts to travel with them. The set is not written here: it is what Maven
resolved for the distributed module (`dependency:list`, runtime scope), minus the modules of the product itself.

For each component the notices reproduce the licence and notice files its own JAR carries, then the upstream files
committed under distribution/third-party/texts/<group>__<artifact>/ for the components whose JAR carries none. A
component must be declared in distribution/third-party/components.tsv with its licence identifier and where its texts
were read; the declaration is checked in both directions:

  THIRD_PARTY_NOTICE_MISSING  a shipped component is not declared, or has no text at all
  THIRD_PARTY_NOTICE_UNUSED   a declaration, or a directory of texts, that no shipped component uses

Either one fails the packaging: a release without the attribution of something it ships is not built.

Usage: write-third-party-notices.py --dependencies <dependency:list output> --third-party <directory>
                                    --jdk-release <release file of the JDK the runtime was linked from>
                                    --runtime-release <release file of the embedded runtime> --output <file>
"""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

OWN_GROUP = "io.github.fturleque"
DEPENDENCY = re.compile(r"^\s*([^:\s]+):([^:\s]+):jar:([^:\s]+):(?:compile|runtime):(.+?)(?:\s+--\s+module.*)?\s*$")
IN_JAR_TEXT = re.compile(r"^(?:META-INF/)?[^/]*(?:LICENSE|NOTICE|COPYING)[^/]*$", re.IGNORECASE)
RELEASE_ENTRY = re.compile(r'^([A-Z_]+)="?(.*?)"?\s*$')
RULE = "=" * 100


@dataclass(frozen=True)
class Component:
    group: str
    artifact: str
    version: str
    jar: Path

    @property
    def coordinates(self) -> str:
        return f"{self.group}:{self.artifact}"

    @property
    def directory(self) -> str:
        return f"{self.group}__{self.artifact}"


def shipped_components(dependency_list: str) -> list[Component]:
    components = {}
    for line in dependency_list.splitlines():
        match = DEPENDENCY.match(line)
        if match and match.group(1) != OWN_GROUP:
            component = Component(match.group(1), match.group(2), match.group(3), Path(match.group(4)))
            components[component.coordinates] = component
    return [components[key] for key in sorted(components)]


def declarations(third_party: Path) -> dict[str, tuple[str, str]]:
    declared = {}
    for line in (third_party / "components.tsv").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            coordinates, licence, source = line.split("\t")
            declared[coordinates] = (licence, source)
    return declared


def texts_in_jar(jar: Path) -> list[tuple[str, str]]:
    with zipfile.ZipFile(jar) as archive:
        names = sorted(name for name in archive.namelist() if IN_JAR_TEXT.match(name))
        return [(name, archive.read(name).decode("utf-8", errors="replace")) for name in names]


def committed_texts(directory: Path) -> list[tuple[str, str]]:
    if not directory.is_dir():
        return []
    return [(entry.name, entry.read_text(encoding="utf-8")) for entry in sorted(directory.iterdir()) if entry.is_file()]


def release_entries(release_file: Path) -> dict[str, str]:
    entries = {}
    for line in release_file.read_text(encoding="utf-8").splitlines():
        match = RELEASE_ENTRY.match(line)
        if match:
            entries[match.group(1)] = match.group(2)
    return entries


def runtime_origin(jdk_release: Path, runtime_release: Path) -> str:
    """The vendor is named by the JDK the runtime was linked from; jlink does not carry it into the runtime."""
    jdk, runtime = release_entries(jdk_release), release_entries(runtime_release)
    if "IMPLEMENTOR" not in jdk or "JAVA_VERSION" not in jdk:
        raise SystemExit(f"{jdk_release} does not name the vendor and version of the JDK the runtime was built from")
    if runtime.get("JAVA_VERSION") != jdk["JAVA_VERSION"]:
        raise SystemExit(f"the embedded runtime is Java {runtime.get('JAVA_VERSION')} and the JDK given as its origin "
                         f"is Java {jdk['JAVA_VERSION']}: the notices would name the wrong runtime")
    return f"{jdk['IMPLEMENTOR']}, Java {jdk.get('JAVA_RUNTIME_VERSION', jdk['JAVA_VERSION'])}"


def refusals(components: list[Component], declared: dict, third_party: Path, texts: dict) -> list[str]:
    shipped = {component.coordinates for component in components}
    used_directories = {component.directory for component in components}
    problems = []
    for component in components:
        if component.coordinates not in declared:
            problems.append(f"THIRD_PARTY_NOTICE_MISSING: {component.coordinates} is shipped and not declared in "
                            "distribution/third-party/components.tsv")
        elif not texts[component.coordinates]:
            problems.append(f"THIRD_PARTY_NOTICE_MISSING: {component.coordinates} has no licence text, neither in its "
                            f"JAR nor under distribution/third-party/texts/{component.directory}/")
    for coordinates in sorted(set(declared) - shipped):
        problems.append(f"THIRD_PARTY_NOTICE_UNUSED: {coordinates} is declared and no longer shipped")
    texts_root = third_party / "texts"
    if texts_root.is_dir():
        for directory in sorted(entry.name for entry in texts_root.iterdir() if entry.is_dir()):
            if directory not in used_directories:
                problems.append(f"THIRD_PARTY_NOTICE_UNUSED: distribution/third-party/texts/{directory}/ belongs to "
                                "no shipped component")
    return problems


def render(components: list[Component], declared: dict, texts: dict, runtime: str) -> str:
    lines = [
        "MORPHEUS - THIRD-PARTY NOTICES",
        "",
        "MORPHEUS is distributed under the terms of the LICENSE file beside this one. This distribution also contains",
        "the third-party components listed below. Each remains under its own licence, reproduced with the notices its",
        "authors ask to be kept. Nothing here changes the terms of those licences.",
        "",
        f"Components: {len(components)}",
    ]
    for component in components:
        lines.append(f"  {component.coordinates} {component.version} ({declared[component.coordinates][0]})")
    lines += [
        "",
        RULE,
        "Embedded Java runtime",
        RULE,
        "",
        f"The runtime shipped in this distribution was built from: {runtime}.",
        "Its licence and the notices of each of its modules are in the `legal` directory of the runtime, as the JDK",
        "provides them.",
    ]
    for component in components:
        licence, source = declared[component.coordinates]
        lines += ["", RULE, f"{component.coordinates} {component.version}", f"Licence: {licence}", f"Source: {source}", RULE]
        for name, text in texts[component.coordinates]:
            lines += ["", f"--- {name} ---", "", text.replace("\r\n", "\n").rstrip("\n")]
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dependencies", type=Path, required=True)
    parser.add_argument("--third-party", type=Path, required=True)
    parser.add_argument("--jdk-release", type=Path, required=True)
    parser.add_argument("--runtime-release", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args(argv[1:])

    components = shipped_components(arguments.dependencies.read_text(encoding="utf-8"))
    if not components:
        raise SystemExit("no third-party component found in the dependency list: the notices would be empty")
    declared = declarations(arguments.third_party)
    texts = {component.coordinates:
             texts_in_jar(component.jar) + committed_texts(arguments.third_party / "texts" / component.directory)
             for component in components}

    problems = refusals(components, declared, arguments.third_party, texts)
    if problems:
        for problem in problems:
            print(problem, file=sys.stderr)
        return 1
    arguments.output.write_text(
        render(components, declared, texts, runtime_origin(arguments.jdk_release, arguments.runtime_release)),
        encoding="utf-8", newline="\n")
    print(f"THIRD-PARTY-NOTICES: {len(components)} components attributed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
