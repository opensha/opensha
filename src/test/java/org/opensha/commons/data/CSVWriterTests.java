package org.opensha.commons.data;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CSVWriterTests {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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

    /**
     * Original OutputStream is closed if CSVWriter is closed
     */
    @Test
    public void testClose() throws IOException {
        File outFile = tempFolder.newFile("testClose.csv");
        FileOutputStream out = new FileOutputStream(outFile);

        CSVWriter writer1 = new CSVWriter(out, false);
        writer1.write(Arrays.asList("a", "b", "c"));
        writer1.write(Arrays.asList("1", "2"));
        writer1.close();
        // Cannot continue using a writer after it's closed.
        try {
            writer1.write(Arrays.asList("d", "e", "f"));
            fail("Expected IOException");
        } catch (IOException e) {
            /* expected */
            assertEquals("a,b,c\n1,2\n", Files.readString(outFile.toPath()));
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertEquals("a,b,c\n1,2\n", Files.readString(outFile.toPath()));
        // Cannot continue to use the same OutputStream with a different writer
        CSVWriter writer2 = new CSVWriter(out, false);
        try {
            writer2.write(Arrays.asList("d", "e", "f"));
            writer2.close();
            fail("Expected IOException");
        } catch (IOException e) {
            /* expected */
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertEquals("a,b,c\n1,2\n", Files.readString(outFile.toPath()));
        // Cannot close a writer multiple times
        try {
            writer2.close();
            fail("Expected IOException");
        } catch (IOException e) {
            /* expected */
        } catch (Exception e) {
           fail("Unexpected exception: " + e.getMessage());
        }
    }
}
