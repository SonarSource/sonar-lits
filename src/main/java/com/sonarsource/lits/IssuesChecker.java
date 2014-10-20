/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.DecoratorBarriers;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueFilter;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.SonarException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DependsUpon(DecoratorBarriers.ISSUES_TRACKED)
public class IssuesChecker implements IssueFilter {

  private static final Logger LOG = LoggerFactory.getLogger(IssuesChecker.class);

  private final File oldDumpFile, newDumpFile;

  /**
   * Previous findings indexed by {@link IssueKey#componentKey}.
   */
  private Map<String, Multiset<IssueKey>> previous;

  /**
   * New findings.
   */
  private final List<IssueKey> dump = Lists.newArrayList();

  private final Set<String> inactiveRules = Sets.newHashSet();
  private final Set<String> missingResources = Sets.newHashSet();

  boolean different = false;
  boolean disabled = false;

  public IssuesChecker(Settings settings, RulesProfile profile) {
    oldDumpFile = getFile(settings, LITSPlugin.OLD_DUMP_PROPERTY);
    newDumpFile = getFile(settings, LITSPlugin.NEW_DUMP_PROPERTY);
    for (ActiveRule activeRule : profile.getActiveRules()) {
      if (activeRule.getSeverity() != RulePriority.INFO) {
        throw new SonarException("Rule '" + activeRule.getRepositoryKey() + ":" + activeRule.getRuleKey() + "' must be declared with severity INFO");
      }
    }
  }

  Map<String, Multiset<IssueKey>> getPrevious() {
    if (previous == null) {
      if (!oldDumpFile.isDirectory()) {
        LOG.warn("Directory not found: " + oldDumpFile);
        previous = ImmutableMap.of();
      } else {
        LOG.info("Loading " + oldDumpFile);
        previous = Dump.load(oldDumpFile);
      }
    }
    return previous;
  }

  Multiset<IssueKey> getByComponentKey(String componentKey) {
    Multiset<IssueKey> issueKeys = getPrevious().get(componentKey);
    if (issueKeys == null) {
      issueKeys = ImmutableMultiset.of();
    }
    return issueKeys;
  }

  @Override
  public boolean accept(Issue issue) {
    if (disabled) {
      return true;
    }

    IssueKey issueKey = new IssueKey(issue.componentKey(), issue.ruleKey().toString(), issue.line());
    dump.add(issueKey);
    Multiset<IssueKey> componentIssues = getByComponentKey(issueKey.componentKey);
    if (componentIssues.contains(issueKey)) {
      // old issue => no need to persist
      componentIssues.remove(issueKey);
      Preconditions.checkState(Severity.INFO.equals(issue.severity()));
      return false;
    } else {
      // new issue => persist
      different = true;
      return true;
    }
  }

  public void inactiveRule(String ruleKey) {
    different = true;
    inactiveRules.add(ruleKey);
  }

  public void missingResource(String componentKey) {
    different = true;
    missingResources.add(componentKey);
  }

  void save() {
    if (newDumpFile.exists()) {
      try {
        FileUtils.forceDelete(newDumpFile);
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }
    if (different) {
      LOG.info("Saving " + newDumpFile);
      Dump.save(dump, newDumpFile);
    } else {
      LOG.info("No differences in issues");
    }
    if (!inactiveRules.isEmpty()) {
      throw new SonarException("Inactive rules: " + Joiner.on(", ").join(inactiveRules));
    }
    if (!missingResources.isEmpty()) {
      throw new SonarException("Missing resources: " + Joiner.on(", ").join(missingResources));
    }
  }

  private static File getFile(Settings settings, String property) {
    String path = settings.getString(property);
    if (path == null) {
      throw new SonarException("Missing property '" + property + "'");
    }
    File file = new File(path);
    if (!file.isAbsolute()) {
      throw new SonarException("Path must be absolute - check property '" + property + "'");
    }
    return file;
  }

}
