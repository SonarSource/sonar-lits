/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.FileMetadata;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.rule.RuleKey;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DumpPhaseTest {

  private IssuesChecker checker;
  private ActiveRules activeRules;
  private DumpPhase decorator;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private SensorContextTester sensorContext;

  @Before
  public void setup() throws IOException {
    checker = mock(IssuesChecker.class);
    activeRules = new ActiveRulesBuilder().build();
    decorator = new DumpPhase(checker, activeRules);

    sensorContext = SensorContextTester.create(new File("src/test/resources"));
    DefaultFileSystem fs = new DefaultFileSystem(new File("src/test/resources"));
    fs.setWorkDir(temporaryFolder.newFolder().toPath());
    fs.add(TestInputFileBuilder
      .create("", "example.cpp")
      .setLanguage("cpp")
      .setMetadata(new FileMetadata().readMetadata(new FileReader("src/test/resources/example.cpp")))
      .build());
    sensorContext.setFileSystem(fs);
  }

  @Test
  public void test_describe() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    decorator.describe(descriptor);
    assertThat(descriptor.name()).isEqualTo("LITS");
    assertThat(descriptor.isGlobal()).isTrue();
  }

  @Test
  public void should_save_on_project() {
    when(checker.getByComponentKey(anyString())).thenReturn(HashMultiset.<IssueKey>create());

    decorator.save();

    verify(checker).save();
  }

  @Test
  public void should_report_missing_issues() {
    Multiset<IssueKey> issues = HashMultiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    issues.add(new IssueKey("", "squid:S00104", null));
    when(checker.getByComponentKey(anyString())).thenReturn(issues);

    activeRules = new ActiveRulesBuilder().create(RuleKey.of("squid", "S00103")).activate().build();
    decorator = new DumpPhase(checker, activeRules);

    decorator.execute(sensorContext);

    assertThat(sensorContext.allIssues()).hasSize(1);
  }

  @Test
  public void should_report_missing_files() {
    Map<String, Multiset<IssueKey>> previous = new HashMap<>();
    Multiset<IssueKey> issues = HashMultiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    previous.put("missing", issues);
    when(checker.getPrevious()).thenReturn(previous);
    when(checker.getByComponentKey(anyString())).thenReturn(ImmutableMultiset.<IssueKey>of());

    decorator.save();

    verify(checker).missingResource("missing");
  }

}
