/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.ActiveRule;

public class DumpPhase implements Decorator {

  private final IssuesChecker checker;
  private final RulesProfile profile;
  private final ResourcePerspectives resourcePerspectives;

  public DumpPhase(IssuesChecker checker, RulesProfile profile, ResourcePerspectives resourcePerspectives) {
    this.checker = checker;
    this.profile = profile;
    this.resourcePerspectives = resourcePerspectives;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return true;
  }

  @Override
  public void decorate(Resource resource, DecoratorContext context) {
    if (Scopes.isHigherThanOrEquals(resource, Scopes.FILE)) {
      checker.dumpPhase = true;

      Issuable issuable = resourcePerspectives.as(Issuable.class, resource);
      for (IssueKey issueKey : checker.getByComponentKey(resource.getEffectiveKey())) {
        // missing issue => create
        checker.different = true;
        RuleKey ruleKey = RuleKey.parse(issueKey.ruleKey);
        ActiveRule activeRule = profile.getActiveRule(ruleKey.repository(), ruleKey.rule());
        if (activeRule == null) {
          // rule not active => skip it
          checker.inactiveRules.add(issueKey.ruleKey);
          continue;
        }
        issuable.addIssue(issuable.newIssueBuilder()
          .ruleKey(ruleKey)
          .severity(Severity.BLOCKER)
          .line(issueKey.line == 0 ? null : issueKey.line)
          .message("Missing")
          .build());
      }
    }
    if (Scopes.isProject(resource)) {
      checker.save();
    }
  }

}
