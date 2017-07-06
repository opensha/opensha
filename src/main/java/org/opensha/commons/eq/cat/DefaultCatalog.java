package org.opensha.commons.eq.cat;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.commons.eq.cat.util.DataType.DEPTH;
import static org.opensha.commons.eq.cat.util.DataType.LATITUDE;
import static org.opensha.commons.eq.cat.util.DataType.LONGITUDE;
import static org.opensha.commons.eq.cat.util.DataType.MAGNITUDE;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.opensha.commons.eq.cat.io.CatalogReader;
import org.opensha.commons.eq.cat.io.CatalogWriter;
import org.opensha.commons.eq.cat.util.DataType;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.util.DataUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Doubles;

/**
 * Default catalog implementation. This class validates all data added to this
 * catalog and provides accessors for basic extents values (min and max lat,
 * lon, depth, mag, and date). The extents values are calculated automatically
 * as these data types are added to the catalog.<br/>
 * <br/>
 * <strong>Note that implementation assumes that input data is sorted ascending
 * by date.</strong><br/>
 * <br/>
 * A <code>DefaultCatalog</code> only allows access to copies of source data via
 * <code>getData()</code>; internal arrays are unmodifiable. To avoid
 * performance loss, especially with larger catalogs, users should use a
 * <code>MutableCatalog</code> instead. <br/>
 * <br/>
 * This class provides a static method to read catalog bounds data from an XML
 * metadata element created when saving as a binary file. The bounds data is
 * provided so that a catalog does not have to be loaded to retrieve min/max
 * data. However, this data is not used to reset internal min/max fields; they
 * are set each time a catalog is initialized/loaded.
 * 
 * @author Peter Powers
 * @version $Id: DefaultCatalog.java 8980 2012-05-22 17:23:51Z pmpowers $
 */

public class DefaultCatalog implements Catalog {

	private Map<DataType, Object> dataMap;
	private boolean readable = false;
	private int size = -1;

	private Date minDate = null;
	private Date maxDate = null;
	// store all double values types in maps
	private Map<DataType, Double> minVals = new HashMap<DataType, Double>();
	private Map<DataType, Double> maxVals = new HashMap<DataType, Double>();

	private Set<DataType> minMaxTypes = EnumSet.of(LATITUDE, LONGITUDE, DEPTH,
		MAGNITUDE);

	private static SimpleDateFormat eventSDF;
	static {
		eventSDF = new SimpleDateFormat("MM/dd/yyyy  HH:mm:ss  z");
		eventSDF.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	/**
	 * Constructs a new empty catalog. Catalog size (event count) is set using
	 * the first data array added; all subsequent data arrays must be the same
	 * size as the first
	 */
	public DefaultCatalog() {
		dataMap = new HashMap<DataType, Object>();
	}

	/**
	 * Constructs a new catalog from the given file using the specified reader.
	 * 
	 * @param file to read
	 * @param reader to process file
	 * @throws IOException if unable to initialize catalog using supplied
	 *         <code>File</code> and <code>CatalogReader</code>
	 */
	public DefaultCatalog(File file, CatalogReader reader) throws IOException {
		this();
		checkNotNull(file, "Supplied file is null");
		checkNotNull(reader, "Supplied catalog reader is null");
		reader.process(file, this);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean contains(DataType type) {
		return dataMap.containsKey(type);
	}

	/**
	 * Returns a deep copy of this catalog.
	 * 
	 * @return a duplicate <code>Catalog</code>
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() {
		DefaultCatalog clone = new DefaultCatalog();
		for (DataType type : dataMap.keySet()) {
			clone.addData(type, duplicateArray(dataMap.get(type)));
		}
		return clone;
	}

	/**
	 * Sets whether <code>getData()</code> returns references to internal data
	 * arrays or copies. Open access can significantly reduce memory overhead
	 * but increases risks of catalog data manipulation if the catalog is re-
	 * written to disk.
	 * 
	 * @param readable whether references to (true) or copies of (false) data
	 *        are returned
	 */
	protected void setReadable(boolean readable) {
		this.readable = readable;
	}

	@Override
	public boolean readable() {
		return readable;
	}

	@Override
	public void addData(DataType type, Object data) {
		checkNotNull(type, "Data type is null");
		checkArgument(!contains(type),
			"Catalog already contains supplied data type");
		checkNotNull(data, "Data array is null");
		checkArgument(data.getClass().isArray(),
			"Supplied 'data' is not an array");
		checkArgument(type.clazz().equals(data.getClass()),
			"Supplied data array class is not of the required type");
		checkArgument(Array.getLength(data) != 0,
			"Supplied data array is empty");
		checkArgument(Array.getLength(data) <= MAX_SIZE,
			"Supplied data array exceeds catalog size limit");
		if (size != -1) { // only if this is not the first 'add'
			checkArgument(Array.getLength(data) == size,
				"Supplied data array size does not match catalog size");
		}
		if (type == DataType.TIME) {
			checkArgument(checkTimesSorted((long[]) data),
				"Event times are not sorted ascending");
		}

		if (type == LONGITUDE) GeoTools.validateLons((double[]) data);
		if (type == LATITUDE) GeoTools.validateLats((double[]) data);
		if (type == DEPTH) GeoTools.validateDepths((double[]) data);
		if (type == MAGNITUDE) CatTools.validateMags((double[]) data);
		runMinMax(type, data);
		dataMap.put(type, data);
		if (size == -1) size = Array.getLength(data);
	}

	@Override
	public Object getData(DataType type) {
		return (readable) ? dataMap.get(type) : copyData(type);
	}

	/**
	 * Returns a deep copy of the requested data type or <code>null</code> if
	 * the type does not esist in this catalog.
	 * 
	 * @param type requested
	 * @return a deep copy of the requested type
	 */
	public Object copyData(DataType type) {
		return duplicateArray(dataMap.get(type));
	}

	/**
	 * Returns an immutable set of the data types in this catalog.
	 * 
	 * @return the data type array
	 */
	public Set<DataType> getDataTypes() {
		return ImmutableSet.copyOf(dataMap.keySet());
	}

	/**
	 * Returns an <code>indices.length</code> sized array of the requested data
	 * type from the indices specified or <code>null</code> if the data type
	 * does not exist in this catalog. This is a deep copy.
	 * 
	 * @param type requested
	 * @param indices to return for type
	 * @return the data array
	 * @throws NullPointerException if index array is <code>null</code>
	 * @throws IndexOutOfBoundsException if any indices are out of range
	 * 
	 * TODO better argument checking
	 */
	public Object deriveData(DataType type, int[] indices) {
		if (!contains(type)) return null;
		return DataUtils.arraySelect(dataMap.get(type), indices);
	}

	/**
	 * Derives a new catalog from this one using the events at the given
	 * indices.
	 * 
	 * @param indices of events to extract
	 * @param catalog the output catalog
	 * @return a reference to the output catalog provided
	 * @throws NullPointerException if index array is <code>null</code>
	 * @throws IndexOutOfBoundsException if any indices are out of range
	 * 
	 * TODO better argument checking
	 */
	public Catalog deriveCatalog(int[] indices, Catalog catalog) {
		for (DataType type : dataMap.keySet()) {
			if (contains(type)) {
				catalog.addData(type, deriveData(type, indices));
			}
		}
		return catalog;
	}

	/**
	 * Writes this catalog to a given file using a specified
	 * <code>CatalogWriter</code>.
	 * 
	 * @param file to write to
	 * @param writer file formatter to use
	 * @throws IOException if unable to write catalog to file
	 */
	public void writeCatalog(File file, CatalogWriter writer)
			throws IOException {
		writer.process(this, file);
	}

	/**
	 * Writes this catalog as a binary file and returns XML metadata that can be
	 * referenced for data type order when deserializing. Metadata also contains
	 * catalog bounds (min/max) data (so that catalog does not have to be loaded
	 * to get summary info).
	 * 
	 * @param file to write to
	 * @return an XML snippet of the write order and bounds metadata
	 * @throws NullPointerException if supplied file is <code>null</code>
	 * @throws IOException if there is a problem writing the file
	 */
	public Element writeCatalog(File file) throws IOException {
		checkNotNull(file, "Supplied file is null");
		Element catData = new DefaultElement(XML_ELEM_CATALOG_DATA);
		Element fields = new DefaultElement(XML_ELEM_CATALOG_DATA_FIELDS);
		catData.add(fields);
		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(new BufferedOutputStream(
				new FileOutputStream(file)));
			for (DataType type : dataMap.keySet()) {
				// add data object to xml
				Element field = new DefaultElement(XML_ELEM_CATALOG_FIELD);
				field.setText(type.toString());
				field.addAttribute("id", type.name());
				fields.add(field);
				// write object
				out.writeObject(dataMap.get(type));
			}
			catData.add(createBoundsData());
		} finally {
			IOUtils.closeQuietly(out);
		}
		return catData;
	}

	/**
	 * Reads this catalog from serialized data arrays. The data types listed in
	 * 'fields' must be valid for catalog class or an exception will be thrown
	 * when an invalid data type is added to internal arrays.
	 * 
	 * @param file to read
	 * @param meta data to read; XML format list of data types to load
	 * @throws NullPointerException if supplied file or XML metadata is
	 *         <code>null</code>
	 * @throws IllegalArgumentException if a <code>DataType</code> read from
	 *         metadata is not a valid type
	 * @throws IOException if there is an IO problem reading the file
	 * @throws ClassNotFoundException if there is a problem reading objects from
	 *         the file
	 */
	public void readCatalog(File file, Element meta) throws IOException,
			ClassNotFoundException {
		checkNotNull(file, "Supplied file is null");
		checkNotNull(meta, "Supplied metadata is null");
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new BufferedInputStream(
				new FileInputStream(file)));
			for (Object obj : meta.elements(XML_ELEM_CATALOG_FIELD)) {
				Element e = (Element) obj;
				DataType type = Enum.valueOf(DataType.class,
					e.attributeValue("id"));
				addData(type, in.readObject());
			}
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	@Override
	public String getEventString(int index) {
		return ("(M " + getValue(MAGNITUDE, index) + ")   " + eventSDF
			.format(getDate(index)));
	}

	@Override
	public String getEventStringWithID(int index) {
		return getEventString(index) + "   ID:" + getEventID(index);
	}

	@Override
	public int getEventID(int index) {
		checkPositionIndex(index, size, "Requested index");
		checkState(dataMap.get(DataType.EVENT_ID) != null,
			"Catalog is missing event ID values");
		return ((int[]) dataMap.get(DataType.EVENT_ID))[index];
	}

	@Override
	public long getTime(int index) {
		checkPositionIndex(index, size, "Requested index");
		return ((long[]) dataMap.get(DataType.TIME))[index];
	}

	@Override
	public Date getDate(int index) {
		return new Date(getTime(index));
	}

	@Override
	public Date minDate() {
		return minDate;
	}

	@Override
	public Date maxDate() {
		return maxDate;
	}

	@Override
	public double getValue(DataType type, int index) {
		checkNotNull(type, "Data type is null");
		checkArgument(minMaxTypes.contains(type), "Invalid data type");
		checkPositionIndex(index, size, "Requested index");
		checkState(dataMap.get(type) != null, "Catalog is missing %s values",
			type);
		return ((double[]) dataMap.get(type))[index];
	}

	@Override
	public double minForType(DataType type) {
		checkNotNull(type, "Data type is null");
		checkArgument(minMaxTypes.contains(type), "Invalid data type");
		checkState(dataMap.get(type) != null, "Catalog is missing %s values",
			type);
		return minVals.get(type);
	}

	@Override
	public double maxForType(DataType type) {
		checkNotNull(type, "Data type is null");
		checkArgument(minMaxTypes.contains(type), "Invalid data type");
		checkState(dataMap.get(type) != null, "Catalog is missing %s values",
			type);
		return maxVals.get(type);
	}

	/*
	 * Sets minimum and maximum values for latitude, longitude, depth,
	 * magnitude, and time.
	 */
	private void runMinMax(DataType type, Object data) {
		if (type == DataType.TIME) {
			minDate = new Date(((long[]) data)[0]);
			maxDate = new Date(((long[]) data)[Array.getLength(data) - 1]);
		} else if (minMaxTypes.contains(type)) {
			minVals.put(type, Doubles.min((double[]) data));
			maxVals.put(type, Doubles.max((double[]) data));
		}
	}

	/*
	 * Duplicates the supplied array.
	 * TODO this is not needed Arrays.copyOf(T[]) works
	 */
	private Object duplicateArray(Object data) {
		int size = Array.getLength(data);
		Object out = Array
			.newInstance(data.getClass().getComponentType(), size);
		System.arraycopy(data, 0, out, 0, size);
		return out;
	}

	/*
	 * Ensures that event times are sorted ascending when added to catalog.
	 */
	private boolean checkTimesSorted(long[] times) {
		long[] timesCopy = (long[]) duplicateArray(times);
		Arrays.sort(timesCopy);
		return Arrays.equals(timesCopy, times);
	}
	
	/* metadata id strings */
	static final String XML_ELEM_CATALOG_DATA = "catalogData";
	static final String XML_ELEM_CATALOG_DATA_FIELDS = "dataFields";
	static final String XML_ELEM_CATALOG_FIELD = "field";
	static final String XML_ELEM_CATALOG_DATA_BOUNDS = "dataBounds";
	static final String XML_ELEM_CATALOG_BOUND = "bound";
	static final String EVENT_COUNT = "eventCount";
	static final String MIN_LAT = "minLat";
	static final String MAX_LAT = "maxLat";
	static final String MIN_LON = "minLon";
	static final String MAX_LON = "maxLon";
	static final String MIN_DEPTH = "minDepth";
	static final String MAX_DEPTH = "maxDepth";
	static final String MIN_MAG = "minMag";
	static final String MAX_MAG = "maxMag";
	static final String MIN_DATE = "minDate";
	static final String MAX_DATE = "maxDate";

	/**
	 * Places catalog bounds data in a <code>Map</code> for easy access. Input
	 * metadata should have been created when saving catalog in binary format.
	 * If inappropriate XML is encountered, method returns <code>null</code>.
	 * 
	 * TODO delete?
	 * 
	 * @param data element to parse
	 * @return hastable of catalog bounds information
	 */
	@Deprecated
	public static Map<String, String> getBoundsData(Element data) {
		Map<String, String> bounds = new HashMap<String, String>();
		for (Object obj : data.elements(XML_ELEM_CATALOG_BOUND)) {
			Element e = (Element) obj;
			bounds.put(e.attributeValue("name"), e.attributeValue("value"));
		}
		if (bounds.size() == 0) return null;
		return bounds;
	}

	/*
	 * Creates an XML element containing bounds data for this catalog
	 */
	private Element createBoundsData() {
		Element e = new DefaultElement(XML_ELEM_CATALOG_DATA_BOUNDS);
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		e.addAttribute(EVENT_COUNT, Integer.toString(size));
		e.add(createBoundElement(MIN_LAT, minVals.get(LATITUDE).toString()));
		e.add(createBoundElement(MAX_LAT, maxVals.get(LATITUDE).toString()));
		e.add(createBoundElement(MIN_LON, minVals.get(LONGITUDE).toString()));
		e.add(createBoundElement(MAX_LON, maxVals.get(LONGITUDE).toString()));
		e.add(createBoundElement(MIN_DEPTH, minVals.get(DEPTH).toString()));
		e.add(createBoundElement(MAX_DEPTH, maxVals.get(DEPTH).toString()));
		e.add(createBoundElement(MIN_MAG, minVals.get(MAGNITUDE).toString()));
		e.add(createBoundElement(MAX_MAG, maxVals.get(MAGNITUDE).toString()));
		e.add(createBoundElement(MIN_DATE, sdf.format(minDate)));
		e.add(createBoundElement(MAX_DATE, sdf.format(maxDate)));
		return e;
	}

	/*
	 * Creates an individual bounds element.
	 */
	private Element createBoundElement(String name, String value) {
		Element e = new DefaultElement(XML_ELEM_CATALOG_BOUND);
		e.addAttribute("name", name);
		e.addAttribute("value", value);
		return e;
	}

}
