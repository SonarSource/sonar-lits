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
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
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
import org.sonar.api.utils.SonarException;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private RulesProfile profile = mock(RulesProfile.class);
  private ResourcePerspectives resourcePerspectives = mock(ResourcePerspectives.class);
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
    checker = new IssuesChecker(settings, profile, resourcePerspectives);
    when(profile.getActiveRule(anyString(), anyString())).thenReturn(new ActiveRule(null, null, RulePriority.BLOCKER));
  }

  @Test
  public void path_must_be_specified() {
    Settings settings = new Settings();
    thrown.expect(SonarException.class);
    thrown.expectMessage("Missing property 'dump.old'");
    new IssuesChecker(settings, profile, resourcePerspectives);
  }

  @Test
  public void path_must_be_absolute() {
    Settings settings = new Settings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    thrown.expect(SonarException.class);
    thrown.expectMessage("Path must be absolute - check property 'dump.old'");
    new IssuesChecker(settings, profile, resourcePerspectives);
  }

  @Test
  public void shouldExecuteOnProject() {
    assertThat(checker.shouldExecuteOnProject(null)).isTrue();
  }

  @Test
  public void should_report_old_issue() {
    Issue issue = new DefaultIssue().setSeverity(Severity.INFO).setComponentKey("project:[default].Example").setRuleKey(RuleKey.of("squid", "S00103")).setLine(1);
    when(issueHandlerContext.issue()).thenReturn(issue);
    checker.onIssue(issueHandlerContext);
    verify(issueHandlerContext).issue();
    verifyNoMoreInteractions(issueHandlerContext);
  }

  @Test
  public void should_report_new_issue() {
    Issue issue = new DefaultIssue().setComponentKey("componentKey").setRuleKey(RuleKey.of("repository", "rule"));
    when(issueHandlerContext.issue()).thenReturn(issue);
    checker.onIssue(issueHandlerContext);
    verify(issueHandlerContext).issue();
    verify(issueHandlerContext).setSeverity(Severity.CRITICAL);
    verifyNoMoreInteractions(issueHandlerContext);
  }

  @Test
  public void should_report_missing_issues() {
    when(resource.getScope()).thenReturn(Scopes.FILE);
    when(resource.getEffectiveKey()).thenReturn("project:[default].Example");

    Issuable.IssueBuilder issueBuilder = mockIssueBuilder();
    Issue issue = mock(Issue.class);
    when(issueBuilder.build()).thenReturn(issue);
    Issuable issuable = mock(Issuable.class);
    when(issuable.newIssueBuilder()).thenReturn(issueBuilder);
    when(resourcePerspectives.as(Issuable.class, resource)).thenReturn(issuable);

    checker.decorate(resource, decoratorContext);

    verify(issueBuilder, times(2)).message("Missing");
    verify(issueBuilder, times(2)).severity(Severity.BLOCKER);
    verify(issuable, times(2)).addIssue(issue);
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
    Issuable.IssueBuilder issueBuilder = mockIssueBuilder();
    Issuable issuable = mock(Issuable.class);
    when(issuable.newIssueBuilder()).thenReturn(issueBuilder);
    when(resourcePerspectives.as(Issuable.class, resource)).thenReturn(issuable);

    when(resource.getScope()).thenReturn(Scopes.FILE);
    when(resource.getEffectiveKey()).thenReturn("project:[default].Example");
    checker.decorate(resource, decoratorContext);
    when(resource.getScope()).thenReturn(Scopes.PROJECT);
    when(resource.getEffectiveKey()).thenReturn("project");
    checker.decorate(resource, decoratorContext);

    assertThat(output).exists();
  }

  private Issuable.IssueBuilder mockIssueBuilder() {
    Issuable.IssueBuilder issueBuilder = mock(Issuable.IssueBuilder.class);
    when(issueBuilder.ruleKey(any(RuleKey.class))).thenReturn(issueBuilder);
    when(issueBuilder.severity(any(String.class))).thenReturn(issueBuilder);
    when(issueBuilder.line(any(Integer.class))).thenReturn(issueBuilder);
    when(issueBuilder.message(any(String.class))).thenReturn(issueBuilder);
    return issueBuilder;
  }

}
