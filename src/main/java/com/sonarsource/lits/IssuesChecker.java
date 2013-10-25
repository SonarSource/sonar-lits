/*
 * Copyright (C) 2013 SonarSource SA
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
import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DecoratorBarriers;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueHandler;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;
import org.sonar.api.rule.RuleKey;
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
public class IssuesChecker implements IssueHandler, Decorator {

  private static final Logger LOG = LoggerFactory.getLogger(IssuesChecker.class);

  private final RulesProfile profile;
  private final ResourcePerspectives resourcePerspectives;
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

  private boolean different = false;

  public IssuesChecker(Settings settings, RulesProfile profile, ResourcePerspectives resourcePerspectives) {
    oldDumpFile = getFile(settings, LITSPlugin.OLD_DUMP_PROPERTY);
    newDumpFile = getFile(settings, LITSPlugin.NEW_DUMP_PROPERTY);
    this.profile = profile;
    this.resourcePerspectives = resourcePerspectives;
    for (ActiveRule activeRule : profile.getActiveRules()) {
      if (activeRule.getSeverity() != RulePriority.INFO) {
        throw new SonarException("Rule '" + activeRule.getRepositoryKey() + ":" + activeRule.getRuleKey() +
          "' must be declared with severity INFO in profile '" + profile.getName() + "'");
      }
    }
  }

  private Multiset<IssueKey> getByComponentKey(String componentKey) {
    if (previous == null) {
      if (!oldDumpFile.isDirectory()) {
        LOG.warn("Directory not found: " + oldDumpFile);
        previous = ImmutableMap.of();
      } else {
        LOG.info("Loading " + oldDumpFile);
        previous = Dump.load(oldDumpFile);
      }
    }
    Multiset<IssueKey> issueKeys = previous.get(componentKey);
    if (issueKeys == null) {
      issueKeys = ImmutableMultiset.of();
    }
    return issueKeys;
  }

  public void onIssue(Context context) {
    Issue issue = context.issue();
    IssueKey issueKey = new IssueKey(issue.componentKey(), issue.ruleKey().toString(), issue.line());
    dump.add(issueKey);
    Multiset<IssueKey> componentIssues = getByComponentKey(issueKey.componentKey);
    if (componentIssues.contains(issueKey)) {
      // old issue => supposed that it was created with severity from profile
      componentIssues.remove(issueKey);
      Preconditions.checkState(Severity.INFO.equals(issue.severity()));
    } else {
      // new issue => increase severity
      context.setSeverity(Severity.CRITICAL);
      different = true;
    }
  }

  public void decorate(Resource resource, DecoratorContext context) {
    if (Scopes.isHigherThanOrEquals(resource, Scopes.FILE)) {
      Issuable issuable = resourcePerspectives.as(Issuable.class, resource);
      for (IssueKey issueKey : getByComponentKey(resource.getEffectiveKey())) {
        // missing issue => create
        different = true;
        RuleKey ruleKey = RuleKey.parse(issueKey.ruleKey);
        ActiveRule activeRule = profile.getActiveRule(ruleKey.repository(), ruleKey.rule());
        if (activeRule == null) {
          // rule not active => skip it
          inactiveRules.add(issueKey.ruleKey);
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
    }
  }

  public boolean shouldExecuteOnProject(Project project) {
    return true;
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
