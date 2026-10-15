Sonar LITS
==========

### SARIF locations

LITS preserves issue text ranges, including multiline ranges and exclusive end columns.
SARIF lines and columns are one-based; columns and character offsets use UTF-16 code units.
Character offsets and lengths retain the source's original line endings and exclude a leading BOM.
Byte offsets and lengths use the file's encoding and include any BOM in the offset.
Byte positions are omitted when the encoding cannot reproduce the original byte prefixes exactly.
Issues without a text range remain line-only or file-level; LITS does not invent columns or offsets.

Precise expectations compare ranges and any supplied offsets. Legacy JSON and line-only SARIF
expectations continue to match by line. Multiple issues at different columns on the same line
remain distinct, and missing-issue diagnostics use the expected range.

### License

Copyright 2013-2022 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
