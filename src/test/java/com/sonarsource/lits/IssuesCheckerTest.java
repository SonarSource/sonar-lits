/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueHandler;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.SonarException;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private RulesProfile profile = mock(RulesProfile.class);
  private IssueHandler.Context issueHandlerContext = mock(IssueHandler.Context.class);
  private Resource resource = mock(Resource.class);
  private DecoratorContext decoratorContext = mock(DecoratorContext.class);
  private IssuesChecker checker;
  private File output;

  @Before
  public void setup() throws Exception {
    output = new File(temporaryFolder.newFolder(), "dump.json");
    Settings settings = new Settings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, new File("src/test/project/differences.json").getAbsolutePath());
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, output.getAbsolutePath());
    checker = new IssuesChecker(settings, profile);
    when(profile.getActiveRule(anyString(), anyString())).thenReturn(new ActiveRule(null, null, RulePriority.BLOCKER));
  }

  @Test
  public void path_must_be_specified() {
    Settings settings = new Settings();
    thrown.expect(SonarException.class);
    thrown.expectMessage("Missing property 'dump.old'");
    new IssuesChecker(settings, profile);
  }

  @Test
  public void path_must_be_absolute() {
    Settings settings = new Settings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    thrown.expect(SonarException.class);
    thrown.expectMessage("Path must be absolute - check property 'dump.old'");
    new IssuesChecker(settings, profile);
  }

  @Test
  public void shouldExecuteOnProject() {
    assertThat(checker.shouldExecuteOnProject(null)).isTrue();
  }

  @Test
  public void should_report_old_issue() {
    Issue issue = new DefaultIssue().setComponentKey("project:[default].Example").setRuleKey(RuleKey.of("squid", "S00103")).setLine(1);
    when(issueHandlerContext.issue()).thenReturn(issue);
    checker.onIssue(issueHandlerContext);
    verify(issueHandlerContext).setSeverity(Severity.INFO);
  }

  @Test
  public void should_report_new_issue() {
    Issue issue = new DefaultIssue().setComponentKey("componentKey").setRuleKey(RuleKey.of("repository", "rule"));
    when(issueHandlerContext.issue()).thenReturn(issue);
    checker.onIssue(issueHandlerContext);
    verify(issueHandlerContext).setSeverity(Severity.CRITICAL);
  }

  @Test
  public void should_report_missing_issues() {
    when(resource.getScope()).thenReturn(Scopes.FILE);
    when(resource.getEffectiveKey()).thenReturn("project:[default].Example");
    checker.decorate(resource, decoratorContext);
    verify(decoratorContext, times(2)).saveViolation(any(Violation.class));
  }

  @Test
  public void should_not_report_missing_issues() {
    when(resource.getScope()).thenReturn(Scopes.PROGRAM_UNIT);
    when(resource.getEffectiveKey()).thenReturn("project:[default].Example");
    checker.decorate(resource, decoratorContext);
    verifyZeroInteractions(decoratorContext);
  }

  @Test
  public void should_not_save_when_no_differences() {
    when(resource.getScope()).thenReturn(Scopes.PROJECT);
    when(resource.getEffectiveKey()).thenReturn("project");
    checker.decorate(resource, decoratorContext);
    verifyZeroInteractions(decoratorContext);
    assertThat(output).doesNotExist();
  }

  @Test
  public void should_save_when_differences() {
    when(resource.getScope()).thenReturn(Scopes.FILE);
    when(resource.getEffectiveKey()).thenReturn("project:[default].Example");
    checker.decorate(resource, decoratorContext);
    when(resource.getScope()).thenReturn(Scopes.PROJECT);
    when(resource.getEffectiveKey()).thenReturn("project");
    checker.decorate(resource, decoratorContext);
    assertThat(output).exists();
  }

}
