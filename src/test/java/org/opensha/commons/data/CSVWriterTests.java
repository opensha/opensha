package org.opensha.commons.data;

import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CSVWriterTests {

    @Test
    public void testWrite() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CSVWriter writer = new CSVWriter(out, false);
        writer.write(Arrays.asList("a", "b", "c"));
        writer.write(Arrays.asList("1", "2"));
        writer.flush();

        assertEquals("a,b,c\n1,2\n", out.toString());
    }

    @Test
    public void testWriteSpecialChars() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CSVWriter writer = new CSVWriter(out, false);
        writer.write(Arrays.asList("a", "b, and more ", "c"));
        writer.write(Arrays.asList("1", "2"));
        writer.flush();

        assertEquals("a,\"b, and more \",c\n1,2\n", out.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteStrictRowSizesFail() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CSVWriter writer = new CSVWriter(out, true);
        writer.write(Arrays.asList("a", "b", "c"));
        writer.write(Arrays.asList("1", "2"));
    }

    @Test
    public void testWriteStrictRowSizesPass() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CSVWriter writer = new CSVWriter(out, true);
        writer.write(Arrays.asList("a", "b", "c"));
        writer.write(Arrays.asList("1", "2", "3"));
        writer.flush();

        assertEquals("a,b,c\n1,2,3\n", out.toString());
    }
}
