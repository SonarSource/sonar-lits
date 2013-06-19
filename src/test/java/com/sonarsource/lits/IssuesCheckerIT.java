/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.Location;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;
import org.sonar.wsclient.services.Violation;
import org.sonar.wsclient.services.ViolationQuery;

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
    .build();

  @Before
  public void resetData() throws Exception {
    orchestrator.getDatabase().truncateInspectionTables();
  }

  private static Location getPluginLocation() {
    String propertyName = "litsVersion";
    String version = System.getProperty(propertyName);
    if (StringUtils.isBlank(version)) {
      throw new RuntimeException("Please provide '" + propertyName + "' property");
    }
    return FileLocation.of("target/sonar-lits-plugin-" + version + ".jar");
  }

  private File projectDir = new File("src/test/project/").getAbsoluteFile();

  @Test
  public void differences() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump.json");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("Test")
      .setProperties("dump.old", new File(projectDir, "differences.json").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(3);
    assertThat(violation("BLOCKER").getLine()).isEqualTo(3);
    assertThat(violation("CRITICAL").getLine()).isEqualTo(2);
    assertThat(violation("INFO").getLine()).isEqualTo(1);
  }

  @Test
  public void no_differences() throws Exception {
    File output = new File(temporaryFolder.newFolder(), "dump.json");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("Test")
      .setProperties("dump.old", new File(projectDir, "no_differences.json").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true")
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    assertThat(output).doesNotExist();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(2);
    assertThat(violations("INFO").size()).isEqualTo(2);
  }

  private Violation violation(String severity) {
    List<Violation> violations = violations(severity);
    assertThat(violations.size()).isEqualTo(1);
    return violations.get(0);
  }

  private List<Violation> violations(String severity) {
    return orchestrator.getServer().getWsClient().findAll(ViolationQuery.createForResource("project").setDepth(-1).setSeverities(severity));
  }

  private Resource project() {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("project", "violations"));
  }

}
