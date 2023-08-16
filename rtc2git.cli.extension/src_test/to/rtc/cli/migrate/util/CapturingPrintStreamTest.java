package to.rtc.cli.migrate.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

@SuppressWarnings("resource")
public class CapturingPrintStreamTest {

    @Test
    public void printlnStringGivenStringsThenDelegatesAndLogsStrings() {
        // Given
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream wrapped = new PrintStream(buffer);
        final CapturingWrapper capturer = new CapturingWrapper();
        final String expected1 = "expectedOne";
        final String expected2 = "expected two";
        final String expected3 = "expected three";
        final List<String> expected = listOf(expected1, expected2, expected3);
        final PrintStream instance = new CapturingPrintStream(wrapped, capturer);

        // When
        instance.println(expected1);
        instance.println(expected2);
        instance.println(expected3);

        // Then
        final List<String> actual = getLinesCaptured(capturer);
        assertThat(actual, equalTo(expected));
        final List<String> wrappedOutput = getLinesOutput(buffer);
        assertThat(wrappedOutput, equalTo(expected));
    }

    @Test
    public void appendGivenStringsThenDelegatesAndLogsStrings() {
        // Given
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream wrapped = new PrintStream(buffer);
        final CapturingWrapper capturer = new CapturingWrapper();
        final List<String> expected = listOf("Foo");
        final PrintStream instance = new CapturingPrintStream(wrapped, capturer);

        // When
        instance.append('F');
        instance.append("oo");
        instance.append("OnlyLastChar\n", 12, 13);

        // Then
        final List<String> actual = getLinesCaptured(capturer);
        assertThat(actual, equalTo(expected));
        final List<String> wrappedOutput = getLinesOutput(buffer);
        assertThat(wrappedOutput, equalTo(expected));
    }

    @Test
    public void printlnGivenVariousThenDelegatesAndLogsEverything() {
        // Given
        final String myObjectString = "MyObject";
        final Object myObject = new Object() {
            public String toString() {
                return myObjectString;
            }
        };
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream wrapped = new PrintStream(buffer);
        final CapturingWrapper capturer = new CapturingWrapper();
        final List<String> expected = listOf("false", "c", "abc", "1.2345", "2345.6", "3456789", "45678901234567890",
                myObjectString);
        final PrintStream instance = new CapturingPrintStream(wrapped, capturer);

        // When
        instance.println(false);
        instance.println('c');
        instance.println(new char[] { 'a', 'b', 'c' });
        instance.println(1.2345d);
        instance.println(2345.6f);
        instance.println(3456789);
        instance.println(45678901234567890l);
        instance.println(myObject);

        // Then
        final List<String> actual = getLinesCaptured(capturer);
        assertThat(actual, equalTo(expected));
        final List<String> wrappedOutput = getLinesOutput(buffer);
        assertThat(wrappedOutput, equalTo(expected));
    }

    @Test
    public void writeGivenBytesThenDelegatesAndLogsStrings() throws IOException {
        // Given
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream wrapped = new PrintStream(buffer);
        final CapturingWrapper capturer = new CapturingWrapper();
        final List<String> expected = listOf("Foo", "bar");
        final PrintStream instance = new CapturingPrintStream(wrapped, capturer);

        // When
        instance.write((int) 'F');
        instance.write(new byte[] { 'o', 'o' });
        instance.write(new byte[] { 65, 10, 'b', 'a', 'r', 10, 65 }, 1, 5);

        // Then
        final List<String> actual = getLinesCaptured(capturer);
        assertThat(actual, equalTo(expected));
        final List<String> wrappedOutput = getLinesOutput(buffer);
        assertThat(wrappedOutput, equalTo(expected));
    }

    @Test
    public void printfGivenFormatThenDelegatesAndLogsStrings() throws IOException {
        // Given
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream wrapped = new PrintStream(buffer);
        final CapturingWrapper capturer = new CapturingWrapper();
        final List<String> expected = listOf("My silly format", "Hello world");
        final PrintStream instance = new CapturingPrintStream(wrapped, capturer);

        // When
        instance.printf((Locale) null, "My %s format\n", "silly");
        instance.printf("Hello %s\n", "world");

        // Then
        final List<String> actual = getLinesCaptured(capturer);
        assertThat(actual, equalTo(expected));
        final List<String> wrappedOutput = getLinesOutput(buffer);
        assertThat(wrappedOutput, equalTo(expected));
    }

    private static List<String> listOf(final String... strings) {
        return new ArrayList<String>(Arrays.asList(strings));
    }

    private static List<String> getLinesCaptured(final CapturingWrapper capturer) {
        return new ArrayList<String>(capturer.getCompleteLines());
    }

    private static List<String> getLinesOutput(final ByteArrayOutputStream buffer) {
        return new ArrayList<String>(Arrays.asList(buffer.toString().split("[\r\n]+")));
    }
}
