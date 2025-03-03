/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2025 SonarSource SA
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

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class IssueKeyTest {

  @Test
  public void creation() {
    IssueKey issueKey = new IssueKey("componentKey", "ruleKey", null);
    assertThat(issueKey.componentKey).isEqualTo("componentKey");
    assertThat(issueKey.ruleKey).isEqualTo("ruleKey");
    assertThat(issueKey.line).isEqualTo(0);
  }

  @Test
  public void equals() {
    IssueKey issueKey = new IssueKey("componentKey", "ruleKey", null);
    assertThat(issueKey.equals(issueKey)).isTrue();
    assertThat(issueKey.equals(new IssueKey("componentKey", "ruleKey", null))).isTrue();
    assertThat(issueKey.equals(new IssueKey("componentKey", "ruleKey", 1))).isFalse();
    assertThat(issueKey.equals(new IssueKey("componentKey", "ruleKey2", null))).isFalse();
    assertThat(issueKey.equals(new IssueKey("componentKey2", "ruleKey", null))).isFalse();
    assertThat(issueKey.equals(new Object())).isFalse();
  }

  @Test
  public void compareTo() {
    IssueKey issueKey = new IssueKey("b", "b", 2);

    assertThat(issueKey.compareTo(new IssueKey("b", "b", 2))).isEqualTo(0);

    assertThat(issueKey.compareTo(new IssueKey("a", "b", 2))).isEqualTo(1);
    assertThat(issueKey.compareTo(new IssueKey("c", "b", 2))).isEqualTo(-1);

    assertThat(issueKey.compareTo(new IssueKey("b", "a", 2))).isEqualTo(1);
    assertThat(issueKey.compareTo(new IssueKey("b", "c", 2))).isEqualTo(-1);

    assertThat(issueKey.compareTo(new IssueKey("b", "b", 1))).isEqualTo(1);
    assertThat(issueKey.compareTo(new IssueKey("b", "b", 3))).isEqualTo(-1);
  }

}
