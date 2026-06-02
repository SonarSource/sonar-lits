/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2025 SonarSource Sàrl
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

import com.google.common.collect.Multiset;
import java.util.Map;
import org.sonar.api.batch.postjob.PostJob;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.PostJobDescriptor;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.rule.RuleKey;

// must be public for SQ picocontainer
public class DumpPostJob implements PostJob {

  private final IssuesChecker checker;
  private final ActiveRules activeRules;

  // must be public for SQ picocontainer
  public DumpPostJob(IssuesChecker checker, ActiveRules activeRules) {
    this.checker = checker;
    this.activeRules = activeRules;
  }

  @Override
  public void describe(PostJobDescriptor descriptor) {
    descriptor.name("LITS");
  }

  @Override
  public void execute(PostJobContext context) {
    for (Map.Entry<String, Multiset<IssueKey>> entry : checker.getPrevious().entrySet()) {
      String componentKey = entry.getKey();
      Multiset<IssueKey> issues = entry.getValue();
      if (!issues.isEmpty()) {
        if (!checker.isKnownResource(componentKey)) {
          checker.missingResource(componentKey);
        } else {
          countDifferences(issues);
          issues.clear();
        }
      }
    }
    checker.save();
  }

  private void countDifferences(Multiset<IssueKey> issues) {
    checker.different = true;
    for (IssueKey issueKey : issues) {
      RuleKey ruleKey = RuleKey.parse(issueKey.ruleKey);
      ActiveRule activeRule = activeRules.find(ruleKey);
      if (activeRule == null) {
        checker.inactiveRule(issueKey.ruleKey);
      } else {
        checker.differences++;
      }
    }
  }
}
