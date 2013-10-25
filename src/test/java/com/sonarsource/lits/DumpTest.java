/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

public class DumpTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void save_load() throws Exception {
    File dir = new File(temporaryFolder.newFolder(), "dump");
    List<IssueKey> issues = Lists.newArrayList();
    issues.add(new IssueKey("componentKey2", "repoKey:ruleKey1", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey1", 1));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey2", 2));
    issues.add(new IssueKey("componentKey1", "repoKey:ruleKey2", 1));

    Dump.save(issues, dir);

    assertThat(dir.listFiles()).hasSize(2);
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
    assertThat(Files.toString(new File(dir, "repoKey-ruleKey1.json"), Charsets.UTF_8)).isEqualTo(expected);
    expected = new StringBuilder()
      .append("{\n")
      .append("'componentKey1':[\n")
      .append("1,\n")
      .append("2,\n")
      .append("],\n")
      .append("}\n")
      .toString();
    assertThat(Files.toString(new File(dir, "repoKey-ruleKey2.json"), Charsets.UTF_8)).isEqualTo(expected);

    Map<String, Multiset<IssueKey>> dump = Dump.load(dir);
    System.out.println(dump);

    assertThat(dump.size()).isEqualTo(2);
    assertThat(dump.get("componentKey1").size()).isEqualTo(3);
    assertThat(dump.get("componentKey2").size()).isEqualTo(1);
  }

  @Test
  public void private_constructor() throws Exception {
    Constructor constructor = Dump.class.getDeclaredConstructor();
    assertThat(constructor.isAccessible()).isFalse();
    constructor.setAccessible(true);
    constructor.newInstance();
  }

}
