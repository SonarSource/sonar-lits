/*
 * SonarSource :: LITS :: ITs :: Plugin
 * Copyright (C) 2013-2025 SonarSource SA
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
package com.sonar.it.lits;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Measures;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.measures.ComponentRequest;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.fest.assertions.Assertions.assertThat;

public class LitsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .useDefaultAdminCredentialsForBuilds(true)
    .setSonarVersion(System.getProperty("sonar.runtimeVersion"))
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", System.getProperty("javaVersion")))
    .addPlugin(FileLocation.byWildcardMavenFilename(new File("../../sonar-lits-plugin/target"), "sonar-lits-plugin-*.jar"))
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile.xml"))
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile_incorrect.xml"))
    .build();

  @Before
  public void before() throws Exception {
    output = new File(temporaryFolder.newFolder(), "dump");
  }

  private final File projectDir = new File("src/test/project/").getAbsoluteFile();
  private File output = null;

  @Test
  public void differences() throws Exception {
    final String projectKey = "differences";
    SonarScanner build = createSonarRunner(projectKey, "dumps/differences/");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(getIssuesMeasure(projectKey)).isEqualTo(2);
    assertThat(issue(projectKey, "BLOCKER").getLine()).isEqualTo(3);
    assertThat(issue(projectKey, "INFO").getLine()).isEqualTo(2);
  }

  @Test
  public void no_differences() throws Exception {
    final String projectKey = "noDifferences";
    SonarScanner build = createSonarRunner(projectKey, "dumps/no_differences/");

    orchestrator.executeBuild(build);

    assertThat(output).doesNotExist();

    assertThat(getIssuesMeasure(projectKey)).isEqualTo(0);
  }

  @Test
  public void rule_removed() throws Exception {
    final String projectKey = "ruleRemoved";
    SonarScanner build = createSonarRunner(projectKey, "dumps/rule_removed/");

    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getLastStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Inactive rules: squid:not_in_profile");

    assertThat(output).exists();
  }

  @Test
  public void missing_issue_on_file() throws Exception {
    final String projectKey = "missingIssueOnFile";
    SonarScanner build = createSonarRunner(projectKey, "dumps/missing_issue_on_file/");

    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(issue(projectKey, "BLOCKER").getLine()).isEqualTo(0);
  }

  @Test
  public void missing_file() throws Exception {
    final String projectKey = "missingFile";
    SonarScanner build = createSonarRunner(projectKey, "dumps/missing_file/");

    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getLastStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Files listed in Expected directory were not analyzed: missingFile:src/Missing.java");

    assertThat(output).exists();
  }

  @Test
  public void profile_incorrect() throws Exception {
    final String projectKey = "profileIncorrect";
    SonarScanner build = createSonarRunner(projectKey, "dumps/differences/");
    orchestrator.getServer().associateProjectToQualityProfile(projectKey, "java", "profile_incorrect");
    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getLastStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Rule 'java:S103' must be declared with severity INFO");
  }

  private SonarScanner createSonarRunner(String projectKey, String dumpOld) throws IOException {
    orchestrator.getServer().provisionProject(projectKey, projectKey);
    orchestrator.getServer().associateProjectToQualityProfile(projectKey, "java", "profile");
    return SonarScanner.create(projectDir)
      .setProperty("sonar.scanner.skipJreProvisioning", "true")
      .setProjectKey(projectKey)
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProperty("sonar.lits.dump.old", new File(projectDir, dumpOld).toString())
      .setProperty("sonar.lits.dump.new", output.toString())
      .setProperty("sonar.lits.differences", temporaryFolder.newFile("differences").getAbsolutePath())
      .setProperty("sonar.cpd.skip", "true");
  }

  private static Issues.Issue issue(String projectKey, String severity) {
    List<Issues.Issue> violations = issues(projectKey, severity);
    assertThat(violations.size()).isEqualTo(1);
    return violations.get(0);
  }

  static List<Issues.Issue> issues(String componentKey, String severity) {
    return newWsClient().issues().search(new SearchRequest()
      .setSeverities(Collections.singletonList(severity))
      .setComponentKeys(singletonList(componentKey))
    ).getIssuesList();
  }

  private static Integer getIssuesMeasure(String projectKey) {
    return getMeasureAsInt(projectKey, "violations");
  }

  @CheckForNull
  private static Integer getMeasureAsInt(String componentKey, String metricKey) {
    Measures.Measure measure = getMeasure(componentKey, metricKey);
    return (measure == null) ? null : Integer.parseInt(measure.getValue());
  }

  @CheckForNull
  private static Measures.Measure getMeasure(String componentKey, String metricKey) {
    Measures.ComponentWsResponse response = newWsClient().measures().component(new ComponentRequest()
      .setComponent(componentKey)
      .setMetricKeys(Collections.singletonList(metricKey)));
    List<Measures.Measure> measures = response.getComponent().getMeasuresList();
    return measures.size() == 1 ? measures.get(0) : null;
  }

  private static WsClient newWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(orchestrator.getServer().getUrl())
      .build());
  }

}
