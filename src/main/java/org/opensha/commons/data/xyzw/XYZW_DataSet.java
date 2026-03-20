package org.opensha.commons.data.xyzw;

public interface XYZW_DataSet extends java.io.Serializable, Cloneable {

	/**
	 * Returns the minimum X value in this dataset.
	 * 
	 * @return minimum X value, or positive infinity if the dataset is empty
	 */
	public double getMinX();

	/**
	 * Returns the maximum X value in this dataset.
	 * 
	 * @return maximum X value, or negative infinity if the dataset is empty
	 */
	public double getMaxX();

	/**
	 * Returns the minimum Y value in this dataset.
	 * 
	 * @return minimum Y value, or positive infinity if the dataset is empty
	 */
	public double getMinY();

	/**
	 * Returns the maximum Y value in this dataset.
	 * 
	 * @return maximum Y value, or negative infinity if the dataset is empty
	 */
	public double getMaxY();

	/**
	 * Returns the minimum depth in this dataset.
	 * 
	 * @return minimum depth, or positive infinity if the dataset is empty
	 */
	public double getMinDepth();

	/**
	 * Returns the maximum depth in this dataset.
	 * 
	 * @return maximum depth, or negative infinity if the dataset is empty
	 */
	public double getMaxDepth();

	/**
	 * Returns the minimum value in this dataset.
	 * 
	 * @return minimum value, or positive infinity if the dataset is empty
	 */
	public double getMinValue();

	/**
	 * Returns the maximum value in this dataset.
	 * 
	 * @return maximum value, or negative infinity if the dataset is empty
	 */
	public double getMaxValue();

	/**
	 * @return the sum of all values
	 */
	public double getSumValues();

	/**
	 * Sets the value at the given point. If the point doesn't exist, it will be added to the dataset.
	 * 
	 * @param x the x value of the point at which to set
	 * @param y the y value of the point at which to set
	 * @param depth the depth value of the point at which to set
	 * @param value the value to set
	 */
	public void set(double x, double y, double depth, double value);

	/**
	 * Sets the value at the given index. If index < 0 or index >= size(), then an exception is thrown.
	 * 
	 * @param index the index of the point to be set
	 * @param value the value to set
	 * @throws IndexOutOfBoundsException if <code>index < 0</code> or <code>index >= size()</code>
	 */
	public void set(int index, double value);

	/**
	 * Adds to the value at the given point. If the point doesn't exist, it will be added to the dataset.
	 * 
	 * @param x the x value of the point at which to add
	 * @param y the y value of the point at which to add
	 * @param depth the depth value of the point at which to add
	 * @param value the value to add
	 */
	public void add(double x, double y, double depth, double value);

	/**
	 * Adds to the value at the given index. If index < 0 or index >= size(), then an exception is thrown.
	 * 
	 * @param index the index of the point to be updated
	 * @param value the value to add
	 * @throws IndexOutOfBoundsException if <code>index < 0</code> or <code>index >= size()</code>
	 */
	public void add(int index, double value);

	/**
	 * Gets the value at the given point.
	 * 
	 * @param x the x value of the point at which to get
	 * @param y the y value of the point at which to get
	 * @param depth the depth value of the point at which to get
	 * @return the value at the given point
	 */
	public double get(double x, double y, double depth);

	/**
	 * Gets the value at the given index. If index < 0 or index >= size(), then an exception is thrown.
	 * 
	 * @param index the index of the point at which to get
	 * @return the value at the given index
	 */
	public double get(int index);

	/**
	 * Gets the X coordinate at the given index.
	 * 
	 * @param index the index of the point at which to get the X coordinate
	 * @return the X coordinate
	 */
	public double getX(int index);

	/**
	 * Gets the Y coordinate at the given index.
	 * 
	 * @param index the index of the point at which to get the Y coordinate
	 * @return the Y coordinate
	 */
	public double getY(int index);

	/**
	 * Gets the depth at the given index.
	 * 
	 * @param index the index of the point at which to get the depth
	 * @return the depth
	 */
	public double getDepth(int index);

	/**
	 * Returns the index of the given point, or -1 if it isn't in the dataset.
	 * 
	 * @param x the x value of the point at which to return the index
	 * @param y the y value of the point at which to return the index
	 * @param depth the depth value of the point at which to return the index
	 * @return index of the given point, or -1 if it isn't in the dataset
	 */
	public int indexOf(double x, double y, double depth);

	/**
	 * Returns true if the dataset contains the given point, false otherwise.
	 * 
	 * @param x the x value of the point to check
	 * @param y the y value of the point to check
	 * @param depth the depth value of the point to check
	 * @return true if the dataset contains the given point, false otherwise
	 */
	public boolean contains(double x, double y, double depth);

	/**
	 * Returns the size of this dataset.
	 * 
	 * @return size of this dataset
	 */
	public int size();

	/**
	 * Sets every point in this dataset from the given dataset.
	 * 
	 * @param dataset dataset whose values are to be set
	 * @throws NullPointerException if <code>dataset</code> is null
	 */
	public void setAll(XYZW_DataSet dataset);

	/**
	 * Returns a deep copy of this dataset.
	 * 
	 * @return deep copy of this dataset
	 */
	public XYZW_DataSet copy();

	/**
	 * Takes the absolute value of each value in the dataset.
	 */
	public void abs();

	/**
	 * Takes the natural log of each value in the dataset.
	 */
	public void log();

	/**
	 * Takes the natural log base 10 of each value in the dataset.
	 */
	public void log10();

	/**
	 * Euler's number e raised to the power of each value in the dataset.
	 */
	public void exp();

	/**
	 * Given number raised to the power of each value in the dataset.
	 */
	public void exp(double base);

	/**
	 * Each value in this dataset is raised to the given power.
	 * 
	 * @param pow the power with which to raise each value in the dataset
	 */
	public void pow(double pow);

	/**
	 * Each value in this dataset is scaled by the given scalar.
	 * 
	 * @param scalar the double value with which to scale each value in the dataset
	 */
	public void scale(double scalar);

	/**
	 * The given value is added to each value in this dataset.
	 * 
	 * @param value the double value to add to each value in the dataset
	 */
	public void add(double value);
}
