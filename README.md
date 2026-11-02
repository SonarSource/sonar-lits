Sonar LITS
==========

### SARIF locations

LITS preserves issue text ranges, including multiline ranges and exclusive end columns.
SARIF lines and columns are one-based; columns use UTF-16 code units.
Character and byte offsets and lengths are omitted, including when supplied in input baselines.
Issues without a text range remain line-only or file-level; LITS does not invent columns.

Precise expectations compare ranges. Legacy JSON and line-only SARIF
expectations continue to match by line. Multiple issues at different columns on the same line
remain distinct, and missing-issue diagnostics use the expected range.

### License

Copyright 2013-2022 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
