package to.rtc.cli.migrate.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogVersionableEntry2DTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.impl.ChangeLogEntryDTOImpl;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.impl.ChangeLogVersionableEntry2DTOImpl;

public class ChangeLogPrintUtilTest {
    @Test
    public void writeLnGivenNullThenWritesNull() throws Exception {
        // Given
        final Object o = null;
        final String expected = "null,\n";

        // When
        final String actual = writeToString(o);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void writeLnGivenStringThenWritesString() throws Exception {
        // Given
        final Object o = "Foo bar";
        final String expected = "\"Foo bar\",\n";

        // When
        final String actual = writeToString(o);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void writeLnGivenIntegerThenWritesNumber() throws Exception {
        // Given
        final Object o = 123456;
        final String expected = "123456,\n";

        // When
        final String actual = writeToString(o);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void writeLnGivenChangeLogVersionableEntry2DTOThenWritesContent() throws Exception {
        // Given
        final ChangeLogVersionableEntry2DTO c1 = new MyChangeLogVersionableEntryDTO();
        c1.setEntryName("MyEntryName");
        c1.setLongVersionId("MyLongVersionID");
        c1.setResolved(true);
        c1.getSegments().add("MySegment1");
        c1.getSegments().add("MySegment2");
        final ChangeLogVersionableEntry2DTO c2 = new MyChangeLogVersionableEntryDTO();
        c2.setEntryName("MyOtherEntryName");
        c2.setResolved(false);
        final ChangeLogEntryDTO o = new MyLogEntryDTO();
        o.getChildEntries().add(c1);
        o.getChildEntries().add(c2);
        final String expected = 
                "\"" + o.getClass().getSimpleName() + "\": {\n" +
                "  \"EntryType\": notset,\n" +
                "  \"EntryName\": notset,\n" +
                "  \"ItemId\": notset,\n" +
                "  \"ChildEntries\": [\n" +
                "    \"" + c1.getClass().getSimpleName() + "\": {\n" +
                "      \"EntryType\": notset,\n" +
                "      \"EntryName\": \"MyEntryName\",\n" +
                "      \"ItemId\": notset,\n" +
                "      \"Resolved\": true,\n" +
                "      \"Segments\": [\n" +
                "        \"MySegment1\",\n" +
                "        \"MySegment2\",\n" +
                "      ],\n" +
                "      \"ShortVersionId\": notset,\n" +
                "      \"LongVersionId\": \"MyLongVersionID\",\n" +
                "      \"ChildEntries\": notset,\n" +
                "    },\n" +
                "    \"" + c2.getClass().getSimpleName() + "\": {\n" +
                "      \"EntryType\": notset,\n" +
                "      \"EntryName\": \"MyOtherEntryName\",\n" +
                "      \"ItemId\": notset,\n" +
                "      \"Resolved\": false,\n" +
                "      \"Segments\": notset,\n" +
                "      \"ShortVersionId\": notset,\n" +
                "      \"LongVersionId\": notset,\n" +
                "      \"ChildEntries\": notset,\n" +
                "    },\n" +
                "  ],\n" +
                "},\n";

        // When
        final String actual = writeToString(o);
        System.out.print(actual);

        // Then
        assertThat(actual, equalTo(expected));
    }

    private static class MyChangeLogVersionableEntryDTO extends ChangeLogVersionableEntry2DTOImpl {
        public String toString() {
            return "MyToString";
        }
    }

    private static class MyLogEntryDTO extends ChangeLogEntryDTOImpl {
        public String toString() {
            return "MyToString";
        }
    }

    private static String writeToString(final Object o) throws UnsupportedEncodingException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos, true, "UTF-8");
        try {
            new ChangeLogPrintUtil(ps).writeLn(o);
            ps.flush();
        } finally {
            ps.close();
        }
        final String actual = new String(baos.toByteArray(), StandardCharsets.UTF_8).replaceAll("\r\n", "\n");
        return actual;
    }
}
