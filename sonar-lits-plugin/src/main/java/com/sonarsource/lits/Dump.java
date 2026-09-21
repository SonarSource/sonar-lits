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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.jcup.sarif_2_1_0.SarifSchema210ImportExportSupport;
import de.jcup.sarif_2_1_0.model.ArtifactLocation;
import de.jcup.sarif_2_1_0.model.Location;
import de.jcup.sarif_2_1_0.model.Message;
import de.jcup.sarif_2_1_0.model.PhysicalLocation;
import de.jcup.sarif_2_1_0.model.Region;
import de.jcup.sarif_2_1_0.model.Result;
import de.jcup.sarif_2_1_0.model.Run;
import de.jcup.sarif_2_1_0.model.SarifSchema210;
import de.jcup.sarif_2_1_0.model.SarifSchema210.Version;
import de.jcup.sarif_2_1_0.model.Tool;
import de.jcup.sarif_2_1_0.model.ToolComponent;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class Dump {

  private static final String SARIF_EXT = "sarif";
  private static final String LEGACY_EXT = "json";
  private static final SarifSchema210ImportExportSupport IMPORT_EXPORT = new SarifSchema210ImportExportSupport();

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
    String ruleKey = ruleKeyFromFileName(file.getName());
    if (hasExtension(file.toPath(), SARIF_EXT)) {
      loadSarifFile(file, ruleKey, result);
    } else {
      loadLegacyFile(file, ruleKey, result);
    }
  }

  private static void loadLegacyFile(File file, String ruleKey, Map<String, Multiset<IssueKey>> result) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
      JsonNode root = mapper.readTree(file);
      java.util.Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String componentKey = entry.getKey();
        Multiset<IssueKey> issues = result.computeIfAbsent(componentKey, key -> Multiset.create());
        for (JsonNode line : entry.getValue()) {
          issues.add(new IssueKey(componentKey, ruleKey, line.asInt()));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void loadSarifFile(File file, String fallbackRuleKey, Map<String, Multiset<IssueKey>> result) {
    SarifSchema210 sarif;
    try {
      sarif = IMPORT_EXPORT.fromFile(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    loadSarif(sarif, fallbackRuleKey, result);
  }

  private static void loadSarif(SarifSchema210 sarif, String fallbackRuleKey, Map<String, Multiset<IssueKey>> result) {
    List<Run> runs = sarif.getRuns();
    if (runs != null) {
      for (Run run : runs) {
        loadRun(run, fallbackRuleKey, result);
      }
    }
  }

  private static void loadRun(Run run, String fallbackRuleKey, Map<String, Multiset<IssueKey>> result) {
    List<Result> results = run.getResults();
    if (results != null) {
      for (Result sarifResult : results) {
        loadResult(sarifResult, fallbackRuleKey, result);
      }
    }
  }

  private static void loadResult(Result sarifResult, String fallbackRuleKey, Map<String, Multiset<IssueKey>> result) {
    String ruleKey = sarifResult.getRuleId() == null ? fallbackRuleKey : sarifResult.getRuleId();
    List<Location> locations = sarifResult.getLocations();
    if (locations != null) {
      Message message = sarifResult.getMessage();
      String issueMessage = message == null ? null : message.getText();
      for (Location location : locations) {
        loadLocation(location, ruleKey, issueMessage, result);
      }
    }
  }

  private static void loadLocation(Location location, String ruleKey, @Nullable String issueMessage, Map<String, Multiset<IssueKey>> result) {
    PhysicalLocation physical = location.getPhysicalLocation();
    if (physical == null) {
      return;
    }
    ArtifactLocation artifact = physical.getArtifactLocation();
    if (artifact == null) {
      return;
    }
    String componentKey = artifact.getUri();
    if (componentKey == null) {
      return;
    }
    Region region = physical.getRegion();
    Integer line = region == null ? null : region.getStartLine();
    result.computeIfAbsent(componentKey, key -> Multiset.create())
      .add(new IssueKey(componentKey, ruleKey, line, issueMessage));
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
    SarifSchema210 sarif = new SarifSchema210();
    sarif.set$schema(URI.create("https://json.schemastore.org/sarif-2.1.0.json"));
    sarif.setVersion(Version._2_1_0);

    Run run = new Run();
    ToolComponent driver = new ToolComponent();
    driver.setName("LITS");
    Tool tool = new Tool();
    tool.setDriver(driver);
    run.setTool(tool);

    List<Result> results = new ArrayList<>();
    for (IssueKey issue : issues) {
      results.add(issueToResult(issue));
    }
    run.setResults(results);
    sarif.setRuns(Collections.singletonList(run));

    try {
      IMPORT_EXPORT.toFile(sarif, dir.toPath().resolve(ruleKeyToFileName(ruleKey)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Result issueToResult(IssueKey issue) {
    Result result = new Result();
    result.setRuleId(issue.ruleKey);

    Message message = new Message();
    message.setText(issue.message == null ? "Issue" : issue.message);
    result.setMessage(message);

    ArtifactLocation artifactLocation = new ArtifactLocation();
    artifactLocation.setUri(issue.componentKey);
    PhysicalLocation physicalLocation = new PhysicalLocation();
    physicalLocation.setArtifactLocation(artifactLocation);
    if (issue.line != 0) {
      Region region = new Region();
      region.setStartLine(issue.line);
      region.setEndLine(issue.line);
      physicalLocation.setRegion(region);
    }
    Location location = new Location();
    location.setPhysicalLocation(physicalLocation);
    result.setLocations(Collections.singletonList(location));
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
      Map<String, File> filesByRule = new HashMap<>();
      paths
        .filter(Files::isRegularFile)
        .filter(path -> hasExtension(path, SARIF_EXT) || hasExtension(path, LEGACY_EXT))
        .forEach(path -> filesByRule.merge(
          ruleKeyFromFileName(path.getFileName().toString()),
          path.toFile(),
          (existing, candidate) -> hasExtension(candidate.toPath(), SARIF_EXT) ? candidate : existing));
      return new ArrayList<>(filesByRule.values());
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
          if (c == 0) {
            c = Comparator.nullsFirst(String::compareTo).compare(left.message, right.message);
          }
        }
      }
      return c;
    }
  }

}
