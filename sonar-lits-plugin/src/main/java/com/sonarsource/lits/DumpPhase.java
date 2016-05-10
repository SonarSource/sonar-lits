/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multiset;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.ActiveRule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Phase(name = Phase.Name.POST)
public class DumpPhase implements Sensor {

  private final IssuesChecker checker;
  private final RulesProfile profile;
  private final ResourcePerspectives resourcePerspectives;
  private final FileSystem fs;

  public DumpPhase(IssuesChecker checker, RulesProfile profile, ResourcePerspectives resourcePerspectives, FileSystem fs) {
    this.checker = checker;
    this.profile = profile;
    this.resourcePerspectives = resourcePerspectives;
    this.fs = fs;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return true;
  }

  @Override
  public void analyse(Project module, final SensorContext context) {
    // disable IssueFilter
    checker.disabled = true;
    Set<InputDir> inputDirs = new HashSet<>();
    for (InputFile inputFile : fs.inputFiles(fs.predicates().all())) {
      InputDir inputDir = fs.inputDir(inputFile.file());
      if (inputDir != null && !inputDirs.contains(inputDir)) {
        createMissingIssues(context.getResource(inputDir));
        inputDirs.add(inputDir);
      }
      createMissingIssues(context.getResource(inputFile));
    }
    save();
  }

  @VisibleForTesting
  protected void createMissingIssues(Resource resource) {
    Issuable issuable = resourcePerspectives.as(Issuable.class, resource);
    if (issuable == null) {
      // not indexed
      return;
    }
    Multiset<IssueKey> componentIssues = checker.getByComponentKey(resource.getEffectiveKey());
    if (!componentIssues.isEmpty()) {
      checker.disabled = true;
      for (IssueKey issueKey : checker.getByComponentKey(resource.getEffectiveKey())) {
        // missing issue => create
        checker.different = true;
        RuleKey ruleKey = RuleKey.parse(issueKey.ruleKey);
        ActiveRule activeRule = profile.getActiveRule(ruleKey.repository(), ruleKey.rule());
        if (activeRule == null) {
          // rule not active => skip it
          checker.inactiveRule(issueKey.ruleKey);
          continue;
        }
        checker.differences++;
        issuable.addIssue(issuable.newIssueBuilder()
          .ruleKey(ruleKey)
          .severity(Severity.BLOCKER)
          .line(issueKey.line == 0 ? null : issueKey.line)
          .message("Missing")
          .build());
      }
      checker.disabled = false;
      componentIssues.clear();
    }
  }

  @VisibleForTesting
  protected void save() {
    for (Map.Entry<String, Multiset<IssueKey>> entry : checker.getPrevious().entrySet()) {
      if (!entry.getValue().isEmpty()) {
        checker.different = true;
        checker.missingResource(entry.getKey());
      }
    }
    checker.save();
  }

}
