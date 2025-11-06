/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Objects;

@Immutable
class IssueKey implements Comparable<IssueKey> {

  final String componentKey;
  final String ruleKey;
  final int line;

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line) {
    this.componentKey = componentKey;
    this.ruleKey = ruleKey;
    this.line = line != null ? line : 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof IssueKey) {
      IssueKey other = (IssueKey) obj;
      return this.line == other.line
        && Objects.equals(this.componentKey, other.componentKey)
        && Objects.equals(this.ruleKey, other.ruleKey);
    }
    return false;
  }

  @Override
  public int hashCode() {
    // Godin: maybe would be better for performance to cache hash code
    return Objects.hash(componentKey, ruleKey, line);
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
      }
    }
    return c;
  }

}
