# workspace-boundary-scan-cost Specification

## Purpose
Define what a source scan refuses at the physical workspace boundary, what it may remember within one scan, and how
much boundary work it may do for each entry it visits.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/workspace-boundary-scan-cost.md](../../../docs/developer/specifications/workspace-boundary-scan-cost.md).

## Requirements

### Requirement: A source scan SHALL refuse every path that leaves the workspace physically

A scan MUST refuse, with the failure code `WORKSPACE_BOUNDARY_ESCAPED`, a source root, directory or file whose path
leaves the workspace lexically, crosses a symbolic link below the workspace root, or resolves outside the physical
workspace; it MUST refuse a workspace root that is itself a symbolic link; and a refusal MUST stop the scan and leave
the refused entry and everything after it out of the inventory.

#### Scenario: A source root reached through a linked intermediate directory

- **WHEN** a source root lies below a directory of the workspace that is a symbolic link to a place outside it
- **THEN** the scan reports `WORKSPACE_BOUNDARY_ESCAPED` for that root and reads no file beneath it

#### Scenario: A workspace root that is a link

- **WHEN** the workspace root given to the scan is a symbolic link
- **THEN** the scan refuses it without walking its target

#### Scenario: A link inside the tree is not followed

- **WHEN** a directory or a file of the tree is a symbolic link, wherever it points
- **THEN** the scan does not enter or read it, and it does not appear in the inventory

#### Scenario: An ordinary tree is scanned completely

- **WHEN** a tree without links is scanned
- **THEN** the scan is complete and its inventory is identical, entry for entry and fingerprint for fingerprint, to
  the one produced before this change

#### Scenario: The refusal says nothing more than before

- **WHEN** a boundary refusal is reported
- **THEN** its code, its displayed path and its message are those reported before this change

### Requirement: The boundary work of a scan SHALL resolve each directory once and link-test every segment before each read

Within one scan, a directory the scan has validated and entered MUST NOT be resolved again for each entry beneath it:
the physical path resolutions made for the boundary before reading MUST be bounded by a constant per directory entered
and per file visited. Before reading an entry the scan MUST still test the workspace root and every segment above the
entry for a symbolic link, one query each, never answered from memory (ADR-0157).

#### Scenario: Deepening a tree adds one link test per segment and no resolution per file

- **WHEN** the same number of files is scanned once at the bottom of a chain of `d` nested directories and once at the
  bottom of a chain of `2d`
- **THEN** the count of boundary queries made before reading grows by the resolution of the `d` added directories and
  by one link test per added segment above each directory and each file, and by no resolution per file

#### Scenario: A directory is resolved once per scan

- **WHEN** a directory holding many files is scanned
- **THEN** each additional file costs the resolution of its own segment and one link test per segment above it, and
  the directories above it are not resolved again

#### Scenario: The source root still takes the full walk

- **WHEN** a scan starts on a source root several segments below the workspace root
- **THEN** every segment between the workspace root and the source root is validated once, before any entry is visited

#### Scenario: The proof holds no duration

- **WHEN** the test that proves the bound is read
- **THEN** it asserts on counts reported by the scan's boundary and on no elapsed time

### Requirement: A segment replaced by a link during a scan MUST still be refused before the file enters the inventory

The validation a scan makes after fingerprinting a file MUST be answered from the file system as it is at that moment,
for every segment between the workspace root and the file, and MUST NOT be answered from what the scan remembered.

#### Scenario: An ancestor is replaced while a file is being fingerprinted

- **WHEN** a directory above a file is replaced by a symbolic link, pointing inside or outside the workspace, after the
  file has been validated and before its fingerprint is accepted
- **THEN** the scan reports the file as crossing a symbolic link under `WORKSPACE_BOUNDARY_ESCAPED`, never
  `SOURCE_UNREADABLE`, stops, and neither the file nor a complete inventory is produced

#### Scenario: The file itself is replaced by a link

- **WHEN** a file is replaced by a symbolic link while it is being fingerprinted
- **THEN** it does not enter the inventory

### Requirement: No content SHALL be read through a link once a validated segment has become one

A scan MUST refuse an entry before reading it when the workspace root or any segment above the entry, validated
earlier in the same scan, has since been replaced by a symbolic link, wherever the link points (ADR-0157).

#### Scenario: A validated ancestor becomes a link resolving inside the workspace

- **WHEN** a directory the scan has validated and entered is replaced by a symbolic link to another directory of the
  workspace, before the next entry beneath it is read
- **THEN** that entry is refused as crossing a symbolic link before it is read, and no other file of the scan is read

#### Scenario: The workspace root becomes a link during a scan

- **WHEN** the workspace root is replaced by a symbolic link after the scan has validated it
- **THEN** the next entry is refused, before it is read, because the workspace root is a symbolic link

### Requirement: What a scan remembers MUST NOT outlive that scan nor reach the watcher

The directories a scan has validated MUST be forgotten when the scan ends, and a check made by the source watcher MUST
be answered from the file system on every call.

#### Scenario: A second scan validates again

- **WHEN** a directory of the workspace is replaced by a symbolic link between two scans made by the same scanner
- **THEN** the second scan refuses it

#### Scenario: A watcher event is checked afresh

- **WHEN** the watcher receives an event for a path below a directory it has checked before, and that directory has
  since been replaced by a symbolic link
- **THEN** the watcher refuses the path
