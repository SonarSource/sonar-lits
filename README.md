Sonar LITS
==========

### SARIF locations

LITS preserves issue text ranges, including multiline ranges and exclusive end columns.
SARIF lines and columns are one-based; columns use UTF-16 code units.
Character and byte offsets and lengths are omitted, including when supplied in input baselines.
Issues without a text range remain line-only or file-level; LITS does not invent columns.

Issue comparisons include the rule, message, and range. Multiple issues at different columns
on the same line remain distinct. Missing-issue diagnostics are shown on the expected line so
they remain valid when the current file no longer has the expected columns or lines.

### SARIF messages

SARIF requires a message, so LITS writes a null issue message as the string `"null"`
and converts that exact string back to null when loading. This string is reserved:
a literal issue message of `"null"` also loads as null. Other messages, including
the previous placeholder `"Issue"`, remain unchanged.

### License

Copyright 2013-2022 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
