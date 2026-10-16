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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.sonarsource.lits.sarif.SarifSchema210;
import com.sonarsource.lits.IssueKey.IssueLocation;
import com.sonarsource.lits.sarif.Region;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

public class DumpTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void save_load() throws Exception {
    File dir = new File(temporaryFolder.newFolder(), "dump");
    List<IssueKey> issues = new ArrayList<>();
    issues.add(new IssueKey("componentKey2", "repoKey:ruleKey1", 1, "Found an error"));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey1", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey2", 2));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey2", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:rule-key3", 1));

    Dump.save(issues, dir);

    assertThat(dir.listFiles()).hasSize(3);
    File sarifFile = new File(dir, "repoKey-ruleKey1.sarif");
    ObjectMapper importExport = new ObjectMapper().registerModule(new Jdk8Module());
    SarifSchema210 parsedSarif = importExport.readValue(sarifFile, SarifSchema210.class);
    assertThat(parsedSarif.get$schema().get().toString()).isEqualTo("https://json.schemastore.org/sarif-2.1.0.json");
    assertThat(parsedSarif.getVersion().value()).isEqualTo("2.1.0");
    String sarif = new String(Files.readAllBytes(sarifFile.toPath()), StandardCharsets.UTF_8);
    assertThat(sarif).doesNotContain(": null");
    assertThat(sarif).doesNotContain(": [ ]").doesNotContain(": { }");
    assertThat(sarif).isEqualTo(readResource("repoKey-ruleKey1-expected.sarif").trim());

    Map<String, Multiset<IssueKey>> dump = Dump.load(dir);

    assertThat(dump.size()).isEqualTo(2);
    assertThat(dump.get("componentKey1").size()).isEqualTo(4);
    assertThat(dump.get("componentKey2").size()).isEqualTo(1);
  }

  @Test
  public void load_legacy_with_trailing_commas() {
    File file = new File("src/test/resources/squid-S00104.json");
    Map<String, Multiset<IssueKey>> result = new HashMap<>();
    Dump.load(file, result);
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get("project:src/Example.java").size()).isEqualTo(2);
  }

  @Test
  public void precise_ranges_round_trip_without_collapsing_same_line_issues() throws Exception {
    File directory = temporaryFolder.newFolder();
    IssueLocation first = new IssueLocation(new Region().withStartLine(2).withStartColumn(2).withEndLine(3).withEndColumn(4)
      .withCharOffset(5).withCharLength(8).withByteOffset(7).withByteLength(11));
    IssueLocation second = new IssueLocation(new Region().withStartLine(2).withStartColumn(5).withEndLine(2).withEndColumn(8)
      .withCharOffset(8).withCharLength(3).withByteOffset(10).withByteLength(3));
    List<IssueKey> issues = new ArrayList<>();
    issues.add(new IssueKey("file", "repo:rule", 2, "first", first));
    issues.add(new IssueKey("file", "repo:rule", 2, "second", second));
    Dump.save(issues, directory);
    Multiset<IssueKey> loaded = Dump.load(directory).get("file");
    assertThat(loaded.size()).isEqualTo(2);
    assertThat(loaded.contains(issues.get(0))).isTrue();
    assertThat(loaded.contains(issues.get(1))).isTrue();
    String sarif = new String(Files.readAllBytes(new File(directory, "repo-rule.sarif").toPath()), StandardCharsets.UTF_8);
    assertThat(sarif).contains("\"columnKind\" : \"utf16CodeUnits\"");
    assertThat(sarif).doesNotContain("\"charOffset\" : -1").doesNotContain("\"byteOffset\" : -1");
  }

  @Test
  public void empty_collections_are_omitted_without_losing_zero_offsets() throws Exception {
    File directory = temporaryFolder.newFolder();
    IssueLocation location = new IssueLocation(new Region().withStartLine(1).withStartColumn(1).withEndLine(1).withEndColumn(1)
      .withCharOffset(0).withCharLength(0).withByteOffset(0).withByteLength(0));
    IssueKey issue = new IssueKey("file", "repo:rule", 1, "Issue", location);
    Dump.save(Collections.singletonList(issue), directory);
    IssueKey loaded = Dump.load(directory).get("file").iterator().next();
    assertThat(loaded.location.charOffset).isEqualTo(0);
    assertThat(loaded.location.charLength).isEqualTo(0);
    assertThat(loaded.location.byteOffset).isEqualTo(0);
    assertThat(loaded.location.byteLength).isEqualTo(0);
  }

  @Test
  public void ranges_without_source_and_file_level_issues_do_not_invent_offsets() throws Exception {
    File directory = temporaryFolder.newFolder();
    List<IssueKey> issues = new ArrayList<>();
    issues.add(new IssueKey("file", "repo:rule", 2, null, IssueLocation.fromRange(IssueKeyTest.range(2, 1, 3, 2))));
    issues.add(new IssueKey("file", "repo:rule", null));
    Dump.save(issues, directory);
    Multiset<IssueKey> loaded = Dump.load(directory).get("file");
    assertThat(loaded.contains(issues.get(0))).isTrue();
    assertThat(loaded.contains(issues.get(1))).isTrue();
    String sarif = new String(Files.readAllBytes(new File(directory, "repo-rule.sarif").toPath()), StandardCharsets.UTF_8);
    assertThat(sarif).doesNotContain("charOffset").doesNotContain("byteOffset").doesNotContain("charLength").doesNotContain("byteLength");
  }

  @Test
  public void old_sarif_default_offsets_do_not_make_line_only_expectations_precise() {
    assertThat(IssueLocation.fromRegion(new Region().withStartLine(1).withEndLine(1))).isNull();
    IssueLocation location = IssueLocation.fromRegion(new Region().withStartLine(1).withStartColumn(2).withEndColumn(4));
    assertThat(location.charOffset).isNull();
    assertThat(location.byteOffset).isNull();
    assertThat(location.endLine).isEqualTo(1);
  }

  @Test
  public void unable_to_load() throws Exception {
    File dir = temporaryFolder.newFolder();
    HashMap<String, Multiset<IssueKey>> map = new HashMap<>();
    assertThrows(RuntimeException.class, () ->
      Dump.load(dir, map));

  }

  @Test
  public void unable_to_save() throws Exception {
    File dir = temporaryFolder.newFile();
    List<IssueKey> list = Collections.emptyList();
    assertThrows(RuntimeException.class, () ->
      Dump.save(list, dir));
  }

  private static String readResource(String name) throws Exception {
    try (InputStream is = DumpTest.class.getResourceAsStream("/" + name)) {
      byte[] bytes = new byte[is.available()];
      is.read(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  @Test
  public void private_constructor() throws Exception {
    Constructor constructor = Dump.class.getDeclaredConstructor();
    assertThat(constructor.isAccessible()).isFalse();
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}
