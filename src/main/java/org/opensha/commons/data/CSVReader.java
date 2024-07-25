package org.opensha.commons.data;

import java.io.*;
import java.util.*;

/**
 * CSV reader that allows for CSV files to be read directly instead of
 * having to load a potentially large intermediate structure into memory.
 */
public class CSVReader implements Closeable {

    protected BufferedReader reader;

    public CSVReader(InputStream in) {
        reader = new BufferedReader(new InputStreamReader(in));
    }

    /**
     * Reads the next row.
     *
     * @return the row. Returns null if the end of the file has been reached.
     */
    public Row read() {
        String line;
        try {
            line = reader.readLine();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        if (line == null) {
            return null;
        }

        return new Row(line);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public static class Row {

        // the data of the current row
        protected final List<String> line;

        Row(String row) {
            this.line = CSVFile.loadLine(row);
        }

        /**
         * Get the data of the specified column
         *
         * @param column the column index, 0-based
         * @return the value as an unparsed string.
         */
        public String get(int column) {
            return line.get(column);
        }

        /**
         * Get an int value at the specified column.
         *
         * @param column the column index, 0-based
         * @return the int value of the column
         */
        public int getInt(int column) {
            return Integer.parseInt(get(column).trim());
        }

        /**
         * Get a double value at the specified column.
         *
         * @param column the column index, 0-based
         * @return the double value of the column
         */
        public double getDouble(int column) {
            return Double.parseDouble(get(column).trim());
        }

        /**
         * Get all values as strings.
         *
         * @return a list of all column values
         */
        public List<String> getLine() {
            return line;
        }
        
        /**
         * Get the number of columns in this row
         * 
         * @return number of columns
         */
        public int columns() {
        	return line.size();
        }
    }
}
