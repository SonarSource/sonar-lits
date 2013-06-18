/*
 * Copyright (C) 2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonarsource.lits;

import com.google.common.base.Throwables;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.io.Closeables;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Dump {

  private Dump() {
  }

  public static Map<String, Multiset<IssueKey>> load(File file) {
    FileInputStream fis = null;
    JSONObject json;
    try {
      fis = new FileInputStream(file);
      json = (JSONObject) JSONValue.parse(fis);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    } finally {
      Closeables.closeQuietly(fis);
    }

    Map<String, Multiset<IssueKey>> result = Maps.newHashMap();

    for (Map.Entry<String, Object> resource : json.entrySet()) {
      String resourceKey = resource.getKey();

      Multiset<IssueKey> issues = HashMultiset.create();
      result.put(resourceKey, issues);

      JSONObject rules = (JSONObject) resource.getValue();
      for (Map.Entry<String, Object> rule : rules.entrySet()) {
        String ruleKey = rule.getKey();
        JSONArray lines = (JSONArray) rule.getValue();
        for (Object line : lines) {
          issues.add(new IssueKey(resourceKey, ruleKey, (Integer) line));
        }
      }
    }

    return result;
  }

  public static void save(List<IssueKey> dump, File file) {
    PrintStream out = null;
    try {
      out = new PrintStream(new FileOutputStream(file));
      Dump.save(dump, out);
    } catch (FileNotFoundException e) {
      throw Throwables.propagate(e);
    } finally {
      Closeables.closeQuietly(out);
    }
  }

  public static void save(List<IssueKey> issues, PrintStream out) {
    Collections.sort(issues);

    String prevRuleKey = null;
    String prevComponentKey = null;
    out.println("{");
    for (IssueKey issueKey : issues) {
      if (!issueKey.componentKey.equals(prevComponentKey)) {
        if (prevRuleKey != null) {
          endComponent(out);
        }
        out.println("  '" + issueKey.componentKey + "': {");
        out.println("    '" + issueKey.ruleKey + "': [");
      } else if (!issueKey.ruleKey.equals(prevRuleKey)) {
        endRule(out);
        out.println("    '" + issueKey.ruleKey + "': [");
      }
      out.println("      " + issueKey.line + ",");
      prevComponentKey = issueKey.componentKey;
      prevRuleKey = issueKey.ruleKey;
    }
    endComponent(out);
    out.println("}");
  }

  private static void endComponent(PrintStream out) {
    endRule(out);
    out.println("  },");
  }

  private static void endRule(PrintStream out) {
    out.println("    ],");
  }

}
