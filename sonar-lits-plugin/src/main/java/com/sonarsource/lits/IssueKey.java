/*
 * Sonar LITS Plugin
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package com.sonarsource.lits;

import com.sonarsource.lits.sarif.Region;
import java.util.Comparator;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.batch.fs.TextRange;

@Immutable
class IssueKey implements Comparable<IssueKey> {

  final String componentKey;
  final String ruleKey;
  final int line;
  @Nullable
  final String message;
  @Nullable
  final IssueLocation location;

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line) {
    this(componentKey, ruleKey, line, null);
  }

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line, @Nullable String message) {
    this(componentKey, ruleKey, line, message, null);
  }

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line, @Nullable String message, @Nullable IssueLocation location) {
    this.componentKey = componentKey;
    this.ruleKey = ruleKey;
    int reportedLine = line == null ? 0 : line;
    this.line = location == null ? reportedLine : location.startLine;
    this.message = message;
    this.location = location;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof IssueKey) {
      IssueKey other = (IssueKey) obj;
      return this.line == other.line
        && Objects.equals(this.componentKey, other.componentKey)
        && Objects.equals(this.ruleKey, other.ruleKey)
        && Objects.equals(this.message, other.message)
        && Objects.equals(this.location, other.location);
    }
    return false;
  }

  @Override
  public int hashCode() {
    // Godin: maybe would be better for performance to cache hash code
    return Objects.hash(componentKey, ruleKey, line, message, location);
  }

  @Override
  public String toString() {
    return componentKey + " " + ruleKey + " " + line;
  }

  @Override
  public int compareTo(IssueKey other) {
    // Godin: maybe would be better for performance to use FastStringComparator from sonar-duplications
    int c = this.componentKey.compareTo(other.componentKey);
    if (c == 0) {
      c = this.ruleKey.compareTo(other.ruleKey);
      if (c == 0) {
        c = this.line - other.line;
        if (c == 0) {
          c = Comparator.nullsFirst(IssueLocation::compareTo).compare(location, other.location);
          if (c == 0) {
            c = Comparator.nullsFirst(String::compareTo).compare(message, other.message);
          }
        }
      }
    }
    return c;
  }

  @Immutable
  static final class IssueLocation implements Comparable<IssueLocation> {
    private static final Comparator<IssueLocation> ORDER = Comparator.comparingInt((IssueLocation position) -> position.startLine)
      .thenComparingInt(position -> position.startColumn).thenComparingInt(position -> position.endLine)
      .thenComparingInt(position -> position.endColumn);

    final int startLine;
    final int startColumn;
    final int endLine;
    final int endColumn;

    IssueLocation(Region region) {
      startLine = region.getStartLine().orElseThrow(IllegalArgumentException::new);
      startColumn = region.getStartColumn().orElseThrow(IllegalArgumentException::new);
      endLine = region.getEndLine().orElse(startLine);
      endColumn = region.getEndColumn().orElseThrow(IllegalArgumentException::new);
    }

    static IssueLocation fromRange(TextRange range) {
      return new IssueLocation(new Region().withStartLine(range.start().line()).withStartColumn(range.start().lineOffset() + 1)
        .withEndLine(range.end().line()).withEndColumn(range.end().lineOffset() + 1));
    }

    @Nullable
    static IssueLocation fromRegion(@Nullable Region region) {
      if (region == null || !region.getStartLine().isPresent() || !region.getStartColumn().isPresent() || !region.getEndColumn().isPresent()) {
        return null;
      }
      return new IssueLocation(region);
    }

    Region toRegion() {
      return new Region().withStartLine(startLine).withStartColumn(startColumn).withEndLine(endLine).withEndColumn(endColumn)
        .withCharOffset(null).withByteOffset(null);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof IssueLocation)) {
        return false;
      }
      IssueLocation location = (IssueLocation) other;
      return startLine == location.startLine && startColumn == location.startColumn && endLine == location.endLine && endColumn == location.endColumn;
    }

    @Override
    public int hashCode() {
      return Objects.hash(startLine, startColumn, endLine, endColumn);
    }

    @Override
    public int compareTo(IssueLocation other) {
      return ORDER.compare(this, other);
    }
  }
}
