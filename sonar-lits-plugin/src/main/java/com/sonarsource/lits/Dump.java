/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.io.Closeables;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Dump {

  private static final String EXT = "json";

  private Dump() {
  }

  public static Map<String, Multiset<IssueKey>> load(File dir) {
    Map<String, Multiset<IssueKey>> result = Maps.newHashMap();
    for (File file : FileUtils.listFiles(dir, new String[]{EXT}, false)) {
      load(file, result);
    }
    return result;
  }

  static void load(File file, Map<String, Multiset<IssueKey>> result) {
    InputStreamReader in = null;
    JSONObject json;
    try {
      in = new InputStreamReader(new FileInputStream(file), Charsets.UTF_8);
      json = (JSONObject) JSONValue.parse(in);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    } finally {
      Closeables.closeQuietly(in);
    }

    String ruleKey = ruleKeyFromFileName(file.getName());
    for (Map.Entry<String, Object> component : json.entrySet()) {
      String componentKey = component.getKey();

      Multiset<IssueKey> issues = result.get(componentKey);
      if (issues == null) {
        issues = HashMultiset.create();
        result.put(componentKey, issues);
      }

      JSONArray lines = (JSONArray) component.getValue();
      for (Object line : lines) {
        issues.add(new IssueKey(componentKey, ruleKey, (Integer) line));
      }
    }
  }

  public static void save(List<IssueKey> issues, File dir) {
    try {
      FileUtils.forceMkdir(dir);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }

    Collections.sort(issues, new IssueKeyComparator());

    PrintStream out = null;
    String prevRuleKey = null;
    String prevComponentKey = null;
    for (IssueKey issueKey : issues) {
      if (!issueKey.ruleKey.equals(prevRuleKey)) {
        if (out != null) {
          endRule(out);
        }
        try {
          out = new PrintStream(new FileOutputStream(new File(dir, ruleKeyToFileName(issueKey.ruleKey))), /* autoFlush: */ true, Charsets.UTF_8.name());
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
        out.print("{\n");
        startComponent(out, issueKey.componentKey);
      } else if (!issueKey.componentKey.equals(prevComponentKey)) {
        endComponent(out);
        startComponent(out, issueKey.componentKey);
      }
      out.print(issueKey.line + ",\n");
      prevComponentKey = issueKey.componentKey;
      prevRuleKey = issueKey.ruleKey;
    }
    if (out != null) {
      endRule(out);
    }
  }

  private static String ruleKeyToFileName(String ruleKey) {
    return ruleKey.replace(':', '-') + "." + EXT;
  }

  private static String ruleKeyFromFileName(String fileName) {
    return fileName.replaceFirst("-", ":").substring(0, fileName.length() - EXT.length() - 1);
  }

  private static void startComponent(PrintStream out, String componentKey) {
    out.print("'" + componentKey + "':[\n");
  }

  private static void endComponent(PrintStream out) {
    out.print("],\n");
  }

  private static void endRule(PrintStream out) {
    endComponent(out);
    out.print("}\n");
    Closeables.closeQuietly(out);
  }

  private static class IssueKeyComparator implements Comparator<IssueKey>, Serializable {
    private static final long serialVersionUID = 1;

    @Override
    public int compare(IssueKey left, IssueKey right) {
      int c = left.ruleKey.compareTo(right.ruleKey);
      if (c == 0) {
        c = left.componentKey.compareTo(right.componentKey);
        if (c == 0) {
          c = left.line - right.line;
        }
      }
      return c;
    }
  }

}
