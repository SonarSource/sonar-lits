/*
 * Sonar LITS Plugin
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

class Dump {

  private static final String SARIF_EXT = "sarif";
  private static final String LEGACY_EXT = "json";

  private Dump() {
  }

  static Map<String, Multiset<IssueKey>> load(File dir) {
    Map<String, Multiset<IssueKey>> result = new HashMap<>();
    for (File file : listDumpFiles(dir.toPath())) {
      load(file, result);
    }
    return result;
  }

  static void load(File file, Map<String, Multiset<IssueKey>> result) {
    JSONObject json;
    try (
      FileInputStream fis = new FileInputStream(file);
      InputStreamReader in = new InputStreamReader(fis, StandardCharsets.UTF_8)
    ) {
      json = (JSONObject) JSONValue.parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    String ruleKey = ruleKeyFromFileName(file.getName());
    if (json.containsKey("version") && json.containsKey("runs")) {
      loadSarif(json, result);
    } else {
      loadLegacy(json, ruleKey, result);
    }
  }

  private static void loadLegacy(JSONObject json, String ruleKey, Map<String, Multiset<IssueKey>> result) {
    for (Map.Entry<String, Object> component : json.entrySet()) {
      String componentKey = component.getKey();
      Multiset<IssueKey> issues = result.computeIfAbsent(componentKey, key -> Multiset.create());
      for (Object line : (JSONArray) component.getValue()) {
        issues.add(new IssueKey(componentKey, ruleKey, (Integer) line));
      }
    }
  }

  private static void loadSarif(JSONObject json, Map<String, Multiset<IssueKey>> result) {
    for (Object runValue : (JSONArray) json.get("runs")) {
      JSONObject run = (JSONObject) runValue;
      for (Object resultValue : (JSONArray) run.get("results")) {
        JSONObject issue = (JSONObject) resultValue;
        String ruleKey = (String) issue.get("ruleId");
        JSONObject message = (JSONObject) issue.get("message");
        String issueMessage = message == null ? null : (String) message.get("text");
        for (Object locationValue : (JSONArray) issue.get("locations")) {
          JSONObject physical = (JSONObject) ((JSONObject) locationValue).get("physicalLocation");
          String componentKey = (String) ((JSONObject) physical.get("artifactLocation")).get("uri");
          JSONObject region = (JSONObject) physical.get("region");
          Integer line = region == null ? null : (Integer) region.get("startLine");
          result.computeIfAbsent(componentKey, key -> Multiset.create())
            .add(new IssueKey(componentKey, ruleKey, line, issueMessage));
        }
      }
    }
  }

  static void save(List<IssueKey> issues, File dir) {
    try {
      Files.createDirectories(dir.toPath());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    issues.sort(new IssueKeyComparator());
    Map<String, List<IssueKey>> issuesByRule = new LinkedHashMap<>();
    for (IssueKey issue : issues) {
      issuesByRule.computeIfAbsent(issue.ruleKey, key -> new ArrayList<>()).add(issue);
    }
    for (Map.Entry<String, List<IssueKey>> entry : issuesByRule.entrySet()) {
      saveRule(dir, entry.getKey(), entry.getValue());
    }
  }

  private static void saveRule(File dir, String ruleKey, List<IssueKey> issues) {
    JSONObject report = new JSONObject();
    report.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
    report.put("version", "2.1.0");

    JSONObject driver = new JSONObject();
    driver.put("name", "LITS");
    JSONObject tool = new JSONObject();
    tool.put("driver", driver);
    JSONArray runs = new JSONArray();
    JSONObject run = new JSONObject();
    run.put("tool", tool);
    JSONArray results = new JSONArray();
    for (IssueKey issue : issues) {
      results.add(issueJson(issue));
    }
    run.put("results", results);
    runs.add(run);
    report.put("runs", runs);

    try {
      Files.write(dir.toPath().resolve(ruleKeyToFileName(ruleKey)),
        JSONValue.toJSONString(report).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JSONObject issueJson(IssueKey issue) {
    JSONObject result = new JSONObject();
    result.put("ruleId", issue.ruleKey);

    JSONObject message = new JSONObject();
    message.put("text", issue.message == null ? "Issue" : issue.message);
    result.put("message", message);

    JSONObject artifactLocation = new JSONObject();
    artifactLocation.put("uri", issue.componentKey);
    JSONObject physicalLocation = new JSONObject();
    physicalLocation.put("artifactLocation", artifactLocation);
    if (issue.line != 0) {
      JSONObject region = new JSONObject();
      region.put("startLine", issue.line);
      region.put("endLine", issue.line);
      physicalLocation.put("region", region);
    }
    JSONObject location = new JSONObject();
    location.put("physicalLocation", physicalLocation);
    JSONArray locations = new JSONArray();
    locations.add(location);
    result.put("locations", locations);
    return result;
  }

  private static String ruleKeyToFileName(String ruleKey) {
    return ruleKey.replace(':', '-') + "." + SARIF_EXT;
  }

  private static String ruleKeyFromFileName(String fileName) {
    int extensionStart = fileName.lastIndexOf('.');
    return fileName.substring(0, extensionStart).replaceFirst("-", ":");
  }

  private static boolean hasExtension(Path path, String extension) {
    return path.getFileName().toString().endsWith("." + extension);
  }

  private static List<File> listDumpFiles(Path dir) {
    try (Stream<Path> paths = Files.list(dir)) {
      List<File> files = new ArrayList<>();
      paths
        .filter(Files::isRegularFile)
        .filter(path -> hasExtension(path, SARIF_EXT) || hasExtension(path, LEGACY_EXT))
        .forEach(path -> files.add(path.toFile()));
      return files;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class IssueKeyComparator implements Comparator<IssueKey>, Serializable {
    private static final long serialVersionUID = 1;

    @Override
    public int compare(IssueKey left, IssueKey right) {
      int c = left.ruleKey.compareTo(right.ruleKey);
      if (c == 0) {
        c = left.componentKey.compareTo(right.componentKey);
        if (c == 0) {
          c = left.line - right.line;
        }
      }
      return c;
    }
  }

}
