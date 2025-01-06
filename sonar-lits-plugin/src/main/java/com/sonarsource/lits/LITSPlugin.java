/*
 * Sonar LITS Plugin
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
package com.sonarsource.lits;

import org.sonar.api.Plugin;

public class LITSPlugin implements Plugin {

  static final String OLD_DUMP_PROPERTY = "sonar.lits.dump.old";
  static final String NEW_DUMP_PROPERTY = "sonar.lits.dump.new";
  static final String DIFFERENCES_PROPERTY = "sonar.lits.differences";

  @Override
  public void define(Context context) {
    context.addExtensions(IssuesChecker.class, DumpPhase.class);
  }

}
