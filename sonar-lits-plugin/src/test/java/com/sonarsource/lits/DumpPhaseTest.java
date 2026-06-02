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
import java.io.FileReader;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.FileMetadata;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DumpPhaseTest {

  private IssuesChecker checker;
  private DumpPhase decorator;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private SensorContextTester sensorContext;

  @Before
  public void setup() throws IOException {
    checker = mock(IssuesChecker.class);
    decorator = new DumpPhase(checker);

    sensorContext = SensorContextTester.create(new File("src/test/resources"));
    DefaultFileSystem fs = new DefaultFileSystem(new File("src/test/resources"));
    fs.setWorkDir(temporaryFolder.newFolder().toPath());
    fs.add(TestInputFileBuilder
      .create("", "example.cpp")
      .setLanguage("cpp")
      .setMetadata(new FileMetadata().readMetadata(new FileReader("src/test/resources/example.cpp")))
      .build());
    sensorContext.setFileSystem(fs);
  }

  @Test
  public void test_describe() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    decorator.describe(descriptor);
    assertThat(descriptor.name()).isEqualTo("LITS");
    assertThat(descriptor.isGlobal()).isTrue();
  }

  @Test
  public void should_collect_known_resources() {
    decorator.execute(sensorContext);

    InputFile inputFile = sensorContext.fileSystem().inputFiles(sensorContext.fileSystem().predicates().all()).iterator().next();
    verify(checker).knownResource(inputFile.key());
    String parentKey = parentKey(inputFile.key());
    if (parentKey != null) {
      verify(checker).knownResource(parentKey);
    }
  }

  private static String parentKey(String componentKey) {
    int lastSlash = componentKey.lastIndexOf('/');
    return lastSlash >= 0 ? componentKey.substring(0, lastSlash) : null;
  }

}
