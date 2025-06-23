package org.opensha.sha.faultSurface;

import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;


/**
 * <p>Title: GriddedSurface</p>
 *
 * <p>Description: Creates a Arbitrary surface that takes in a list of locations.
 * </p>
 *
 * @author Nitin Gupta
 * @version 1.0
 */

public class GriddedSurfaceImpl extends AbstractEvenlyGriddedSurface implements XMLSaveable {

	public static String XML_METADATA_NAME = "GriddedSurfaceImpl";

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 *  Constructor for the GriddedSurface object
	 *
	 * @param  numRows  Number of grid points along width of fault
	 * @param  numCols  Number of grid points along length of fault
	 */
	public GriddedSurfaceImpl(int numRows, int numCols, double gridSpacing ) {
		super(numRows, numCols, gridSpacing);
	}

	/**
	 * This allows one to set the location
	 * @param row
	 * @param column
	 * @param location
	 */
	public void setLocation(int row, int column, Location location) {
		set(row, column, location);
	}

	@Override
	public double getAveDip() throws UnsupportedOperationException {
		throw new RuntimeException("Method not yet supported (need to implement computation)");
	}

	@Override
	public double getAveStrike() throws UnsupportedOperationException {
		return this.getRowAsTrace(0).getAveStrike();
	}

	@Override
	public double getAveDipDirection() {
		throw new RuntimeException("Method not yet supported (need to implement computation)");
	}

	@Override
	public double getAveRupTopDepth() {
		double dep=0;
		for (int col=0; col<numCols; col++)
			dep += getLocation(0, col).depth;
		return dep/(double)numCols;
	}

	@Override
	public double getAveRupBottomDepth() {
		final int lastRow = numRows-1;
		double dep=0;
		for (int col=0; col<numCols; col++)
			dep += getLocation(lastRow, col).depth;
		return dep/(double)numCols;
	}

	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		return new GriddedSurfaceImpl(numRows, numCols, getGridSpacingAlongStrike());
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		el.addAttribute("rows", getNumRows()+"");
		el.addAttribute("cols", getNumCols()+"");
		el.addAttribute("gridSpacing", getAveGridSpacing()+"");
		
		for (int row=0; row<getNumRows(); row++) {
			for (int col=0; col<getNumCols(); col++) {
				Element pointEl = el.addElement("Point");
				pointEl.addAttribute("row", row+"");
				pointEl.addAttribute("col", col+"");
				getLocation(row, col).toXMLMetadata(pointEl);
			}
		}
		
		return root;
	}
	
	public static GriddedSurfaceImpl fromXMLMetadata(Element el) {
		int rows = Integer.parseInt(el.attributeValue("rows"));
		int cols = Integer.parseInt(el.attributeValue("cols"));
		double gridSpacing = Double.parseDouble(el.attributeValue("gridSpacing"));
		
		GriddedSurfaceImpl surf = new GriddedSurfaceImpl(rows, cols, gridSpacing);
		
		for (Element pointEl : XMLUtils.getSubElementsList(el, "Point")) {
			int row = Integer.parseInt(pointEl.attributeValue("row"));
			int col = Integer.parseInt(pointEl.attributeValue("col"));
			Location loc = Location.fromXMLMetadata(pointEl.element(Location.XML_METADATA_NAME));
			surf.set(row, col, loc);
		}
		
		return surf;
	}

}

