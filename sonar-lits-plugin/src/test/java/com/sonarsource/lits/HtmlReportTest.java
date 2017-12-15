/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
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

import com.google.common.collect.ImmutableSet;
import com.sonarsource.lits.LitsReport.ReportedIssue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputDir;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import static org.fest.assertions.Assertions.assertThat;

public class HtmlReportTest {

  @org.junit.Rule
  public ExpectedException thrown = ExpectedException.none();

  @org.junit.Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String MODULE_KEY = "moduleKey";

  File moduleBaseDir = new File("src/test/resources");
  DefaultFileSystem fileSystem;
  HtmlReport htmlReport;
  File workDir;
  DefaultInputFile inputFile;

  @Before
  public void setUp() throws Exception {
    SensorContextTester sensorContextTester = SensorContextTester.create(moduleBaseDir);
    workDir = temporaryFolder.newFolder();
    fileSystem = sensorContextTester.fileSystem();
    fileSystem.setWorkDir(workDir.toPath());
    htmlReport = new HtmlReport(fileSystem);

    inputFile = inputFile("example.cpp");
  }

  @Test
  public void should_generate_report() throws Exception {
    Map<String, Set<ReportedIssue>> differentIssues = new HashMap<>();
    differentIssues.put(inputFile.key(), ImmutableSet.of(
      issue(inputFile.key(), 1, MODULE_KEY),
      issue(inputFile.key(), 9, inputFile)));

    htmlReport.print(new LitsReport(differentIssues, fileSystem));

    assertThat(new File(workDir, "issues-report/issues-report.html").exists()).isTrue();
  }

  @Test
  public void should_fail_when_invalid_component() throws Exception {
    Map<String, Set<ReportedIssue>> differentIssues = new HashMap<>();
    differentIssues.put("invalid-component-key", ImmutableSet.of(
      issue("invalid-component-key", 3, MODULE_KEY)));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("No InputFile found for component with key invalid-component-key");

    htmlReport.print(new LitsReport(differentIssues, fileSystem));
  }

  @Test
  public void should_not_fail_on_file_level_issues() throws Exception {
    Map<String, Set<ReportedIssue>> differentIssues = new HashMap<>();
    differentIssues.put(inputFile.key(), ImmutableSet.of(
      issue(inputFile.key(), 0, MODULE_KEY),
      issue(inputFile.key(), 10, MODULE_KEY),
      issue(inputFile.key(), 0, inputFile)));

    htmlReport.print(new LitsReport(differentIssues, fileSystem));

    assertThat(new File(workDir, "issues-report/issues-report.html").exists()).isTrue();
  }

  @Test
  public void should_show_several_files() throws Exception {
    InputFile anotherInputFile = inputFile("anotherExample.cpp");

    Map<String, Set<ReportedIssue>> differentIssues = new HashMap<>();
    differentIssues.put(inputFile.key(), ImmutableSet.of(issue(inputFile.key(), 3, MODULE_KEY)));
    differentIssues.put(anotherInputFile.key(), ImmutableSet.of(issue(anotherInputFile.key(), 0, MODULE_KEY)));

    htmlReport.print(new LitsReport(differentIssues, fileSystem));

    assertThat(new File(workDir, "issues-report/issues-report.html").exists()).isTrue();
  }

  @Test
  public void should_fail_with_issue_on_module_level() throws Exception {
    Map<String, Set<ReportedIssue>> differentIssues = new HashMap<>();
    differentIssues.put(MODULE_KEY, ImmutableSet.of(issue(MODULE_KEY, 3, MODULE_KEY)));

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Module/project level issues are not supported by LITS plugin");

    htmlReport.print(new LitsReport(differentIssues, fileSystem));
  }

  @Test
  public void should_show_issue_on_directory_level() throws Exception {
    DefaultInputDir nestedDir1 = inputDir("nestedDir1");
    DefaultInputDir nestedDir2 = inputDir("nestedDir2");

    Map<String, Set<ReportedIssue>> differentIssues = new HashMap<>();
    differentIssues.put(nestedDir1.key(), ImmutableSet.of(issue(nestedDir1.key(), 0, MODULE_KEY)));
    differentIssues.put(nestedDir2.key(), ImmutableSet.of(issue(nestedDir1.key(), 0, nestedDir2)));

    htmlReport.print(new LitsReport(differentIssues, fileSystem));

    assertThat(new File(workDir, "issues-report/issues-report.html").exists()).isTrue();
  }

  private ReportedIssue issue(String componentKey, int line, String moduleKey) {
    return new ReportedIssue("squid:S4200", componentKey, line, "Don't do that", "INFO", moduleKey);
  }

  private ReportedIssue issue(String componentKey, int line, InputComponent inputComponent) {
    return new ReportedIssue("squid:S4200", componentKey, line, "Don't do that", "INFO", inputComponent);
  }

  private DefaultInputFile inputFile(String relativePath) throws IOException {
    DefaultInputFile defaultInputFile = new TestInputFileBuilder(MODULE_KEY, relativePath)
      .setModuleBaseDir(moduleBaseDir.toPath())
      .initMetadata(new String(Files.readAllBytes(new File(moduleBaseDir, relativePath).toPath()), StandardCharsets.UTF_8))
      .setCharset(StandardCharsets.UTF_8)
      .build();

    fileSystem.add(defaultInputFile);

    return defaultInputFile;
  }

  private DefaultInputDir inputDir(String relativePath) throws IOException {
    DefaultInputDir defaultInputDir = new DefaultInputDir(MODULE_KEY, relativePath).setModuleBaseDir(moduleBaseDir.toPath());
    fileSystem.add(defaultInputDir);
    return defaultInputDir;
  }
}
