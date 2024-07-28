package org.opensha.commons.util.cpt;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.data.Named;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.XMLUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * This class represents a GMT CPT file.
 *
 * @author kevin
 *
 */

public class CPT extends ArrayList<CPTVal> implements Named, Serializable, Cloneable, XMLSaveable {

	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	public static final String XML_METADATA_NAME = "CPT";
	private Color nanColor, belowMinColor, aboveMaxColor, gapColor;
	public Blender blender;
	
	private double preferredTickInterval = Double.NaN;
	
	private String name;
	
//	int nanColor[] = new int[0];

	/**
	 * Constructor which has no colors except for the default nanColor as
	 * Color.ORANGE,gapColor as Color.MAGENTA , belowMin as Color.BLACK, and
	 * aboveMax as Color.WHITE. <b>NOTE: the gapColor and naNColor are not
	 * blended</b>
	 *
	 * Use the ArrayList getters and setter for finer control.
	 *
	 * @see java.util.ArrayList
	 */
	public CPT() {
		this(null);
	}
	
	public CPT(String name) {
		this.name = name;
		nanColor = Color.BLACK;
		gapColor = Color.BLACK;
		belowMinColor = Color.BLACK;
		aboveMaxColor = Color.BLACK;
		blender = new LinearBlender();
	}
	
	/**
	 * Constructor to quickly generate evenly split CPT ranges. Must supply at least 2 colors. Above max
	 * and below min colors will be set to the last/fist colors.
	 * @param minVal
	 * @param maxVal
	 * @param colors
	 */
	public CPT(double minVal, double maxVal, Color... colors) {
		this(null);
		
		Preconditions.checkArgument(minVal < maxVal, "min must be less than max");
		Preconditions.checkArgument(colors.length > 1, "must specify at least 2 colors");
		
		double delta = (maxVal - minVal)/(colors.length - 1);
		for (int i=0; i<colors.length-1; i++) {
			float start = (float)(minVal + delta*i);
			float end = (float)(minVal + delta*(i+1));
			add(new CPTVal(start, colors[i], end, colors[i+1]));
		}
		
		setBelowMinColor(colors[0]);
		setAboveMaxColor(colors[colors.length-1]);
	}

	/**
	 * Sets color to be used when given value is not a number (NaN)
	 *
	 * @see java.awt.Color for more information on rgb values.
	 *
	 * @param r
	 * @param g
	 * @param b
	 */
	public void setNanColor(int r, int g, int b) {
		nanColor = new Color(r, g, b);
	}

	/**
	 * Sets color to be used when given value is not a number (NaN)
	 *
	 * @see java.awt.Color
	 */
	public void setNanColor(Color color) {
		nanColor = color;

	}

	/**
	 * Set The value of the color returned by getColor if the value is below the
	 * range of the CPT class using r,g,b values as used in Color3f.
	 *
	 * @see java.awt.Color
	 *
	 *
	 */
	public void setBelowMinColor(Color color) {
		belowMinColor = color;
	}

	/**
	 * Set The value of the color returned by getColor if the value is above the
	 * range of the CPT class using r,g,b values as used in Color3f.
	 *
	 * @see java.awt.Color
	 *
	 */
	public void setAboveMaxColor(Color color) {
		aboveMaxColor = color;
	}

	/**
	 * Sets the color to be take on for values in the range of the minValue and
	 * maxValue, but without a specified color
	 *
	 * @param color
	 */
	public void setGapColor(Color color) {
		gapColor = color;
	}

	/**
	 * @see setGapColor(Color3f color)
	 * @see java.awt.Color
	 */
	public void setGapColor(int r, int g, int b) {
		gapColor = new Color(r, g, b);
	}

	/**
	 * @see setBelowMinColor(Color3f color)
	 * @see java.awt.Color
	 */
	public void setBelowMinColor(int r, int g, int b) {
		belowMinColor = new Color(r, g, b);
	}

	/**
	 * @see setAboveMaxColor(Color3f color)
	 * @see java.awt.Color
	 */
	public void setAboveMaxColor(int r, int g, int b) {
		aboveMaxColor = new Color(r, g, b);
	}

	/**
	 *
	 * @return The value of the color returned by getColor if the value is above
	 *         the range of the CPT class
	 */
	public Color getAboveMaxColor() {
		return aboveMaxColor;
	}

	/**
	 *
	 * @return The value of the color returned by getColor if the value is below
	 *         the range of the CPT class
	 */
	public Color getBelowMinColor() {
		return belowMinColor;
	}

	/**
	 *
	 * @return the Color3f associated with the minimum value in the CPT color
	 *         range
	 */
	public Color getMinColor() {
		if (this.size() > 0) {
			return this.get(0).minColor;
		}
		return null;
	}

	/**
	 *
	 * @return the Color3f associated with the maximum value in the CPT color
	 *         range or null if the color is undefined
	 */
	public Color getMaxColor() {
		if (this.size() > 0) {
			return this.get(this.size() - 1).maxColor;
		}
		return null;
	}

	public Color getNanColor() {
		return nanColor;
	}

	/**
	 * @return the color for values within the range of the CPT val/color
	 *         combination, but unspecified
	 */
	public Color getGapColor() {
		return gapColor;
	}

	/**
	 * This returns a color given a value for this specific CPT file or null if
	 * the color is undefined
	 *
	 * @param value
	 * @return Color corresponding to value
	 */
	public Color getColor(float value) {
		CPTVal cpt_val = getCPTVal(value);

		if (cpt_val != null) {
			if (value == cpt_val.start) {
				return cpt_val.minColor;
			} else if (value == cpt_val.end) {
				return cpt_val.maxColor;
			} else if (value > cpt_val.start && value < cpt_val.end) {
				float adjVal = (value - cpt_val.start)
						/ (cpt_val.end - cpt_val.start);
				return blendColors(cpt_val.minColor, cpt_val.maxColor, adjVal);
			}
		}

		// if we got here, it's not in the CPT file
		if (value < this.get(0).start)
			return getBelowMinColor();
		else if (value > this.get(this.size() - 1).end)
			return getAboveMaxColor();
		else if (Float.isNaN(value))
			return nanColor;
		else {
			return gapColor;
		}
	}

	private Color blendColors(Color smallColor, Color bigColor, float bias) {
		return blender.blend(smallColor, bigColor, bias);

	}

	/**
	 * This loads a CPT file into a CPT object The CPT format can be found here:
	 * http://hraun.vedur.is/~gg/hugb/gmt/doc/html/tutorial/node68.html
	 *
	 * The default overflow and underflow colors are the colors associated with
	 * the min and max values of the CPT file
	 *
	 * @param dataFile
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static CPT loadFromFile(File dataFile)
	throws FileNotFoundException, IOException
	{
		BufferedReader in = new BufferedReader(new FileReader(dataFile));
		return loadFromBufferedReader(in);
	}

	public static CPT loadFromStream(InputStream is)
	throws IOException
	{
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		return loadFromBufferedReader(in);
	}
	
	private static Color loadColor(StringTokenizer tok) {
		int R = Integer.parseInt(tok.nextToken());
		int G = Integer.parseInt(tok.nextToken());
		int B = Integer.parseInt(tok.nextToken());
		
		return new Color(R, G, B);
	}

	private static CPT loadFromBufferedReader(BufferedReader in)
	throws IOException
	{
		CPT cpt = new CPT();
		String line;
		int lineNumber = 0;
		
		boolean hasMin = false;
		boolean hasMax = false;
		
		while (in.ready()) {
			lineNumber++;
			line = in.readLine().trim();
			
			if (line.length() == 0)
				continue;

			StringTokenizer tok = new StringTokenizer(line);
			int tokens = tok.countTokens();
			char firstChar = line.charAt(0);
			
			try {
				switch (firstChar) {
				case '#':
					// comment
					continue;
				case 'N':
					tok.nextToken();
					cpt.setNanColor(loadColor(tok));
					continue;
				case 'B':
					tok.nextToken();
					cpt.setBelowMinColor(loadColor(tok));
					hasMin = true;
					continue;
				case 'F':
					tok.nextToken();
					cpt.setAboveMaxColor(loadColor(tok));
					hasMax = true;
					continue;
				default:
					if (tokens < 8) {
						System.out.println("Skipping line: " + lineNumber
								+ "! (Comment or not properly formatted.): " + line);
						continue;
					}
					float start = Float.parseFloat(tok.nextToken());
					Color minColor = loadColor(tok);
					float end = Float.parseFloat(tok.nextToken());
					Color maxColor = loadColor(tok);
					
					CPTVal cpt_val = new CPTVal(start, minColor, end, maxColor);
					cpt.add(cpt_val);
				}
			} catch (NumberFormatException e1) {
				System.out.println("Skipping line: " + lineNumber
						+ "! (bad number parse): " + line);
				continue;
			}
			
			if (tokens < 8 || line.charAt(0) == '#') {
				System.out.println("Skipping line: " + lineNumber
						+ "! (Comment or not properly formatted.): " + line);
				continue;
			}
		}

//		Set the colors that will be taken when the file gets a value out of
//		range to a default of the color associate with the minimum value in
//		the range of the CPT file and similarly for the max
		if (!hasMin)
			cpt.setBelowMinColor(cpt.getMinColor());
		if (!hasMax)
			cpt.setAboveMaxColor(cpt.getMaxColor());

		return cpt;
	}
	
	@Override
	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(XML_METADATA_NAME);
		
		for (CPTVal val : this)
			val.toXMLMetadata(xml);
		
		XMLUtils.colorToXML(xml, aboveMaxColor, "AboveMaxColor");
		XMLUtils.colorToXML(xml, belowMinColor, "BelowMinColor");
		XMLUtils.colorToXML(xml, gapColor, "GapColor");
		XMLUtils.colorToXML(xml, nanColor, "NanColor");
		
		if (name != null && !name.isEmpty())
			xml.addAttribute("name", name);
		
		return root;
	}
	
	public static CPT fromXMLMetadata(Element cptElem) {
		CPT cpt;
		Attribute nameAtt = cptElem.attribute("name");
		if (nameAtt != null)
			cpt = new CPT(nameAtt.getStringValue());
		else
			cpt = new CPT();
		
		Iterator<Element> it = cptElem.elementIterator(CPTVal.XML_METADATA_NAME);
		while (it.hasNext())
			cpt.add(CPTVal.fromXMLMetadata(it.next()));
		
		cpt.setAboveMaxColor(XMLUtils.colorFromXML(cptElem.element("AboveMaxColor")));
		cpt.setBelowMinColor(XMLUtils.colorFromXML(cptElem.element("BelowMinColor")));
		cpt.setGapColor(XMLUtils.colorFromXML(cptElem.element("GapColor")));
		cpt.setNanColor(XMLUtils.colorFromXML(cptElem.element("NanColor")));
		
		return cpt;
	}
	
	private String getCPTValStr(CPTVal val) {
		return val.start + "\t" + val.minColor.getRed() + "\t" + val.minColor + "";
	}
	
	public void writeCPTFile(String fileName) throws IOException {
		writeCPTFile(new File(fileName));
	}
	
	public void writeCPTFile(File file) throws IOException {
		FileWriter fw = new FileWriter(file);
		fw.write(this.toString());
		fw.close();
	}

	/**
	 * @return this objects blender
	 */
	public Blender getBlender() {
		return blender;
	}

	/**
	 * @param blender,
	 *            the blender to be used
	 * @see interface Blender
	 */

	public void setBlender(Blender blender) {
		this.blender = blender;
	}

	/**
	 * Does a lookup for the color range (CPTVal) associated with this value
	 *
	 * @param containingValue
	 * @return null if the color range was not found
	 */
	public CPTVal getCPTVal(float value) {
		int size = size();
		for (int i=0; i<size; i++) {
			CPTVal val = get(i);
			if (val.contains(value)) {
				return val;
			} else if (val.end == value) {
				// it equals the end of this range, check for special cases where we should return this one
				// rather than the next
				if (i < size-1) {
					if (value < get(i+1).start)
						// we're not contiguous, call it a match
						return val;
				} else {
					// this is the last value and it matches the end, call it a match
					return val;
				}
			}
		}
		return null;
	}

	/**
	 * Adds the cpt_val into this list. Note it will overwrite and replace
	 * intersecting cpt_vals
	 *
	 * @param cpt_val
	 * @return
	 */
	public void setCPTVal(CPTVal newcpt) {
		// We rely on the correct ordering of CPTVals in this list and the
		// intersecting list

		// Is the list empty?
		if (this.size() == 0) {
			// Add to the list
			this.add(newcpt);
		}
		// Is the newcpt supposed to be at the beginning of the list?
		else if (newcpt.compareTo(get(0)) < 0) {
			// Then add to the beginning
			this.add(0, newcpt);
		}
		// Is the newcpt supposed to be at the end of the list?
		else if (newcpt.compareTo(get(this.size() - 1)) > 0) {
			// Then add to the end
			this.add(newcpt);
		}
		// The newcpt must be in the middle of the range or intersecting with
		// the head or tail so fix conflicts by removing and trimming other
		//cpt_vals in the list
		else {
			// There are 4 cases we need to handle:
			// There are cur values contained in the range of newcpt
			// -The cur overlaps with the head or tail of newcpt
			// -The cur is within newcpt
			// The range of newcpt is contained within a cur value

			boolean added = false;
			ListIterator<CPTVal> iter = this.listIterator();

			while (iter.hasNext()) {
				CPTVal cur = iter.next();

				// If cur is completely within the range of the new value remove
				// it
				// NStart--cStart--cEnd--NEnd
				if (newcpt.start <= cur.start && cur.end <= newcpt.end) {
					// Can happen multiple times
					iter.remove();
					if (!added) {
						iter.add(newcpt);
						added = true;
					}
				}
				// If cur is intersecting with the head of the newcpt
				// cStart--NStart--cEnd--NEnd
				else if (newcpt.start <= cur.end && cur.end <= newcpt.end) {
					// This condition should only happen once
					cur.end = newcpt.start;
					if (cur.start == cur.end) {
						iter.remove();
					}
					// We couldn't possible have added before this
					iter.add(newcpt);
					added = true;
					// Now the loop will continue and cleanup
				}
				// If cur is intersecting with the tail of the newcpt
				// NStart--cStart--NEnd--cEnd
				else if (newcpt.start <= cur.start && cur.start <= newcpt.end) {
					cur.start = newcpt.end;
					if (cur.start == cur.end) {
						iter.remove();
					}
					if (!added) {
						iter.add(newcpt);
						added = true;
					}
					return;
				}
				// cur contains newcpt
				// cStart--NStart--NEnd--cEnd
				else if (cur.start <= newcpt.start && newcpt.end <= cur.end) {
					// Create a new node for the second half of cur and truncate
					// cur
					CPTVal newcur = new CPTVal(newcpt.end, newcpt.maxColor,
							cur.end, cur.maxColor);
					cur.end = newcpt.start;
					if (cur.end == cur.start) {
						iter.remove();
					}
					if (newcur.start != newcur.end) {
						iter.add(newcur);
					}
					// now put newcpt in the middle
					iter.add(newcpt);
					// added = true; //no need to go through the rest of the
					// list
					return;
				}
			}
		}
	}
	
	public void paintGrid(BufferedImage bi) {
		int width = bi.getWidth();
		int height = bi.getHeight();
		Graphics2D g = bi.createGraphics();

		// Fill in with the gapColor by default
		Color color = getGapColor();
		g.setColor(color);
		g.fillRect(0, 0, width, height);

		//Make sure there are CPTVals to get color information from
		if (size() > 0) {

			// Establish the increase in value for each change in pixel
			float minStart = this.get(0).start;
			float maxEnd = this.get(size() - 1).end;
			float valsPerPixel = (maxEnd - minStart) / width; //To ensure that the last value is included

			// If we've lit pixel +1 pixels the next pixel is lit with the color
			// from valsPerPixel*x + minStart
			int pixel = 0;
			float val = 0;

			for( CPTVal cptval: this ) {
				// Get the CPTVals in order and get then paint the associated lines with colors corresponding to the range of values of that CPTVal
				float start = cptval.start;
				float end = cptval.end;
				Color startC = cptval.minColor;
				Color endC = cptval.maxColor;

				//Compute the value associated with the current pixel
				val = pixel * valsPerPixel + minStart;

				//If there is a gap in coverage skip the gap
				while( val < start){
					pixel++;
					val = pixel * valsPerPixel + minStart;
				}

				//If the CPTVal has only one value in its range just paint one line
				if(start == end){
					//Draw line and go to next pixel
					g.setColor(startC);
					g.drawLine(pixel, 0, pixel, height);
					pixel++;
					continue;
				}

				//Start filling in the gradient
				while (pixel < width && start <= val && val <= end ) {
					//Calculate color of line
					float bias = (val - start) / (end - start);
					Color blend = blender.blend(startC, endC, bias);

					//Draw line and go to next pixel
					g.setColor(blend);
					g.drawLine(pixel, 0, pixel, height);

					pixel++;
					val = pixel * valsPerPixel + minStart;
				}
			}
		}
	}
	
	public static String tabDelimColor(Color color) {
		return color.getRed() + "\t" + color.getGreen() + "\t" + color.getBlue();
	}

	//TODO handle B F N values
	public String toString() {
		String out= 	"# CPT File generated by OpenSHA (";
		try {
			out += "version "+ApplicationVersion.loadBuildVersion()+", ";
		} catch (IOException e) {}
		out += "http://www.opensha.org)";
		out += ": " + this.getClass().getName() + "\n";
		out += 			"# Date: " + (new Date()) + "\n";
		for(CPTVal v: this){
			out += v.toString() + "\n";
		}
		if (belowMinColor != null)
			out += "B\t" + tabDelimColor(belowMinColor) + "\n";
		if (aboveMaxColor != null)
			out += "F\t" + tabDelimColor(aboveMaxColor) + "\n";
		if (nanColor != null)
			out += "N\t" + tabDelimColor(nanColor) + "\n";
		//Output out of bounds colors
		return out;
	}
	
	public float getMinValue() {
		float min = Float.POSITIVE_INFINITY;
		for (CPTVal cptVal : this) {
			if (cptVal.start < min)
				min = cptVal.start;
			if (cptVal.end < min)
				min = cptVal.end;
		}
		return min;
	}
	
	public float getMaxValue() {
		float max = Float.NEGATIVE_INFINITY;
		for (CPTVal cptVal : this) {
			if (cptVal.start > max)
				max = cptVal.start;
			if (cptVal.end > max)
				max = cptVal.end;
		}
		return max;
	}
	
	public static void main(String args[]) throws FileNotFoundException, IOException {
		CPT cpt = CPT.loadFromFile(new File("/usr/share/gmt/cpt/GMT_seis.cpt"));
		System.out.println(cpt);
	}

	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public Object clone() {
		CPT cpt = new CPT(getName()+"");
		
		cpt.setBelowMinColor(getBelowMinColor());
		cpt.setAboveMaxColor(getAboveMaxColor());
		cpt.setGapColor(getGapColor());
		cpt.setNanColor(getNanColor());
		cpt.setBlender(getBlender());
		
		for (CPTVal val : this)
			cpt.add((CPTVal)val.clone());
		
		cpt.setPreferredTickInterval(getPreferredTickInterval());
		
		return cpt;
	}

	public CPT asLog10() {
		Preconditions.checkState(getMinValue() > 0, "can only get log10 representation when min > 0");
		return rescale(Math.log10(getMinValue()), Math.log10(getMaxValue()));
	}
	
	public CPT asPow10() {
		return rescale(Math.pow(10, getMinValue()), Math.pow(10, getMaxValue()));
	}
	
	public CPT asDiscrete(int num, boolean preserveEdges) {
		CPT cpt = (CPT)clone();
		CPT orig = this;
		double min = this.getMinValue();
		double max = this.getMaxValue();
		double delta = (max - min)/num;
		if (preserveEdges) {
			orig = (CPT)clone();
			orig = orig.rescale(min+0.5*delta, max-0.5*delta);
		}
		cpt.clear();
		
		for (int i=0; i<num; i++) {
			float start = (float)(min + i*delta);
			float end = (float)(min + (i+1)*delta);
			Color color;
			if (preserveEdges && i == 0)
				color = getMinColor();
			else if (preserveEdges && i == num-1)
				color = getMaxColor();
			else
				color = orig.getColor((float)(0.5*(end + start)));
			cpt.add(new CPTVal(start, color, end, color));
		}
		
		return cpt;
	}
	
	public CPT asDiscrete(double delta, boolean preserveEdges) {
		CPT cpt = (CPT)clone();
		CPT orig = this;
		double min = this.getMinValue();
		double max = this.getMaxValue();
		if (preserveEdges) {
			orig = (CPT)clone();
			double lastBinStart = 0d;
			for (double start = min; (float)start < (float)max; start += delta)
				lastBinStart = start;
			orig = orig.rescale(min+0.5*delta, lastBinStart+0.5*delta);
		}
		cpt.clear();
		
		for (double start = min; (float)start < (float)max; start += delta) {
			double end = start + delta;
			Color color;
			if (preserveEdges && start == min)
				color = getMinColor();
			else if (preserveEdges && (float)end >= (float)max)
				color = getMaxColor();
			else
				color = orig.getColor((float)(0.5*(end + start)));
			cpt.add(new CPTVal((float)start, color, (float)end, color));
		}
		
		return cpt;
	}
	
	/**
	 * @return rescaled version of this CPT
	 */
	public CPT rescale(double min, double max) {
		Preconditions.checkState(getMaxValue() > getMinValue(), "in order to rescale, current max must be > min");
		Preconditions.checkArgument(max > min, "new max must be > min: %s !> %s", max, min);
		CPT cpt = (CPT)clone();
		cpt.clear();
		
		for (CPTVal val : this) {
			float start = (float)rescaleValue(val.start, min, max);
			float end = (float)rescaleValue(val.end, min, max);
			CPTVal newVal = new CPTVal(start, val.minColor, end, val.maxColor);
			cpt.add(newVal);
		}
		
		return cpt;
	}
	
	private double rescaleValue(double oldVal, double newMin, double newMax) {
		double oldDelta = getMaxValue() - getMinValue();
		double newDelta = newMax - newMin;
		
		return newMin + ((oldVal - getMinValue()) / oldDelta) * newDelta;
	}
	
	public CPT trim(double newMin, double newMax) {
		Preconditions.checkState(newMin >= getMinValue(), "new minimum is lower than original minimum");
		Preconditions.checkState(newMax <= getMaxValue(), "new maximum is greater than original maximum");
		Preconditions.checkState(newMax > newMin, "new max must be greater than new max");
		CPT cpt = (CPT)clone();
		cpt.clear();
		
		for (CPTVal val : this) {
			if (val.end < (float)newMin)
				// completely before the start
				continue;
			if (val.start > (float)newMax)
				// completely after the end
				break;
			// if we're here, we are inside or at least overlap a new bound
			CPTVal newVal = val;
			if (val.start < (float)newMin) {
				// we started before the new minimum, truncate the lower bound
				Color minColor = getColor((float)newMin);
				newVal = new CPTVal((float)newMin, minColor, newVal.end, newVal.maxColor);
			}
			if (val.end > (float)newMax) {
				// we end after the new maximum, truncate the upper bound
				Color maxColor = getColor((float)newMax);
				newVal = new CPTVal(newVal.start, newVal.minColor, (float)newMax, maxColor);
			}
			cpt.add(newVal);
		}
		cpt.setBelowMinColor(cpt.getMinColor());
		cpt.setAboveMaxColor(cpt.getMaxColor());
		
		return cpt;
	}
	
	/**
	 * @return reversed version of this CPT file
	 */
	public CPT reverse() {
		CPT cpt = (CPT)clone();
		
		List<Color> colors = Lists.newArrayList();
		
		for (CPTVal val : cpt) {
			colors.add(val.minColor);
			colors.add(val.maxColor);
		}
		
		for (int i=0; i<cpt.size(); i++) {
			cpt.get(i).minColor = colors.remove(colors.size()-1);
			cpt.get(i).maxColor = colors.remove(colors.size()-1);
		}
		
		cpt.setBelowMinColor(getAboveMaxColor());
		cpt.setAboveMaxColor(getBelowMinColor());
		
		return cpt;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((aboveMaxColor == null) ? 0 : aboveMaxColor.hashCode());
		result = prime * result + ((belowMinColor == null) ? 0 : belowMinColor.hashCode());
		result = prime * result + ((blender == null) ? 0 : blender.hashCode());
		result = prime * result + ((gapColor == null) ? 0 : gapColor.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((nanColor == null) ? 0 : nanColor.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		CPT other = (CPT) obj;
		if (aboveMaxColor == null) {
			if (other.aboveMaxColor != null)
				return false;
		} else if (!aboveMaxColor.equals(other.aboveMaxColor))
			return false;
		if (belowMinColor == null) {
			if (other.belowMinColor != null)
				return false;
		} else if (!belowMinColor.equals(other.belowMinColor))
			return false;
		if (blender == null) {
			if (other.blender != null)
				return false;
		} else if (!blender.equals(other.blender))
			return false;
		if (gapColor == null) {
			if (other.gapColor != null)
				return false;
		} else if (!gapColor.equals(other.gapColor))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (nanColor == null) {
			if (other.nanColor != null)
				return false;
		} else if (!nanColor.equals(other.nanColor))
			return false;
		return true;
	}

	public double getPreferredTickInterval() {
		return preferredTickInterval;
	}

	public void setPreferredTickInterval(double preferredTickInterval) {
		this.preferredTickInterval = preferredTickInterval;
	}
	
}
