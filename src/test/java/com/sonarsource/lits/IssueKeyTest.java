/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
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
