package org.opensha.commons.eq.cat;

import java.util.Date;

import org.opensha.commons.eq.cat.util.DataType;

/**
 * Catalog interface providing reference fields for primary catalog data types.
 * 
 * @author Peter Powers
 * @version $Id: Catalog.java 7478 2011-02-15 04:56:25Z pmpowers $
 * 
 */
public interface Catalog {

	// TODO clean
	// If new catalog data types are added, the following must
	// also be updated:
	// -- static initializer in DefaultCatalog
	// -- data arrays in AbstractReader & AbstractWriter
	// -- data array blanking; clearArrays() in AbstractReader & AbstractWriter
	//
	// If new magnitude or event types are added, update:
	// -- valueForMagnitudeType or valueForEventType in AbstractReader

	/** Maximum size of a catalog; currently set to 800,000 events. */
	public static final int MAX_SIZE = 800000;

	/**
	 * Adds an array of data of the specified type to this catalog. The first
	 * call to this method sets the catalog size based on the supplied array.
	 * Method sets the size of the catalog if it has not yet been set. When
	 * event times are added to the catalog, they must be sorted ascending.
	 * 
	 * @param type of data to be added
	 * @param data array to be added
	 * @throws NullPointerException if <code>type</code> or <code>data</code>
	 *         are <code>null</code>
	 * @throws IllegalArgumentException if (1) <code>type</code> already exists
	 *         in catalog, (2) <code>data</code> is not an array, (3)
	 *         <code>data</code> is not of the class specified by
	 *         {@link DataType#clazz()}, (4) <code>data</code> is longer than
	 *         MAX_SIZE, (5) <code>data</code> is not the first array being
	 *         added and <code>data.length != size()</code>, or (6)
	 *         <code>type</code> is <code>DataType.TIME</code> and
	 *         <code>data</code> is not sorted ascending
	 * @throws IllegalArgumentException if <code>type</code> is one of
	 *         [LONGITUDE, LATITUDE, DEPTH, MAGNITUDE] and data contains values
	 *         that are out of range for the <code>type</code>
	 */
	public void addData(DataType type, Object data);

	/**
	 * Returns a copy of the requested data type or <code>null</code> if the
	 * <code>DataType</code> is not present in this catalog. Implementations may
	 * return a reference or a deep copy of the requested data array.
	 * 
	 * @param type requested
	 * @return the data array
	 */
	public Object getData(DataType type);

	/**
	 * Returns the number of events in this catalog or -1 if the catalog is
	 * empty.
	 * 
	 * @return the catalog size
	 */
	public int size();

	/**
	 * Returns whether this catalog contains a particular <code>DataType</code>.
	 * 
	 * @param type to look for
	 * @return whether data of the specified type exists in this catalog
	 */
	public boolean contains(DataType type);

	/**
	 * Returns whether copies of or references to internal data are provided by
	 * <code>getData()</code>.
	 * 
	 * @return read access status
	 */
	public boolean readable();

	/**
	 * Returns a string representation of the event at a given index. Method
	 * will throw an exception if index is out of range or catalog does not
	 * contain time and magnitude data.
	 * 
	 * @param index of event
	 * @return a string describing an earthquake event
	 * @throws IndexOutOfBoundsException if requested index is out of range
	 * @throws IllegalStateException if catalog does not include magnitude or
	 *         date values
	 */
	public String getEventString(int index);

	/**
	 * Returns a string representation of the event at a given index.
	 * 
	 * @param index of event
	 * @return a string describing an earthquake event
	 * @throws IndexOutOfBoundsException if requested index is out of range
	 * @throws IllegalStateException if catalog does not include event ID,
	 *         magnitude or date values
	 */
	public String getEventStringWithID(int index);

	/**
	 * Returns the eventID of the event at index.
	 * 
	 * @param index of event
	 * @return the eventID value
	 * @throws IndexOutOfBoundsException if requested index is out of range
	 * @throws IllegalStateException if catalog does not include event ID values
	 */
	public int getEventID(int index);

	/**
	 * Returns the time of the event at index (in milliseconds).
	 * 
	 * @param index of event
	 * @return the time of the event
	 * @throws IndexOutOfBoundsException if requested index is out of range
	 * @throws IllegalStateException if catalog does not include event time
	 *         values
	 */
	public long getTime(int index);

	/**
	 * Returns the <code>Date</code> of the event at index.
	 * 
	 * @param index of event
	 * @return the <code>Date</code> of the event
	 * @throws IndexOutOfBoundsException if requested index is out of range
	 * @throws IllegalStateException if catalog does not include event time
	 *         values
	 */
	public Date getDate(int index);

	/**
	 * Returns start date of this catalog or <code>null</code> if it is not set.
	 * 
	 * @return the catalog start <code>Date</code>
	 */
	public Date minDate();

	/**
	 * Returns end date of this catalog or <code>null</code> if it is not set.
	 * 
	 * @return the catalog end <code>Date</code>
	 */
	public Date maxDate();

	/**
	 * Returns the value of a the requested <code>DataType</code> for the event
	 * at the specified index. Method is only valid for the following
	 * double-valued <code>DataTypes</code>: [LONGITUDE, LATITUDE, DEPTH,
	 * MAGNITUDE]
	 * 
	 * @param type requested
	 * @param index of event
	 * @return the <code>type</code> value at event <code>index</code>
	 * @throws NullPointerException if <code>type</code> is <code>null</code>
	 * @throws IllegalArgumentException if requested <code>type</code> is not
	 *         valid
	 * @throws IndexOutOfBoundsException if requested index is out of range
	 * @throws IllegalStateException if catalog does not include values for the
	 *         requested <code>type</code>
	 */
	public double getValue(DataType type, int index);

	/**
	 * Returns the minimum value of the requested <code>DataType</code>. Method
	 * is only valid for the following double-valued <code>DataTypes</code>:
	 * [LONGITUDE, LATITUDE, DEPTH, MAGNITUDE]
	 * 
	 * @param type requested
	 * @return the minimum value for the <code>DatType</code>
	 * @throws NullPointerException if <code>type</code> is <code>null</code>
	 * @throws IllegalArgumentException if requested <code>type</code> is not
	 *         valid
	 * @throws IllegalStateException if requested <code>type</code> is valid but
	 *         was never added to catalog
	 */
	public double minForType(DataType type);

	/**
	 * Returns the maximum value of the requested <code>DataType</code>. Method
	 * is only valid for the following double-valued <code>DataTypes</code>:
	 * [LONGITUDE, LATITUDE, DEPTH, MAGNITUDE]
	 * 
	 * @param type requested
	 * @return the maximum value for the <code>DatType</code>
	 * @throws NullPointerException if <code>type</code> is <code>null</code>
	 * @throws IllegalArgumentException if requested <code>type</code> is not
	 *         valid
	 * @throws IllegalStateException if requested <code>type</code> is valid but
	 *         was never added to catalog
	 */
	public double maxForType(DataType type);

}
