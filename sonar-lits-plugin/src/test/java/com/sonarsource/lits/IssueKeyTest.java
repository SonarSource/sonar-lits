/*
 * Sonar LITS Plugin
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import org.junit.Test;
import com.sonarsource.lits.IssueKey.IssueLocation;
import com.sonarsource.lits.sarif.Region;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.scanner.plugin.api.impl.fs.DefaultTextPointer;
import org.sonar.scanner.plugin.api.impl.fs.DefaultTextRange;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueKeyTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void creation() {
    IssueKey issueKey = new IssueKey("componentKey", "ruleKey", null);
    assertThat(issueKey.componentKey).isEqualTo("componentKey");
    assertThat(issueKey.ruleKey).isEqualTo("ruleKey");
    assertThat(issueKey.line).isEqualTo(0);
  }

  @Test
  public void equals() {
    IssueKey issueKey = new IssueKey("componentKey", "ruleKey", null);
    assertThat(issueKey.equals(issueKey)).isTrue();
    assertThat(issueKey.equals(new IssueKey("componentKey", "ruleKey", null))).isTrue();
    assertThat(issueKey.equals(new IssueKey("componentKey", "ruleKey", 1))).isFalse();
    assertThat(issueKey.equals(new IssueKey("componentKey", "ruleKey2", null))).isFalse();
    assertThat(issueKey.equals(new IssueKey("componentKey2", "ruleKey", null))).isFalse();
    assertThat(issueKey.equals(new Object())).isFalse();
  }

  @Test
  public void compareTo() {
    IssueKey issueKey = new IssueKey("b", "b", 2);

    assertThat(issueKey.compareTo(new IssueKey("b", "b", 2))).isEqualTo(0);

    assertThat(issueKey.compareTo(new IssueKey("a", "b", 2))).isEqualTo(1);
    assertThat(issueKey.compareTo(new IssueKey("c", "b", 2))).isEqualTo(-1);

    assertThat(issueKey.compareTo(new IssueKey("b", "a", 2))).isEqualTo(1);
    assertThat(issueKey.compareTo(new IssueKey("b", "c", 2))).isEqualTo(-1);

    assertThat(issueKey.compareTo(new IssueKey("b", "b", 1))).isEqualTo(1);
    assertThat(issueKey.compareTo(new IssueKey("b", "b", 3))).isEqualTo(-1);
  }

  @Test
  public void precise_locations_preserve_identity_and_order() {
    IssueLocation first = new IssueLocation(new Region().withStartLine(1).withStartColumn(1).withEndLine(1).withEndColumn(3)
      .withCharOffset(0).withCharLength(2).withByteOffset(0).withByteLength(2));
    IssueLocation second = new IssueLocation(new Region().withStartLine(1).withStartColumn(5).withEndLine(1).withEndColumn(7)
      .withCharOffset(4).withCharLength(2).withByteOffset(4).withByteLength(2));
    IssueKey firstKey = new IssueKey("file", "rule", 1, null, first);
    IssueKey secondKey = new IssueKey("file", "rule", 1, null, second);
    assertThat(firstKey).isNotEqualTo(secondKey);
    assertThat(firstKey.compareTo(secondKey)).isLessThan(0);
    assertThat(firstKey).isEqualTo(new IssueKey("file", "rule", 1, "different message", first));
    assertThat(firstKey.hashCode()).isEqualTo(new IssueKey("file", "rule", 1, null, first).hashCode());
    assertThat(firstKey).isNotEqualTo(new IssueKey("file", "rule", 1));
    Multiset<IssueKey> issues = Multiset.create();
    issues.add(firstKey);
    issues.add(secondKey);
    assertThat(issues.remove(firstKey)).isTrue();
    assertThat(issues.contains(secondKey)).isTrue();
  }

  @Test
  public void offsets_include_unicode_and_original_line_endings() throws Exception {
    IssueLocation location = locate("a\uD83D\uDE00é\r\nbé\rcd\n", StandardCharsets.UTF_8, range(2, 1, 3, 1));
    assertThat(location.startColumn).isEqualTo(2);
    assertThat(location.endLine).isEqualTo(3);
    assertThat(location.endColumn).isEqualTo(2);
    assertThat(location.charOffset).isEqualTo(7);
    assertThat(location.charLength).isEqualTo(3);
    assertThat(location.byteOffset).isEqualTo(10);
    assertThat(location.byteLength).isEqualTo(4);
  }

  @Test
  public void utf8_bom_is_excluded_from_characters_but_included_in_bytes() throws Exception {
    IssueLocation location = locate("\uFEFFaé\n", StandardCharsets.UTF_8, range(1, 0, 1, 2));
    assertThat(location.charOffset).isEqualTo(0);
    assertThat(location.charLength).isEqualTo(2);
    assertThat(location.byteOffset).isEqualTo(3);
    assertThat(location.byteLength).isEqualTo(3);
  }

  @Test
  public void raw_file_bytes_preserve_boms_stripped_by_sonar_input_streams() throws Exception {
    File source = temporaryFolder.newFile();
    Files.write(source.toPath(), "\uFEFFaé".getBytes(StandardCharsets.UTF_8));
    InputFile file = mock(InputFile.class);
    when(file.path()).thenReturn(source.toPath());
    when(file.charset()).thenReturn(StandardCharsets.UTF_8);
    when(file.inputStream()).thenReturn(new ByteArrayInputStream("aé".getBytes(StandardCharsets.UTF_8)));
    IssueLocation location = new IssueLocation.Source(file).locate(range(1, 1, 1, 2));
    assertThat(location.charOffset).isEqualTo(1);
    assertThat(location.byteOffset).isEqualTo(4);
    assertThat(location.byteLength).isEqualTo(2);
  }

  @Test
  public void generic_utf16_detects_little_endian_bom() throws Exception {
    File source = temporaryFolder.newFile();
    Files.write(source.toPath(), "\uFEFFaé".getBytes(StandardCharsets.UTF_16LE));
    InputFile file = mock(InputFile.class);
    when(file.path()).thenReturn(source.toPath());
    when(file.charset()).thenReturn(StandardCharsets.UTF_16);
    IssueLocation location = new IssueLocation.Source(file).locate(range(1, 1, 1, 2));
    assertThat(location.charOffset).isEqualTo(1);
    assertThat(location.byteOffset).isEqualTo(4);
    assertThat(location.byteLength).isEqualTo(2);
  }

  @Test
  public void utf32_bom_and_supplementary_characters_have_precise_offsets() throws Exception {
    IssueLocation location = locate("\uFEFFa\uD83D\uDE00", Charset.forName("UTF-32LE"), range(1, 1, 1, 3));
    assertThat(location.charOffset).isEqualTo(1);
    assertThat(location.charLength).isEqualTo(2);
    assertThat(location.byteOffset).isEqualTo(8);
    assertThat(location.byteLength).isEqualTo(4);
  }

  @Test
  public void utf16_and_single_byte_encodings_have_real_byte_offsets() throws Exception {
    IssueLocation utf16 = locate("a\r\né", StandardCharsets.UTF_16, range(2, 0, 2, 1));
    assertThat(utf16.charOffset).isEqualTo(3);
    assertThat(utf16.byteOffset).isEqualTo(8);
    assertThat(utf16.byteLength).isEqualTo(2);
    IssueLocation latin = locate("a\né", StandardCharsets.ISO_8859_1, range(2, 0, 2, 1));
    assertThat(latin.byteOffset).isEqualTo(2);
    assertThat(latin.byteLength).isEqualTo(1);
  }

  @Test
  public void supplementary_characters_use_utf16_units_and_eof_ranges_are_supported() throws Exception {
    IssueLocation emoji = locate("a\uD83D\uDE00", StandardCharsets.UTF_8, range(1, 1, 1, 3));
    assertThat(emoji.charLength).isEqualTo(2);
    assertThat(emoji.byteLength).isEqualTo(4);
    IssueLocation eof = locate("a\n", StandardCharsets.UTF_8, range(2, 0, 2, 0));
    assertThat(eof.charOffset).isEqualTo(2);
    assertThat(eof.charLength).isEqualTo(0);
    assertThat(eof.byteOffset).isEqualTo(2);
    assertThat(eof.byteLength).isEqualTo(0);
  }

  @Test
  public void byte_offsets_are_not_invented_inside_utf8_surrogate_pairs() throws Exception {
    IssueLocation location = locate("\uD83D\uDE00", StandardCharsets.UTF_8, range(1, 0, 1, 1));
    assertThat(location.charLength).isEqualTo(1);
    assertThat(location.byteOffset).isNull();
    assertThat(location.byteLength).isNull();
  }

  private static IssueLocation locate(String text, Charset charset, TextRange range) throws Exception {
    InputFile file = mock(InputFile.class);
    when(file.charset()).thenReturn(charset);
    when(file.inputStream()).thenReturn(new ByteArrayInputStream(text.getBytes(charset)));
    return new IssueLocation.Source(file).locate(range);
  }

  static TextRange range(int startLine, int startOffset, int endLine, int endOffset) {
    return new DefaultTextRange(new DefaultTextPointer(startLine, startOffset), new DefaultTextPointer(endLine, endOffset));
  }

}
