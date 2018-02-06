package de.grinder.android_fi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.grinder.android_fi.experimentResultDetection.CircularStringBuffer;

public class CircularStringBufferTest {

    private static final String testString = "To run unit test via Maven, issue this command";

    @Test
    public void testFlatPut() {
        final CircularStringBuffer buffer = new CircularStringBuffer(testString.length());
        buffer.put(testString);
        assertEquals(testString, buffer.toString());
    }

    @Test
    public void testCircularPut() {
        final CircularStringBuffer buffer = new CircularStringBuffer(testString.length() - 5);
        buffer.put(testString);

        final String result = testString.substring(5);
        assertEquals(result, buffer.toString());
    }

    @Test
    public void testContainsTrue() {
        final String match = "Maven";
        final CircularStringBuffer buffer = new CircularStringBuffer(testString.length());
        buffer.put(testString);
        assertEquals(testString.contains(match), buffer.contains(match));
    }

    @Test
    public void testContainsFalse() {
        final String match = "false";
        final CircularStringBuffer buffer = new CircularStringBuffer(testString.length());
        buffer.put(testString);
        assertEquals(testString.contains(match), buffer.contains(match));
    }

    @Test
    public void testCircularContainsTrue() {
        final int offset = 10;
        final String match = testString.subSequence(testString.length() - offset,
                             testString.length()).toString();
        final CircularStringBuffer buffer = new CircularStringBuffer(testString.length()
                - offset / 2);
        buffer.put(testString);
        assertEquals(true, buffer.contains(match));
    }
}
