/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
