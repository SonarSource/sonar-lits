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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.FileMetadata;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.ActiveRule;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DumpPhaseTest {

  private IssuesChecker checker;
  private RulesProfile rulesProfile;
  private DumpPhase decorator;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private SensorContextTester sensorContext;

  @Before
  public void setup() throws IOException {
    checker = mock(IssuesChecker.class);
    rulesProfile = mock(RulesProfile.class);
    decorator = new DumpPhase(checker, rulesProfile);

    sensorContext = SensorContextTester.create(new File("src/test/resources"));
    sensorContext.fileSystem().setWorkDir(temporaryFolder.newFolder());
    sensorContext.fileSystem().add(
      new DefaultInputFile("", "example.cpp")
        .setLanguage("cpp")
        .initMetadata(new FileMetadata().readMetadata(new FileReader("src/test/resources/example.cpp")))
    );
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

    ActiveRule activeRule = mock(ActiveRule.class);
    when(rulesProfile.getActiveRule("squid", "S00103")).thenReturn(activeRule);

    decorator.execute(sensorContext);

    assertThat(sensorContext.allIssues()).hasSize(1);
  }

  @Test
  public void should_report_missing_files() {
    Map<String, Multiset<IssueKey>> previous = Maps.newHashMap();
    Multiset<IssueKey> issues = HashMultiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    previous.put("missing", issues);
    when(checker.getPrevious()).thenReturn(previous);
    when(checker.getByComponentKey(anyString())).thenReturn(ImmutableMultiset.<IssueKey>of());

    decorator.save();

    verify(checker).missingResource("missing");
  }

}
