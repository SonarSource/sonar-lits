/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

public class DumpTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void save_load() throws Exception {
    File dir = new File(temporaryFolder.newFolder(), "dump");
    List<IssueKey> issues = new ArrayList<>();
    issues.add(new IssueKey("componentKey2", "repoKey:ruleKey1", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey1", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey2", 2));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey2", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:rule-key3", 1));

    Dump.save(issues, dir);

    assertThat(dir.listFiles()).hasSize(3);
    String expected = new StringBuilder()
      .append("{\n")
      .append("'componentKey1':[\n")
      .append("1,\n")
      .append("],\n")
      .append("'componentKey2':[\n")
      .append("1,\n")
      .append("],\n")
      .append("}\n")
      .toString();
    assertThat(Files.toString(new File(dir, "repoKey-ruleKey1.json"), StandardCharsets.UTF_8)).isEqualTo(expected);
    expected = new StringBuilder()
      .append("{\n")
      .append("'componentKey1':[\n")
      .append("1,\n")
      .append("2,\n")
      .append("],\n")
      .append("}\n")
      .toString();
    assertThat(Files.toString(new File(dir, "repoKey-ruleKey2.json"), StandardCharsets.UTF_8)).isEqualTo(expected);
    expected = new StringBuilder()
      .append("{\n")
      .append("'componentKey1':[\n")
      .append("1,\n")
      .append("],\n")
      .append("}\n")
      .toString();
    assertThat(Files.toString(new File(dir, "repoKey-rule-key3.json"), StandardCharsets.UTF_8)).isEqualTo(expected);

    Map<String, Multiset<IssueKey>> dump = Dump.load(dir);
    System.out.println(dump);

    assertThat(dump.size()).isEqualTo(2);
    assertThat(dump.get("componentKey1").size()).isEqualTo(4);
    assertThat(dump.get("componentKey2").size()).isEqualTo(1);
  }

  @Test
  public void unable_to_load() throws Exception {
    thrown.expect(RuntimeException.class);
    Dump.load(temporaryFolder.newFolder(), new HashMap<>());
  }

  @Test
  public void unable_to_save() throws Exception {
    thrown.expect(RuntimeException.class);
    Dump.save(Collections.emptyList(), temporaryFolder.newFile());
  }

  @Test
  public void private_constructor() throws Exception {
    Constructor constructor = Dump.class.getDeclaredConstructor();
    assertThat(constructor.isAccessible()).isFalse();
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}