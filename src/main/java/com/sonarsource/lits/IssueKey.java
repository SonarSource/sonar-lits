/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class IssueKey implements Comparable<IssueKey> {

  final String componentKey;
  final String ruleKey;
  final int line;

  public IssueKey(String componentKey, String ruleKey, @Nullable Integer line) {
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
