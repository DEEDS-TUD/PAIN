package de.grinder.android_fi.experimentResultDetection;

import java.util.Arrays;

/**
 * 
 * 
 * <p>
 * This class is thread safe, i.e., it should be safe to invoke its methods from multiple
 * threads.
 */
public class CircularStringBuffer {
  private final char[] buffer;
  private final int bufferSize;
  private int currentBufferPos = 0;

  public CircularStringBuffer(final int size) {
    bufferSize = size;
    buffer = new char[size];
  }

  private void append(final String value) {
    for (int i = 0; i < value.length(); i++) {
      buffer[i + currentBufferPos] = value.charAt(i);
    }
    currentBufferPos += value.length();
  }

  private void insertInto(final int index, final String value, final int offset,
      final int length) {
    for (int i = 0; i < length; i++) {
      buffer[index + i] = value.charAt(offset + i);
    }
  }

  public synchronized void clear() {
    currentBufferPos = 0;
    Arrays.fill(buffer, '\0');
  }

  public synchronized boolean contains(final String match) {
    int matchPointer = 0;

    // care for possible match beginning at the end of the buffer and
    // continuing at it's beginning
    for (int i = 0; i < bufferSize + match.length(); i++) {
      if (buffer[i % bufferSize] == match.charAt(matchPointer)) {
        matchPointer++;
      } else {
        matchPointer = 0;
      }

      if (matchPointer == match.length()) {
        return true;
      }
    }

    return false;
  }

  public synchronized void put(final String value) {
    final int valueSize = value.length();

    if (currentBufferPos + valueSize <= bufferSize) {
      append(value);
    } else {
      final int left = bufferSize - currentBufferPos;
      final int overhead = valueSize - left;
      insertInto(currentBufferPos, value, 0, left);
      insertInto(0, value, left, overhead);
      currentBufferPos = overhead;
    }
  }

  @Override
  public synchronized String toString() {
    final StringBuilder sb = new StringBuilder(bufferSize);
    sb.append(buffer, currentBufferPos, bufferSize - currentBufferPos);
    sb.append(buffer, 0, currentBufferPos);
    return sb.toString();
  }
}