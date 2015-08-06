/*
 * Sonar LITS Plugin
 * Copyright (C) 2013 SonarSource
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
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
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.batch.IssueFilter;
import org.sonar.api.issue.batch.IssueFilterChain;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.utils.MessageException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IssuesChecker implements IssueFilter {

  private static final Logger LOG = LoggerFactory.getLogger(IssuesChecker.class);

  private final File oldDumpFile;
  private final File newDumpFile;
  private final File differencesFile;

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
  int differences = 0;

  public IssuesChecker(Settings settings, RulesProfile profile) {
    oldDumpFile = getFile(settings, LITSPlugin.OLD_DUMP_PROPERTY);
    newDumpFile = getFile(settings, LITSPlugin.NEW_DUMP_PROPERTY);
    differencesFile = settings.hasKey(LITSPlugin.DIFFERENCES_PROPERTY) ? getFile(settings, LITSPlugin.DIFFERENCES_PROPERTY) : null;
    for (ActiveRule activeRule : profile.getActiveRules()) {
      if (!activeRule.getSeverity().toString().equals(Severity.INFO)) {
        throw MessageException.of("Rule '" + activeRule.getRepositoryKey() + ":" + activeRule.getRuleKey() + "' must be declared with severity INFO");
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
  public boolean accept(Issue issue, IssueFilterChain chain) {
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
      differences++;
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

  private static void forceDelete(File file) {
    if (file.exists()) {
      try {
        FileUtils.forceDelete(file);
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }
  }

  void save() {
    forceDelete(newDumpFile);
    List<String> messages = new ArrayList<>();
    MessageException exception = null;
    if (different) {
      LOG.info("Saving " + newDumpFile);
      Dump.save(dump, newDumpFile);
      messages.add("Issues differences: " + differences);
    } else {
      LOG.info("No differences in issues");
    }
    if (!inactiveRules.isEmpty()) {
      String message = "Inactive rules: " + Joiner.on(", ").join(inactiveRules);
      messages.add(message);
      exception = MessageException.of(message);
    }
    if (!missingResources.isEmpty()) {
      String message = "Missing resources: " + Joiner.on(", ").join(missingResources);
      messages.add(message);
      exception = MessageException.of(message);
    }
    if (differencesFile != null) {
      forceDelete(differencesFile);
      try {
        differencesFile.createNewFile();
        Files.write(differencesFile.toPath(), Joiner.on("\n").join(messages).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }
    if (exception != null) {
      throw exception;
    }
  }

  private static File getFile(Settings settings, String property) {
    String path = settings.getString(property);
    if (path == null) {
      throw MessageException.of("Missing property '" + property + "'");
    }
    File file = new File(path);
    if (!file.isAbsolute()) {
      throw MessageException.of("Path must be absolute - check property '" + property + "'" );
    }
    return file;
  }

}
