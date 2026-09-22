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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
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
    assertThat(sarif).contains("startLine");
    assertThat(sarif).contains("endLine");
    assertThat(sarif).contains("componentKey1");
    assertThat(sarif).contains("componentKey2");
    assertThat(sarif).contains("Found an error");

    Map<String, Multiset<IssueKey>> dump = Dump.load(dir);
    System.out.println(dump);

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

  @Test
  public void private_constructor() throws Exception {
    Constructor constructor = Dump.class.getDeclaredConstructor();
    assertThat(constructor.isAccessible()).isFalse();
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}
