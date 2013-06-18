/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.base.Objects;

public class IssueKey implements Comparable<IssueKey> {

  final String componentKey;
  final String ruleKey;
  final int line;

  public IssueKey(String componentKey, String ruleKey, Integer line) {
    this.componentKey = componentKey;
    this.ruleKey = ruleKey;
    this.line = Objects.firstNonNull(line, 0);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof IssueKey) {
      IssueKey other = (IssueKey) obj;
      return Objects.equal(this.componentKey, other.componentKey)
        && Objects.equal(this.ruleKey, other.ruleKey)
        && this.line == other.line;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(componentKey, ruleKey, line);
  }

  @Override
  public String toString() {
    return componentKey + " " + ruleKey + " " + line;
  }

  public int compareTo(IssueKey other) {
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
