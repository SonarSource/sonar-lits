/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

public class DumpTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void load() throws Exception {
    File file = new File("src/test/project/dump.json");
    Map<String, Multiset<IssueKey>> dump = Dump.load(file);
    System.out.println(dump);

    assertThat(dump.size()).isEqualTo(1);
    String componentKey = "sample:[default].Example";
    String ruleKey = "squid:S00103";
    Multiset<IssueKey> component = dump.get(componentKey);
    assertThat(component.contains(new IssueKey(componentKey, ruleKey, 1))).isTrue();
    assertThat(component.contains(new IssueKey(componentKey, ruleKey, 3))).isTrue();
  }

  @Test
  public void save_load() throws Exception {
    File file = temporaryFolder.newFile("dump.json");

    List<IssueKey> issues = Lists.newArrayList();
    issues.add(new IssueKey("componentKey1", "ruleKey", 1));
    issues.add(new IssueKey("componentKey2", "ruleKey1", 1));
    issues.add(new IssueKey("componentKey2", "ruleKey2", 1));
    Dump.save(issues, file);

    Map<String, Multiset<IssueKey>> dump = Dump.load(file);
    System.out.println(dump);
    assertThat(dump.size()).isEqualTo(2);
    assertThat(dump.get("componentKey1").size()).isEqualTo(1);
    assertThat(dump.get("componentKey2").size()).isEqualTo(2);
  }

}
