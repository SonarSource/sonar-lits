/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DumpPhaseTest {

  private IssuesChecker checker;
  private RulesProfile rulesProfile;
  private ResourcePerspectives resourcePerspectives;
  private DumpPhase decorator;

  @Before
  public void setup() {
    checker = mock(IssuesChecker.class);
    rulesProfile = mock(RulesProfile.class);
    resourcePerspectives = mock(ResourcePerspectives.class);
    decorator = new DumpPhase(checker, rulesProfile, resourcePerspectives);
  }

  @Test
  public void should_execute_on_any_project() {
    assertThat(decorator.shouldExecuteOnProject(null)).isTrue();
  }

  @Test
  public void should_save_on_project() {
    Resource resource = mockProject();
    DecoratorContext context = mock(DecoratorContext.class);

    when(checker.getByComponentKey(anyString())).thenReturn(HashMultiset.<IssueKey>create());

    decorator.decorate(resource, context);

    verify(checker).save();
  }

  @Test
  public void should_report_missing_issues() {
    Resource resource = mockProject();
    DecoratorContext context = mock(DecoratorContext.class);

    Multiset<IssueKey> issues = HashMultiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    when(checker.getByComponentKey(anyString())).thenReturn(issues);

    ActiveRule activeRule = mock(ActiveRule.class);
    when(rulesProfile.getActiveRule("squid", "S00103")).thenReturn(activeRule);

    Issuable.IssueBuilder issueBuilder = mockIssueBuilder();
    Issuable issuable = mock(Issuable.class);
    when(issuable.newIssueBuilder()).thenReturn(issueBuilder);
    when(resourcePerspectives.as(eq(Issuable.class), any(Resource.class))).thenReturn(issuable);

    decorator.decorate(resource, context);

    verify(issuable).addIssue(any(Issue.class));
  }

  private Issuable.IssueBuilder mockIssueBuilder() {
    Issuable.IssueBuilder issueBuilder = mock(Issuable.IssueBuilder.class);
    when(issueBuilder.ruleKey(any(RuleKey.class))).thenReturn(issueBuilder);
    when(issueBuilder.severity(any(String.class))).thenReturn(issueBuilder);
    when(issueBuilder.line(any(Integer.class))).thenReturn(issueBuilder);
    when(issueBuilder.message(any(String.class))).thenReturn(issueBuilder);
    return issueBuilder;
  }

  private Resource mockProject() {
    Resource resource = mock(Resource.class);
    when(resource.getScope()).thenReturn(Scopes.PROJECT);
    when(resource.getEffectiveKey()).thenReturn("project");
    return resource;
  }

}
