/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.HashMultiset;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
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

  private Resource mockProject() {
    Resource resource = mock(Resource.class);
    when(resource.getScope()).thenReturn(Scopes.PROJECT);
    when(resource.getEffectiveKey()).thenReturn("project");
    return resource;
  }

}
