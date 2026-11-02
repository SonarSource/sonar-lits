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
import org.sonar.api.batch.fs.TextRange;
import org.sonar.scanner.plugin.api.impl.fs.DefaultTextPointer;
import org.sonar.scanner.plugin.api.impl.fs.DefaultTextRange;

import static org.fest.assertions.Assertions.assertThat;

public class IssueKeyTest {

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
    IssueLocation first = new IssueLocation(new Region().withStartLine(1).withStartColumn(1).withEndLine(1).withEndColumn(3));
    IssueLocation second = new IssueLocation(new Region().withStartLine(1).withStartColumn(5).withEndLine(1).withEndColumn(7));
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
  public void ranges_preserve_multiline_coordinates_and_exclusive_end_columns() {
    IssueLocation location = IssueLocation.fromRange(range(2, 1, 3, 4));
    assertThat(location.startLine).isEqualTo(2);
    assertThat(location.startColumn).isEqualTo(2);
    assertThat(location.endLine).isEqualTo(3);
    assertThat(location.endColumn).isEqualTo(5);
  }

  @Test
  public void supplied_offsets_and_lengths_do_not_affect_identity_or_order() {
    Region region = new Region().withStartLine(1).withStartColumn(2).withEndLine(1).withEndColumn(4);
    IssueLocation expected = new IssueLocation(region);
    IssueLocation withOffsets = new IssueLocation(region.withCharOffset(42).withCharLength(7).withByteOffset(99).withByteLength(12));
    assertThat(withOffsets).isEqualTo(expected);
    assertThat(withOffsets.hashCode()).isEqualTo(expected.hashCode());
    assertThat(withOffsets.compareTo(expected)).isEqualTo(0);
  }

  static TextRange range(int startLine, int startOffset, int endLine, int endOffset) {
    return new DefaultTextRange(new DefaultTextPointer(startLine, startOffset), new DefaultTextPointer(endLine, endOffset));
  }

}
