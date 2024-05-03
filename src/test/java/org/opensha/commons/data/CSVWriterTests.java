package org.opensha.commons.data;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CSVWriterTests {

    @Test
    public void testWrite() throws IOException {
        List<List<String>> csvLines =
        Arrays.asList(
                Arrays.asList("a", "b", "c"),
                Arrays.asList("1", "2")
        );

        CSVWriter writer = new CSVWriter.CSVWriterStream(csvLines.stream(), false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeToStream(buffer);
        assertEquals("a,b,c\n1,2\n", buffer.toString());
    }

    @Test
    public void testWriteSpecialChars() throws IOException {
        List<List<String>> csvLines =
                Arrays.asList(
                        Arrays.asList("a", "b, and more ", "c"),
                        Arrays.asList("1", "2")
                );

        CSVWriter writer = new CSVWriter.CSVWriterStream(csvLines.stream(), false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeToStream(buffer);
        assertEquals("a,\"b, and more \",c\n1,2\n", buffer.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteStrictRowSizesFail() throws IOException {
        List<List<String>> csvLines =
                Arrays.asList(
                        Arrays.asList("a", "b", "c"),
                        Arrays.asList("1", "2")
                );

        CSVWriter writer = new CSVWriter.CSVWriterStream(csvLines.stream(), true);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeToStream(buffer);
    }

    @Test
    public void testWriteStrictRowSizesPass() throws IOException {
        List<List<String>> csvLines =
                Arrays.asList(
                        Arrays.asList("a", "b", "c"),
                        Arrays.asList("1", "2", "3")
                );

        CSVWriter writer = new CSVWriter.CSVWriterStream(csvLines.stream(), true);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writer.writeToStream(buffer);
        assertEquals("a,b,c\n1,2,3\n", buffer.toString());
    }
}
