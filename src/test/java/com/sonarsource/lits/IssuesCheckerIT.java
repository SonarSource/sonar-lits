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
      .setProfile("Test")
      .setProperties("dump.old", new File(projectDir, "dump.json").toString(), "dump.new", output.toString())
      .setProperty("sonar.cpd.skip", "true");
    orchestrator.executeBuild(build);

    assertThat(output).exists();

    Resource resource = getResource("sample");
    assertThat(resource.getMeasure("blocker_violations").getValue()).isEqualTo(1);
    assertThat(resource.getMeasure("critical_violations").getValue()).isEqualTo(1);
    assertThat(resource.getMeasure("info_violations").getValue()).isEqualTo(1);
    assertThat(resource.getMeasure("violations").getValue()).isEqualTo(3);
  }

  private Resource getResource(String key) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(key, "violations", "blocker_violations", "critical_violations", "info_violations"));
  }

}
