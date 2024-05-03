package org.opensha.commons.data;

import com.google.common.base.Preconditions;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * A CSVFile for sequentially writing large CSV data to file in a memory efficient way while avoiding storing
 * intermediate internal List<String> representations.
 * This interface is not meant for manipulating or querying CSV data.
 */

public interface CSVWriter {

    /**
     * Writes the CSV file to the given output stream. The stream will not be closed.
     *
     * @param stream
     * @throws IOException
     */
    void writeToStream(OutputStream out) throws IOException;

    /**
     * A CSVWriter based on a lazy stream of List<String> objects.
     * Usage: Instead of creating a CSVFile and repeatedly calling addline(), create a stream of List<String> objects
     * and pass the stream into the constructor. In effect, this is like passing a function that can lazily produce
     * all CSV lines.
     */
    class CSVWriterStream implements CSVWriter {

        Stream<List<String>> stream;
        boolean strictRowSizes;
        int cols = 0;

        public CSVWriterStream(Stream<List<String>> lines, boolean strictRowSizes) {
            this.stream = lines;
            this.strictRowSizes = strictRowSizes;
        }

        protected boolean validateLine(List<String> line) {
            if (!strictRowSizes) {
                return true;
            }

            if (cols == 0) {
                cols = line.size();
            }

            Preconditions.checkArgument(line.size() == cols, "New line must contain" +
                    " same number of values as columns (expected " + cols + ", got " + line.size() + ")");

            return true;
        }

        @Override
        public void writeToStream(OutputStream out) throws IOException {
            OutputStreamWriter writer = new OutputStreamWriter(out);
            stream.
                    filter(this::validateLine).
                    map(CSVFile::getLineStr).
                    forEach(line -> {
                        try {
                            writer.write(line);
                            writer.write('\n');
                        } catch (IOException x) {
                            throw new RuntimeException(x);
                        }
                    });
            writer.flush();
        }
    }
}
