/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multiset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;

// must be public for SQ picocontainer
@Phase(name = Phase.Name.POST)
public class DumpPhase implements Sensor {

  private final IssuesChecker checker;
  private final ActiveRules activeRules;

  // must be public for SQ picocontainer
  public DumpPhase(IssuesChecker checker, ActiveRules activeRules) {
    this.checker = checker;
    this.activeRules = activeRules;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("LITS")
      .global();
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
  private void createMissingIssues(SensorContext context, InputComponent resource) {
    Multiset<IssueKey> componentIssues = checker.getByComponentKey(resource.key());
    if (!componentIssues.isEmpty()) {
      checker.disabled = true;
      for (IssueKey issueKey : checker.getByComponentKey(resource.key())) {
        // missing issue => create
        checker.different = true;
        RuleKey ruleKey = RuleKey.parse(issueKey.ruleKey);
        ActiveRule activeRule = activeRules.find(ruleKey);
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
  void save() {
    for (Map.Entry<String, Multiset<IssueKey>> entry : checker.getPrevious().entrySet()) {
      if (!entry.getValue().isEmpty()) {
        checker.different = true;
        checker.missingResource(entry.getKey());
      }
    }
    checker.save();
  }

}
