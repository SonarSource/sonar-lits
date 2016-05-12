/*
 * SonarSource :: LITS :: ITs :: Plugin
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
package com.sonar.it.lits;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.FileLocation;
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
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class LitsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .addPlugin("java")
    .addPlugin(FileLocation.of("../../target/sonar-lits-plugin.jar"))
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile.xml"))
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile_incorrect.xml"))
    .build();

  @Before
  public void resetData() throws Exception {
    orchestrator.resetData();
    output = new File(temporaryFolder.newFolder(), "dump");
  }

  private final File projectDir = new File("src/test/project/").getAbsoluteFile();
  private File output = null;

  @Test
  public void differences() throws Exception {
    SonarRunner build = createSonarRunner("dumps/differences/");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(2);
    assertThat(issue("BLOCKER").line()).isEqualTo(3);
    assertThat(issue("INFO").line()).isEqualTo(2);
  }

  @Test
  public void no_differences() throws Exception {
    SonarRunner build = createSonarRunner("dumps/no_differences/");

    orchestrator.executeBuild(build);

    assertThat(output).doesNotExist();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(0);
  }

  @Test
  public void rule_removed() throws Exception {
    SonarRunner build = createSonarRunner("dumps/rule_removed/");

    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Inactive rules: squid:not_in_profile");

    assertThat(output).exists();

    assertThat(project()).isNull();
  }

  @Test
  public void missing_issue_on_file() throws Exception {
    SonarRunner build = createSonarRunner("dumps/missing_issue_on_file/");

    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(issue("BLOCKER").line()).isNull();
  }

  @Test
  public void missing_file() throws Exception {
    SonarRunner build = createSonarRunner("dumps/missing_file/");

    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Missing resources: project:src/Missing.java");

    assertThat(output).exists();

    assertThat(project()).isNull();
  }

  @Test
  public void profile_incorrect() throws Exception {
    SonarRunner build = createSonarRunner("dumps/differences/");
    build.setProfile("profile_incorrect");
    BuildResult buildResult = orchestrator.executeBuildQuietly(build);

    assertThat(buildResult.getStatus()).isNotEqualTo(0);
    assertThat(buildResult.getLogs()).contains("Rule 'squid:S00103' must be declared with severity INFO");
  }

  private SonarRunner createSonarRunner(String dumpOld) throws IOException {
    return SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("profile")
      .setProperty("dump.old", new File(projectDir, dumpOld).toString())
      .setProperty("dump.new", output.toString())
      .setProperty("lits.differences", temporaryFolder.newFile("differences").getAbsolutePath())
      .setProperty("sonar.cpd.skip", "true");
  }

  private static Issue issue(String severity) {
    List<Issue> violations = issues(severity);
    assertThat(violations.size()).isEqualTo(1);
    return violations.get(0);
  }

  private static List<Issue> issues(String severity) {
    return orchestrator.getServer().wsClient().issueClient().find(IssueQuery.create().severities(severity)).list();
  }

  private static Resource project() {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("project", "violations"));
  }

}
