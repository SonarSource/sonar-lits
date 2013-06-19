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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;
import org.sonar.wsclient.services.Violation;
import org.sonar.wsclient.services.ViolationQuery;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;

public class IssuesCheckerIT {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .addPlugin(getPluginLocation())
    .restoreProfileAtStartup(FileLocation.of("src/test/project/profile.xml"))
    .build();

  private static Location getPluginLocation() {
    String propertyName = "litsVersion";
    String version = System.getProperty(propertyName);
    if (StringUtils.isBlank(version)) {
      throw new RuntimeException("Please provide '" + propertyName + "' property");
    }
    return FileLocation.of("target/sonar-lits-plugin-" + version + ".jar");
  }

  @Test
  public void test() throws Exception {
    File projectDir = new File("src/test/project/").getAbsoluteFile();
    File output = new File(temporaryFolder.getRoot(), "new_dump.json");
    SonarRunner build = SonarRunner.create(projectDir)
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setSourceDirs("src")
      .setProfile("Test")
      .setProperties("dump.old", new File(projectDir, "dump.json").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    assertThat(project().getMeasure("violations").getValue()).isEqualTo(3);
    assertThat(violation("BLOCKER").getLine()).isEqualTo(3);
    assertThat(violation("CRITICAL").getLine()).isEqualTo(2);
    assertThat(violation("INFO").getLine()).isEqualTo(1);
  }

  private Violation violation(String severity) {
    return orchestrator.getServer().getWsClient().find(ViolationQuery.createForResource("project").setDepth(-1).setLimit(1).setSeverities(severity));
  }

  private Resource project() {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("project", "violations"));
  }

}
