# Spec Delta

## Purpose

Define what a source scan refuses at the physical workspace boundary, what it may remember within one scan, and how
much boundary work it may do for each entry it visits.

## ADDED Requirements

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

### Requirement: The boundary work of a scan SHALL grow with the entries visited, not with their depth

Within one scan, a directory that the scan has validated and entered MUST NOT be validated again for each entry
beneath it: the number of physical path resolutions and link tests the scan makes for the boundary MUST be bounded by
a constant per directory entered and a constant per file visited, plus, for the re-validation of a file after its
fingerprint, at most a term proportional to that file's depth.

#### Scenario: Deepening a tree does not multiply the work per file

- **WHEN** the same number of files is scanned once at the bottom of a chain of `d` nested directories and once at the
  bottom of a chain of `2d`
- **THEN** the count of boundary queries made before reading grows by a constant times `d`, for the added directories,
  and not by the number of files times `d`

#### Scenario: A directory is validated once per scan

- **WHEN** a directory holding many files is scanned
- **THEN** the count of boundary queries on the segments above that directory does not grow with the number of files
  for the checks made before reading

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
- **THEN** the scan reports the file as crossing a symbolic link, and neither the file nor a complete inventory is
  produced

#### Scenario: The file itself is replaced by a link

- **WHEN** a file is replaced by a symbolic link while it is being fingerprinted
- **THEN** it does not enter the inventory

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
