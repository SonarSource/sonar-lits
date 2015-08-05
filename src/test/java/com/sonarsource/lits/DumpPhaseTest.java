/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;

import java.util.Map;

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
  private FileSystem fs;

  @Before
  public void setup() {
    checker = mock(IssuesChecker.class);
    rulesProfile = mock(RulesProfile.class);
    resourcePerspectives = mock(ResourcePerspectives.class);
    fs = mock(FileSystem.class);
    FilePredicates filePredicates = mock(FilePredicates.class);
    when(fs.predicates()).thenReturn(filePredicates);
    decorator = new DumpPhase(checker, rulesProfile, resourcePerspectives, fs);
  }

  @Test
  public void should_execute_on_any_project() {
    assertThat(decorator.shouldExecuteOnProject(null)).isTrue();
  }

  @Test
  public void should_save_on_project() {
    when(checker.getByComponentKey(anyString())).thenReturn(HashMultiset.<IssueKey>create());

    decorator.save();

    verify(checker).save();
  }

  @Test
  public void should_report_missing_issues() {
    Project project = new Project("project", null, "project");

    Multiset<IssueKey> issues = HashMultiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    when(checker.getByComponentKey(anyString())).thenReturn(issues);

    ActiveRule activeRule = mock(ActiveRule.class);
    when(rulesProfile.getActiveRule("squid", "S00103")).thenReturn(activeRule);

    Issuable.IssueBuilder issueBuilder = mockIssueBuilder();
    Issuable issuable = mock(Issuable.class);
    when(issuable.newIssueBuilder()).thenReturn(issueBuilder);
    when(resourcePerspectives.as(eq(Issuable.class), any(Resource.class))).thenReturn(issuable);

    SensorContext sensorContext = mock(SensorContext.class);
    Resource resource = mock(Resource.class);
    when(sensorContext.getResource(any(InputPath.class))).thenReturn(resource);

    InputFile inputFile = mock(InputFile.class);
    when(fs.inputFiles(any(FilePredicate.class))).thenReturn(ImmutableList.of(inputFile));
    decorator.analyse(project, sensorContext);

    verify(issuable).addIssue(any(Issue.class));
  }

  @Test
  public void should_report_missing_files() {
    Map<String, Multiset<IssueKey>> previous = Maps.newHashMap();
    Multiset<IssueKey> issues = HashMultiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    previous.put("missing", issues);
    when(checker.getPrevious()).thenReturn(previous);
    when(checker.getByComponentKey(anyString())).thenReturn(ImmutableMultiset.<IssueKey>of());

    decorator.save();

    verify(checker).missingResource("missing");
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
