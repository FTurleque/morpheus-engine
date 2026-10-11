"""Tests of scripts/write-third-party-notices.py on fabricated components, and of the committed declarations.

The fabricated cases build small JARs and a small declarations directory, so that each refusal is seen on its own.
The last test reads the real distribution/third-party directory: every declared component that needs committed texts
has some, and no directory of texts is left without a declaration.
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "write-third-party-notices.py"
OWN = "io.github.fturleque"


class Workspace:
    def __init__(self, root: Path):
        self.root = root
        self.third_party = root / "third-party"
        (self.third_party / "texts").mkdir(parents=True)
        self.dependencies: list[str] = []
        self.declared: list[str] = []
        self.jdk = self.release("jdk-release", 'IMPLEMENTOR="Example Vendor"\nJAVA_VERSION="21.0.9"\n')
        self.runtime = self.release("runtime-release", 'JAVA_VERSION="21.0.9"\nMODULES="java.base"\n')

    def release(self, name: str, content: str) -> Path:
        path = self.root / name
        path.write_text(content, encoding="utf-8")
        return path

    def ship(self, group: str, artifact: str, version: str, entries: dict[str, str], scope: str = "compile") -> None:
        jar = self.root / f"{artifact}-{version}.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("example/Thing.class", "bytes")
            for name, text in entries.items():
                archive.writestr(name, text)
        self.dependencies.append(f"   {group}:{artifact}:jar:{version}:{scope}:{jar} -- module example (auto)")

    def declare(self, coordinates: str, licence: str = "Apache-2.0", source: str = "inside the JAR") -> None:
        self.declared.append(f"{coordinates}\t{licence}\t{source}")

    def commit_text(self, directory: str, name: str, text: str) -> None:
        (self.third_party / "texts" / directory).mkdir(parents=True, exist_ok=True)
        (self.third_party / "texts" / directory / name).write_text(text, encoding="utf-8")

    def run(self) -> tuple[subprocess.CompletedProcess, Path]:
        listing = self.root / "dependencies.txt"
        listing.write_text("\nThe following files have been resolved:\n" + "\n".join(self.dependencies) + "\n",
                           encoding="utf-8")
        (self.third_party / "components.tsv").write_text(
            "# comment\n" + "\n".join(self.declared) + "\n", encoding="utf-8")
        output = self.root / "THIRD-PARTY-NOTICES"
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--dependencies", str(listing), "--third-party", str(self.third_party),
             "--jdk-release", str(self.jdk), "--runtime-release", str(self.runtime), "--output", str(output)],
            capture_output=True, text=True, check=False)
        return result, output


class ThirdPartyNoticesTest(unittest.TestCase):
    def setUp(self):
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.workspace = Workspace(Path(self._temp.name))

    def test_texts_come_from_the_jar_then_from_the_repository_and_own_modules_are_left_out(self):
        workspace = self.workspace
        workspace.ship(OWN, "morpheus-domain", "1.2.1", {})
        workspace.ship("org.example", "with-texts", "2.0", {"META-INF/LICENSE": "LICENCE IN JAR\r\n", "META-INF/NOTICE": "NOTICE IN JAR"})
        workspace.ship("org.example", "without-texts", "3.1", {}, scope="runtime")
        workspace.declare("org.example:with-texts")
        workspace.declare("org.example:without-texts", "MIT", "https://example.org/without-texts at tag v3.1")
        workspace.commit_text("org.example__without-texts", "LICENSE", "Copyright (c) Example\nPermission is hereby granted\n")

        result, output = workspace.run()

        self.assertEqual(0, result.returncode, result.stderr)
        notices = output.read_bytes().decode("utf-8")
        self.assertIn("Components: 2", notices)
        self.assertIn("org.example:with-texts 2.0 (Apache-2.0)", notices)
        self.assertIn("org.example:without-texts 3.1 (MIT)", notices)
        self.assertIn("LICENCE IN JAR", notices)
        self.assertIn("NOTICE IN JAR", notices)
        self.assertIn("Copyright (c) Example", notices)
        self.assertIn("Example Vendor, Java 21.0.9", notices)
        self.assertNotIn("morpheus-domain", notices)
        self.assertNotIn("\r", notices, "the notices are written with LF only, whatever the platform")

    def test_a_shipped_component_that_is_not_declared_is_refused(self):
        workspace = self.workspace
        workspace.ship("org.example", "undeclared", "1.0", {"META-INF/LICENSE": "text"})

        result, output = workspace.run()

        self.assertEqual(1, result.returncode)
        self.assertIn("THIRD_PARTY_NOTICE_MISSING: org.example:undeclared", result.stderr)
        self.assertFalse(output.exists(), "no notices file may be written when a component is not attributed")

    def test_a_declared_component_without_any_text_is_refused(self):
        workspace = self.workspace
        workspace.ship("org.example", "textless", "1.0", {})
        workspace.declare("org.example:textless")

        result, _ = workspace.run()

        self.assertEqual(1, result.returncode)
        self.assertIn("THIRD_PARTY_NOTICE_MISSING: org.example:textless has no licence text", result.stderr)

    def test_a_declaration_and_a_directory_nobody_ships_are_refused(self):
        workspace = self.workspace
        workspace.ship("org.example", "kept", "1.0", {"META-INF/LICENSE": "text"})
        workspace.declare("org.example:kept")
        workspace.declare("org.example:removed")
        workspace.commit_text("org.example__orphan", "LICENSE", "text")

        result, _ = workspace.run()

        self.assertEqual(1, result.returncode)
        self.assertIn("THIRD_PARTY_NOTICE_UNUSED: org.example:removed", result.stderr)
        self.assertIn("THIRD_PARTY_NOTICE_UNUSED: distribution/third-party/texts/org.example__orphan/", result.stderr)

    def test_a_version_bump_needs_no_edit(self):
        workspace = self.workspace
        workspace.ship("org.example", "bumped", "9.9.9", {"META-INF/LICENSE": "text"})
        workspace.declare("org.example:bumped")

        result, output = workspace.run()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("org.example:bumped 9.9.9", output.read_text(encoding="utf-8"))

    def test_a_runtime_that_is_not_the_one_of_the_jdk_given_is_refused(self):
        workspace = self.workspace
        workspace.ship("org.example", "kept", "1.0", {"META-INF/LICENSE": "text"})
        workspace.declare("org.example:kept")
        workspace.runtime.write_text('JAVA_VERSION="17.0.1"\n', encoding="utf-8")

        result, _ = workspace.run()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("would name the wrong runtime", result.stderr)

    def test_an_empty_dependency_list_is_refused_rather_than_written(self):
        result, _ = self.workspace.run()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("the notices would be empty", result.stderr)

    def test_a_malformed_declaration_is_refused_by_file_and_line(self):
        workspace = self.workspace
        workspace.ship("org.example", "kept", "1.0", {"META-INF/LICENSE": "text"})
        workspace.declared.append("org.example:kept\tApache-2.0")

        result, _ = workspace.run()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("components.tsv line 2 must hold three tab-separated columns", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_the_committed_declarations_and_texts_agree(self):
        third_party = ROOT / "distribution" / "third-party"
        declared = {}
        for line in (third_party / "components.tsv").read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                coordinates, licence, source = line.split("\t")
                declared[coordinates] = source
        self.assertGreaterEqual(len(declared), 6, "the distributed program ships third-party components")

        directories = {entry.name for entry in (third_party / "texts").iterdir() if entry.is_dir()}
        for coordinates, source in declared.items():
            directory = coordinates.replace(":", "__")
            if source != "inside the JAR":
                self.assertIn(directory, directories, f"{coordinates} declares texts read upstream and commits none")
                texts = [entry for entry in (third_party / "texts" / directory).iterdir() if entry.stat().st_size > 0]
                self.assertTrue(texts, f"{directory} holds no text")
        self.assertEqual(set(), directories - {coordinates.replace(":", "__") for coordinates in declared},
                         "a directory of texts belongs to no declared component")


if __name__ == "__main__":
    unittest.main()
