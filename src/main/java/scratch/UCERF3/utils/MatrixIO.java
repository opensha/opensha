package scratch.UCERF3.utils;

import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.map.tdouble.AbstractLongDoubleMap;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCMDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseRCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseRCMDoubleMatrix2D;

public class MatrixIO {

	/**
	 * Saves a binary file containing the given sparse matrix. Only non zero values are stored.
	 * <br><br>
	 * Output format:<br>
	 * 3 integers, for rows, cols, # values.<br>
	 * Then for each value, 2 integers for row, col, then 1 double for value. 
	 * 
	 * @param mat
	 * @param file
	 * @throws IOException
	 */
	public static void saveSparse(DoubleMatrix2D mat, File file) throws IOException {
		Preconditions.checkNotNull(mat, "array cannot be null!");
		Preconditions.checkArgument(mat.rows() > 0 && mat.columns() > 0, "matrix can't be empty!");
		
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
		out.writeInt(mat.rows());
		out.writeInt(mat.columns());

		if (mat instanceof SparseDoubleMatrix2D) {
			AbstractLongDoubleMap map = ((SparseDoubleMatrix2D)mat).elements();
			int nnz = mat.cardinality();
			long[] keys = map.keys().elements();
			double[] values = map.values().elements();
			
			out.writeInt(values.length);
			
			int columns = mat.columns();
			for (int i = 0; i < nnz; i++) {
				int row = (int) (keys[i] / columns);
				int column = (int) (keys[i] % columns);
				//				A.setQuick(row, column, values[i]);
				out.writeInt(row);
				out.writeInt(column);
				out.writeDouble(values[i]);
			}
		} else {
			IntArrayList rowList = new IntArrayList();
			IntArrayList colList = new IntArrayList();
			DoubleArrayList valList = new DoubleArrayList();
			
			mat.getNonZeros(rowList, colList, valList);
			
			Preconditions.checkState(rowList.size()>0, "rowList is empty!");
			Preconditions.checkState(rowList.size() == colList.size() && colList.size() == valList.size(),
			"array sizes incorrect!");
			
			// write header: rows, cols, values
			out.writeInt(valList.size());

			for (int i=0; i<valList.size(); i++) {
				int row = rowList.get(i);
				int col = colList.get(i);
				double val = valList.get(i);

				out.writeInt(row);
				out.writeInt(col);
				out.writeDouble(val);
			}
		}

		out.close();
	}

	/**
	 * Loads a matrix saved in the format of {@link MatrixIO.saveSparse}.
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 * @see MatrixIO.saveSparse
	 */
	public static DoubleMatrix2D loadSparse(File file) throws IOException {
		return loadSparse(file, null);
	}

	public static DoubleMatrix2D loadSparse(File file, Class<? extends DoubleMatrix2D> clazz) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");
		return loadSparse(new FileInputStream(file), clazz);
	}

	public static DoubleMatrix2D loadSparse(InputStream input) throws IOException {
		return loadSparse(input, null);
	}

	public static DoubleMatrix2D loadSparse(InputStream input, Class<? extends DoubleMatrix2D> clazz) throws IOException {
		Preconditions.checkNotNull(input, "Input stream cannot be null!");
		if (!(input instanceof BufferedInputStream))
			input = new BufferedInputStream(input);
		DataInputStream in = new DataInputStream(input);

		int nRows = in.readInt();
		int nCols = in.readInt();
		int nVals = in.readInt();

		System.out.println("Mat size: "+nRows+"x"+nCols);
		System.out.println("Num non zero: "+nVals);

		Preconditions.checkState(nRows > 0, "file contains no rows!");
		Preconditions.checkState(nCols > 0, "file contains no columns!");

		int[] cols = new int[nVals];
		int[] rows = new int[nVals];
		double[] vals = new double[nVals];

		for (int i=0; i<nVals; i++) {
			rows[i] = in.readInt();
			cols[i] = in.readInt();
			vals[i] = in.readDouble();
			Preconditions.checkState(!Double.isNaN(vals[i]), "no NaN's allowed!");
		}

		in.close();

		DoubleMatrix2D mat;
		if (clazz == null || clazz.equals(SparseCCDoubleMatrix2D.class))
			// default
			mat = new SparseCCDoubleMatrix2D(nRows, nCols, rows, cols, vals, false, false, false);
		else if (clazz.equals(SparseRCDoubleMatrix2D.class))
			mat = new SparseRCDoubleMatrix2D(nRows, nCols, rows, cols, vals, false, false, false);
		else if (clazz.equals(SparseDoubleMatrix2D.class))
			mat = new SparseDoubleMatrix2D(nRows, nCols, rows, cols, vals);
		else if (clazz.equals(SparseCCMDoubleMatrix2D.class)) {
			mat = new SparseCCMDoubleMatrix2D(nRows, nCols);
			for (int i=0; i<nVals; i++)
				mat.set(rows[i], cols[i], vals[i]);
		} else if (clazz.equals(SparseRCMDoubleMatrix2D.class)) {
			mat = new SparseRCMDoubleMatrix2D(nRows, nCols);
			for (int i=0; i<nVals; i++)
				mat.set(rows[i], cols[i], vals[i]);
		} else
			throw new IllegalArgumentException("Unknown matrix type: "+clazz);

		return mat;
	}

	/**
	 * Writes the given double array to a file. Output file simply contains a series of big endian double values.
	 * @param array
	 * @param file
	 * @throws IOException
	 */
	public static void doubleArrayToFile(double[] array, File file) throws IOException {
		Preconditions.checkNotNull(array, "array cannot be null!");
		Preconditions.checkArgument(array.length > 0, "array cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		for (double val : array) {
			out.writeDouble(val);
		}

		out.close();
	}

	/**
	 * Reads a file created by {@link MatrixIO.doubleArrayToFile} into a double array.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static double[] doubleArrayFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();

		return doubleArrayFromInputStream(new FileInputStream(file), len);
	}

	/**
	 * Reads an imput stream created by {@link MatrixIO.doubleArrayToFile} into a double array.
	 * @param file
	 * @param length
	 * @return
	 * @throws IOException
	 */
	public static double[] doubleArrayFromInputStream(InputStream is, long length) throws IOException {
		Preconditions.checkState(length > 0, "file is empty!");
		Preconditions.checkState(length % 8 == 0, "file size isn't evenly divisible by 8, " +
		"thus not a sequence of double values.");

		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		int size = (int)(length / 8);

		double[] array = new double[size];

		for (int i=0; i<size; i++)
			array[i] = in.readDouble();

		in.close();

		return array;
	}
	
	
	
	/**
	 * Writes the given int array to a file. Output file simply contains a series of big endian int values.
	 * @param array
	 * @param file
	 * @throws IOException
	 */
	public static void intArrayToFile(int[] array, File file) throws IOException {
		Preconditions.checkNotNull(array, "array cannot be null!");
		Preconditions.checkArgument(array.length > 0, "array cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		for (int val : array) {
			out.writeInt(val);
		}

		out.close();
	}

	/**
	 * Reads a file created by {@link MatrixIO.intArrayToFile} into an int array.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static int[] intArrayFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();

		return intArrayFromInputStream(new FileInputStream(file), len);
	}

	/**
	 * Reads an imput stream created by {@link MatrixIO.doubleArrayToFile} into a double array.
	 * @param file
	 * @param length
	 * @return
	 * @throws IOException
	 */
	public static int[] intArrayFromInputStream(InputStream is, long length) throws IOException {
		Preconditions.checkState(length > 0, "file is empty!");
		Preconditions.checkState(length % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of int values.");

		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		DataInputStream in = new DataInputStream(is);

		int size = (int)(length / 4);

		int[] array = new int[size];

		for (int i=0; i<size; i++)
			array[i] = in.readInt();

		in.close();

		return array;
	}


	/**
	 * Writes the given list of double arrays to a file. All values are stored big endian. Output format contains
	 * first an integer, specifying the size of the list, then for each element in the list, an integer, denoting
	 * the size (n) of the array, followed by n double values (the array values). 
	 * @param list
	 * @param file
	 * @throws IOException
	 */
	public static void doubleArraysListToFile(List<double[]> list, File file) throws IOException {
		Preconditions.checkNotNull(list, "list cannot be null!");
		Preconditions.checkArgument(!list.isEmpty(), "list cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		out.writeInt(list.size());

		for (double[] array : list) {
			Preconditions.checkNotNull(array, "array cannot be null!");
//			Preconditions.checkState(array.length > 0, "array cannot be empty!"); // actually it can be!
			out.writeInt(array.length);
			for (double val : array)
				out.writeDouble(val);
		}

		out.close();
	}

	/**
	 * Reads a file created by {@link MatrixIO.doubleArraysListFromFile} into a double array.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static List<double[]> doubleArraysListFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of double & integer values.");

		return doubleArraysListFromInputStream(new FileInputStream(file));
	}

	/**
	 * Reads a file created by {@link MatrixIO.doubleArraysListFromFile} into a double array.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static List<double[]> doubleArraysListFromInputStream(InputStream is) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);

		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();

		Preconditions.checkState(size > 0, "Size must be > 0!");

		ArrayList<double[]> list = new ArrayList<double[]>(size);

		for (int i=0; i<size; i++) {
			int arraySize = in.readInt();

			double[] array = new double[arraySize];
			for (int j=0; j<arraySize; j++)
				array[j] = in.readDouble();

			list.add(array);
		}

		in.close();

		return list;
	}
	
	
	/**
	 * Writes the given list of float arrays to a file. All values are stored big endian. Output format contains
	 * first an integer, specifying the size of the list, then for each element in the list, an integer, denoting
	 * the size (n) of the array, followed by n float values (the array values). 
	 * @param list
	 * @param file
	 * @throws IOException
	 */
	public static void floatArraysListToFile(List<float[]> list, File file) throws IOException {
		Preconditions.checkNotNull(list, "list cannot be null!");
		Preconditions.checkArgument(!list.isEmpty(), "list cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		out.writeInt(list.size());

		for (float[] array : list) {
			Preconditions.checkNotNull(array, "array cannot be null!");
//			Preconditions.checkState(array.length > 0, "array cannot be empty!"); // actually it can be!
			out.writeInt(array.length);
			for (float val : array)
				out.writeFloat(val);
		}

		out.close();
	}
	
	
	/**
	 * Writes the given list of float lists to a file. All values are stored big endian. Output format contains
	 * first an integer, specifying the size of the list, then for each element in the list, an integer, denoting
	 * the size (n) of the array, followed by n float values (the array values). 
	 * @param list
	 * @param file
	 * @throws IOException
	 */
	public static void floatListListToFile(List<? extends List<Float>> list, File file) throws IOException {
		Preconditions.checkNotNull(list, "list cannot be null!");
		Preconditions.checkArgument(!list.isEmpty(), "list cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		out.writeInt(list.size());

		for (List<Float> floatList : list) {
			Preconditions.checkNotNull(floatList, "array cannot be null!");
//			Preconditions.checkState(array.length > 0, "array cannot be empty!"); // actually it can be!
			out.writeInt(floatList.size());
			for (float val : floatList)
				out.writeFloat(val);
		}

		out.close();
	}


	/**
	 * Reads a file created by {@link MatrixIO.floatArraysListFromFile} into a float array.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static List<float[]> floatArraysListFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of float & integer values.");

		return floatArraysListFromInputStream(new FileInputStream(file));
	}

	/**
	 * Reads a file created by {@link MatrixIO.floatArraysListFromFile} into a float array.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static List<float[]> floatArraysListFromInputStream(InputStream is) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);

		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();

		Preconditions.checkState(size > 0, "Size must be > 0!");

		ArrayList<float[]> list = new ArrayList<float[]>(size);

		for (int i=0; i<size; i++) {
			int arraySize = in.readInt();

			float[] array = new float[arraySize];
			for (int j=0; j<arraySize; j++)
				array[j] = in.readFloat();

			list.add(array);
		}

		in.close();

		return list;
	}

	
	
	

	
	/**
	 * This writes the discretized functions to a file. No metadata is preserved.
	 * 
	 * @param funcs
	 * @param file
	 * @throws IOException 
	 */
	public static void discFuncsToFile(DiscretizedFunc[] funcs, File file) throws IOException {
		List<double[]> arrays = Lists.newArrayList();
		// pack as xvals, yvals and save as array list
		for (DiscretizedFunc func : funcs) {
			int num = func.size();
			double[] xVals = new double[num];
			double[] yVals = new double[num];
			for (int i=0; i<num; i++) {
				Point2D pt = func.get(i);
				xVals[i] = pt.getX();
				yVals[i] = pt.getY();
			}
			arrays.add(xVals);
			arrays.add(yVals);
		}
		doubleArraysListToFile(arrays, file);
	}
	
	/**
	 * Reads a file created by {@link MatrixIO.discFuncsToFile} into a DiscretizedFunc array.
	 * @param file
	 * @return
	 * @throws FileNotFoundException 
	 * @throws IOException
	 */
	public static DiscretizedFunc[] discFuncsFromFile(File file) throws FileNotFoundException, IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of double & integer values.");

		return discFuncsFromInputStream(new FileInputStream(file));
	}
	
	/**
	 * Reads an inputstream created by {@link MatrixIO.discFuncsToFile} into a DiscretizedFunc array.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static DiscretizedFunc[] discFuncsFromInputStream(InputStream is) throws IOException {
		List<double[]> arrays = doubleArraysListFromInputStream(is);
		
		Preconditions.checkState(arrays.size() % 2 == 0, "should be even number of arrays in file");
		
		DiscretizedFunc[] funcs = new DiscretizedFunc[arrays.size()/2];
		for (int i=0; i<arrays.size(); i+=2) {
			int ind = i/2;
			funcs[ind] = new LightFixedXFunc(arrays.get(i), arrays.get(i+1));
		}
		
		return funcs;
	}

	/**
	 * Writes the given list of integer lists to a file. All values are stored big endian. Output format contains
	 * first an integer, specifying the size of the list, then for each element in the list, an integer, denoting
	 * the size (n) of the array, followed by n integer values (the list values). 
	 * @param list
	 * @param file
	 * @throws IOException
	 */
	public static void intListListToFile(List<? extends List<Integer>> list, File file) throws IOException {
		Preconditions.checkNotNull(list, "list cannot be null!");
		Preconditions.checkArgument(!list.isEmpty(), "list cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		out.writeInt(list.size());

		for (List<Integer> ints : list) {
			Preconditions.checkNotNull(ints, "list cannot be null!");
			//			Preconditions.checkState(!ints.isEmpty(), "list cannot be empty!");
			out.writeInt(ints.size());
			for (int val : ints)
				out.writeInt(val);
		}

		out.close();
	}

	/**
	 * Reads a file created by {@link MatrixIO.intListListToFile} into an integer list list.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static List<List<Integer>> intListListFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of double & integer values.");

		return intListListFromInputStream(new FileInputStream(file));
	}

	/**
	 * Reads a file created by {@link MatrixIO.intListListToFile} into an integer list list.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static List<List<Integer>> intListListFromInputStream(
			InputStream is) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);

		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();

		Preconditions.checkState(size > 0, "Size must be > 0!");

		ArrayList<List<Integer>> list = new ArrayList<List<Integer>>();

		for (int i=0; i<size; i++) {
			int listSize = in.readInt();

			// use shorts if possible
			int[] intArray = new int[listSize];
			short[] shortArray = new short[listSize];
			boolean shortSafe = true;
			for (int j=0; j<listSize; j++) {
				int val = in.readInt();
				shortSafe = shortSafe && val < Short.MAX_VALUE;
				intArray[j] = val;
				shortArray[j] = (short)val;
			}

			if (shortSafe)
				list.add(new ShortListWrapper(shortArray));
			else
				list.add(Ints.asList(intArray));
		}

		in.close();

		return list;
	}
	
	/**
	 * Class to use shorts as the backing array for memory savings
	 * @author kevin
	 *
	 */
	private static class ShortListWrapper extends AbstractList<Integer> {
		
		private short[] vals;
		
		public ShortListWrapper(short[] vals) {
			this.vals = vals;
		}

		@Override
		public Integer get(int index) {
			return new Integer(vals[index]);
		}

		@Override
		public int size() {
			return vals.length;
		}
		
	}
	
	/**
	 * Will return a primitive backed array, and will use shorts if possible
	 * @param otherArray
	 * @return
	 */
	public static List<Integer> getMemoryEfficientIntArray(List<Integer> otherArray) {
		// use shorts if possible
		int listSize = otherArray.size();
		int[] intArray = new int[listSize];
		short[] shortArray = new short[listSize];
		boolean shortSafe = true;
		for (int j=0; j<listSize; j++) {
			int val = otherArray.get(j);
			shortSafe = shortSafe && ((val >= 0 && val < Short.MAX_VALUE) || (val < 0 && -val < Short.MAX_VALUE));
			intArray[j] = val;
			shortArray[j] = (short)val;
		}

		if (shortSafe)
			return new ShortListWrapper(shortArray);
		else
			return Ints.asList(intArray);
	}

	/**
	 * Writes the given list of integer arrays to a file. All values are stored big endian. Output format is identical
	 * to {@link MatrixIO.intListListToFile}.
	 * @param list
	 * @param file
	 * @throws IOException
	 */
	public static void intArraysListToFile(List<int[]> list, File file) throws IOException {
		Preconditions.checkNotNull(list, "list cannot be null!");
		Preconditions.checkArgument(!list.isEmpty(), "list cannot be empty!");

		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

		out.writeInt(list.size());

		for (int[] ints : list) {
			Preconditions.checkNotNull(ints, "list cannot be null!");
			//			Preconditions.checkState(!ints.isEmpty(), "list cannot be empty!");
			out.writeInt(ints.length);
			for (int val : ints)
				out.writeInt(val);
		}

		out.close();
	}
	
	

	/**
	 * Reads a file created by {@link MatrixIO.intListListToFile} or {@link MatrixIO.intArraysListToFile}
	 * into an integer array list.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static List<int[]> intArraysListFromFile(File file) throws IOException {
		Preconditions.checkNotNull(file, "File cannot be null!");
		Preconditions.checkArgument(file.exists(), "File doesn't exist!");

		long len = file.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of double & integer values.");

		return intArraysListFromInputStream(new FileInputStream(file));
	}

	/**
	 * Reads a file created by {@link MatrixIO.intListListToFile} or {@link MatrixIO.intArraysListToFile}
	 * into an integer array list.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static List<int[]> intArraysListFromInputStream(
			InputStream is) throws IOException {
		Preconditions.checkNotNull(is, "InputStream cannot be null!");
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);

		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();

		Preconditions.checkState(size > 0, "Size must be > 0!");

		ArrayList<int[]> list = Lists.newArrayList();

		for (int i=0; i<size; i++) {
			int listSize = in.readInt();

			int[] intArray = new int[listSize];
			for (int j=0; j<listSize; j++) {
				int val = in.readInt();
				intArray[j] = val;
			}
			list.add(intArray);
		}

		in.close();

		return list;
	}

}
