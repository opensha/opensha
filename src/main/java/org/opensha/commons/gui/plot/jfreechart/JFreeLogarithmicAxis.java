/* ======================================
 * JFreeChart : a free Java chart library
 * ======================================
 *
 * Project Info:  http://www.jfree.org/jfreechart/index.html
 * Project Lead:  David Gilbert (david.gilbert@object-refinery.com);
 *
 * (C) Copyright 2000-2003, by Object Refinery Limited and Contributors.
 *
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 * --------------------
 * JFreeLogarithmicAxis.java
 * --------------------
 * (C) Copyright 2000-2003, by Object Refinery Limited and Contributors.
 *
 * Original Author:  Michael Duffy / Eric Thomas / Edward (Ned) Field and Nitin Gupta;
 * Contributor(s):   David Gilbert (for Object Refinery Limited);
 *                   David M. O'Donnell;
 *
 * $Id: JFreeLogarithmicAxis.java 7943 2011-06-08 22:55:36Z kmilner $
 *
 * Changes
 * -------
 * 14-Mar-2002 : Version 1 contributed by Michael Duffy (DG);
 * 19-Apr-2002 : drawVerticalString(...) is now drawRotatedString(...) in RefineryUtilities (DG);
 * 23-Apr-2002 : Added a range property (DG);
 * 15-May-2002 : Modified to be able to deal with negative and zero values (via new
 *               'adjustedLog10()' method);  occurrences of "Math.log(10)" changed to "LOG10_VALUE";
 *               changed 'intValue()' to 'longValue()' in 'refreshTicks()' to fix label-text value
 *               out-of-range problem; removed 'draw()' method; added 'autoRangeMinimumSize' check;
 *               added 'log10TickLabelsFlag' parameter flag and implementation (ET);
 * 25-Jun-2002 : Removed redundant import (DG);
 * 25-Jul-2002 : Changed order of parameters in ValueAxis constructor (DG);
 * 16-Jul-2002 : Implemented support for plotting positive values arbitrarily
 *               close to zero (added 'allowNegativesFlag' flag) (ET).
 * 05-Sep-2002 : Updated constructor reflecting changes in the Axis class (DG);
 * 02-Oct-2002 : Fixed errors reported by Checkstyle (DG);
 * 08-Nov-2002 : Moved to new package com.jrefinery.chart.axis (DG);
 * 22-Nov-2002 : Bug fixes from David M. O'Donnell (DG);
 * 14-Jan-2003 : Changed autoRangeMinimumSize from Number --> double (DG);
 * 20-Jan-2003 : Removed unnecessary constructors (DG);
 * 26-Mar-2003 : Implemented Serializable (DG);
 * 08-May-2003 : Fixed plotting of datasets with lower==upper bounds when
 *               'minAutoRange' is very small; added 'strictValuesFlag'
 *               and default functionality of throwing a runtime exception
 *               if 'allowNegativesFlag' is false and any values are less
 *               than or equal to zero; added 'expTickLabelsFlag' and
 *               changed to use "1e#"-style tick labels by default
 *               ("10^n"-style tick labels still supported via 'set'
 *               method); improved generation of tick labels when range of
 *               values is small; changed to use 'NumberFormat.getInstance()'
 *               to create 'numberFormatterObj' (ET);
 * 14-May-2003 : Merged HorizontalJFreeLogarithmicAxis and VerticalJFreeLogarithmicAxis (DG);
 * 15-Nov-2003 : Removed the Plotting of the Negative Axis and Zero. Also removed the
 *               ("10^n")-style labelling and added the ("10"Power"n" in the superscript form)-
 *               style labelling and it is the default style of labelling. Also functionality
 *               has been that minimum 1 major axis will always be included in the range.
 *               If one uses the ("10"Power"n" in the superscript form) labeling then
 *               user has the capabilty to label the minor axis ticks. A flag has been
 *               added to label the minor axis ticks. But if the user is using the
 *               "1e#" style labelling then he won't be able to label the minor axis.
 *               Following rules are being for labellig the tickLabels:
 *               minorAxis tick Labels will be labelled if they don't overlap each other
 *               and have sufficient room to label the majorAxis tick label.
 *               If the power of 10 is greater than 3 or less than -3 then no minorAxis tick labels
 *               will be labelled.
 *               There will be atleast 2 major axis tick labels included in all Log plots.
 *               If the difference in power of 10 of any range is greater than 3
 *               then minor axis won't be labelled. (EF and NG).
 *               Definition for Major Axis: The number with Absolute power of 10, eg: .1,1,10,100......
 *               Definition for Minor Axis: Eg: .2,.3...,2,3,...30....,200,300,.......
 *               
 * 26-Sept-2006: Made the changes done on Nov,15,2003 consistent with the new version(1.0.2) of JFreechart. 
 *               Font varies for Major and Minor axis. Major axis are labelled with larger font as compared 
 *               with minor axis. Similarly, when major axis are labelled as superscript( "10" power "n" )
 *               form then superscript label font is smaller as compared to font of "10".
 *               Removed RefreshTicks() method (NG)  
 *
 */

package org.opensha.commons.gui.plot.jfreechart;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.Tick;
import org.jfree.chart.axis.ValueTick;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.ValueAxisPlot;
import org.jfree.chart.text.TextUtils;
import org.jfree.data.Range;

import com.google.common.base.Preconditions;

import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;


/**
 * A numerical axis that uses a logarithmic scale.
 * 
 * Note from Kevin in 2025: JFreeChart comes with log axis implementations (including LogAxis which this extends), but
 * they don't have the major-minor notation we support here. I remember hearing that we contributed some code related
 * to this back to JFreeChart (before I arrived in 2008), so maybe this is part of that? I also don't know who Michael
 * Duffy is, but the JFreeChart LogAxis class claims to have been originally contributed by him.
 *
 * @author Michael Duffy
 */
public class JFreeLogarithmicAxis extends LogAxis {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Useful constant for log(10). */
	public static final double LOG10_VALUE = Math.log(10.0);

	/** Smallest arbitrarily-close-to-zero value allowed. */
	public static final double SMALL_LOG_VALUE = 1e-100;


	/** Flag set true make axis throw exception if any values are
	 * <= 0 and 'allowNegativesFlag' is false. */
	protected boolean strictValuesFlag = true;

	/** Number formatter for generating numeric strings. */
	protected final NumberFormat numberFormatterObj = NumberFormat.getInstance();

	/** Flag set true for "1e#"-style tick labels. */
	protected boolean expTickLabelsFlag = false;


	/** Flag set true for representing the tick labels as the super script of 10 */
	protected boolean log10TickLabelsInPowerFlag = true;

	/** Flag set true to show the  tick labels for minor axis */
	protected boolean minorAxisTickLabelFlag = true;

	private float verticalAnchorShift = 0f;

	public JFreeLogarithmicAxis(String label) {
		super(label);
	}

	/**
	 * Sets the 'strictValuesFlag' flag; if true;
	 * then this axis will throw a runtime exception if any of its
	 * values are less than or equal to zero; if false then the axis will
	 * adjust for values less than or equal to zero as needed.
	 *
	 * @param flgVal true for strict enforcement.
	 */
	public void setStrictValuesFlag(boolean flgVal) {
		strictValuesFlag = flgVal;
	}

	/**
	 * Returns the 'strictValuesFlag' flag;
	 * if true then this axis will throw a runtime exception if any of its
	 * values are less than or equal to zero; if false then the axis will
	 * adjust for values less than or equal to zero as needed.
	 *
	 * @return true if strict enforcement is enabled.
	 */
	public boolean getStrictValuesFlag() {
		return strictValuesFlag;
	}

	/**
	 * Sets the 'expTickLabelsFlag' flag to true for for "1e#"-style tick labels,
	 * false for representing the power of 10 in superScript.
	 *
	 */
	public void setExpTickLabelsFlag() {
		expTickLabelsFlag = true;
		log10TickLabelsInPowerFlag = false;
		setupNumberFmtObj();             //setup number formatter obj
	}

	/**
	 * Sets up the number formatter object according to the
	 * 'expTickLabelsFlag' flag.
	 */
	protected void setupNumberFmtObj() {
		if (numberFormatterObj instanceof DecimalFormat) {
			//setup for "1e#"-style tick labels or regular
			// numeric tick labels, depending on flag:
			((DecimalFormat) numberFormatterObj).applyPattern(expTickLabelsFlag ? "0E0" : "0.###");
		}
	}

	/**
	 * Returns the 'expTickLabelsFlag' flag.
	 *
	 * @return  true for "1e#"-style tick labels, false for log10 or
	 * regular numeric tick labels.
	 */
	public boolean getExpTickLabelsFlag() {
		return expTickLabelsFlag;
	}




	/**
	 * Sets the 'log10TickLabelsInPowerFlag' flag to true for representing the power of 10 in superScript ,
	 * false for "1e#"-style tick labels.
	 *
	 */
	public void setLog10TickLabelsInPowerFlag() {
		log10TickLabelsInPowerFlag = true;
		expTickLabelsFlag = false;
	}


	/**
	 * Returns the 'log10TickLabelsInPowerFlag' flag.
	 *
	 * @return true for representing the power of 10 in superScript , false for "1e#"-style
	 * or "10^n"-style tick labels.
	 */
	public boolean getLog10TickLabelsInPowerFlag() {
		return log10TickLabelsInPowerFlag;
	}


	/**
	 * Sets the minorAxisTickLabelFlag to true for showing the tick labels for
	 * minor axis, else sets it false. If "1e#" style of labelling is being used
	 * then this function will not show the minor axis tick labelling if flag set to true.
	 * @param flag : sets the minorAxisTickLabel flag to true or false
	 */
	public void setMinorAxisTickLabelFlag(boolean flag) {
		minorAxisTickLabelFlag = flag;
	}

	/**
	 *
	 * @return true if minorAxis tick labels are to shown, false if they
	 * are not to be shown.
	 */
	public boolean getMinorAxisTickLabelFlag() {
		return minorAxisTickLabelFlag;
	}

	/**
	 * Tick label bounds. The full bounds will be in index 0. For major ticks in power mode, the major and minor
	 * bounds will be included in indexes 1 and 2, respectively.
	 * 
	 * The bound coordinates will be relative to the given anchor 
	 * 
	 * @param g2
	 * @param label
	 * @param majorAxis
	 * @param majorTickFont
	 * @param minorTickFont
	 * @param anchor
	 * @return
	 */
	private Rectangle2D[] getTickLabelBounds(Graphics2D g2, String label, boolean majorAxis, boolean verticalAxis,
			Font majorTickFont, Font minorTickFont, TextAnchor anchor) {
		return getTickLabelBounds(g2, label, majorAxis, verticalAxis, majorTickFont, minorTickFont, anchor, 0d, 0d);
	}



	/**
	 * Tick label bounds. The full bounds will be in index 0. For major ticks in power mode, the major and minor
	 * bounds will be included in indexes 1 and 2, respectively.
	 * 
	 * The bound coordinates will be relative to the given anchor and the given offsets
	 * 
	 * @param g2
	 * @param label
	 * @param majorAxis
	 * @param majorTickFont
	 * @param minorTickFont
	 * @param anchor
	 * @param xOffset
	 * @param yOffset
	 * @return
	 */
	private Rectangle2D[] getTickLabelBounds(Graphics2D g2, String label, boolean majorAxis, boolean verticalAxis,
			Font majorTickFont, Font minorTickFont, TextAnchor anchor, double xOffset, double yOffset) {
		// this part will be aligned to (0, 0)
		Rectangle2D[] ret;
		if (label.isEmpty()) {
			// easy
			ret = new Rectangle2D[] {getStringBounds(label, minorTickFont, g2)};
			// re-align to (0, 0)
			ret[0] = new Rectangle2D.Double(0, 0, ret[0].getWidth(), ret[0].getHeight());
		} else if (!majorAxis) {
			// minor axis
			ret = new Rectangle2D[] {getStringBounds(label, minorTickFont, g2)};
			double x;
			if (verticalAxis) {
				// shift to the leftso that it doesn't line up with the exponent
				x = -ret[0].getWidth();
			} else {
				// re-align to (0, 0)
				x = 0;
			}
			ret[0] = new Rectangle2D.Double(x, 0, ret[0].getWidth(), ret[0].getHeight());
		} else if (!log10TickLabelsInPowerFlag) {
			// major axis, but keeping the 1EN notation, also easy
			ret = new Rectangle2D[] {getStringBounds(label, majorTickFont, g2)};
			// re-align to (0, 0)
			ret[0] = new Rectangle2D.Double(0, 0, ret[0].getWidth(), ret[0].getHeight());
		} else {
			// we're a major tick, and we're using the 10^n notation, more complicated
			int eIndex = label.toLowerCase().indexOf("e");
			Preconditions.checkState(eIndex > 0, "Passed in tick label ('%s') doesn't use E notation?", label);
			Rectangle2D largeBounds = getStringBounds(label.substring(0, eIndex), majorTickFont, g2);
			Rectangle2D smallBounds = getStringBounds(label.substring(eIndex+1), minorTickFont, g2);
			// small bounds here are for the actual string, but we want to make sure everything lines up the same
			// no matter how many digits in the exponent; we also don't want to consider the negative sign
			Range range = getRange();
			int maxExp = (int)Math.max(Math.abs(Math.floor(switchedLog10(range.getLowerBound()))),
					Math.abs(Math.ceil(switchedLog10(range.getUpperBound()))));
			Rectangle2D refSmallBounds = getStringBounds(maxExp+"", minorTickFont, g2);
//			double smallMaxX = Math.max(largeBounds.getWidth()*0.8, largeBounds.getWidth()-2d) + refSmallBounds.getWidth();
			double smallMaxX = largeBounds.getWidth() + refSmallBounds.getWidth();
			double smallX = smallMaxX - smallBounds.getWidth();
//			double smallY = -3-(int)(0.4*this.getTickLabelFont().getSize());
			double smallY = -(int)(0.4*majorTickFont.getSize());
			double largeX = 0;
			double largeY = 0; // small will actually go above the anchor, which is ok
			largeBounds = new Rectangle2D.Double(largeX, largeY, largeBounds.getWidth(), largeBounds.getHeight());
			smallBounds = new Rectangle2D.Double(smallX, smallY, smallBounds.getWidth(), smallBounds.getHeight());
			Rectangle2D combBounds = new Rectangle2D.Double(0d, 0d,
					Math.max(largeBounds.getWidth(), smallX+smallBounds.getWidth()),
					largeY+largeBounds.getHeight());
			ret = new Rectangle2D[] {combBounds, largeBounds, smallBounds};
		}
		double totalWidth = ret[0].getWidth();
		double totalHeight = ret[0].getHeight();
		//		System.out.println("Plotting label '"+label+"'; original offset was: "+xOffset+", "+yOffset+", anchor is "+anchor);
		if (anchor.isHorizontalCenter())
			xOffset -= totalWidth*0.5;
		else if (anchor.isRight())
			xOffset -= totalWidth;
		if (anchor.isVerticalCenter())
			yOffset -= totalHeight*0.5;
		else if (anchor.isBottom())
			yOffset -= totalHeight;
		if (verticalAxis && ret.length == 1) {
			// correct for the slight vertical offset of using getStringBounds rather than the actual glyph bounds
			double offset = calcVerticalStringOffset(label, majorAxis ? majorTickFont : minorTickFont, g2);
			yOffset -= offset;
		}
		// re-align everything
		for (int i=0; i<ret.length; i++)
			ret[i] = new Rectangle2D.Double(ret[i].getX()+xOffset, ret[i].getY()+yOffset, ret[i].getWidth(), ret[i].getHeight());
		//		System.out.print("Returned bounds:");
		//		for (Rectangle2D rect : ret)
		//			System.out.println("\t"+rect);
		return ret;
	}

	private static Rectangle2D getStringBounds(String text, Font font, Graphics2D g2) {
		return font.getStringBounds(text, g2.getFontRenderContext());
		// this can be used to get the actual visual bounds, but that's not very useful because printing it will put it
		// in the string bounds above anyway
		//		String origStr = text;
		//		if (origStr.isEmpty())
		//			text = " ";
		//		TextLayout tl = new TextLayout(text, font, g2.getFontRenderContext());
		//		Rectangle2D visual = tl.getBounds();
		//		double tightWidth  = visual.getWidth();
		//		double tightHeight = visual.getHeight();
		//		if (origStr.isEmpty())
		//			tightWidth = 0;
		//		return new Rectangle2D.Double(0d, 0d, tightWidth, tightHeight);
	}
	
	/**
	 * {@link Font#getStringBounds(String, java.awt.font.FontRenderContext)} returns padded boundaries that usually
	 * extend above (and, less so, below) the actual visual bounds for a string. This returns how far below the center
	 * of those regular string bounds the actual printed center lies, in pixels.
	 * 
	 * @param text the string to measure
	 * @param font the font used to draw the string
	 * @param g2   the Graphics2D (providing the FontRenderContext)
	 * @return the vertical offset (in pixels) by which the visual center of the text is below the logical center
	 */
	private static double calcVerticalStringOffset(String text, Font font, Graphics2D g2) {
		if (text == null || text.isEmpty()) {
			return 0d;
		}
		FontRenderContext frc = g2.getFontRenderContext();

		// 1) Compute the logical bounds (ascent + descent + leading)
		Rectangle2D logical = font.getStringBounds(text, frc);
		double logicalCenter = logical.getY() + logical.getHeight() / 2.0;

		// 2) Compute the visual (pixel‐tight) bounds using a GlyphVector
		GlyphVector gv = font.createGlyphVector(frc, text);
		Rectangle2D visual = gv.getVisualBounds();
		double visualCenter = visual.getY() + visual.getHeight() / 2.0;

		// 3) The offset is how far the actual printed center lies below
		//    the logical center. Positive → visual center is lower on screen.
		return visualCenter - logicalCenter;
	}

	private static double TICK_OVERLAP_BUFFER = 6; // in pixels

	/**
	 * Calculates the positions of the tick labels for the axis, storing the results in the
	 * tick label list (ready for drawing).
	 *
	 * @param g2  the graphics device.
	 * @param dataArea  the area in which the plot should be drawn.
	 * @param edge  the location of the axis.
	 */
	@Override
	public List refreshTicksHorizontal(Graphics2D g2,
			Rectangle2D dataArea,
			RectangleEdge edge) {

		List<MajorMinorNumberTick> ticks = new ArrayList<>();
		List<Double> tickEndVals = new ArrayList<>();
		//get lower bound value:
		double lowerBoundVal = getRange().getLowerBound();
		//if small log values and lower bound value too small
		// then set to a small value (don't allow <= 0):
		if (lowerBoundVal < SMALL_LOG_VALUE) {
			lowerBoundVal = SMALL_LOG_VALUE;
		}
		//get upper bound value
		final double upperBoundVal = getRange().getUpperBound();

		//get log10 version of lower bound and round to integer:
		int iBegCount = (int) StrictMath.floor(switchedLog10(lowerBoundVal));
		//get log10 version of upper bound and round to integer:
		int iEndCount = (int) StrictMath.ceil(switchedLog10(upperBoundVal));
		
		boolean showMinor = shouldShowMinor(dataArea, edge);

		//		System.out.println("refreshTicksHoriz: lower="+lowerBoundVal+", upper="+upperBoundVal);
		//		System.out.println("\tiBegCount="+iBegCount+", iEndCount="+iEndCount);

		double tickVal;
		String tickLabel="";

		//if both iBegCount and iEndCount are absolute power of 10 and are equal
		//reduce the lowerdBound to one major Axis below
		if(iBegCount == iEndCount)
			--iBegCount;

		//Add one major Axis in the range if there is none in the range.
		//The one major Axis added is the one below the lowerBoundVal.
		//And checks if the upperBound is not a major Axis, then no need to include one major axis
		if(iEndCount - iBegCount ==1 && (upperBoundVal!=Double.parseDouble("1e"+iEndCount)))
			setRange(Double.parseDouble("1e"+iBegCount),upperBoundVal);

		Font majorTickFont = getMajorTickFont();
		Font minorTickFont = getMinorTickFont();

		for (int i = iBegCount; i <= iEndCount; i++) {
			//for each tick with a label to be displayed
			int jEndCount = 9;
			if (i == iEndCount) {
				jEndCount = 1;
			}

			for (int j = 0; j < jEndCount; j++) {
				//for each tick to be displayed

				//small log values in use
				tickVal = Double.parseDouble("1e"+i) * (1 + j);
				boolean majorAxis = j == 0;
				//j=0 means that it is the major Axis with absolute power of 10.
				if (j == 0) {
					//checks to if tick Labels to be represented in the form of superscript of 10.
					if(log10TickLabelsInPowerFlag){

						//if flag is true
						tickLabel ="10E" +i; //create a "10E" type label, "E" would be trimmed from the tick
						//label to represent if the form of superscript of 10.
					}
					else {    //not "log10"-type label
						if (expTickLabelsFlag) {
							//if flag then
							tickLabel = "1e" + i;   //create "1e#"-type label
						}
					}
				}
				else {   //not first tick to be displayed and it is the minor Axis tick label processing
					if(log10TickLabelsInPowerFlag && minorAxisTickLabelFlag)
						tickLabel = ""+(j+1);     //no tick label
					else
						tickLabel = "";
				}

				//				System.out.println("\t\tj="+j+", tickVal="+tickVal+", tickLabel="+tickLabel);

				if (tickVal > upperBoundVal && !Precision.equals(tickVal, upperBoundVal, upperBoundVal*1e-6)) {
					//					System.out.println("We're past it: "+tickVal+" > "+upperBoundVal);
					return checkMinNumMinor(ticks);     //if past highest data value then exit method
				}

				if (tickVal >= lowerBoundVal - SMALL_LOG_VALUE) {
					TextAnchor anchor;
					TextAnchor rotationAnchor;
					if (isVerticalTickLabels()) {
						anchor = TextAnchor.CENTER_RIGHT;
						rotationAnchor = TextAnchor.CENTER_RIGHT;
					} else if (edge == RectangleEdge.TOP) {
						anchor = TextAnchor.BOTTOM_CENTER;
						rotationAnchor = TextAnchor.BOTTOM_CENTER;
					} else {
						anchor = TextAnchor.TOP_CENTER;
						rotationAnchor = TextAnchor.TOP_CENTER;
					}
					double angle = 0.0;	
					Rectangle2D tickLabelBounds = getTickLabelBounds(g2, tickLabel, majorAxis, false,
							majorTickFont, minorTickFont, TextAnchor.TOP_LEFT)[0];
					Preconditions.checkState(tickLabelBounds.getX() == 0d);
					Preconditions.checkState(tickLabelBounds.getY() == 0d);
					double tickCenter = valueToJava2D(tickVal, dataArea, edge);
					double tickLabelStart, tickLabelEnd;
					if (isVerticalTickLabels()) {
						tickLabelStart = tickCenter - 0.5*tickLabelBounds.getHeight();
						tickLabelEnd = tickCenter + 0.5*tickLabelBounds.getHeight();
						if (edge == RectangleEdge.TOP) {
							angle = Math.PI / 2.0;
						}
						else {
							angle = -Math.PI / 2.0;
						}
					} else {
						tickLabelStart = tickCenter - 0.5*tickLabelBounds.getWidth();
						tickLabelEnd = tickCenter + 0.5*tickLabelBounds.getWidth();
					}
					if(this.log10TickLabelsInPowerFlag){
						//removing the minor labelling, if the ticks overlap.
						/* also if the difference in the powers of the smallest major axis
						 * and largest major axis is larger than 3 then don't label the minor axis
						 **/
						//						 if((x<x0 || (iEndCount-iBegCount>3)) && j!=0 && minorAxisTickLabelFlag)
						double prevBufferedEnd = 0d;
						for (int k=ticks.size(); --k>=0;) {
							if (!ticks.get(k).getText().isEmpty()) {
								prevBufferedEnd = tickEndVals.get(k) + TICK_OVERLAP_BUFFER;
								break;
							}
						}
						if (minorAxisTickLabelFlag) {
							if (j == 0) {
								// this is major
								if (!ticks.isEmpty()) {
									// remove any previous minor tick labels overlap this major
									for (int k=tickEndVals.size(); --k>=0;) {
										double testBufferedEnd = tickEndVals.get(k) + TICK_OVERLAP_BUFFER;
										if (tickLabelStart < testBufferedEnd) {
											// we overlap, but see if it has actually been labeled
											MajorMinorNumberTick tempTick = ticks.get(k);
											if(!tempTick.getText().equals("") && !tempTick.getText().contains("E")) {
												// it has a label, clear it
												double value = tempTick.getValue();
												ticks.set(k, new MajorMinorNumberTick(tempTick, ""));
												tickEndVals.set(k, value);
											}
										} else {
											break;
										}
									}
								}
							} else {
								// this is minor, see if we overlap the previous one (or have more than max decades)
								if (tickLabelStart < prevBufferedEnd || !showMinor) {
									// we do
									tickLabel = "";
									tickLabelStart = tickCenter;
									tickLabelEnd = tickCenter;
								}
							}
						}
					}
					//					System.out.println("adding tick with val="+tickVal+", label="+tickLabel);
					MajorMinorNumberTick tick = new MajorMinorNumberTick(tickVal, tickLabel, anchor, rotationAnchor, angle, j==0);
					ticks.add(tick);
					tickEndVals.add(tickLabelEnd);
				}
			}
		}
		//		System.out.println("Returning this tick list:");
		//		for (int i=0; i<ticks.size(); i++) {
		//			ValueTick tick = (ValueTick)ticks.get(i);
		//			System.out.println("\t"+i+". val="+tick.getValue()+", label="+tick.getText());
		//		}
		return checkMinNumMinor(ticks);
	}

	/**
	 * Returns the log10 value, depending on if values between 0 and
	 * 1 are being plotted.  If negative values are not allowed and
	 * the lower bound is between 0 and 10 then a normal log is
	 * returned; otherwise the returned value is adjusted if the
	 * given value is less than 10.
	 *
	 * @param val the value.
	 *
	 * @return log<sub>10</sub>(val).
	 */
	protected double switchedLog10(double val) {
		return StrictMath.log(val) / LOG10_VALUE ;
	}
	
	private static final double MIN_PIXELS_PER_DECADE_FOR_MINOR = 100d;
	private static double MIN_NUM_MINOR = 3; // if fewer minor than this, don't show any
	
	private boolean shouldShowMinor(Rectangle2D dataArea, RectangleEdge edge) {
		Range range = getRange();
		double lowerPixel = valueToJava2D(range.getLowerBound(), dataArea, edge);
		double upperPixel = valueToJava2D(range.getUpperBound(), dataArea, edge);
		double totPixels = Math.abs(upperPixel - lowerPixel); // abs here because for vertical, upperPixel < lowerPixel
		if (totPixels < 100)
			// way too small
			return false;
		double decades = switchedLog10(range.getUpperBound()) - switchedLog10(range.getLowerBound());

		// show minor only if we have at least MIN_PIXELS_PER_DECADE_FOR_MINOR for each decade
		double pixelsPerDecade = totPixels / decades;
		return pixelsPerDecade >= MIN_PIXELS_PER_DECADE_FOR_MINOR;
	}

	/**
	 * Calculates the positions of the tick labels for the axis, storing the
	 * results in the tick label list (ready for drawing).
	 *
	 * @param g2  the graphics device.
	 * @param dataArea  the area in which the plot should be drawn.
	 * @param edge  the axis location.
	 */
	@Override
	public List refreshTicksVertical(Graphics2D g2,
			Rectangle2D dataArea,
			RectangleEdge edge) {

		List<MajorMinorNumberTick> ticks = new ArrayList<>();
		List<Double> tickEndVals = new ArrayList<>();
		//get lower bound value:
		double lowerBoundVal = getRange().getLowerBound();
		//if small log values and lower bound value too small
		// then set to a small value (don't allow <= 0):
		if (lowerBoundVal < SMALL_LOG_VALUE) {
			lowerBoundVal = SMALL_LOG_VALUE;
		}

		//get upper bound value
		double upperBoundVal = getRange().getUpperBound();

		//get log10 version of lower bound and round to integer:
		int iBegCount = (int) StrictMath.floor(switchedLog10(lowerBoundVal));
		//get log10 version of upper bound and round to integer:
		int iEndCount = (int) StrictMath.ceil(switchedLog10(upperBoundVal));
		
		boolean showMinor = shouldShowMinor(dataArea, edge);

		//if both iBegCount and iEndCount are absolute power of 10 and are equal
		//reduce the lowerdBound to one major Axis below
		if(iBegCount == iEndCount)
			--iBegCount;

		//Add one major Axis in ther range if there is none in the range.
		//The one major Axis added is the one below the lowerBoundVal.
		//And checks if the upperBound is not a major Axis, then no need to include one major axis
		if(iEndCount - iBegCount ==1 && (upperBoundVal!=Double.parseDouble("1e"+iEndCount)))
			setRange(Double.parseDouble("1e"+iBegCount),upperBoundVal);

		Font majorTickFont = getMajorTickFont();
		Font minorTickFont = getMinorTickFont();
		
		// vertical has some extra buffer in the bounding boxes already, subtract that
//		double TICK_OVERLAP_BUFFER = Math.max(0d, JFreeLogarithmicAxis.TICK_OVERLAP_BUFFER
//				-calcVerticalStringOffset("1", minorTickFont, g2));
//		System.out.println("UPDATED TICK_OVERLAP_BUFFER="+(float)JFreeLogarithmicAxis.TICK_OVERLAP_BUFFER+" to "+(float)TICK_OVERLAP_BUFFER);

		double tickVal;
		String tickLabel="";

		for (int i = iBegCount; i <= iEndCount; i++) {
			//for each tick with a label to be displayed
			int jEndCount = 9;
			if (i == iEndCount) {
				jEndCount = 1;
			}

			for (int j = 0; j < jEndCount; j++) {
				//for each tick to be displayed
				tickVal = Double.parseDouble("1e"+i) * (1 + j);
				//j=0 means that it is the major Axis with absolute power of 10.
				boolean majorAxis = j == 0;
				if (j == 0) {
					//checks to if tick Labels to be represented in the form of superscript of 10.
					if(log10TickLabelsInPowerFlag){
						//if flag is true
						tickLabel ="10E" +i; //create a "10E" type label, "E" would be trimmed from the tick
						//label to represent if the form of superscript of 10.
					}
					else {    //not "log10"-type label
						if (expTickLabelsFlag) {
							//if flag then
							tickLabel = "1e" + i;   //create "1e#"-type label
						}
					}
				}
				else {   //not first tick to be displayed and it is the minor Axis tick label processing
					if(log10TickLabelsInPowerFlag && minorAxisTickLabelFlag)
						tickLabel = ""+(j+1);     //no tick label
					else 
						tickLabel = "";
				}

				if (tickVal > upperBoundVal) {
					return checkMinNumMinor(ticks);     //if past highest data value then exit method
				}

				if (tickVal >= lowerBoundVal - SMALL_LOG_VALUE) {
					//tick value not below lowest data value
					TextAnchor anchor;
					TextAnchor rotationAnchor;
					if (isVerticalTickLabels()) {
						if (edge == RectangleEdge.LEFT) {
							anchor = TextAnchor.BOTTOM_CENTER;
							rotationAnchor = TextAnchor.BOTTOM_CENTER;
						} else {
							anchor = TextAnchor.BOTTOM_CENTER;
							rotationAnchor = TextAnchor.BOTTOM_CENTER;
						}
					} else {
						if (edge == RectangleEdge.LEFT) {
							anchor = TextAnchor.CENTER_RIGHT;
							rotationAnchor = TextAnchor.CENTER_RIGHT;
						}
						else {
							anchor = TextAnchor.CENTER_LEFT;
							rotationAnchor = TextAnchor.CENTER_LEFT;
						}
					}
					double angle = 0.0;	
					//get bounds for tick label:
					Rectangle2D tickLabelBounds = getTickLabelBounds(g2, tickLabel, majorAxis, true,
							majorTickFont, minorTickFont, TextAnchor.TOP_LEFT)[0];
					// x and y can be non-zero for vertical
					// y will be non-zero because we're correcting for glyph placing
					//get X-position for tick label:
					double tickCenter = valueToJava2D(tickVal, dataArea, edge);
					double tickLabelStart, tickLabelEnd;
					if (isVerticalTickLabels()) {
						// start is at the bottom of the label, which means greater y (y=0 is top)
						tickLabelStart = tickCenter + 0.5*tickLabelBounds.getWidth();
						// end is at the top of the label, which means smaller y (y=0 is top)
						tickLabelEnd = tickCenter - 0.5*tickLabelBounds.getWidth();
						if (edge == RectangleEdge.LEFT)
							angle = -Math.PI / 2.0;
						else
							angle = Math.PI / 2.0;
					} else {
						// tick is padded at the top and we correct for this by offsetting the location in the y direction
						// but only do the start (bottom), indicating that we're moving the tick up; leave the end (top)
						// at the original (non-offset) location.
						double yOffset = tickLabelBounds.getY();
						tickLabelStart = tickCenter + 0.5*tickLabelBounds.getHeight() - yOffset;
						tickLabelEnd = tickCenter - 0.5*tickLabelBounds.getHeight();
					}
					Preconditions.checkState(tickLabelEnd <= tickLabelStart);
					double prevBufferedEnd = Double.MAX_VALUE;
					for (int k=ticks.size(); --k>=0;) {
						if (!ticks.get(k).getText().isEmpty()) {
							prevBufferedEnd = tickEndVals.get(k) - TICK_OVERLAP_BUFFER;
							break;
						}
					}
					if (minorAxisTickLabelFlag) {
						if (j == 0) {
							// this is major
							if (!ticks.isEmpty()) {
								// remove any previous minor tick labels overlap this major
								for (int k=tickEndVals.size(); --k>=0;) {
									double testBufferedEnd = tickEndVals.get(k) - TICK_OVERLAP_BUFFER;
									if (tickLabelStart > testBufferedEnd) {
										// we overlap, but see if it has actually been labeled
										MajorMinorNumberTick tempTick = ticks.get(k);
										if(!tempTick.getText().equals("") && !tempTick.getText().contains("E")) {
											// it has a label, clear it
											double value = tempTick.getValue();
											ticks.set(k, new MajorMinorNumberTick(tempTick, ""));
											tickEndVals.set(k, value);
										}
									} else {
										break;
									}
								}
							}
						} else {
							// this is minor, see if we overlap the previous one (or have more than max decades)
							if (tickLabelStart > prevBufferedEnd || !showMinor) {
								// we do
								tickLabel = "";
								tickLabelStart = tickCenter;
								tickLabelEnd = tickCenter;
							}
						}
					}
					//create tick object and add to list:
					ticks.add(new MajorMinorNumberTick(tickVal, tickLabel, anchor, rotationAnchor, angle, j == 0));
					//					ticksYVals.add(Double.valueOf(y));
					tickEndVals.add(tickLabelEnd);
				}
			}
		}
		return checkMinNumMinor(ticks);
	}

	private static class MajorMinorNumberTick extends NumberTick {

		private boolean major;
		
		public MajorMinorNumberTick(MajorMinorNumberTick prev, String label) {
			this(prev.getNumber(), label, prev.getTextAnchor(), prev.getRotationAnchor(), prev.getAngle(), prev.major);
		}

		public MajorMinorNumberTick(Number number, String label, TextAnchor textAnchor, TextAnchor rotationAnchor,
				double angle, boolean major) {
			super(number, label, textAnchor, rotationAnchor, angle);
			this.major = major;
		}
		
	}
	
	private List<MajorMinorNumberTick> checkMinNumMinor(List<MajorMinorNumberTick> ticks) {
		if (MIN_NUM_MINOR == 0)
			// no restriction
			return ticks;
		
		Range range = getRange();
		double decades = switchedLog10(range.getUpperBound()) - switchedLog10(range.getLowerBound());
		if (decades < 1.001d)
			// we don't even have a full decade, so we expect to only have a few
			return ticks;
		
		int numMajor = 0;
		int curNumMinor = 0;
		int maxNumMinor = 0;
		for (MajorMinorNumberTick tick : ticks) {
			if (tick.major) {
				curNumMinor = 0;
				numMajor++;
			} else if (!tick.getText().equals("") && !tick.getText().contains("E")) {
				curNumMinor++;
				maxNumMinor = Integer.max(maxNumMinor, curNumMinor);
			}
		}
		if (numMajor > 1 && maxNumMinor > 0 && maxNumMinor < MIN_NUM_MINOR) {
			// we don't have enough minor ticks, clear them all
			for (int i=0; i<ticks.size(); i++) {
				MajorMinorNumberTick tick = ticks.get(i);
				if (!tick.major && !tick.getText().equals("") && !tick.getText().contains("E"))
					ticks.set(i, new MajorMinorNumberTick(tick, ""));
			}
		}
		return ticks;
	}

	/**
	 * removes the previous tick label so that powers of 10 can be displayed
	 */

	private void removePreviousTick(List ticks) {
		int size = ticks.size();
		for(int i=size-1;i>0;--i) {
			Tick tick = (Tick)ticks.get(i);
			if(tick.getText().trim().equalsIgnoreCase("")) continue;
			//System.out.println("Removing tickVal:"+tick.getNumericalValue());
			TextAnchor anchor = tick.getTextAnchor();
			TextAnchor rotationAnchor = tick.getRotationAnchor();
			double angle = tick.getAngle();
			ticks.remove(i);
			ticks.add(new NumberTick(((ValueTick)tick).getValue(),"",anchor,rotationAnchor,angle));
			return;
		}
	}

	/**
	 * This allows you to shift the tick labels vertically to fix some overlap issues (specifically with X axis).
	 * 
	 * @param verticalAnchorShift
	 */
	public void setVerticalAnchorShift(float verticalAnchorShift) {
		this.verticalAnchorShift = verticalAnchorShift;
	}

	private Font getMajorTickFont() {
		Font tickFont = this.getTickLabelFont();
		return new Font(tickFont.getName(), tickFont.getStyle(), tickFont.getSize() + (int)(tickFont.getSize() * 0.2));
	}

	private Font getMinorTickFont() {
		Font tickFont = this.getTickLabelFont();
		return new Font(tickFont.getName(), tickFont.getStyle(), tickFont.getSize() - (int)(tickFont.getSize() * 0.2));
	}

	private Font getPowerTickFont() {
		Font tickFont = this.getTickLabelFont();
		return new Font(tickFont.getName(), tickFont.getStyle(), tickFont.getSize() - (int)(tickFont.getSize() * 0.2));
	}
	
	private static final boolean DEBUG_PRINT_BOUNDS = false;

	/**
	 * Draws the axis line, tick marks and tick mark labels.
	 *
	 * @param g2  the graphics device.
	 * @param cursor  the cursor.
	 * @param plotArea  the plot area.
	 * @param dataArea  the data area.
	 * @param edge  the edge that the axis is aligned with.
	 *
	 * @return The width or height used to draw the axis.
	 */
	@Override
	protected AxisState drawTickMarksAndLabels(Graphics2D g2, double cursor,
			Rectangle2D plotArea,
			Rectangle2D dataArea, RectangleEdge edge) {
		AxisState state = new AxisState(cursor);

		//calls the super class function if user wants to use the "1e#" style of labelling of ticks.
		if(!log10TickLabelsInPowerFlag)
			return super.drawTickMarksAndLabels(g2, cursor,plotArea, dataArea, edge);

		if (isAxisLineVisible()) {
			drawAxisLine(g2, cursor, dataArea, edge);
		}
		//		double ol = getTickMarkOutsideLength();
		//		double il = getTickMarkInsideLength();
		float minorOutside = (float) getTickMarkOutsideLength();
		float minorInside  = (float) getTickMarkInsideLength();
		// make major tick marks longer
		float majorOutside = minorOutside * 2.5f;
		float majorInside  = minorInside  * 2.5f;
		
		boolean verticalAxis = edge == RectangleEdge.LEFT || edge == RectangleEdge.RIGHT;

		List ticks = refreshTicks(g2, state, dataArea, edge);
		state.setTicks(ticks);

		Font majorTickFont = getMajorTickFont();
		Font minorTickFont = getMinorTickFont();
		Font powerTickFont = getPowerTickFont();

		g2.setFont(getTickLabelFont());
		Iterator iterator = ticks.iterator();
		//		System.out.println("Plotting log axis labels");
		while (iterator.hasNext()) {
			ValueTick tick = (ValueTick)iterator.next();

			int eIndex =-1;
			if(this.log10TickLabelsInPowerFlag)
				eIndex =tick.getText().indexOf("E");
			boolean majorAxis = eIndex >= 0;

			g2.setFont(majorAxis ? majorTickFont : minorTickFont);
			float ol = majorAxis ? majorOutside : minorOutside;
			float il = majorAxis ? majorInside  : minorInside;

			if (isTickLabelsVisible()) {
				g2.setPaint(getTickLabelPaint());
				float[] anchorPoint = calculateAnchorPoint(
						tick, cursor, dataArea, edge);
				anchorPoint[1] += verticalAnchorShift;
				//				if (!tick.getText().isEmpty()) {
				//					System.out.println("tick: value="+tick.getValue()+", label="+tick.getText()+", vertical="+isVerticalTickLabels()+", edge="+edge);
				//					System.out.println("\tcursor="+cursor+", anchor="+anchorPoint[0]+","+anchorPoint[1]);
				//				}

				if (isVerticalTickLabels()) {
					TextUtils.drawRotatedString(
							tick.getText(), g2, 
							anchorPoint[0], anchorPoint[1],
							tick.getTextAnchor(), 
							tick.getAngle(),
							tick.getRotationAnchor()
							);
				}
				else{
					Rectangle2D[] bounds = getTickLabelBounds(g2, tick.getText(), majorAxis, verticalAxis,
							majorTickFont, minorTickFont, tick.getTextAnchor(), anchorPoint[0], anchorPoint[1]);
					// we use bounds x and y below, which is always the top left of the box. the original text anchor
					// has already been used when determining that location, so don't use tick.getTextAnchor()
					TextAnchor boundsAnchor = TextAnchor.TOP_LEFT;
					if (DEBUG_PRINT_BOUNDS) {
						// debug: draw the anchor point
						g2.drawRect((int)anchorPoint[0]-1, (int)anchorPoint[1]-1, 2, 2);
						// draw the bounds
						for (Rectangle2D b : bounds)
							g2.drawRect((int)b.getX(), (int)b.getY(), (int)b.getWidth(), (int)b.getHeight());
					}
					if (!majorAxis) {
						// minor axis (smaller font)
						TextUtils.drawRotatedString(
								tick.getText(), g2, 
								(float)bounds[0].getX(), (float)bounds[0].getY(),
								boundsAnchor,
								tick.getAngle(),
								tick.getRotationAnchor()
								);
					} else {
						// major axis (10)
						TextUtils.drawRotatedString(
								"10", g2, 
								(float)bounds[1].getX(), (float)bounds[1].getY(),
								boundsAnchor,
								tick.getAngle(),
								tick.getRotationAnchor()
								);
						// setting the font properties to show the power of 10
						g2.setFont(powerTickFont);

						//						float horzOffset = (int)(0.3*getTickLabelFont().getSize());
						//						if (!tick.getText().startsWith("-") && edge == RectangleEdge.BOTTOM)
						//							horzOffset *= 2;
						TextUtils.drawRotatedString(
								tick.getText().substring(eIndex+1), g2, 
								//								anchorPoint[0]+horzOffset,
								//								anchorPoint[1]-3-(int)(0.4*this.getTickLabelFont().getSize()),
								(float)bounds[2].getX(), (float)bounds[2].getY(),
								boundsAnchor,
								tick.getAngle(),
								tick.getRotationAnchor()
								);
					}
				}
			}
			if (isTickMarksVisible()) {
				float xx = (float) valueToJava2D(
						tick.getValue(), dataArea, edge
						);
				Line2D mark = null;
				g2.setStroke(getTickMarkStroke());
				g2.setPaint(getTickMarkPaint());
				if (edge == RectangleEdge.LEFT) {
					mark = new Line2D.Double(cursor - ol, xx, cursor + il, xx);
				}
				else if (edge == RectangleEdge.RIGHT) {
					mark = new Line2D.Double(cursor + ol, xx, cursor - il, xx);
				}
				else if (edge == RectangleEdge.TOP) {
					mark = new Line2D.Double(xx, cursor - ol, xx, cursor + il);
				}
				else if (edge == RectangleEdge.BOTTOM) {
					mark = new Line2D.Double(xx, cursor + ol, xx, cursor - il);
				}
				g2.draw(mark);
			}
		}
		//    need to work out the space used by the tick labels...
		// so we can update the cursor...
		double used = 0.0;
		if (isTickLabelsVisible()) {
			if (edge == RectangleEdge.LEFT) {
				used += findMaximumTickLabelWidth(
						ticks, g2, plotArea, isVerticalTickLabels()
						);  
				state.cursorLeft(used);      
			}
			else if (edge == RectangleEdge.RIGHT) {
				used = findMaximumTickLabelWidth(
						ticks, g2, plotArea, isVerticalTickLabels()
						);
				state.cursorRight(used);      
			}
			else if (edge == RectangleEdge.TOP) {
				used = findMaximumTickLabelHeight(
						ticks, g2, plotArea, isVerticalTickLabels()
						);
				state.cursorUp(used);
			}
			else if (edge == RectangleEdge.BOTTOM) {
				used = findMaximumTickLabelHeight(
						ticks, g2, plotArea, isVerticalTickLabels()
						);
				state.cursorDown(used);
			}
		}

		return state;
	}






	/**
	 * Converts the given value to a tick label string.
	 *
	 * @param val the value to convert.
	 * @param forceFmtFlag true to force the number-formatter object
	 * to be used.
	 *
	 * @return The tick label string.
	 */
	protected String makeTickLabel(double val, boolean forceFmtFlag) {
		if (expTickLabelsFlag || forceFmtFlag) {
			//using exponents or force-formatter flag is set
			// (convert 'E' to lower-case 'e'):
			return numberFormatterObj.format(val).toLowerCase();
		}
		return getTickUnit().valueToString(val);
	}

	/**
	 * Converts the given value to a tick label string.
	 * @param val the value to convert.
	 *
	 * @return The tick label string.
	 */
	protected String makeTickLabel(double val) {
		return makeTickLabel(val, false);
	}

	/**
	 * Returns the largest (closest to positive infinity) double value that is
	 * not greater than the argument, is equal to a mathematical integer and
	 * satisfying the condition that log base 10 of the value is an integer
	 * (i.e., the value returned will be a power of 10: 1, 10, 100, 1000, etc.).
	 *
	 * @param lower a double value below which a floor will be calcualted.
	 *
	 * @return 10<sup>N</sup> with N .. { 1 ... }
	 */
	protected double computeLogFloor(double lower) {

		double logFloor;

		// The Math.log() function is based on e not 10.
		logFloor = Math.log(lower) / LOG10_VALUE;
		logFloor = Math.floor(logFloor);
		logFloor = Math.pow(10, logFloor);

		return logFloor;
	}

	/**
	 * Returns the smallest (closest to negative infinity) double value that is
	 * not less than the argument, is equal to a mathematical integer and
	 * satisfying the condition that log base 10 of the value is an integer
	 * (i.e., the value returned will be a power of 10: 1, 10, 100, 1000, etc.).
	 *
	 * @param upper a double value above which a ceiling will be calcualted.
	 *
	 * @return 10<sup>N</sup> with N .. { 1 ... }
	 */
	protected double computeLogCeil(double upper) {

		double logCeil;


		// The Math.log() function is based on e not 10.
		logCeil = Math.log(upper) / LOG10_VALUE;
		logCeil = Math.ceil(logCeil);
		logCeil = Math.pow(10, logCeil);

		return logCeil;
	}

	/**
	 * Rescales the axis to ensure that all data is visible.
	 */
	@Override
	public void autoAdjustRange() {

		Plot plot = getPlot();
		if (plot == null) {
			return;  // no plot, no data.
		}

		if (plot instanceof ValueAxisPlot) {
			ValueAxisPlot vap = (ValueAxisPlot) plot;

			double lower;
			Range r = vap.getDataRange(this);
			if (r == null) {
				//no real data present
				//				r = new Range(DEFAULT_LOWER_BOUND, DEFAULT_UPPER_BOUND);
				r = new Range(0.01, 1.0);
				lower = r.getLowerBound();    //get lower bound value
			}
			else {
				//actual data is present
				lower = r.getLowerBound();    //get lower bound value
				if (strictValuesFlag && lower <= 0.0){
					//strict flag set, allow-negatives not set and values <= 0
					throw new RuntimeException("Values less than or equal to "
							+ "zero not allowed with logarithmic axis");
				}
			}

			//change to log version of lowest value to make range
			lower = computeLogFloor(lower);

			if (lower >0.0 && lower < SMALL_LOG_VALUE) {
				//negatives not allowed and lower range bound is zero
				lower = r.getLowerBound();    //use data range bound instead
			}

			double upper = r.getUpperBound();

			upper = computeLogCeil(upper);  //use nearest log value
			// ensure the autorange is at least <minRange> in size...
			double minRange = getAutoRangeMinimumSize();
			if (upper - lower < minRange) {
				// Edge case: where <minRange> would lead us to a negative lower boundary, we adjust it.
				if (upper + lower <= minRange / 2){
					minRange = upper / 2;
				}
				upper = (upper + lower + minRange) / 2;
				lower = (upper + lower - minRange) / 2;
				//if autorange still below minimum then adjust by 1%
				// (can be needed when minRange is very small):
				if (upper - lower < minRange) {
					final double absUpper = Math.abs(upper);
					//need to account for case where upper==0.0
					final double adjVal = (absUpper > SMALL_LOG_VALUE) ? absUpper / 100.0 : 0.01;
					upper = (upper + lower + adjVal) / 2 ;
					lower = (upper + lower - adjVal) / 2;
				}
			}

			setRange(new Range(lower, upper), false, false);
		}
	}

}
