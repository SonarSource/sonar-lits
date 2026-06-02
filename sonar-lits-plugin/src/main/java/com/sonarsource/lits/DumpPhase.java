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

import java.util.HashSet;
import java.util.Set;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

// must be public for SQ picocontainer
@Phase(name = Phase.Name.POST)
public class DumpPhase implements Sensor {

  private final IssuesChecker checker;

  // must be public for SQ picocontainer
  public DumpPhase(IssuesChecker checker) {
    this.checker = checker;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("LITS")
      .global();
  }

  @Override
  public void execute(SensorContext context) {
    Set<String> knownResources = new HashSet<>();
    FileSystem fs = context.fileSystem();
    for (InputFile inputFile : fs.inputFiles(fs.predicates().all())) {
      InputDir inputDir = fs.inputDir(inputFile.file());
      if (inputDir != null && knownResources.add(inputDir.key())) {
        checker.knownResource(inputDir.key());
      }
      if (knownResources.add(inputFile.key())) {
        checker.knownResource(inputFile.key());
      }
    }
  }

}
