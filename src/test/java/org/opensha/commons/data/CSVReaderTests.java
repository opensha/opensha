package org.opensha.commons.data;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class CSVReaderTests {

    @Test
    public final void testRead() throws IOException {
        String sourceCSV =
                          "a, 1, 2.3, \"a comma,\", the end\n"
                        + "1,2,3";
        CSVReader reader = new CSVReader(new ByteArrayInputStream(sourceCSV.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, reader.getNumRows());
        assertTrue(reader.next());
        assertEquals("a", reader.get(0));
        assertEquals(1, reader.getInt(1));
        assertEquals(2.3, reader.getDouble(2), 0.0000000001);
        assertEquals(" a comma,", reader.get(3));
        assertEquals(" the end", reader.get(4));
        assertTrue(reader.next());
        assertEquals(1, reader.getInt(0));
        assertFalse(reader.next());
    }
}
