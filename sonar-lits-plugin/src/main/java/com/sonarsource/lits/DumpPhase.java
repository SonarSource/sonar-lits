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
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Phase(name = Phase.Name.POST)
public class DumpPhase implements Sensor {

  private final IssuesChecker checker;
  private final RulesProfile profile;

  public DumpPhase(IssuesChecker checker, RulesProfile profile) {
    this.checker = checker;
    this.profile = profile;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
  }

  @Override
  public void execute(SensorContext context) {
    // disable IssueFilter
    checker.disabled = true;
    Set<InputDir> inputDirs = new HashSet<>();
    FileSystem fs = context.fileSystem();
    for (InputFile inputFile : fs.inputFiles(fs.predicates().all())) {
      InputDir inputDir = fs.inputDir(inputFile.file());
      if (inputDir != null && !inputDirs.contains(inputDir)) {
        createMissingIssues(context, inputDir);
        inputDirs.add(inputDir);
      }
      createMissingIssues(context, inputFile);
    }
    save();
  }

  @VisibleForTesting
  protected void createMissingIssues(SensorContext context, InputComponent resource) {
    Multiset<IssueKey> componentIssues = checker.getByComponentKey(resource.key());
    if (!componentIssues.isEmpty()) {
      checker.disabled = true;
      for (IssueKey issueKey : checker.getByComponentKey(resource.key())) {
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
        NewIssue newIssue = context.newIssue();
        NewIssueLocation location = newIssue.newLocation()
          .on(resource)
          .message("Missing");
        if (issueKey.line != 0) {
          location.at(((InputFile) resource).selectLine(issueKey.line));
        }
        newIssue
          .forRule(ruleKey)
          .overrideSeverity(Severity.BLOCKER)
          .at(location)
          .save();
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
