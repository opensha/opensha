package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import org.dom4j.Element;
import org.opensha.commons.data.Named;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * A wrapper for 2D double-valued data that provides access to data points via
 * {@link Point2D}s.
 * 
 * <p><i>Note:</i> Use of the word 'Set' in this class does not imply adherence
 * to the {@link Set} interface. An {@code XY_DataSet} may contain multiple
 * identical points, although subclasses are free to provide alternate behavior.
 * </p>
 * 
 * @author Kevin Milner
 * @author Peter Powers
 * @version $Id: XY_DataSet.java 10931 2015-01-27 22:04:06Z kmilner $
 */
@JsonAdapter(XY_DataSet.XYAdapter.class)
public interface XY_DataSet extends PlotElement, Named, XMLSaveable, Serializable, Iterable<Point2D> {

	/* ******************************/
	/* Basic Fields Getters/Setters */
	/* ******************************/

	/** Sets the name of this function. */
	public void setName( String name );

	/** Sets the info string of this function. */
	public void setInfo( String info );
	
	/** Returns the info of this function.  */
	public String getInfo();


	/* ******************************/
	/* Metrics about list as whole  */
	/* ******************************/

	/** returns the number of points in this function list */
	public int size();

	/** return the minimum x value along the x-axis. */
	public double getMinX() throws IndexOutOfBoundsException;

	/** return the maximum x value along the x-axis */
	public double getMaxX() throws IndexOutOfBoundsException;

	/** return the minimum y value along the y-axis */
	public double getMinY() throws IndexOutOfBoundsException;

	/** return the maximum y value along the y-axis */
	public double getMaxY() throws IndexOutOfBoundsException;


	/* ******************/
	/* Point Accessors  */
	/* ******************/

	/** Returns the nth (x,y) point in the Function by index, or null if no such point exists */
	public Point2D get(int index);

	/** Returns the x-value given an index */
	public double getX(int index) throws IndexOutOfBoundsException;

	/** Returns the y-value given an index */
	public double getY(int index) throws IndexOutOfBoundsException;
	
	/**
	 * Get the Y value for the point with closest X. If multiple points are equidistant, the smaller
	 * X will be returned.
	 * 
	 * @param x
	 * @return
	 */
	public double getClosestYtoX(double x);

	/**
	 * Get the X value for the point with closest Y. If multiple points are equidistant, the smaller
	 * X will be returned.
	 * 
	 * @param y
	 * @return
	 */
	public double getClosestXtoY(double y);


	/* ***************/
	/* Point Setters */
	/* ***************/

	/** Either adds a new DataPoint, or replaces an existing one, within tolerance */
	public void set(Point2D point);

	/**
	 * Creates a new DataPoint, then either adds it if it doesn't exist,
	 * or replaces an existing one, within tolerance
	 */
	public void set(double x, double y);

	/** Replaces a DataPoint y-value at the specifed index. */
	public void set(int index, double Y) throws IndexOutOfBoundsException;

	/**
	 * Maps a user-defined function F to each point, setting y = F(x).
	 *
	 * @param mappedFn(x) a function that takes an x value and returns a new y value.
	 */
	public default void setYofX(DoubleUnaryOperator mappedFn){
		for(int i=0; i<this.size();i++) {
			this.set(i, mappedFn.applyAsDouble(getX(i)));
		}
	}

	/**
	 * Maps a user-defined function F to each point, setting y = F(x, y).
	 *
	 * @param mappedFn(x, y) a function that takes an ax and a y value and returns a new y value.
	 */
	public default void setYofX(DoubleBinaryOperator mappedFn){
		for(int i=0; i<this.size();i++) {
			this.set(i, mappedFn.applyAsDouble(getX(i), getY(i)));
		}
	}


	/* **********/
	/* Queries  */
	/* **********/
	
	/**
	 * Determine whether a point exists in the list,
	 * as determined by it's x-value within tolerance (if applicable).
	 */
	public boolean hasX(double x);


	/* ************/
	/* Iterators  */
	/* ************/


	/**
	 * Returns an iterator over all x-values in the list. Results returned
	 * in sorted order.
	 * @return
	 */
	public Iterator<Double> getXValuesIterator();


	/**
	 * Returns an iterator over all y-values in the list. Results returned
	 * in sorted order along the x-axis.
	 * @return
	 */
	public Iterator<Double> getYValuesIterator();



	/* **************************/
	/* Standard Java Functions  */
	/* **************************/

	/**
	 * Standard java function, usually used for debugging, prints out
	 * the state of the list, such as number of points, the value of each point, etc.
	 */
	public String toString();

//	/**
//	 * Determines if two lists are equal. Typical implementation would verify
//	 * same number of points, and the all points are equal, using the DataPoint2D
//	 * equals() function.
//	 */
//	public boolean equals( XY_DataSetAPI function );

	/**
	 * prints out the state of the list, such as number of points,
	 * the value of each point, etc.
	 * @return value of each point in the function in String format
	 */
	public String getMetadataString();
	
	/**
	 * This function returns a new copy of this list, including copies
	 * of all the points. A shallow clone would only create a new DiscretizedFunc
	 * instance, but would maintain a reference to the original points. <p>
	 *
	 * Since this is a clone, you can modify it without changing the original.
	 */
	public XY_DataSet deepClone();

	/**
	 * It finds out whether the X values are within tolerance of an integer value
	 * @param tolerance tolerance value to consider  rounding errors
	 *
	 * @return true if all X values are within the tolerance of an integer value
	 * else returns false
	 */
	public boolean areAllXValuesInteger(double tolerance);
	
	/**
	 * Sets the name of the X Axis
	 * @param xName String
	 */
	public void setXAxisName(String xName);
	
	/**
	 * Gets the name of the X Axis
	 */
	public String getXAxisName();
	
	/**
	 * Sets the name of the X Axis
	 * @param xName String
	 */
	public void setYAxisName(String xName);
	
	/**
	 * Gets the name of the Y Axis
	 */
	public String getYAxisName();
	

	public List<Double> xValues();
	public List<Double> yValues();
	
	public Element toXMLMetadata(Element root, String elName);
	
	public Element toXMLMetadata(Element root, String elName, NumberFormat format);
	
	public static class XYAdapter extends AbstractAdapter<XY_DataSet> {
		
		protected XY_DataSet instance(Double minX, Double maxX, Integer size) {
			return new DefaultXY_DataSet();
		}
		
	}
	
	public static abstract class AbstractAdapter<E extends XY_DataSet> extends TypeAdapter<E> {

		@Override
		public void write(JsonWriter out, E xy) throws IOException {
			if (xy == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			
			String name = xy.getName();
			if (name != null && !name.isEmpty())
				out.name("name").value(name);
			
			String info = xy.getInfo();
			if (info != null && !info.isEmpty())
				out.name("info").value(info);
			
			String xName = xy.getXAxisName();
			if (xName != null && !xName.isEmpty())
				out.name("xAxisName").value(xName);
			
			String yName = xy.getYAxisName();
			if (yName != null && !yName.isEmpty())
				out.name("yAxisName").value(yName);
			
			out.name("size").value(xy.size());
			out.name("minX").value(xy.getMinX());
			out.name("maxX").value(xy.getMaxX());
			
			serializeExtras(out, xy);
			
			out.name("values").beginArray();
			
			for (int i=0; i<xy.size(); i++) {
				out.beginArray();
				Point2D val = xy.get(i);
				out.value(val.getX()).value(val.getY());
				out.endArray();
			}
			
			out.endArray();
			
			out.endObject();
		}
		
		protected void serializeExtras(JsonWriter out, E xy) throws IOException {
			// do nothing
		}
		
		protected abstract E instance(Double minX, Double maxX, Integer size);
		
		protected Consumer<E> deserializeExtra(JsonReader in, String name) throws IOException {
			in.skipValue();
			return null;
		}

		@Override
		public E read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			in.beginObject();
			
			String xyName = null;
			String info = null;
			String xName = null;
			String yName = null;
			Integer size = null;
			Double minX = null;
			Double maxX = null;
			
			E xy = null;
			
			List<Consumer<E>> consumers = new ArrayList<>();
			
			while (in.hasNext()) {
				String name = in.nextName();
				
				switch (name) {
				case "name":
					xyName = in.nextString();
					break;
				case "info":
					info = in.nextString();
					break;
				case "xAxisName":
					xName = in.nextString();
					break;
				case "yAxisName":
					yName = in.nextString();
					break;
				case "size":
					size = in.nextInt();
					break;
				case "minX":
					minX = in.nextDouble();
					break;
				case "maxX":
					maxX = in.nextDouble();
					break;
				case "values":
					xy = instance(minX, maxX, size);
					
					in.beginArray();
					
					while (in.hasNext()) {
						in.beginArray();
						double x = in.nextDouble();
						double y = in.nextDouble();
						xy.set(x, y);
						in.endArray();
					}
					
					in.endArray();
					break;

				default:
					Consumer<E> consumer = deserializeExtra(in, name);
					if (consumer != null)
						consumers.add(consumer);
					break;
				}
			}
			
			Preconditions.checkNotNull(xy, "missing 'values'");
			xy.setName(xyName);
			xy.setInfo(info);
			xy.setXAxisName(xName);
			xy.setYAxisName(yName);
			
			for (Consumer<E> consumer : consumers)
				consumer.accept(xy);
			
			in.endObject();
			
			return xy;
		}
		
	}
	
}
