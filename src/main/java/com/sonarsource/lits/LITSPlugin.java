/*
 * Copyright (C) 2013-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.collect.ImmutableList;
import org.sonar.api.SonarPlugin;

import java.util.List;

public class LITSPlugin extends SonarPlugin {

  public static final String OLD_DUMP_PROPERTY = "dump.old";
  public static final String NEW_DUMP_PROPERTY = "dump.new";

  @Override
  public List getExtensions() {
    return ImmutableList.of(IssuesChecker.class, DumpPhase.class);
  }

}
