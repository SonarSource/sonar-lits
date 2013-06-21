/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class LITSPluginTest {

  @Test
  public void test() {
    assertThat(new LITSPlugin().getExtensions()).containsOnly(IssuesChecker.class);
  }

}
