/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;
import java.util.List;
/**
 * <p>Title: XYZ_DataSetAPI</p>
 * <p>Description: This interface defines the DataSet for the X,Y and Z.
 * This is the parent interface for <code>GeographicDataSetAPI</code>, which
 * should be used for any Geographic (Location based) XYZ datasets.</p>
 * <p>Copyright: Copyright (c) 2010</p>
 * <p>Company: </p>
 * @author : Kevin Milner
 * @version 1.0
 */

public interface XYZ_DataSet extends java.io.Serializable, Cloneable {

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
	 * Returns the minimum Z value in this dataset.
	 * 
	 * @return minimum Z value, or positive infinity if the dataset is empty
	 */
	public double getMinZ();

	/**
	 * Returns the maximum Z value in this dataset.
	 * 
	 * @return maximum Z value, or negative infinity if the dataset is empty
	 */
	public double getMaxZ();
	
	/**
	 * @return the sum of all Z values
	 */
	public double getSumZ();
	
	/**
	 * Sets the value at the given point. If the point doesn't exist, it will be added
	 * to the dataset.
	 * 
	 * @param point - the point at which to set
	 * @param z - the value to set
	 * @throws NullPointerException if <code>point</code> is null
	 */
	public void set(Point2D point, double z);
	
	/**
	 * Sets the value at the given point. If the point doesn't exist, it will be added
	 * to the dataset.
	 * 
	 * @param x - the x value of the point at which to set
	 * @param y - the y value of the point at which to set
	 * @param z - the value to set
	 */
	public void set(double x, double y, double z);
	
	/**
	 * Sets the value at the given index. If index < 0 or index >= size(),
	 * then an exception is thrown.
	 * 
	 * @param index - the index of the point to be set
	 * @param z - the value to set
	 * @throws IndexOutOfBoundsException if <code>index < 0</code> or <code>index >= size()</code>
	 */
	public void set(int index, double z);
	
	/**
	 * Gets the value at the given point. If the point doesn't exist, null will be returned.
	 * 
	 * @param point - the point at which to get
	 * @return the value at the given point, or null if the point is not contained in the dataset
	 */
	public double get(Point2D point);
	
	/**
	 * Gets the value at the given point. If the point doesn't exist, null will be returned.
	 * 
	 * @param x - the x value of the point at which to get
	 * @param y - the y value of the point at which to get
	 * @return the value at the given point
	 */
	public double get(double x, double y);

	/**
	 * Gets the value at the given index. If index < 0 or index >= size(),
	 * then an exception is thrown.
	 * @param index - the index of the point at which to get
	 * @return the value at the given index
	 */
	public double get(int index);
	
	/**
	 * Gets the point at the given index. If index < 0 or index >= size(),
	 * then an exception is thrown.
	 * 
	 * @param index - the index of the point to get
	 * @return the point at the given index
	 * @throws IndexOutOfBoundsException if <code>index</code> < 0 or index >= size()
	 */
	public Point2D getPoint(int index);
	
	/**
	 * Returns the index of the given point, or -1 if it isn't in the dataset.
	 * 
	 * @param point - the point at which to return the index
	 * @return index of the given point, or -1 if it isn't in the dataset.
	 */
	public int indexOf(Point2D point);
	
	/**
	 * Returns the index of the given point, or -1 if it isn't in the dataset.
	 * 
	 * @param x - the x value of the point at which to return the index
	 * @param y - the y value of the point at which to return the index
	 * @return index of the given point, or -1 if it isn't in the dataset.
	 */
	public int indexOf(double x, double y);
	
	/**
	 * Returns true if the dataset contains the given point, false otherwise.
	 * 
	 * @param point - the point to check
	 * @return true if <code>point</code> is not null and is contained by the dataset, false otherwise
	 */
	public boolean contains(Point2D point);
	
	/**
	 * Returns true if the dataset contains the given point, false otherwise.
	 * 
	 * @param x - the x value of the point to check
	 * @param y - the y value of the point to check
	 * @return true if the dataset contains the given point, false otherwise
	 */
	public boolean contains(double x, double y);
	
	/**
	 * Returns the size of this dataset.
	 * 
	 * @return size of this dataset
	 */
	public int size();
	
	/**
	 * Sets every point in this dataset from the given dataset.
	 * 
	 * @param dataset - dataset who's values are to be set
	 * @throws NullPointerException if <code>dataset</code> is null
	 */
	public void setAll(XYZ_DataSet dataset);
	
	/**
	 * Returns a list of all points in the correct order (as defined by indexOf). If the dataset is empty,
	 * then an empty list will be returned.
	 * 
	 * @return list of all points in the dataset
	 */
	public List<Point2D> getPointList();
	
	/**
	 * Returns a list of all values in the correct order (as defined by indexOf). If the dataset is empty,
	 * then an empty list will be returned.
	 * 
	 * @return list of all values in the dataset
	 */
	public List<Double> getValueList();
	
	/**
	 * Returns a shallow copy of this <code>XYZ_DataSet</code>. Internal points are not cloned.
	 * 
	 * @return shallow copy of this <code>XYZ_DataSet</code>
	 */
	public XYZ_DataSet copy();
	
	/*
	 * 		******************************* MATH OPERATIONS *******************************
	 */
	
	/**
	 * Takes the absolute value of the each value in the dataset.
	 * 
	 * @param map
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
	 * @param pow - the power with which to raise each value in the dataset
	 */
	public void pow(double pow);
	
	/**
	 * Each value in this dataset is scaled by the given scalar.
	 * 
	 * @param scalar - the double value with which to scale each value in the dataset
	 */
	public void scale(double scalar);
	
	/**
	 * The given value is added to each value in this dataset.
	 * 
	 * @param value - the double value to add to each value in the dataset
	 */
	public void add(double value);
	
	
}
