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

import java.io.File;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.utils.MessageException;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @org.junit.Rule
  public ExpectedException thrown = ExpectedException.none();

  @org.junit.Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private RulesProfile profile = mock(RulesProfile.class);
  private IssuesChecker checker;
  private File output;
  private File assertion;

  @Before
  public void setup() throws Exception {
    output = new File(temporaryFolder.newFolder(), "dump");
    assertion = new File(temporaryFolder.newFolder(), "assertion");
    Configuration settings = newCorrectSettings().asConfig();
    checker = new IssuesChecker(settings, profile);
  }

  @Test
  public void path_must_be_specified() {
    Configuration settings = new MapSettings().asConfig();
    thrown.expect(MessageException.class);
    thrown.expectMessage("Missing property 'dump.old'");
    new IssuesChecker(settings, profile);
  }

  @Test
  public void path_must_be_absolute() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    thrown.expect(MessageException.class);
    thrown.expectMessage("Path must be absolute - check property 'dump.old'");
    new IssuesChecker(settings.asConfig(), profile);
  }

  @Test
  public void differences_file_must_be_specified() {
    MapSettings settings = newCorrectSettings();
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, (String) null);
    thrown.expect(MessageException.class);
    thrown.expectMessage("Missing property 'lits.differences'");
    new IssuesChecker(settings.asConfig(), profile);
  }

  @Test
  public void should_fail_when_incorrect_severity() {
    Configuration settings = newCorrectSettings().asConfig();
    ActiveRule activeRule = new ActiveRule(profile, Rule.create("repositoryKey", "ruleKey"), RulePriority.BLOCKER);
    when(profile.getActiveRules()).thenReturn(Arrays.asList(activeRule));
    when(profile.getName()).thenReturn("profileName");

    thrown.expect(MessageException.class);
    thrown.expectMessage("Rule 'repositoryKey:ruleKey' must be declared with severity INFO");
    new IssuesChecker(settings, profile);
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

    assertThat(checker.accept(issue, null)).isTrue();
    checker.save();

    assertThat(output).exists();
  }

  @Test
  public void should_not_save_when_disabled() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    checker.disabled = true;
    assertThat(checker.accept(issue, null)).isTrue();
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

    assertThat(checker.accept(issue, null)).isFalse();
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
  public void should_fail_when_missing_resources() {
    checker.missingResource("missing_resource");
    try {
      checker.save();
      fail("Expected exception");
    } catch (MessageException e) {
      assertThat(e.getMessage()).isEqualTo("Missing resources: missing_resource");
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
