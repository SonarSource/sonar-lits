/*
 * Sonar LITS Plugin
 * Copyright (C) 2013-2017 SonarSource SA
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


import com.google.common.collect.Maps;
import freemarker.template.Template;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;

public class HtmlReport {
  private static final Logger LOG = LoggerFactory.getLogger(HtmlReport.class);

  private static final String REPORT_NAME = "issues-report";
  private final FileSystem fs;

  public HtmlReport(FileSystem fs) {
    this.fs = fs;
  }

  public void print(LitsReport litsReport) {
    File reportFileDir = getReportFileDir();
    File reportFile = new File(reportFileDir, REPORT_NAME + ".html");
    LOG.debug("Generating HTML Report to: " + reportFile.getAbsolutePath());
    writeToFile(litsReport, reportFile);
    LOG.info("HTML Issues Report generated: " + reportFile.getAbsolutePath());

    try {
      copyDependencies(reportFileDir);
    } catch (Exception e) {
      throw new IllegalStateException("Fail to copy HTML report resources to: " + reportFileDir, e);
    }
  }

  private File getReportFileDir() {
    File reportFileDir = new File(fs.workDir(), REPORT_NAME);
    try {
      FileUtils.forceMkdir(reportFileDir);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to create the directory " + reportFileDir, e);
    }
    return reportFileDir;
  }

  private void writeToFile(LitsReport report, File toFile) {
    try {
      freemarker.log.Logger.selectLoggerLibrary(freemarker.log.Logger.LIBRARY_NONE);
      freemarker.template.Configuration cfg = new freemarker.template.Configuration();
      cfg.setClassForTemplateLoading(HtmlReport.class, "");

      Map<String, Object> root = Maps.newHashMap();
      root.put("report", report);

      Template template = cfg.getTemplate("issuesreport.ftl");

      try (FileOutputStream fos = new FileOutputStream(toFile); Writer writer = new OutputStreamWriter(fos, fs.encoding())) {
        template.process(root, writer);
        writer.flush();
      }
    } catch (Exception e) {
      throw new IllegalStateException("Fail to generate HTML Issues Report to: " + toFile, e);

    }
  }

  private void copyDependencies(File toDir) throws IOException {
    File target = new File(toDir, "files");
    FileUtils.forceMkdir(target);

    // I don't know how to extract a directory from classpath, that's why an exhaustive list of files
    // is provided here :
    copyDependency(target, "sonar.eot");
    copyDependency(target, "sonar.svg");
    copyDependency(target, "sonar.ttf");
    copyDependency(target, "sonar.woff");
    copyDependency(target, "favicon.ico");
    copyDependency(target, "PRJ.png");
    copyDependency(target, "DIR.png");
    copyDependency(target, "FIL.png");
    copyDependency(target, "jquery.min.js");
    copyDependency(target, "sep12.png");
    copyDependency(target, "sonar.css");
  }

  private void copyDependency(File target, String filename) {
    try (InputStream input = getClass().getResourceAsStream("/com/sonarsource/lits/files/" + filename);
         OutputStream output = new FileOutputStream(new File(target, filename))) {
      IOUtils.copy(input, output);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to copy file " + filename + " to " + target, e);
    }
  }

}
