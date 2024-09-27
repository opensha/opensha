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

        CSVReader.Row row = reader.read();
        assertNotNull(row);
        assertEquals("a", row.get(0));
        assertEquals(1, row.getInt(1));
        assertEquals(2.3, row.getDouble(2), 0.0000000001);
        assertEquals(" a comma,", row.get(3));
        assertEquals(" the end", row.get(4));
        row = reader.read();
        assertNotNull(row);
        assertEquals(1, row.getInt(0));
        assertNull(reader.read());
    }
}
