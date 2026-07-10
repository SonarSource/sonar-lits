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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Multiset<E> implements Iterable<E> {

  private final Map<E, Integer> counts = new LinkedHashMap<>();
  private int size;

  static <E> Multiset<E> create() {
    return new Multiset<>();
  }

  static <E> Multiset<E> empty() {
    return new Multiset<>();
  }

  void add(E element) {
    counts.merge(element, 1, Integer::sum);
    size++;
  }

  boolean contains(E element) {
    return counts.containsKey(element);
  }

  boolean remove(E element) {
    Integer count = counts.get(element);
    if (count == null) {
      return false;
    }
    if (count == 1) {
      counts.remove(element);
    } else {
      counts.put(element, count - 1);
    }
    size--;
    return true;
  }

  boolean isEmpty() {
    return size == 0;
  }

  int size() {
    return size;
  }

  void clear() {
    counts.clear();
    size = 0;
  }

  @Override
  public Iterator<E> iterator() {
    List<E> elements = new ArrayList<>(size);
    for (Map.Entry<E, Integer> entry : counts.entrySet()) {
      for (int i = 0; i < entry.getValue(); i++) {
        elements.add(entry.getKey());
      }
    }
    return elements.iterator();
  }

  @Override
  public String toString() {
    return counts.toString();
  }
}
