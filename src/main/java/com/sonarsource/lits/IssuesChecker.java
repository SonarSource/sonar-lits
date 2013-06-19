/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DecoratorBarriers;
import org.sonar.api.batch.DecoratorContext;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.IssueHandler;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.Scopes;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.SonarException;

import java.io.File;
import java.util.List;
import java.util.Map;

@DependsUpon(DecoratorBarriers.ISSUES_TRACKED)
public class IssuesChecker implements IssueHandler, Decorator {

  private static final Logger LOG = LoggerFactory.getLogger(IssuesChecker.class);

  /**
   * Previous findings indexed by {@link IssueKey#componentKey}.
   */
  private Map<String, Multiset<IssueKey>> previous;

  /**
   * New findings.
   */
  private final List<IssueKey> dump = Lists.newArrayList();

  private boolean different = false;

  public Multiset<IssueKey> getByComponentKey(String componentKey) {
    if (previous == null) {
      if (!oldDumpFile.isFile()) {
        LOG.warn("File not found: " + oldDumpFile);
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

  private final File oldDumpFile, newDumpFile;
  private final RuleFinder ruleFinder;

  public IssuesChecker(Settings settings, RuleFinder ruleFinder) {
    oldDumpFile = getFile(settings, LITSPlugin.OLD_DUMP_PROPERTY);
    newDumpFile = getFile(settings, LITSPlugin.NEW_DUMP_PROPERTY);
    this.ruleFinder = ruleFinder;
  }

  public void onIssue(Context context) {
    Issue issue = context.issue();
    IssueKey issueKey = new IssueKey(issue.componentKey(), issue.ruleKey().toString(), issue.line());
    dump.add(issueKey);
    Multiset<IssueKey> componentIssues = getByComponentKey(issueKey.componentKey);
    if (componentIssues.contains(issueKey)) {
      componentIssues.remove(issueKey);
      // old issue => decrease severity
      context.setSeverity(Severity.INFO);
    } else {
      // new issue => increase severity
      context.setSeverity(Severity.CRITICAL);
      different = true;
    }
  }

  public void decorate(Resource resource, DecoratorContext context) {
    if (Scopes.isHigherThanOrEquals(resource, Scopes.FILE)) {
      for (IssueKey issueKey : getByComponentKey(resource.getEffectiveKey())) {
        // missing issue => create, severity will be taken from profile
        Rule rule = ruleFinder.findByKey(RuleKey.parse(issueKey.ruleKey));
        context.saveViolation(Violation.create(rule, resource)
          .setLineId(issueKey.line)
          .setMessage("Missing"));
        different = true;
      }
    }
    if (Scopes.isProject(resource)) {
      if (different) {
        LOG.info("Saving " + newDumpFile);
        Dump.save(dump, newDumpFile);
      } else {
        LOG.info("No differences in issues");
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
