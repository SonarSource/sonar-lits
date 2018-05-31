/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.sonarsource.lits;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
class IssueKey implements Comparable<IssueKey> {

  final String componentKey;
  final String ruleKey;
  final int line;

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line) {
    this.componentKey = componentKey;
    this.ruleKey = ruleKey;
    this.line = Objects.firstNonNull(line, 0);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof IssueKey) {
      IssueKey other = (IssueKey) obj;
      return this.line == other.line
        && Objects.equal(this.componentKey, other.componentKey)
        && Objects.equal(this.ruleKey, other.ruleKey);
    }
    return false;
  }

  @Override
  public int hashCode() {
    // Godin: maybe would be better for performance to cache hash code
    return Objects.hashCode(componentKey, ruleKey, line);
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
