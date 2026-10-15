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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.scanner.plugin.api.impl.fs.DefaultFileSystem;
import com.sonarsource.lits.IssueKey.IssueLocation;
import com.sonarsource.lits.sarif.Region;
import com.sonarsource.scanner.engine.sensor.test.fixtures.TestInputFileBuilder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.config.Configuration;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.scan.issue.filter.FilterableIssue;
import org.sonar.api.scan.issue.filter.IssueFilterChain;
import org.sonar.api.testfixtures.log.LogTester;
import org.sonar.api.utils.MessageException;
import org.sonar.scanner.plugin.api.impl.config.MapSettings;
import org.sonar.scanner.plugin.api.impl.rule.ActiveRulesBuilder;
import org.sonar.scanner.plugin.api.impl.rule.NewActiveRule;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssuesCheckerTest {

  @org.junit.Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  private ActiveRules activeRules;
  private IssuesChecker checker;
  private DefaultFileSystem fileSystem;
  private File output;
  private File assertion;
  private IssueFilterChain chainReturnTrue = (issue) -> true;
  private IssueFilterChain chainReturnFalse = (issue) -> false;

  @Before
  public void setup() throws Exception {
    output = new File(temporaryFolder.newFolder(), "dump");
    assertion = new File(temporaryFolder.newFolder(), "assertion");
    Configuration settings = newCorrectSettings().asConfig();
    activeRules = new ActiveRulesBuilder().build();
    fileSystem = new DefaultFileSystem(temporaryFolder.getRoot());
    checker = new IssuesChecker(settings, activeRules, fileSystem);
  }

  @Test
  public void path_must_be_specified() {
    Configuration settings = new MapSettings().asConfig();
    MessageException e = assertThrows(MessageException.class, () ->
      new IssuesChecker(settings, activeRules, null));
    assertEquals("Missing property 'sonar.lits.dump.old'", e.getMessage());
  }

  @Test
  public void path_must_be_absolute() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, "target/dump.json");
    Configuration configuration = settings.asConfig();
    MessageException e = assertThrows(MessageException.class, () ->
        new IssuesChecker(configuration, activeRules, fileSystem));
    assertEquals("Path must be absolute - check property 'sonar.lits.dump.old'", e.getMessage());
  }

  @Test
  public void differences_file_must_be_specified() {
    MapSettings settings = newCorrectSettings();
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, (String) null);
    Configuration configuration = settings.asConfig();
    MessageException e = assertThrows(MessageException.class, () ->
      new IssuesChecker(configuration, activeRules, fileSystem));
    assertEquals("Missing property 'sonar.lits.differences'", e.getMessage());
  }

  @Test
  public void should_fail_when_incorrect_severity() {
    Configuration settings = newCorrectSettings().asConfig();
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of("repositoryKey", "ruleKey"))
        .setSeverity(RulePriority.BLOCKER.toString())
        .build())
      .build();

    MessageException e = assertThrows(MessageException.class, () ->
      new IssuesChecker(settings, activeRules, null));
    assertEquals("Rule 'repositoryKey:ruleKey' must be declared with severity INFO", e.getMessage());
  }

  @Test
  public void should_not_save_when_no_differences() {
    checker.save();

    assertThat(output).doesNotExist();
  }

  @Test
  public void should_save_when_differences() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    assertThat(checker.accept(issue, chainReturnTrue)).isTrue();
    checker.save();

    assertThat(output).exists();
  }

  @Test
  public void should_not_save_when_disabled() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    checker.disabled = true;
    assertThat(checker.accept(issue, chainReturnTrue)).isTrue();
    checker.save();

    assertThat(output).doesNotExist();
  }

  @Test
  public void should_return_false_if_chain_return_false() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));

    assertThat(checker.accept(issue, chainReturnFalse)).isFalse();
    checker.save();

    assertThat(output).doesNotExist();
  }

  @Test
  public void should_hide_old_issues() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("project:src/Example.java");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));
    when(issue.line()).thenReturn(1);
    when(issue.severity()).thenReturn("INFO");

    assertThat(checker.accept(issue, chainReturnTrue)).isFalse();
  }

  @Test
  public void precise_actual_issues_still_match_legacy_line_expectations() {
    FilterableIssue issue = preciseIssue("project:src/Example.java", IssueKeyTest.range(1, 2, 1, 4));
    assertThat(checker.accept(issue, chainReturnTrue)).isFalse();
    assertThat(checker.getByComponentKey(issue.componentKey()).contains(new IssueKey(issue.componentKey(), "squid:S00103", 1))).isFalse();
  }

  @Test
  public void precise_expectations_detect_column_and_end_range_changes() {
    TextRange range = IssueKeyTest.range(1, 2, 2, 4);
    FilterableIssue issue = preciseIssue("file", range);
    Multiset<IssueKey> previous = expected("file", IssueLocation.fromRange(range));
    assertThat(checker.accept(preciseIssue("file", IssueKeyTest.range(1, 3, 2, 4)), chainReturnTrue)).isTrue();
    assertThat(checker.accept(preciseIssue("file", IssueKeyTest.range(1, 2, 2, 5)), chainReturnTrue)).isTrue();
    assertThat(previous.size()).isEqualTo(1);
    assertThat(checker.accept(issue, chainReturnTrue)).isFalse();
    assertThat(previous.isEmpty()).isTrue();
  }

  @Test
  public void same_line_precise_expectations_are_consumed_separately_before_legacy_fallback() {
    TextRange first = IssueKeyTest.range(1, 2, 1, 4);
    TextRange second = IssueKeyTest.range(1, 6, 1, 8);
    Multiset<IssueKey> previous = expected("file", IssueLocation.fromRange(first));
    previous.add(new IssueKey("file", "squid:S00103", 1, null, IssueLocation.fromRange(second)));
    previous.add(new IssueKey("file", "squid:S00103", 1));
    assertThat(checker.accept(preciseIssue("file", second), chainReturnTrue)).isFalse();
    assertThat(previous.contains(new IssueKey("file", "squid:S00103", 1))).isTrue();
    assertThat(checker.accept(preciseIssue("file", first), chainReturnTrue)).isFalse();
    assertThat(checker.accept(preciseIssue("file", first), chainReturnTrue)).isFalse();
    assertThat(checker.accept(preciseIssue("file", second), chainReturnTrue)).isTrue();
  }

  @Test
  public void capture_and_save_use_source_content_and_encoding() {
    InputFile file = TestInputFileBuilder.create("project", "example.java")
      .setModuleBaseDir(temporaryFolder.getRoot().toPath()).setContents("aé\r\nbé\n")
      .setCharset(StandardCharsets.UTF_8).build();
    fileSystem.add(file);
    TextRange range = file.newRange(2, 1, 2, 2);
    FilterableIssue issue = preciseIssue(file.key(), range);
    assertThat(checker.accept(issue, chainReturnTrue)).isTrue();
    checker.save();
    IssueKey saved = Dump.load(output).get(file.key()).iterator().next();
    assertThat(saved.location.charOffset).isEqualTo(5);
    assertThat(saved.location.charLength).isEqualTo(1);
    assertThat(saved.location.byteOffset).isEqualTo(6);
    assertThat(saved.location.byteLength).isEqualTo(2);
  }

  @Test
  public void precise_offsets_are_checked_when_present_in_expectations() {
    TextRange range = IssueKeyTest.range(1, 2, 1, 4);
    Multiset<IssueKey> previous = expected("file", new IssueLocation(new Region().withStartLine(1).withStartColumn(3).withEndLine(1).withEndColumn(5)
      .withCharOffset(2).withCharLength(2).withByteOffset(2).withByteLength(2)));
    assertThat(checker.accept(preciseIssue("file", range), chainReturnTrue)).isTrue();
    assertThat(previous.size()).isEqualTo(1);
  }

  private Multiset<IssueKey> expected(String component, IssueLocation location) {
    Multiset<IssueKey> previous = Multiset.create();
    previous.add(new IssueKey(component, "squid:S00103", location.startLine, null, location));
    checker.getPrevious().put(component, previous);
    return previous;
  }

  private static FilterableIssue preciseIssue(String component, TextRange range) {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn(component);
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));
    when(issue.line()).thenReturn(range.start().line());
    when(issue.textRange()).thenReturn(range);
    when(issue.severity()).thenReturn("INFO");
    return issue;
  }

  @Test
  public void should_fail_when_previous_issue_is_not_info() {
    FilterableIssue issue = mock(FilterableIssue.class);
    when(issue.componentKey()).thenReturn("project:src/Example.java");
    when(issue.ruleKey()).thenReturn(RuleKey.of("squid", "S00103"));
    when(issue.line()).thenReturn(1);
    when(issue.severity()).thenReturn("BLOCKER");

    assertThrows(IllegalStateException.class, () ->
      checker.accept(issue, chainReturnTrue));
  }

  @Test
  public void should_fail_when_inactive_rules() {
    checker.inactiveRule("squid:S00103");
    MessageException e = assertThrows(MessageException.class, () ->
      checker.save());
    assertThat(e.getMessage()).isEqualTo("Inactive rules: squid:S00103");
    assertThat(output).exists();
  }

  @Test
  public void getPrevious_should_return_empty_list_when_no_output_directory() throws IOException {
    String nonExistingPath = new File("file/does/not/exist").getCanonicalPath();

    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, nonExistingPath);
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, nonExistingPath);
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, nonExistingPath);
    checker = new IssuesChecker(settings.asConfig(), activeRules, new DefaultFileSystem(temporaryFolder.getRoot()));

    Map previous = checker.getPrevious();

    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs().get(0)).startsWith("Directory not found:");
    assertThat(previous).isEmpty();
  }

  @Test
  public void should_fail_when_missing_resources() {
    checker.missingResource("missing_resource");
    MessageException e = assertThrows(MessageException.class, () ->
      checker.save());
    assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: missing_resource");
    assertThat(output).exists();
  }

  @Test
  public void should_delete_existing_output_and_difference_artifacts() throws Exception {
    File nestedDirectory = new File(output, "nested");
    assertThat(nestedDirectory.mkdirs()).isTrue();
    Files.write(new File(nestedDirectory, "stale.txt").toPath(), "stale".getBytes(StandardCharsets.UTF_8));
    Files.write(assertion.toPath(), "old".getBytes(StandardCharsets.UTF_8));

    checker.save();

    assertThat(output).doesNotExist();
    assertThat(assertion).exists();
    assertThat(Files.readAllBytes(assertion.toPath())).isEmpty();
  }

  @Test
  public void multiple_missing_resources_show_all_names_in_error_message() {
    checker.missingResource("first_missing");
    checker.missingResource("second_missing");
    checker.missingResource("third_missing");
    MessageException e = assertThrows(MessageException.class, () ->
      checker.save());
    assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: first_missing, third_missing, second_missing");
    assertThat(output).exists();
  }

  private MapSettings newCorrectSettings() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, new File("src/test/resources/").getAbsolutePath());
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, output.getAbsolutePath());
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, assertion.getAbsolutePath());
    return settings;
  }

}
