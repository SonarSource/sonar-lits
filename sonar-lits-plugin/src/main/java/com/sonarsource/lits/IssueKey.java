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

import com.sonarsource.lits.sarif.Region;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;

@Immutable
class IssueKey implements Comparable<IssueKey> {

  final String componentKey;
  final String ruleKey;
  final int line;
  @Nullable
  final String message;
  @Nullable
  final IssueLocation location;

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line) {
    this(componentKey, ruleKey, line, null);
  }

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line, @Nullable String message) {
    this(componentKey, ruleKey, line, message, null);
  }

  IssueKey(String componentKey, String ruleKey, @Nullable Integer line, @Nullable String message, @Nullable IssueLocation location) {
    this.componentKey = componentKey;
    this.ruleKey = ruleKey;
    int reportedLine = line == null ? 0 : line;
    this.line = location == null ? reportedLine : location.startLine;
    this.message = message;
    this.location = location;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof IssueKey) {
      IssueKey other = (IssueKey) obj;
      return this.line == other.line
        && Objects.equals(this.componentKey, other.componentKey)
        && Objects.equals(this.ruleKey, other.ruleKey)
        && Objects.equals(this.location, other.location);
    }
    return false;
  }

  @Override
  public int hashCode() {
    // Godin: maybe would be better for performance to cache hash code
    return Objects.hash(componentKey, ruleKey, line, location);
  }

  @Override
  public String toString() {
    return componentKey + " " + ruleKey + " " + line;
  }

  @Override
  public int compareTo(IssueKey other) {
    // Godin: maybe would be better for performance to use FastStringComparator from sonar-duplications
    int c = this.componentKey.compareTo(other.componentKey);
    if (c == 0) {
      c = this.ruleKey.compareTo(other.ruleKey);
      if (c == 0) {
        c = this.line - other.line;
        if (c == 0) {
          c = Comparator.nullsFirst(IssueLocation::compareTo).compare(location, other.location);
        }
      }
    }
    return c;
  }

  @Immutable
  static final class IssueLocation implements Comparable<IssueLocation> {
    private static final Comparator<IssueLocation> ORDER = Comparator.comparingInt((IssueLocation position) -> position.startLine)
      .thenComparingInt(position -> position.startColumn).thenComparingInt(position -> position.endLine)
      .thenComparingInt(position -> position.endColumn)
      .thenComparing(position -> position.charOffset, Comparator.nullsFirst(Integer::compareTo))
      .thenComparing(position -> position.charLength, Comparator.nullsFirst(Integer::compareTo))
      .thenComparing(position -> position.byteOffset, Comparator.nullsFirst(Integer::compareTo))
      .thenComparing(position -> position.byteLength, Comparator.nullsFirst(Integer::compareTo));

    final int startLine;
    final int startColumn;
    final int endLine;
    final int endColumn;
    @Nullable
    final Integer charOffset;
    @Nullable
    final Integer charLength;
    @Nullable
    final Integer byteOffset;
    @Nullable
    final Integer byteLength;

    IssueLocation(Region region) {
      startLine = region.getStartLine().orElseThrow(IllegalArgumentException::new);
      startColumn = region.getStartColumn().orElseThrow(IllegalArgumentException::new);
      endLine = region.getEndLine().orElse(startLine);
      endColumn = region.getEndColumn().orElseThrow(IllegalArgumentException::new);
      charOffset = region.getCharOffset().filter(offset -> offset >= 0).orElse(null);
      charLength = region.getCharLength().orElse(null);
      byteOffset = region.getByteOffset().filter(offset -> offset >= 0).orElse(null);
      byteLength = region.getByteLength().orElse(null);
    }

    static IssueLocation fromRange(TextRange range) {
      return new IssueLocation(new Region().withStartLine(range.start().line()).withStartColumn(range.start().lineOffset() + 1)
        .withEndLine(range.end().line()).withEndColumn(range.end().lineOffset() + 1));
    }

    @Nullable
    static IssueLocation fromRegion(@Nullable Region region) {
      if (region == null || !region.getStartLine().isPresent() || !region.getStartColumn().isPresent() || !region.getEndColumn().isPresent()) {
        return null;
      }
      return new IssueLocation(region);
    }

    Region toRegion() {
      return new Region().withStartLine(startLine).withStartColumn(startColumn).withEndLine(endLine).withEndColumn(endColumn)
        .withCharOffset(charOffset).withCharLength(charLength).withByteOffset(byteOffset).withByteLength(byteLength);
    }

    boolean matches(@Nullable IssueLocation actual) {
      return actual != null && startLine == actual.startLine && startColumn == actual.startColumn
        && endLine == actual.endLine && endColumn == actual.endColumn
        && matchesValue(charOffset, actual.charOffset) && matchesValue(charLength, actual.charLength)
        && matchesValue(byteOffset, actual.byteOffset) && matchesValue(byteLength, actual.byteLength);
    }

    private static boolean matchesValue(@Nullable Integer expected, @Nullable Integer actual) {
      return expected == null || expected.equals(actual);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof IssueLocation)) {
        return false;
      }
      IssueLocation location = (IssueLocation) other;
      return startLine == location.startLine && startColumn == location.startColumn && endLine == location.endLine && endColumn == location.endColumn
        && Objects.equals(charOffset, location.charOffset) && Objects.equals(charLength, location.charLength)
        && Objects.equals(byteOffset, location.byteOffset) && Objects.equals(byteLength, location.byteLength);
    }

    @Override
    public int hashCode() {
      return Objects.hash(startLine, startColumn, endLine, endColumn, charOffset, charLength, byteOffset, byteLength);
    }

    @Override
    public int compareTo(IssueLocation other) {
      return ORDER.compare(this, other);
    }

    static final class Source {
      private static final String UTF16 = "UTF-16";
      private final byte[] bytes;
      private final Charset charset;
      private final String text;
      private final boolean hasDecodingErrors;
      private final int bomBytes;
      private final int[] utf8Offsets;
      private final List<Integer> lineStarts = new ArrayList<>();

      Source(InputFile file) throws IOException {
        Path path = file.path();
        bytes = path != null && Files.isRegularFile(path) ? Files.readAllBytes(path) : readStream(file);
        charset = effectiveCharset(file.charset(), bytes);
        bomBytes = bomLength(charset, bytes);
        String decodedText;
        boolean decodingErrors = false;
        try {
          decodedText = charset.newDecoder().decode(ByteBuffer.wrap(bytes, bomBytes, bytes.length - bomBytes)).toString();
        } catch (CharacterCodingException exception) {
          decodedText = new String(bytes, bomBytes, bytes.length - bomBytes, charset);
          decodingErrors = true;
        }
        text = decodedText;
        hasDecodingErrors = decodingErrors;
        utf8Offsets = !hasDecodingErrors && charset.name().equals("UTF-8") ? utf8Offsets(text, bomBytes) : new int[0];
        lineStarts.add(0);
        int index = 0;
        while (index < text.length()) {
          char character = text.charAt(index);
          index++;
          if (character == '\r') {
            if (index < text.length() && text.charAt(index) == '\n') {
              index++;
            }
            lineStarts.add(index);
          } else if (character == '\n') {
            lineStarts.add(index);
          }
        }
      }

      IssueLocation locate(TextRange range) {
        IssueLocation location = fromRange(range);
        int start = lineStarts.get(location.startLine - 1) + location.startColumn - 1;
        int end = lineStarts.get(location.endLine - 1) + location.endColumn - 1;
        Integer startByte = byteOffset(start);
        Integer endByte = byteOffset(end);
        Region region = location.toRegion().withCharOffset(start).withCharLength(end - start);
        if (startByte != null && endByte != null) {
          region.withByteOffset(startByte).withByteLength(endByte - startByte);
        }
        return new IssueLocation(region);
      }

      @Nullable
      private Integer byteOffset(int offset) {
        if (hasDecodingErrors) {
          return null;
        }
        if (utf8Offsets.length != 0) {
          return utf8Offsets[offset] < 0 ? null : utf8Offsets[offset];
        }
        if (charset.name().startsWith(UTF16)) {
          return bomBytes + (offset * 2);
        }
        byte[] prefix = text.substring(0, offset).getBytes(charset);
        int end = bomBytes + prefix.length;
        return end <= bytes.length && Arrays.equals(prefix, Arrays.copyOfRange(bytes, bomBytes, end)) ? end : null;
      }

      private static int[] utf8Offsets(String text, int bomBytes) {
        int[] offsets = new int[text.length() + 1];
        int position = bomBytes;
        offsets[0] = position;
        int index = 0;
        while (index < text.length()) {
          char character = text.charAt(index);
          index++;
          if (Character.isHighSurrogate(character)) {
            offsets[index] = -1;
            index++;
            position += 4;
          } else {
            position += utf8Length(character);
          }
          offsets[index] = position;
        }
        return offsets;
      }

      private static int utf8Length(char character) {
        if (character < 0x80) {
          return 1;
        }
        return character < 0x800 ? 2 : 3;
      }

      private static byte[] readStream(InputFile file) throws IOException {
        try (InputStream input = file.inputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
          }
          return output.toByteArray();
        }
      }

      private static Charset effectiveCharset(Charset declared, byte[] bytes) {
        if (declared.name().equals(UTF16)) {
          return Charset.forName(startsWith(bytes, 0xFF, 0xFE) ? "UTF-16LE" : "UTF-16BE");
        }
        if (declared.name().equals("UTF-32")) {
          return Charset.forName(startsWith(bytes, 0xFF, 0xFE, 0, 0) ? "UTF-32LE" : "UTF-32BE");
        }
        return declared;
      }

      private static int bomLength(Charset charset, byte[] bytes) {
        String name = charset.name();
        if (name.equals("UTF-8") && startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
          return 3;
        }
        if (name.startsWith(UTF16) && (startsWith(bytes, 0xFE, 0xFF) || startsWith(bytes, 0xFF, 0xFE))) {
          return 2;
        }
        if (name.startsWith("UTF-32") && (startsWith(bytes, 0, 0, 0xFE, 0xFF) || startsWith(bytes, 0xFF, 0xFE, 0, 0))) {
          return 4;
        }
        return 0;
      }

      private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
          return false;
        }
        for (int index = 0; index < prefix.length; index++) {
          if ((bytes[index] & 0xFF) != prefix[index]) {
            return false;
          }
        }
        return true;
      }
    }
  }
}
