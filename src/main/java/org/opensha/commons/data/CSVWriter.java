package org.opensha.commons.data;

import com.google.common.base.Preconditions;

import java.io.*;
import java.util.*;

/**
 * For writing large CSV data to file without building intermediate internal List<String> representations.
 * Remember to call flush() when done writing to the archive.
 */

public class CSVWriter implements Flushable {

    protected boolean strictRowSizes;
    protected int cols = 0;
    protected Writer writer;

    /**
     * Creates a CSVWriter
     *
     * @param out            the OutputStream to write to
     * @param strictRowSizes whether this write should enforce strict row sizes
     * @throws IOException
     */
    public CSVWriter(OutputStream out, boolean strictRowSizes)
            throws IOException {
        this.strictRowSizes = strictRowSizes;
        this.writer = new OutputStreamWriter(new BufferedOutputStream(out));
    }

    protected void validateLine(List<String> line) {
        if (!strictRowSizes) {
            return;
        }

        if (cols == 0) {
            cols = line.size();
        }

        Preconditions.checkArgument(line.size() == cols, "New line must contain" +
                " same number of values as columns (expected " + cols + ", got " + line.size() + ")");
    }

    /**
     * Write a CSV-formatted row to the OutputStream.
     *
     * @param line
     * @throws IOException
     */
    public void write(List<String> line) throws IOException {
        validateLine(line);
        CSVFile.writeLine(writer, line);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}
