/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.config.Configuration;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilter;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

// must be public for SQ picocontainer
public class IssuesChecker implements IssueFilter {

  private static final Logger LOG = Loggers.get(IssuesChecker.class);

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
  private final List<IssueKey> dump = new ArrayList<>();

  private final Set<String> inactiveRules = new HashSet<>();
  private final Set<String> missingResources = new HashSet<>();

  boolean different = false;
  boolean disabled = false;
  int differences = 0;

  // must be public for SQ picocontainer
  public IssuesChecker(Configuration settings, ActiveRules activerules) {
    oldDumpFile = getFile(settings, LITSPlugin.OLD_DUMP_PROPERTY);
    newDumpFile = getFile(settings, LITSPlugin.NEW_DUMP_PROPERTY);
    differencesFile = getFile(settings, LITSPlugin.DIFFERENCES_PROPERTY);
    for (ActiveRule activeRule : activerules.findAll()) {
      if (!activeRule.severity().equals(Severity.INFO)) {
        RuleKey ruleKey = activeRule.ruleKey();
        throw MessageException.of("Rule '" + ruleKey.repository() + ":" + ruleKey.rule() + "' must be declared with severity INFO");
      }
    }
  }

  Map<String, Multiset<IssueKey>> getPrevious() {
    if (previous == null) {
      if (!oldDumpFile.isDirectory()) {
        LOG.warn("Directory not found: {}", oldDumpFile);
        previous = ImmutableMap.of();
      } else {
        LOG.info("Loading {}", oldDumpFile);
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
  public boolean accept(FilterableIssue issue, IssueFilterChain chain) {
    if (!chain.accept(issue)) {
      return false;
    }
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

  void inactiveRule(String ruleKey) {
    different = true;
    inactiveRules.add(ruleKey);
  }

  void missingResource(String componentKey) {
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
      LOG.info("Saving {}", newDumpFile);
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
      String message = "Files listed in Expected directory were not analyzed: " + Joiner.on(", ").join(missingResources);
      messages.add(message);
      exception = MessageException.of(message);
    }
    forceDelete(differencesFile);
    try {
      differencesFile.createNewFile();
      Files.write(differencesFile.toPath(), Joiner.on("\n").join(messages).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
    if (exception != null) {
      throw exception;
    }
  }

  private static File getFile(Configuration settings, String property) {
    String path = settings.get(property).orElseThrow(() -> MessageException.of("Missing property '" + property + "'"));
    File file = new File(path);
    if (!file.isAbsolute()) {
      throw MessageException.of("Path must be absolute - check property '" + property + "'");
    }
    return file;
  }

}
