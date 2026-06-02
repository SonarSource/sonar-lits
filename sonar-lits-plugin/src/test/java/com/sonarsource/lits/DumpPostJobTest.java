/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2025 SonarSource Sàrl
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
package com.sonarsource.lits;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.MessageException;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class DumpPostJobTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private File oldDump;
  private File newDump;
  private File differences;

  @Before
  public void setup() throws Exception {
    oldDump = temporaryFolder.newFolder("old");
    newDump = new File(temporaryFolder.getRoot(), "new");
    differences = new File(temporaryFolder.getRoot(), "differences");
  }

  @Test
  public void should_count_missing_issues_on_known_resources() {
    Dump.save(
      Arrays.asList(
        new IssueKey("project:src/Example.java", "squid:S00103", 1),
        new IssueKey("project:src/Example.java", "squid:S00104", 2)
      ),
      oldDump
    );

    ActiveRules activeRules = new ActiveRulesBuilder()
      .create(RuleKey.of("squid", "S00103"))
      .setSeverity("INFO")
      .activate()
      .create(RuleKey.of("squid", "S00104"))
      .setSeverity("INFO")
      .activate()
      .build();
    IssuesChecker checker = new IssuesChecker(newSettings().asConfig(), activeRules);
    checker.knownResource("project:src/Example.java");

    new DumpPostJob(checker, activeRules).execute(mock(PostJobContext.class));

    assertThat(checker.differences).isEqualTo(2);
    assertThat(checker.getPrevious().get("project:src/Example.java")).isEmpty();
    assertThat(read(differences)).isEqualTo("Issues differences: 2");
  }

  @Test
  public void should_fail_when_remaining_issues_are_inactive() {
    Dump.save(
      Arrays.asList(new IssueKey("project:src/Example.java", "squid:S00103", 1)),
      oldDump
    );

    ActiveRules activeRules = new ActiveRulesBuilder().build();
    IssuesChecker checker = new IssuesChecker(newSettings().asConfig(), activeRules);
    checker.knownResource("project:src/Example.java");

    MessageException e = assertThrows(
      MessageException.class,
      () -> new DumpPostJob(checker, activeRules).execute(mock(PostJobContext.class))
    );

    assertThat(e.getMessage()).isEqualTo("Inactive rules: squid:S00103");
  }

  @Test
  public void should_fail_when_expected_resources_were_not_analyzed() {
    Dump.save(
      Arrays.asList(new IssueKey("project:src/Example.java", "squid:S00103", 1)),
      oldDump
    );

    ActiveRules activeRules = new ActiveRulesBuilder()
      .create(RuleKey.of("squid", "S00103"))
      .setSeverity("INFO")
      .activate()
      .build();
    IssuesChecker checker = new IssuesChecker(newSettings().asConfig(), activeRules);

    MessageException e = assertThrows(
      MessageException.class,
      () -> new DumpPostJob(checker, activeRules).execute(mock(PostJobContext.class))
    );

    assertThat(e.getMessage()).isEqualTo("Files listed in Expected directory were not analyzed: project:src/Example.java");
  }

  private MapSettings newSettings() {
    MapSettings settings = new MapSettings();
    settings.setProperty(LITSPlugin.OLD_DUMP_PROPERTY, oldDump.getAbsolutePath());
    settings.setProperty(LITSPlugin.NEW_DUMP_PROPERTY, newDump.getAbsolutePath());
    settings.setProperty(LITSPlugin.DIFFERENCES_PROPERTY, differences.getAbsolutePath());
    return settings;
  }

  private static String read(File file) {
    try {
      return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

}
