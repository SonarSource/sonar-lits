/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.Iterables;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.Location;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.issue.Issue;
import org.sonar.wsclient.issue.IssueQuery;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;

import java.io.File;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class IssuesCheckerIT {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .addPlugin(getPluginLocation())
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile.xml"))
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile_incorrect.xml"))
    .build();

  @Before
  public void resetData() throws Exception {
    orchestrator.resetData();
  }

  private static Location getPluginLocation() {
    return FileLocation.of(Iterables.getOnlyElement(FileUtils.listFiles(new File("target"), new String[] {"jar"}, false)));
  }

  private final File projectDir = new File("src/test/project/").getAbsoluteFile();

  @Test
  public void differences() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile")
      .setProperties("dump.old", new File(projectDir, "dumps/differences/").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(2);
    assertThat(violation("BLOCKER").line()).isEqualTo(3);
    assertThat(violation("INFO").line()).isEqualTo(2);
  }

  @Test
  public void no_differences() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile")
      .setProperties("dump.old", new File(projectDir, "dumps/no_differences/").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    assertThat(output).doesNotExist();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(0);
  }

  @Test
  public void rule_removed() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile")
      .setProperties("dump.old", new File(projectDir, "dumps/rule_removed/").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Inactive rules: squid:not_in_profile");

    assertThat(output).exists();

    assertThat(project()).isNull();
  }

  @Test
  public void missing_issue_on_file() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile")
      .setProperties("dump.old", new File(projectDir, "dumps/missing_issue_on_file/").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(violation("BLOCKER").line()).isNull();
  }

  @Test
  public void missing_file() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile")
      .setProperties("dump.old", new File(projectDir, "dumps/missing_file/").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Missing resources: project:src/Missing.java");

    assertThat(output).exists();

    assertThat(project()).isNull();
  }

  @Test
  public void profile_incorrect() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile_incorrect")
      .setProperties("dump.old", new File(projectDir, "dumps/differences/").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Rule 'squid:S00103' must be declared with severity INFO");
  }

  private Issue violation(String severity) {
    List<Issue> violations = violations(severity);
    assertThat(violations.size()).isEqualTo(1);
    return violations.get(0);
  }

  private List<Issue> violations(String severity) {
    return orchestrator.getServer().wsClient().issueClient().find(IssueQuery.create().severities(severity)).list();
  }

  private Resource project() {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("project", "violations"));
  }

}
