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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sonarsource.lits.sarif.ArtifactLocation;
import com.sonarsource.lits.sarif.Location;
import com.sonarsource.lits.sarif.Message;
import com.sonarsource.lits.sarif.PhysicalLocation;
import com.sonarsource.lits.sarif.Region;
import com.sonarsource.lits.sarif.Result;
import com.sonarsource.lits.sarif.Run;
import com.sonarsource.lits.sarif.SarifSchema210;
import com.sonarsource.lits.sarif.SarifSchema210.Version;
import com.sonarsource.lits.sarif.Tool;
import com.sonarsource.lits.sarif.ToolComponent;

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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Dump {

  private static final String SARIF_EXT = "sarif";
  private static final String LEGACY_EXT = "json";
  private static final ObjectMapper SARIF_MAPPER = new ObjectMapper()
    .registerModule(new Jdk8Module())
    .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      @Override
      public JsonInclude.Value findPropertyInclusion(Annotated a) {
        JsonInclude.Value v = super.findPropertyInclusion(a);
        if (v.getValueInclusion() == JsonInclude.Include.NON_NULL) {
          return v.withValueInclusion(JsonInclude.Include.NON_ABSENT);
        }
        return v;
      }
    });

  private static final ObjectMapper LEGACY_MAPPER = JsonMapper.builder()
    .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
    .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
    .build();

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
      JsonNode root = LEGACY_MAPPER.readTree(file);
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
      sarif = SARIF_MAPPER.readValue(file, SarifSchema210.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    loadSarif(sarif, fallbackRuleKey, result);
  }

  private static void loadSarif(SarifSchema210 sarif, String fallbackRuleKey, Map<String, Multiset<IssueKey>> result) {
    nullToEmpty(sarif.getRuns()).stream()
      .flatMap(run -> run.getResults().orElse(Collections.emptyList()).stream())
      .flatMap(sarifResult -> toIssueKeys(sarifResult, fallbackRuleKey))
      .forEach(issueKey -> result.computeIfAbsent(issueKey.componentKey, key -> Multiset.create()).add(issueKey));
  }

  private static Stream<IssueKey> toIssueKeys(Result sarifResult, String fallbackRuleKey) {
    String ruleKey = sarifResult.getRuleId().orElse(fallbackRuleKey);
    String issueMessage = sarifResult.getMessage() == null ? null : sarifResult.getMessage().getText().orElse(null);
    return sarifResult.getLocations().orElse(Collections.emptyList()).stream()
      .map(location -> toIssueKey(location, ruleKey, issueMessage))
      .filter(Objects::nonNull);
  }

  @Nullable
  private static IssueKey toIssueKey(Location location, String ruleKey, @Nullable String issueMessage) {
    return location.getPhysicalLocation()
      .flatMap(physical -> physical.getArtifactLocation()
        .flatMap(ArtifactLocation::getUri)
        .map(componentKey -> new IssueKey(componentKey, ruleKey,
          physical.getRegion().flatMap(Region::getStartLine).orElse(null), issueMessage)))
      .orElse(null);
  }

  private static <T> List<T> nullToEmpty(@Nullable List<T> list) {
    return list == null ? Collections.emptyList() : list;
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
    SarifSchema210 sarif = new SarifSchema210()
      .with$schema(URI.create("https://json.schemastore.org/sarif-2.1.0.json"))
      .withVersion(Version._2_1_0)
      .withRuns(Collections.singletonList(new Run()
        .withTool(new Tool().withDriver(new ToolComponent().withName("LITS")))
        .withResults(issues.stream().map(Dump::issueToResult).collect(Collectors.toList()))));

    try {
      // Pretty-printed output is deliberate: SARIF results carry ruleId, message, and nested locations,
      // so pretty-printing gives more precise git diffs on single-field edits than one-line-per-result.
      SARIF_MAPPER.writerWithDefaultPrettyPrinter().writeValue(dir.toPath().resolve(ruleKeyToFileName(ruleKey)).toFile(), sarif);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Result issueToResult(IssueKey issue) {
    PhysicalLocation physicalLocation = new PhysicalLocation()
      .withArtifactLocation(new ArtifactLocation().withUri(issue.componentKey));
    if (issue.line != 0) {
      physicalLocation.withRegion(new Region().withStartLine(issue.line).withEndLine(issue.line));
    }
    return new Result()
      .withRuleId(issue.ruleKey)
      .withMessage(new Message().withText(issue.message == null ? "Issue" : issue.message))
      .withLocations(Collections.singletonList(new Location().withPhysicalLocation(physicalLocation)));
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
