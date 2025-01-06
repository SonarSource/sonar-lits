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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.LogTester;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @org.junit.Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  private ActiveRules activeRules;
  private IssuesChecker checker;
  private File output;
  private File assertion;
  private IssueFilterChain chainReturnTrue = (issue) -> true;
  private IssueFilterChain chainReturnFalse = (issue) -> false;

  @Before
  public void setup() throws Exception {
    output = new File(temporaryFolder.newFolder(), "dump");
    assertion = new File(temporaryFolder.newFolder(), "assertion");
    Configuration settings = newCorrectSettings().asConfig();
    activeRules = new ActiveRulesBuilder().build();
    checker = new IssuesChecker(settings, activeRules);
  }

  @Test
  public void path_must_be_specified() {
    Configuration settings = new MapSettings().asConfig();
    MessageException e = assertThrows(MessageException.class, () ->
      new IssuesChecker(settings, activeRules));
    assertEquals("Missing property 'sonar.lits.dump.old'", e.getMessage());
  }

  @Test
  public void path_must_be_absolute() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    MessageException e = assertThrows(MessageException.class, () ->
        new IssuesChecker(settings.asConfig(), activeRules));
    assertEquals("Path must be absolute - check property 'sonar.lits.dump.old'", e.getMessage());
  }

  @Test
  public void differences_file_must_be_specified() {
    MapSettings settings = newCorrectSettings();
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, (String) null);
    MessageException e = assertThrows(MessageException.class, () ->
      new IssuesChecker(settings.asConfig(), activeRules));
    assertEquals("Missing property 'sonar.lits.differences'", e.getMessage());
  }

  @Test
  public void should_fail_when_incorrect_severity() {
    Configuration settings = newCorrectSettings().asConfig();
    activeRules = new ActiveRulesBuilder()
      .create(RuleKey.of("repositoryKey", "ruleKey"))
      .setSeverity(RulePriority.BLOCKER.toString())
      .activate()
      .build();

    MessageException e = assertThrows(MessageException.class, () ->
      new IssuesChecker(settings, activeRules));
    assertEquals("Rule 'repositoryKey:ruleKey' must be declared with severity INFO", e.getMessage());
  }

  @Test
  public void should_not_save_when_no_differences() {
    checker.save();

    assertThat(output).doesNotExist();
  }

  @Test
  public void should_save_when_differences() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    assertThat(checker.accept(issue, chainReturnTrue)).isTrue();
    checker.save();

    assertThat(output).exists();
  }

  @Test
  public void should_not_save_when_disabled() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    checker.disabled = true;
    assertThat(checker.accept(issue, chainReturnTrue)).isTrue();
    checker.save();

    assertThat(output).doesNotExist();
  }

  @Test
  public void should_return_false_if_chain_return_false() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    assertThat(checker.accept(issue, chainReturnFalse)).isFalse();
    checker.save();

    assertThat(output).doesNotExist();
  }

  @Test
  public void should_hide_old_issues() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("project:src/Example.java");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));
    when(issue.line()).thenReturn(1);
    when(issue.severity()).thenReturn("INFO");

    assertThat(checker.accept(issue, chainReturnTrue)).isFalse();
  }

  @Test
  public void should_fail_when_inactive_rules() {
    checker.inactiveRule("squid:S00103");
    MessageException e = assertThrows(MessageException.class, () ->
      checker.save());
    assertThat(e.getMessage()).isEqualTo("Inactive rules: squid:S00103");
    assertThat(output).exists();
  }

  @Test
  public void getPrevious_should_return_empty_list_when_no_output_directory() throws IOException {
    String nonExistingPath = new File("file/does/not/exist").getCanonicalPath();

    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, nonExistingPath);
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, nonExistingPath);
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, nonExistingPath);
    checker = new IssuesChecker(settings.asConfig(), activeRules);

    Map previous = checker.getPrevious();

    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs().get(0)).startsWith("Directory not found:");
    assertThat(previous).isEmpty();
  }

  @Test
  public void should_fail_when_missing_resources() {
    checker.missingResource("missing_resource");
    MessageException e = assertThrows(MessageException.class, () ->
      checker.save());
    assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: missing_resource");
    assertThat(output).exists();
  }

  @Test
  public void multiple_missing_resources_show_all_names_in_error_message() {
    checker.missingResource("first_missing");
    checker.missingResource("second_missing");
    checker.missingResource("third_missing");
    MessageException e = assertThrows(MessageException.class, () ->
      checker.save());
    assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: first_missing, third_missing, second_missing");
    assertThat(output).exists();
  }

  private MapSettings newCorrectSettings() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, new File("src/test/resources/").getAbsolutePath());
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, output.getAbsolutePath());
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, assertion.getAbsolutePath());
    return settings;
  }

}
