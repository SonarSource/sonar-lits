/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2021 SonarSource SA
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

import java.io.File;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import org.sonar.api.utils.log.LoggerLevel;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @org.junit.Rule
  public ExpectedException thrown = ExpectedException.none();

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
    thrown.expect(MessageException.class);
    thrown.expectMessage("Missing property 'sonar.lits.dump.old'");
    new IssuesChecker(settings, activeRules);
  }

  @Test
  public void path_deprecated_property_name() {
    MapSettings settings = new MapSettings();
    settings.setProperty("dump.old", "/some/old/dump");
    settings.setProperty("dump.new", "/some/new/dump");
    settings.setProperty("lits.differences", "/some/diff/file");

    new IssuesChecker(settings.asConfig(), activeRules);

    assertThat(logTester.logs(LoggerLevel.WARN)).containsExactly(
      "Property 'dump.old' is deprecated, use 'sonar.lits.dump.old' instead",
      "Property 'dump.new' is deprecated, use 'sonar.lits.dump.new' instead",
      "Property 'lits.differences' is deprecated, use 'sonar.lits.differences' instead"
    );
  }

  @Test
  public void path_must_be_absolute() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    thrown.expect(MessageException.class);
    thrown.expectMessage("Path must be absolute - check property 'sonar.lits.dump.old'");
    new IssuesChecker(settings.asConfig(), activeRules);
  }

  @Test
  public void differences_file_must_be_specified() {
    MapSettings settings = newCorrectSettings();
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, (String) null);
    thrown.expect(MessageException.class);
    thrown.expectMessage("Missing property 'sonar.lits.differences'");
    new IssuesChecker(settings.asConfig(), activeRules);
  }

  @Test
  public void should_fail_when_incorrect_severity() {
    Configuration settings = newCorrectSettings().asConfig();
    activeRules = new ActiveRulesBuilder()
      .create(RuleKey.of("repositoryKey", "ruleKey"))
      .setSeverity(RulePriority.BLOCKER.toString())
      .activate()
      .build();

    thrown.expect(MessageException.class);
    thrown.expectMessage("Rule 'repositoryKey:ruleKey' must be declared with severity INFO");
    new IssuesChecker(settings, activeRules);
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
    try {
      checker.save();
      fail("Expected exception");
    } catch (MessageException e) {
      assertThat(e.getMessage()).isEqualTo("Inactive rules: squid:S00103");
      assertThat(output).exists();
    }
  }

  @Test
  public void getPrevious_should_return_empty_list_when_no_output_directory() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "/file/does/not/exist");
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, "/file/does/not/exist");
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, "/file/does/not/exist");
    checker = new IssuesChecker(settings.asConfig(), activeRules);

    Map previous = checker.getPrevious();

    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs().get(0)).startsWith("Directory not found:");
    assertThat(previous).isEmpty();
  }

  @Test
  public void should_fail_when_missing_resources() {
    checker.missingResource("missing_resource");
    try {
      checker.save();
      fail("Expected exception");
    } catch (MessageException e) {
      assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: missing_resource");
      assertThat(output).exists();
    }
  }

  @Test
  public void multiple_missing_resources_show_all_names_in_error_message() {
    checker.missingResource("first_missing");
    checker.missingResource("second_missing");
    checker.missingResource("third_missing");
    try {
      checker.save();
      fail("Expected exception");
    } catch (MessageException e) {
      assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: first_missing, third_missing, second_missing");
      assertThat(output).exists();
    }
  }

  private MapSettings newCorrectSettings() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, new File("src/test/resources/").getAbsolutePath());
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, output.getAbsolutePath());
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, assertion.getAbsolutePath());
    return settings;
  }

}
