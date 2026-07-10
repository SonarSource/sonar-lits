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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class MultisetTest {

  @Test
  public void remove_should_return_false_for_missing_element() {
    Multiset<String> multiset = Multiset.create();
    multiset.add("value");

    assertThat(multiset.remove("missing")).isFalse();
    assertThat(multiset.isEmpty()).isFalse();
    assertThat(multiset.size()).isEqualTo(1);
  }

  @Test
  public void remove_should_decrement_count_without_removing_entry() {
    Multiset<String> multiset = Multiset.create();
    multiset.add("value");
    multiset.add("value");

    assertThat(multiset.remove("value")).isTrue();

    List<String> values = new ArrayList<>();
    for (String value : multiset) {
      values.add(value);
    }

    assertThat(values).containsExactly("value");
    assertThat(multiset.size()).isEqualTo(1);
  }
}
