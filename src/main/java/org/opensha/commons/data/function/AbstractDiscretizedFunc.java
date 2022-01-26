package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.data.Named;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.Interpolate;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> DiscretizedFunc<p>
 *
 * <b>Description:</b> Abstract implementation of the DiscretizedFuncAPI. Performs standard
 * simple or default functions so that subclasses don't have to keep reimplementing the
 * same function bodies.<p>
 *
 * A Discretized Function is a collection of x and y values grouped together as
 * the points that describe a function. A discretized form of a function is the
 * only ways computers can represent functions. Instead of having y=x^2, you
 * would have a sample of possible x and y values. <p>
 *
 * The basic functions this abstract class implements are:<br>
 * <ul>
 * <li>get, set Name()
 * <li>get, set, Info()
 * <li>get, set, Tolerance()
 * <li>equals() - returns true if all three fields have the same values.
 * </ul>
 *
 * See the interface documentation for further explanation of this framework<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public abstract class AbstractDiscretizedFunc extends AbstractXY_DataSet implements DiscretizedFunc,
Named,java.io.Serializable{

	private static final long serialVersionUID = 2798699443929196424l;

	/** Class name used for debbuging */
	protected final static String C = "DiscretizedFunc";
	/** if true print out debugging statements */
	protected final static boolean D = false;

	public final static String XML_METADATA_NAME = "discretizedFunction";
	public final static String XML_METADATA_POINTS_NAME = "Points";
	public final static String XML_METADATA_POINT_NAME = "Point";


	/**
	 * The tolerance allowed in specifying a x-value near a real x-value,
	 * so that the real x-value is used. Note that the tolerance must be smaller
	 * than 1/2 the delta between data points for evenly discretized function, no
	 * restriction for arb discretized function, no standard delta.
	 */
	protected double tolerance = 0.0;

	@Override
	public double getTolerance() { return tolerance; }
	
	@Override
	public void setTolerance(double newTolerance) throws InvalidRangeException {
		if( newTolerance < 0 )
			throw new InvalidRangeException("Tolerance must be larger or equal to 0");
		tolerance = newTolerance;
	}
	
	@Override
	public boolean hasX(double x) {
		return getXIndex(x) >= 0;
	}
	
	/**
	 * Returns the x index that is before the given X value. If a point exists at this
	 * x value, the index before that point should be returned. Return value is undefined
	 * if x < minX or x > maxX, error checking should be done externally.
	 * @param x
	 * @return
	 */
	abstract int getXIndexBefore(double x);
	
	@Override
	public double getInterpolatedY(double x) {
		return getInterpolatedY(x, false, false);
	}
	
	@Override
	public double getInterpolatedY_inLogXLogYDomain(double x) {
		return getInterpolatedY(x, true, true);
	}
	
	@Override
	public double getInterpolatedY_inLogXDomain(double x) {
		return getInterpolatedY(x, true, false);
	}
	
	@Override
	public double getInterpolatedY_inLogYDomain(double x) {
		return getInterpolatedY(x, false, true);
	}
	
	private double getInterpolatedY(double x, boolean logX, boolean logY) {
		//if passed parameter(x value) is not within range then throw exception
		double minX = getMinX();
		double maxX = getMaxX();
		if(x>maxX+tolerance || x<minX-tolerance)
			throw new InvalidRangeException("x Value ("+x+") must be within the range: "
					+getX(0)+" and "+getX(size()-1));
		if (x >= maxX)
			// this means it is just barely above, but within tolerance of maxX
			return getY(size()-1);
		if (x <= minX)
			// this means it is just barely below, but within tolerance of minX
			return getY(0);

		int x1Ind = getXIndexBefore(x);
		if(x1Ind == -1)	// this happens if x<minX (but within tolerance)
			return getY(0);

		double x1 = getX(x1Ind);
		double x2 = getX(x1Ind+1);

		//finding the y values for the coressponding x values
		double y1=getY(x1);
		double y2=getY(x2);
		
		if(y1==0 && y2==0)
			return 0;
		
		if (logX) {
//			Preconditions.checkState(x1 > 0 && x2 > 0 && x > 0, "Cannot interpolate in logX domain with any x<=0");
			// old version did this check at the end, but I think the above is better:
			//		if (expY == Double.MIN_VALUE) expY = 0.0;
			x1 = Math.log(x1);
			x2 = Math.log(x2);
			x = Math.log(x);
		}
		if (logY) {
			y1 = Math.log(y1);
			y2 = Math.log(y2);
		}
		//using the linear interpolation equation finding the value of y for given x
		double y = Interpolate.findY(x1, y1, x2, y2, x);
		if (logY)
			y = Math.exp(y);
		
		return y;
	}
	
	@Override
	public double getFirstInterpolatedX(double y) {
		return getFirstInterpolatedX(y, false, false);
	}
	
	@Override
	public double getFirstInterpolatedX_inLogXLogYDomain(double y) {
		return getFirstInterpolatedX(y, true, true);
	}
	
	public double getFirstInterpolatedX_inLogYDomain(double y) {
		return getFirstInterpolatedX(y, false, true);
	}

	
	private double getFirstInterpolatedX(double y, boolean logX, boolean logY) {
		double y1=Double.NaN;
		double y2=Double.NaN;
		int i;
		
		int num = size();

		//if Size of the function is 1 and Y value is equal to Y val of function
		//return the only X value
		if(num == 1 && y == getY(0))
			return getX(0);

		boolean found = false; // this boolean hold whether the passed y value lies within range

		//finds the Y values within which the the given y value lies
		for(i=0;i<num-1;++i)
		{
			y1=getY(i);
			y2=getY(i+1);
			if((y<=y1 && y>=y2 && y2<=y1) || (y>=y1 && y<=y2 && y2>=y1)) {
				found = true;
				break;
			}
		}

		//if passed parameter(y value) is not within range then throw exception
		if(!found) throw new InvalidRangeException(
				"Y Value ("+y+") must be within the range: "+getY(0)+" and "+getY(num-1));


		//finding the x values for the coressponding y values
		double x1=getX(i);
		double x2=getX(i+1);
		
		if(x1==0 && x2==0)
			return 0;
		
		if (logX) {
//			Preconditions.checkState(x1 > 0 && x2 > 0 && x > 0, "Cannot interpolate in logX domain with any x<=0");
			// old version did this check at the end, but I think the above is better:
			//		if (expY == Double.MIN_VALUE) expY = 0.0;
			x1 = Math.log(x1);
			x2 = Math.log(x2);
		}
		if (logY) {
			y1 = Math.log(y1);
			y2 = Math.log(y2);
			y = Math.log(y);
		}

		//using the linear interpolation equation finding the value of x for given y
		double x = Interpolate.findX(x1, y1, x2, y2, y);
		if (logX)
			x = Math.exp(x);
		return x;
	}
	
	private boolean areBothNull(String first, String second) {
		return first == null && second == null;
	}
	
	private boolean isOneNull(String first, String second) {
		return first == null || second == null;
	}
	
	private boolean isSameWithNull(String first, String second) {
		if (areBothNull(first, second))
			return true;
		if (isOneNull(first, second))
			return false;
		return first.equals(second);
	}

	/**
	 * Default equals for all Discretized Functions. Determines if two functions
	 * are the same by comparing that the name, info, and values are the same.
	 */
	@Override
	public boolean equals(Object obj){
//		if (true)
//			return true;
		if (this == obj)
			return true;
		if (!(obj instanceof DiscretizedFunc))
			return false;
		DiscretizedFunc function = (DiscretizedFunc)obj;
		
		// now check names equal
		if (!isSameWithNull(getName(), function.getName()))
			return false;
			
		if ((getName() == null && function.getName() != null) ||
				(getName() != null && !getName().equals(function.getName() )))
			return false;

		if( D ) {
			String S = C + ": equals(): ";
			System.out.println(S + "This info = " + getInfo() );
			System.out.println(S + "New info = " + function.getInfo() );

		}

		// now check info equal
		if (!isSameWithNull(getInfo(), function.getInfo()))
			return false;
//		if( !getInfo().equals(function.getInfo() )  ) return false;
		
		// now check size
		if (this.size() != function.size())
			return false;
		
		// now check that the points are equal
		for (int i=0; i<this.size(); i++) {
			Point2D pt1 = this.get(i);
			Point2D pt2 = function.get(i);
			if (!pt1.equals(pt2))
				return false;
		}
		return true;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		return toXMLMetadata(root, AbstractDiscretizedFunc.XML_METADATA_NAME);
	}
	
	@Override
	public Element toXMLMetadata(Element root, String elementName) {
		return toXMLMetadata(root, elementName, null);
	}
	
	public Element toXMLMetadata(Element root, String elementName, NumberFormat format) {
		Element xml = root.addElement(elementName);
		
		xml.addAttribute("info", this.getInfo());
		xml.addAttribute("name", this.getName());

		xml.addAttribute("tolerance", this.getTolerance() + "");
		xml.addAttribute("xAxisName", this.getXAxisName());
		xml.addAttribute("yAxisName", this.getYAxisName());
		xml.addAttribute("num", this.size() + "");
		xml.addAttribute("minX", this.getMinX() + "");
		xml.addAttribute("maxX", this.getMaxX() + "");
		if (this instanceof EvenlyDiscretizedFunc) {
			xml.addAttribute("delta", valToStr(((EvenlyDiscretizedFunc)this).getDelta(), format));
		}

		Element points = xml.addElement(AbstractDiscretizedFunc.XML_METADATA_POINTS_NAME);
		for (int i=0; i<this.size(); i++) {
			Element point = points.addElement(AbstractDiscretizedFunc.XML_METADATA_POINT_NAME);
			point.addAttribute("x", valToStr(this.getX(i), format));
			point.addAttribute("y", valToStr(this.getY(i), format));
		}

		return root;
	}
	
	private static String valToStr(double val, NumberFormat format) {
		if (format == null)
			return val+"";
		return format.format(val);
	}

	public static AbstractDiscretizedFunc fromXMLMetadata(Element funcElem) {
		String info = funcElem.attributeValue("info");
		String name = funcElem.attributeValue("name");
		String xAxisName = funcElem.attributeValue("xAxisName");
		String yAxisName = funcElem.attributeValue("yAxisName");
		
		AbstractDiscretizedFunc func;
		Attribute deltaAtt = funcElem.attribute("delta");
		if (deltaAtt == null) {
			func = new ArbitrarilyDiscretizedFunc();
		} else {
			int num = Integer.parseInt(funcElem.attributeValue("num"));
			double minX = Double.parseDouble(funcElem.attributeValue("minX"));
			double delta = Double.parseDouble(deltaAtt.getStringValue());
			func = new EvenlyDiscretizedFunc(minX, num, delta);
		}

		double tolerance = Double.parseDouble(funcElem.attributeValue("tolerance"));

		func.setInfo(info);
		func.setName(name);
		func.setXAxisName(xAxisName);
		func.setYAxisName(yAxisName);
		func.setTolerance(tolerance);

		Element points = funcElem.element(AbstractDiscretizedFunc.XML_METADATA_POINTS_NAME);
		Iterator<Element> it = points.elementIterator();
		while (it.hasNext()) {
			Element point = it.next();
			double x = Double.parseDouble(point.attributeValue("x"));
			double y = Double.parseDouble(point.attributeValue("y"));
			func.set(x, y);
		}

		return func;
	}

	public static void writeSimpleFuncFile(DiscretizedFunc func, String fileName) throws IOException {
		writeSimpleFuncFile(func, new File(fileName));
	}

	public static void writeSimpleFuncFile(DiscretizedFunc func, File outFile) throws IOException {
		FileWriter fr = new FileWriter(outFile);
		for (int i = 0; i < func.size(); ++i)
			fr.write(func.getX(i) + " " + func.getY(i) + "\n");
		fr.close();
	}

	public static ArbitrarilyDiscretizedFunc loadFuncFromSimpleFile(String fileName) throws FileNotFoundException, IOException {
		ArrayList<String> fileLines = FileUtils.loadFile(fileName);
		String dataLine;
		StringTokenizer st;
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();

		for(int i=0;i<fileLines.size();++i) {
			dataLine=(String)fileLines.get(i);
			st=new StringTokenizer(dataLine);
			//using the currentIML and currentProb we interpolate the iml or prob
			//value entered by the user.
			double x = Double.parseDouble(st.nextToken());
			double y= Double.parseDouble(st.nextToken());
			func.set(x, y);
		}
		return func;
	}

	public static ArbitrarilyDiscretizedFunc loadFuncFromSimpleFile(InputStream is) throws FileNotFoundException, IOException {
	    if (!(is instanceof BufferedInputStream))
	    	is = new BufferedInputStream(is);
	    Scanner scanner = new Scanner(is);
	    
		StringTokenizer st;
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		
	    try {
	    	while (scanner.hasNextLine()){
	    		st=new StringTokenizer(scanner.nextLine());
				//using the currentIML and currentProb we interpolate the iml or prob
				//value entered by the user.
				double x = Double.parseDouble(st.nextToken());
				double y = Double.parseDouble(st.nextToken());
				func.set(x, y);
	    	}
	    }
	    finally{
	    	scanner.close();
	    }
		return func;
	}
	
	@Override
	public double calcSumOfY_Vals() {
		double sum=0;
		for(int i=0; i<size();i++) sum += getY(i);
		return sum;
	}
	
	@Override
	public void scale(double val) {
		for(int i=0; i<size();i++) this.set(i, val*getY(i));
	}


}
