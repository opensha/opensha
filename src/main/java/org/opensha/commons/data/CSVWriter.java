package org.opensha.commons.data;

import com.google.common.base.Preconditions;
import org.apache.commons.io.output.CloseShieldOutputStream;

import java.io.*;
import java.util.*;

/**
 * For writing large CSV data to file without building intermediate internal List<String> representations.
 * Remember to call flush() or close() when done writing to the archive.
 * <p>
 * Note that calling close() on the CSVWriter doesn't close the provided OutputStream.
 * It simply flushes the buffer and closes the internal proxy stream, preventing further writes with the CSVWriter.
 * This is necessary as the OutputStream may be shared with other writers or invoked directly elsewhere.
 * </p>
 *
 * It's preferable to use try-with-resources as this ensures that the data is flushed
 * and resources are properly released.
 * The underlying shared OutputStream should be closed manually at a higher context and independent of its writers.
 * <p>
 *   try (CSVWriter csvWriter = new CSVWriter(new FileOutputStream(file), true)) {
 *     // write data ...
 * }   // Automatically calls close() -> proxy stream closure
 * </p>
 *
 */

public class CSVWriter implements Flushable, Closeable {

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
        this.writer = new OutputStreamWriter(new BufferedOutputStream(CloseShieldOutputStream.wrap(out)));
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

    /**
     * Calling close on the CSVWriter only flushes and closes the proxy stream.
     * This means that the CSVWriter will not be able to continue writing to the stream,
     * although the OutputStream provided to the CSVWriter constructor will still be open.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        try {
            flush();
        } finally {
            writer.close();
        }
    }
}
