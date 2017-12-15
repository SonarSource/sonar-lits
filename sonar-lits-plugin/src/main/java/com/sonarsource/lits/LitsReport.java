/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2017 SonarSource SA
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;

public class LitsReport {

  private static final Logger LOG = LoggerFactory.getLogger(LitsReport.class);

  private List<ResourceReport> resourceReports;
  private int issuesCount;

  public LitsReport(Map<String, Set<ReportedIssue>> differentIssues, FileSystem fileSystem) {
    this.resourceReports = new ArrayList<>();
    int n = 0;
    for (Entry<String, Set<ReportedIssue>> stringSetEntry : differentIssues.entrySet()) {
      resourceReports.add(new ResourceReport(stringSetEntry.getKey(), stringSetEntry.getValue(), fileSystem));
      n += stringSetEntry.getValue().size();
    }

    this.issuesCount = n;
  }

  public int issuesCount() {
    return issuesCount;
  }

  public List<ResourceReport> resourceReports() {
    return resourceReports;
  }


  public static class ReportedIssue {

    String message;
    InputComponent inputComponent;
    String moduleKey;
    String ruleKey;
    String componentKey;
    String severity;
    int line;

    public ReportedIssue(String ruleKey, String componentKey, int line, String message, String severity, String moduleKey) {
      this.ruleKey = ruleKey;
      this.componentKey = componentKey;
      this.moduleKey = moduleKey;
      this.line = line;
      this.message = message;
      this.severity = severity;
    }

    public ReportedIssue(String ruleKey, String componentKey, int line, String message, String severity, InputComponent inputComponent) {
      this.ruleKey = ruleKey;
      this.componentKey = componentKey;
      this.inputComponent = inputComponent;
      this.line = line;
      this.message = message;
      this.severity = severity;
    }

    public String message() {
      return message;
    }

    public String ruleKey() {
      return ruleKey;
    }

    public String componentKey() {
      return componentKey;
    }

    public int line() {
      return line;
    }

    public String severity() {
      return severity;
    }

    public int key() {
      return this.hashCode();
    }
  }


  public static class ResourceReport {

    Set<ReportedIssue> issues;

    // moduleKey:relative/path/to/file.foo
    String componentKey;

    InputComponent inputComponent;

    boolean isFile;

    private Map<Integer, List<ReportedIssue>> issuesPerLine = new HashMap<>();

    public ResourceReport(String componentKey, Set<ReportedIssue> issues, FileSystem fileSystem) {
      this.componentKey = componentKey;
      this.issues = issues;

      // InputFile corresponding to this resource, it's null for new issues
      InputComponent inputComponent = null;

      // Module key, first part before colon
      // It's required to correctly extract relative path from componentKey
      // It's null for missing issues
      String moduleKey = null;

      for (ReportedIssue issue : this.issues) {
        if (issue.inputComponent != null && inputComponent == null) {
          inputComponent = issue.inputComponent;
        }

        if (issue.moduleKey != null && moduleKey == null) {
          moduleKey = issue.moduleKey;
        }

        if (issuesPerLine.containsKey(issue.line)) {
          issuesPerLine.get(issue.line).add(issue);
        } else {
          List<ReportedIssue> perLine = new ArrayList<>();
          perLine.add(issue);
          issuesPerLine.put(issue.line, perLine);
        }
      }

      retrieveInputComponent(fileSystem, inputComponent, moduleKey);

      this.isFile = this.inputComponent instanceof InputFile;

    }

    public Set<ReportedIssue> issues() {
      return issues;
    }

    public String type() {
      return isFile ? "FIL" : "DIR";
    }

    public String componentKey() {
      return componentKey;
    }

    public List<ReportedIssue> issuesAtLine(int line) {
      return issuesPerLine.getOrDefault(line, new ArrayList<>());
    }

    public boolean isDisplayableLine(@Nullable Integer lineNumber) {
      if (lineNumber == null || lineNumber < 1) {
        return false;
      }
      for (int i = lineNumber - 2; i <= lineNumber + 2; i++) {
        if (hasIssues(i)) {
          return true;
        }
      }
      return false;
    }

    private void retrieveInputComponent(FileSystem fileSystem, @Nullable InputComponent inputComponent, @Nullable String moduleKey) {
      if (inputComponent != null) {
        this.inputComponent = inputComponent;

      } else {
        if (componentKey.equals(moduleKey)) {
          throw new IllegalStateException("Module/project level issues are not supported by LITS plugin");
        }

        String relativePath = componentKey.substring(moduleKey.length() + 1);
        this.inputComponent = fileSystem.inputFile(fileSystem.predicates().hasRelativePath(relativePath));
        if (this.inputComponent == null) {
          this.inputComponent = fileSystem.inputDir(new File(fileSystem.baseDir(), relativePath));
        }
      }

      if (this.inputComponent == null) {
        throw new IllegalStateException("No InputFile found for component with key " + componentKey);
      }
    }

    public List<String> getEscapedSource() {

      if (!isFile) {
        return new ArrayList<>();
      }

      InputFile inputFile = (InputFile) inputComponent;

      try {
        String[] lines = inputFile.contents().split("\\r?\\n");
        List<String> escapedLines = new ArrayList<>(lines.length);
        for (String line : lines) {
          escapedLines.add(StringEscapeUtils.escapeHtml(line));
        }
        return escapedLines;
      } catch (IOException e) {
        LOG.warn("Unable to read source code of resource {}", inputFile, e);
        return Collections.emptyList();
      }
    }

    private boolean hasIssues(Integer lineId) {
      List<ReportedIssue> issuesAtLine = issuesPerLine.get(lineId);
      return issuesAtLine != null && !issuesAtLine.isEmpty();
    }

  }

}
