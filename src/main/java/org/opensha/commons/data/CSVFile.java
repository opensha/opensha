package org.opensha.commons.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.util.FileUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

public class CSVFile<E> implements Iterable<List<E>> {
	
	private List<List<E>> values;
//	private List<String> colNames;
//	private Map<String, ? extends List<E>> values;
	private int cols;
	private boolean strictRowSizes;
	
	public CSVFile(boolean strictRowSizes) {
		this(null, strictRowSizes);
	}
	
	public CSVFile(List<List<E>> values, boolean strictRowSizes) {
		if (values == null)
			values = new ArrayList<List<E>>();
		this.strictRowSizes = strictRowSizes;
		cols = -1;
		if (strictRowSizes) {
			for (List<E> row : values) {
				if (cols < 0)
					cols = row.size();
				else
					Preconditions.checkArgument(cols == row.size(),
							"Values lists aren't the same size!");
			}
		}
		this.values = values;
	}
	
	public int getNumRows() {
		return values.size();
	}
	
	/**
	 * Return the number or rows, or -1 if empty or non strict row sizes
	 * @return
	 */
	public int getNumCols() {
		return cols;
	}
	
	/**
	 * @return true if all rows must have the same number of columns, false otherwise
	 */
	public boolean isStrictRowSizes() {
		return strictRowSizes;
	}
	
	public void set(int row, int col, E value) {
		getLine(row).set(col, value);
	}
	
	public void addLine(List<E> line) {
		checkValidLine(line);
		values.add(line);
	}
	
	public void addLine(E... line) {
		addLine(Lists.newArrayList(line));
	}
	
	public void addLine(int index, List<E> line) {
		checkValidLine(line);
		values.add(index, line);
	}
	
	public void setLine(int index, List<E> line) {
		checkValidLine(line);
		values.set(index, line);
	}
	
	public void addAll(Collection<List<E>> lines) {
		Preconditions.checkNotNull(lines, "lines cannot be null!");
		// first make sure they're ALL going to pass before adding anything
		for (List<E> line : lines) {
			checkValidLine(line);
		}
		// add them.
		for (List<E> line : lines) {
			values.add(line);
		}
	}
	
	public List<E> removeLine(int index) {
		List<E> ret = values.remove(index);
		
		// if list is now empty, reset column size
		if (values.isEmpty())
			cols = -1;
		
		return ret;
	}
	
	public void addColumn(List<E> vals) {
		if (getNumRows() == 0) {
			// this is an empty CSV
			for (int i=0; i<vals.size(); i++)
				addLine(new ArrayList<E>());
		}
		Preconditions.checkArgument(vals.size() == getNumRows());
		
		int prevNumCols = cols;
		
		for (int row=0; row<vals.size(); row++) {
			List<E> line = getLine(row);
			E val = vals.get(row);
			if (!strictRowSizes && line.size()<prevNumCols) {
				// this means that we don't have strict column counts, and this one is already short
				// if val is null here we don't have to do anything
				if (val != null) {
					// populate with nulls to get it the right size
					while (line.size()<prevNumCols)
						line.add(null);
					line.add(val);
				}
			} else {
				line.add(val);
			}
		}
		cols++;
	}
	
	private void checkValidLine(List<E> line) {
		Preconditions.checkNotNull(line, "Cannot add a null line!");
		if (strictRowSizes) {
			if (cols < 0) {
				// this means it's empty
				cols = line.size();
			} else {
				Preconditions.checkArgument(line.size() == cols, "New line must contain" +
					" same number of values as columns (expected "+cols+", got "+line.size()+")");
			}
		}
	}
	
	public E get(int row, int col) {
		return getLine(row).get(col);
	}
	
	public double getDouble(int row, int col) throws NumberFormatException {
		return Double.parseDouble(get(row, col).toString());
	}
	
	public float getFloat(int row, int col) throws NumberFormatException {
		return Float.parseFloat(get(row, col).toString());
	}
	
	public int getInt(int row, int col) throws NumberFormatException {
		return Integer.parseInt(get(row, col).toString());
	}
	
	public long getLong(int row, int col) throws NumberFormatException {
		return Long.parseLong(get(row, col).toString());
	}
	
	public boolean getBoolean(int row, int col) throws NumberFormatException {
		return Boolean.parseBoolean(get(row, col).toString());
	}
	
	public List<E> getLine(int index) {
		return values.get(index);
	}
	
	public String getLineStr(int i) {
		return getLineStr(getLine(i));
	}
	
	public static String getLineStr(List<?> line) {
		return getLineStr(line.toArray());
	}
	
	public List<E> getColumn(int col) {
		Preconditions.checkArgument(col < getNumCols(), "No column at "+col);
		List<E> colVals = Lists.newArrayList();
		for (int row=0; row<getNumRows(); row++) {
			List<E> line = getLine(row);
			if (!strictRowSizes && col >= line.size())
				colVals.add(null);
			else
				colVals.add(line.get(col));
		}
		return colVals;
	}
	
	public static String getLineStr(Object[] line) {
		String lineStr = null;
		for (Object val : line) {
			if (lineStr == null)
				lineStr = "";
			else
				lineStr += ",";
			String valStr;
			if (val == null)
				valStr = ""+null;
			else
				valStr = val.toString();
			// if it contains a comma, surround it in quotation marks if not already
			if (valStr.contains(",") && !(valStr.startsWith("\"") && valStr.endsWith("\"")))
				valStr = "\""+valStr+"\"";
			lineStr += valStr;
		}
		return lineStr;
	}
	
	public String getHeader() {
		return getLineStr(getLine(0));
	}
	
	public void writeToFile(File file) throws IOException {
		FileWriter fw = new FileWriter(file);
		writeWriter(fw);
		fw.close();
	}
	
	/**
	 * Writes the CSV file to the given output stream. The stream will not be closed.
	 * @param stream
	 * @throws IOException
	 */
	public void writeToStream(OutputStream stream) throws IOException {
		if (!(stream instanceof BufferedOutputStream))
			stream = new BufferedOutputStream(stream);
		writeWriter(new OutputStreamWriter(stream));
	}
	
	private void writeWriter(Writer w) throws IOException {
		for (int i=0; i<getNumRows(); i++) {
			w.write(getLineStr(i) + "\n");
		}
		w.flush();
	}
	
	public void writeToTabSeparatedFile(File file, int headerLines) throws IOException {
		FileWriter fw = new FileWriter(file);

		for (int i=0; i<getNumRows(); i++) {
			List<E> line = getLine(i);

			String lineStr = null;
			for (E val : line) {
				if (lineStr == null)
					lineStr = "";
				else
					lineStr += "\t";
				lineStr += val.toString();
			}

			if (i < headerLines)
				// header
				lineStr = "# "+lineStr;

			fw.write(lineStr + "\n");
		}

		fw.close();
	}
	
	public void removeColumn(int i) {
		Preconditions.checkArgument(i >= 0, "column must be >= 0");
		Preconditions.checkArgument(cols < 0 || i < cols, "invalid column: "+i);
		
		for (List<E> list : values) {
			if (list.size() > i)
				list.remove(i);
		}
	}
	
	private static ArrayList<String> loadLine(String line, int num) {
		line = line.trim();
		ArrayList<String> vals = new ArrayList<String>();
		boolean inside = false;
		String cur = "";
		for (int i=0; i<line.length(); i++) {
			char c = line.charAt(i);
			if (!inside && c == ',') {
				// we're done with a value
				vals.add(cur);
				cur = "";
				continue;
			}
			if (c == '"') {
				inside = !inside;
				continue;
			}
			cur += c;
		}
		if (!cur.isEmpty())
			vals.add(cur);
		while (vals.size() < num)
			vals.add("");
		return vals;
	}
	
	public static CSVFile<String> readFile(File file, boolean strictRowSizes) throws IOException {
		return readFile(file, strictRowSizes, -1);
	}
	
	public static CSVFile<String> readFile(File file, boolean strictRowSizes, int cols) throws IOException {
		return readURL(file.toURI().toURL(), strictRowSizes, cols);
	}
	
	public static CSVFile<String> readURL(URL url, boolean strictRowSizes) throws IOException {
		return readURL(url, strictRowSizes, -1);
	}
	
	public static CSVFile<String> readURL(URL url, boolean strictRowSizes, int cols) throws IOException {
		return readStream((InputStream)url.getContent(), strictRowSizes, cols);
	}
	
	public static CSVFile<String> readStream(InputStream is, boolean strictRowSizes) throws IOException {
		return readStream(is, strictRowSizes, -1);
	}
	
	public static CSVFile<String> readStream(InputStream is, boolean strictRowSizes, int cols)
			throws IOException {
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		List<List<String>> values = new ArrayList<List<String>>();
		for (String line : FileUtils.loadStream(is)) {
			if (strictRowSizes && cols < 0) {
				cols = loadLine(line, -1).size();
			}
			ArrayList<String> vals = loadLine(line, cols);
			if (strictRowSizes && vals.size() > cols)
				throw new IllegalStateException("Line lenghts inconsistant and strictRowSizes=true");
			values.add(vals);
		}
		
		return new CSVFile<String>(values, strictRowSizes);
	}
	
	public static CSVFile<Double> readFileNumeric(File file, boolean strictRowSizes,
			int headerLines) throws NumberFormatException, IOException {
		return readURLNumeric(file.toURI().toURL(), strictRowSizes, headerLines);
	}
	
	public static CSVFile<Double> readURLNumeric(URL url, boolean strictRowSizes,
			int headerLines) throws NumberFormatException, IOException {
		return readStreamNumeric((InputStream)url.getContent(), strictRowSizes, -1, headerLines);
	}
	
	public static CSVFile<Double> readStreamNumeric(InputStream is, boolean strictRowSizes,
			int cols, int headerLines) throws NumberFormatException, IOException {
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		List<List<Double>> values = new ArrayList<List<Double>>();
		int lineCount = 0;
		for (String line : FileUtils.loadStream(is)) {
			if (headerLines > lineCount) {
				lineCount++;
				continue;
			}
			if (strictRowSizes && cols < 0) {
				cols = loadLine(line, -1).size();
			}
			ArrayList<String> vals = loadLine(line, cols);
			if (strictRowSizes && vals.size() > cols)
				throw new IllegalStateException("Line lenghts inconsistant and strictRowSizes=true");
			List<Double> doubles;
			if (strictRowSizes) {
				// use backing array for memory efficiency
				double[] array = new double[vals.size()];
				for (int i=0; i<array.length; i++)
					array[i] = Double.parseDouble(vals.get(i));
				doubles = Doubles.asList(array);
			} else {
				doubles = Lists.newArrayList();
				for (String val : vals)
					doubles.add(Double.parseDouble(val));
			}
			values.add(doubles);
			lineCount++;
		}
		
		return new CSVFile<Double>(values, strictRowSizes);
	}

	@Override
	public Iterator<List<E>> iterator() {
		return values.iterator();
	}
	
	public void sort(int col, int headerRows, Comparator<E> comparator) {
		ArrayList<List<E>> header = Lists.newArrayList();
		for (int row=0; row<headerRows; row++)
			header.add(removeLine(0));
		
		// sort
		ColumnComparator comp = new ColumnComparator(col, comparator);
		Collections.sort(values, comp);
		
		Collections.reverse(header);
		for (List<E> line : header)
			addLine(0, line);
	}
	
	private class ColumnComparator implements Comparator<List<E>> {
		private Comparator<E> comp;
		private int col;
		
		public ColumnComparator(int col, Comparator<E> comp) {
			this.col = col;
			this.comp = comp;
		}

		@Override
		public int compare(List<E> o1, List<E> o2) {
			return comp.compare(o1.get(col), o2.get(col));
		}
		
	}
	
	public void addColumn() {
		for (List<E> line : values) {
			line.add(null);
		}
	}
	
	public void printPretty(String delim) {
		// assemble column lengths
		List<Integer> colSizes = Lists.newArrayList();
		
		for (int row=0; row<getNumRows(); row++) {
			List<E> line = getLine(row);
			for (int col=0; col<line.size(); col++) {
				int len = line.get(col).toString().length();
				if (colSizes.size() == col)
					colSizes.add(len);
				else if (len > colSizes.get(col))
					colSizes.set(col, len);
			}
		}
		
		Joiner j = Joiner.on(delim);
		
		// now print
		for (List<E> line : this) {
			List<String> paddedLine = Lists.newArrayList();
			for (int col=0; col<line.size(); col++) {
				E e = line.get(col);
				int len = colSizes.get(col);
				String str = e.toString();
				while (str.length() < len)
					str = str+" ";
				paddedLine.add(str);
			}
			System.out.println(j.join(paddedLine));
		}
	}

}
