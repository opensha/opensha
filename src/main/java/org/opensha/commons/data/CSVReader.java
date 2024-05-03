package org.opensha.commons.data;

import com.google.common.base.Preconditions;

import java.io.*;
import java.util.*;

/**
 * Memory efficient CSV reader. Not thread safe.
 * Calling next() advances the reader to the next row.
 * get*() methods can be used to get values from the columns of the current row.
 * next() must have been called at least once before a call to a get*() method.
 */
public class CSVReader implements Closeable {

    protected LargeLineBuffer buffer;

    // the data of the current row
    protected List<String> line = null;

    public CSVReader(InputStream in) throws IOException {
        buffer = LargeLineBuffer.fromStream(in);
    }

    /**
     * Advances the reader to the next row.
     *
     * @return true if the row exists, false if the end of the data has been reached.
     */
    public boolean next() {
        String bufferLine;
        try {
            bufferLine = buffer.readLine();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        if (bufferLine == null) {
            return false;
        }

        line = CSVFile.loadLine(bufferLine);
        return true;
    }

    /**
     * Get the number of rows in the CSV file.
     *
     * @return the number of rows
     */
    public int getNumRows() {
        return buffer.getLineCount();
    }

    /**
     * Get the data of the specified column in the current row.
     *
     * @param column the column index, 0-based
     * @return the value as an unparsed string.
     */
    public String get(int column) {
        return line.get(column);
    }

    /**
     * Get an int values from the current row at the specified column.
     *
     * @param column the column index, 0-based
     * @return the int value of the column
     */
    public int getInt(int column) {
        return Integer.parseInt(get(column).trim());
    }

    /**
     * Get a double value from the current row at the specified column.
     *
     * @param column the column index, 0-based
     * @return the double value of the column
     */
    public double getDouble(int column) {
        return Double.parseDouble(get(column).trim());
    }

    /**
     * Gets all values of the current row.
     *
     * @return a list of all column values
     */
    public List<String> getLine() {
        return line;
    }

    @Override
    public void close() throws IOException {
        buffer = null;
        line = null;
    }

    /**
     * A helper class to store the lines of a CSV file in memory. This is to avoid expanding each line into List<String>
     *     which can be memory expensive.
     * Java arrays can only store up to 2.1 billion entries, meaning we use a list of byte arrays in order to store files
     * larger than 2 GB.
     */
    protected static class LargeLineBuffer {
        protected List<byte[]> buffers = new ArrayList<>();
        protected int lineCount = 0;
        protected ByteArrayOutputStream outBuffer;
        protected Writer out;

        BufferedReader reader;
        int readBuffer = 0;

        public LargeLineBuffer() {
            outBuffer = new ByteArrayOutputStream();
            out = new OutputStreamWriter(outBuffer);
        }

        public static LargeLineBuffer fromStream(InputStream is)
                throws IOException {
            if (!(is instanceof BufferedInputStream))
                is = new BufferedInputStream(is);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            LargeLineBuffer buffer = new LargeLineBuffer();
            String line = br.readLine();
            while (line != null) {
                buffer.append(line);
                line = br.readLine();
            }
            br.close();
            buffer.finalise();
            return buffer;
        }

        public void append(String line) throws IOException {
            Preconditions.checkState(out != null, "Buffer is finalised.");
            if (outBuffer.size() > Integer.MAX_VALUE - 1_000_000) {
                buffers.add(outBuffer.toByteArray());
                outBuffer = new ByteArrayOutputStream();
                out = new OutputStreamWriter(outBuffer);
            }
            lineCount++;
            out.write(line);
            out.write('\n');
            out.flush();
        }

        public int getLineCount() {
            return lineCount;
        }

        public void finalise() {
            buffers.add(outBuffer.toByteArray());
            outBuffer = null;
            out = null;
        }

        public String readLine() throws IOException {
            Preconditions.checkState(out == null, "Buffer is not finalised");
            if (reader == null) {
                if (readBuffer >= buffers.size()) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffers.get(readBuffer))));
                readBuffer++;
            }
            String line = reader.readLine();
            if (line == null) {
                reader.close();
                reader = null;
                return readLine();
            }
            return line;
        }
    }
}
