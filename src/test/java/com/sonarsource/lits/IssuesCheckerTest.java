/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.MessageException;

import java.io.File;
import java.util.Arrays;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @org.junit.Rule
  public ExpectedException thrown = ExpectedException.none();

  @org.junit.Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private RulesProfile profile = mock(RulesProfile.class);
  private DecoratorContext decoratorContext = mock(DecoratorContext.class);
  private IssuesChecker checker;
  private File output;

  @Before
  public void setup() throws Exception {
    output = new File(temporaryFolder.newFolder(), "dump");
    Settings settings = newCorrectSettings();
    checker = new IssuesChecker(settings, profile);
  }

  @Test
  public void path_must_be_specified() {
    Settings settings = new Settings();
    thrown.expect(MessageException.class);
    thrown.expectMessage("Missing property 'dump.old'");
    new IssuesChecker(settings, profile);
  }

  @Test
  public void path_must_be_absolute() {
    Settings settings = new Settings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    thrown.expect(MessageException.class);
    thrown.expectMessage("Path must be absolute - check property 'dump.old'");
    new IssuesChecker(settings, profile);
  }

  @Test
  public void should_fail_when_incorrect_severity() {
    Settings settings = newCorrectSettings();
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

    verifyZeroInteractions(decoratorContext);
    assertThat(output).doesNotExist();
  }

  @Test
  public void should_save_when_differences() {
    Issue issue = mock(Issue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    assertThat(checker.accept(issue)).isTrue();
    checker.save();

    assertThat(output).exists();
  }

  @Test
  public void should_hide_old_issues() {
    Issue issue = mock(Issue.class);
    when(issue.componentKey()).thenReturn("project:src/Example.java");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));
    when(issue.line()).thenReturn(1);
    when(issue.severity()).thenReturn("INFO");

    assertThat(checker.accept(issue)).isFalse();
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

  private Settings newCorrectSettings() {
    Settings settings = new Settings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, new File("src/test/project/dumps/differences/").getAbsolutePath());
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, output.getAbsolutePath());
    return settings;
  }

}
