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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.sonarsource.lits.IssueKey.IssueLocation;
import com.sonarsource.lits.sarif.Region;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.InputFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.sonarsource.scanner.engine.sensor.test.fixtures.SensorContextTester;
import com.sonarsource.scanner.engine.sensor.test.fixtures.TestInputFileBuilder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.rule.RuleKey;
import org.sonar.scanner.plugin.api.impl.fs.DefaultFileSystem;
import org.sonar.scanner.plugin.api.impl.fs.FileMetadata;
import org.sonar.scanner.plugin.api.impl.rule.ActiveRulesBuilder;
import org.sonar.scanner.plugin.api.impl.rule.NewActiveRule;
import org.sonar.scanner.plugin.api.impl.sensor.DefaultSensorDescriptor;

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
      .setMetadata(new FileMetadata(mock(AnalysisWarnings.class))
        .readMetadata(new FileReader("src/test/resources/example.cpp")))
      .build());
    sensorContext.setFileSystem(fs);
  }

  @Test
  public void test_describe() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    decorator.describe(descriptor);
    assertThat(descriptor.name()).isEqualTo("LITS");
    assertThat(descriptor.isGlobal()).isFalse();
  }

  @Test
  public void should_save_on_project() {
    when(checker.getByComponentKey(anyString())).thenReturn(Multiset.<IssueKey>create());

    decorator.save();

    verify(checker).save();
  }

  @Test
  public void should_report_missing_issues() {
    Multiset<IssueKey> issues = Multiset.create();
    issues.add(new IssueKey("", "squid:S00103", 1));
    issues.add(new IssueKey("", "squid:S00104", null));
    when(checker.getByComponentKey(anyString())).thenReturn(issues);

    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of("squid", "S00103"))
        .build())
      .build();
    decorator = new DumpPhase(checker, activeRules);

    decorator.execute(sensorContext);

    assertThat(sensorContext.allIssues()).hasSize(1);
    assertThat(sensorContext.allIssues().iterator().next().primaryLocation().textRange()).isNotNull();
  }

  @Test
  public void should_report_missing_files() {
    Map<String, Multiset<IssueKey>> previous = new HashMap<>();
    Multiset<IssueKey> issues = Multiset.create();
    issues.add(new IssueKey("", "squid:S00103", null));
    previous.put("missing", issues);
    when(checker.getPrevious()).thenReturn(previous);
    when(checker.getByComponentKey(anyString())).thenReturn(Multiset.<IssueKey>empty());

    decorator.save();

    verify(checker).missingResource("missing");
  }

  @Test
  public void missing_issues_preserve_expected_precise_range() {
    sensorContext = SensorContextTester.create(temporaryFolder.getRoot());
    InputFile file = TestInputFileBuilder.create("", "precise.cpp")
      .setModuleBaseDir(temporaryFolder.getRoot().toPath()).setContents("abcd\nefgh\n").build();
    sensorContext.fileSystem().add(file);
    Multiset<IssueKey> issues = Multiset.create();
    issues.add(new IssueKey("", "squid:S00103", 1, "expected", new IssueLocation(new Region().withStartLine(1).withStartColumn(2).withEndLine(2).withEndColumn(3)
      .withCharOffset(null).withCharLength(null).withByteOffset(null).withByteLength(null))));
    when(checker.getByComponentKey(anyString())).thenReturn(Multiset.empty());
    when(checker.getByComponentKey(file.key())).thenReturn(issues);
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder().setRuleKey(RuleKey.of("squid", "S00103")).build()).build();
    decorator = new DumpPhase(checker, activeRules);
    decorator.execute(sensorContext);
    TextRange range = sensorContext.allIssues().iterator().next().primaryLocation().textRange();
    assertThat(range.start().line()).isEqualTo(1);
    assertThat(range.start().lineOffset()).isEqualTo(1);
    assertThat(range.end().line()).isEqualTo(2);
    assertThat(range.end().lineOffset()).isEqualTo(2);
  }

  @Test
  public void missing_issues_with_empty_expected_range_select_line() {
    sensorContext = SensorContextTester.create(temporaryFolder.getRoot());
    InputFile file = TestInputFileBuilder.create("", "empty.cpp")
      .setModuleBaseDir(temporaryFolder.getRoot().toPath()).setContents("abcd\n\nefgh\n").build();
    sensorContext.fileSystem().add(file);
    Multiset<IssueKey> issues = Multiset.create();
    issues.add(new IssueKey("", "squid:S00103", 2, "expected", new IssueLocation(new Region().withStartLine(2).withStartColumn(1).withEndLine(2).withEndColumn(1)
      .withCharOffset(null).withCharLength(null).withByteOffset(null).withByteLength(null))));
    when(checker.getByComponentKey(anyString())).thenReturn(Multiset.empty());
    when(checker.getByComponentKey(file.key())).thenReturn(issues);
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder().setRuleKey(RuleKey.of("squid", "S00103")).build()).build();
    decorator = new DumpPhase(checker, activeRules);
    decorator.execute(sensorContext);
    TextRange range = sensorContext.allIssues().iterator().next().primaryLocation().textRange();
    assertThat(range.start().line()).isEqualTo(2);
    assertThat(range.end().line()).isEqualTo(2);
  }

}
