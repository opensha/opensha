package org.opensha.sha.calc.disaggregation.chart3d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.awt.Insets;

import org.jfree.chart3d.data.DefaultKeyedValues2D;
import org.jfree.chart3d.table.AbstractTableElement;
import org.jfree.chart3d.table.TableElement;
import org.jfree.chart3d.table.TableElementOnDraw;
import org.jfree.chart3d.table.TableElementVisitor;

/**
 * A table element that contains a grid of elements.  
 * <br><br>
 * NOTE: This class is serializable, but the serialization format is subject 
 * to change in future releases and should not be relied upon for persisting 
 * instances of this class.
 */
@SuppressWarnings("serial")
class CustomWidthGridElement<R extends Comparable<R>, C extends Comparable<C>> 
extends AbstractTableElement 
implements TableElement, Serializable {

	private static final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);

	/** Storage for the cell elements. */
	private DefaultKeyedValues2D<R, C, TableElement> elements;

	/**
	 * Creates a new empty grid.
	 */
	public CustomWidthGridElement() {
		this.elements = new DefaultKeyedValues2D<>();
		setBackgroundColor(TRANSPARENT_COLOR);
	}

	/**
	 * Adds (or updates) a cell in the grid.
	 * 
	 * @param element  the element ({@code null} permitted).
	 * @param rowKey  the row key ({@code null} not permitted).
	 * @param columnKey  the column key ({@code null} not permitted).
	 */
	public void setElement(TableElement element, R rowKey, C columnKey) {
		// defer argument checking
		this.elements.setValue(element, rowKey, columnKey);
	}

	/**
	 * Receives a visitor by calling the visitor's {@code visit()} method 
	 * for each of the children in the grid, and finally for the grid itself. 
	 * 
	 * @param visitor  the visitor ({@code null} not permitted).
	 * 
	 * @since 1.2
	 */
	@Override
	public void receive(TableElementVisitor visitor) {
		for (int r = 0; r < this.elements.getRowCount(); r++) {
			for (int c = 0; c < this.elements.getColumnCount(); c++) {
				TableElement element = this.elements.getValue(r, c);
				if (element != null) {
					element.receive(visitor);
				}
			}
		}
		visitor.visit(this);
	}

	/**
	 * Finds the cell dimensions.
	 * 
	 * @param g2  the graphics target (required to calculate font sizes).
	 * @param bounds  the bounds.
	 * 
	 * @return The cell dimensions (result[0] is the widths, result[1] is the 
	 *     heights). 
	 */
	private double[][] findCellDimensions(Graphics2D g2, Rectangle2D bounds) {
		int rowCount = this.elements.getRowCount();
		int columnCount = this.elements.getColumnCount();
		// calculate the maximum width for each column
		double maxWidth = 0d;
		double maxHeight = 0d;
		for (int r = 0; r < elements.getRowCount(); r++) {
			for (int c = 0; c < this.elements.getColumnCount(); c++) {
				TableElement element = this.elements.getValue(r, c);
				if (element == null) {
					continue;
				}
				Dimension2D dim = element.preferredSize(g2, bounds);
				maxWidth = Math.max(maxWidth, dim.getWidth());
				maxHeight = Math.max(maxHeight, dim.getHeight());
			}
		}
//		System.out.println("findCellDimensions with input width="+bounds.getWidth()+", count="+columnCount+", maxWidth="+maxWidth);
		double[] widths = new double[columnCount];
		for (int c=0; c<columnCount; c++)
			// if column count is 1, use full width
			widths[c] = columnCount == 1 ? bounds.getWidth() : maxWidth;
		double[] heights = new double[rowCount];
		for (int r=0; r<rowCount; r++)
			heights[r] = maxHeight;
		return new double[][] { widths, heights };
	}


	/**
	 * Returns the preferred size of the element (including insets).
	 * 
	 * @param g2  the graphics target.
	 * @param bounds  the bounds.
	 * @param constraints  the constraints (ignored for now).
	 * 
	 * @return The preferred size. 
	 */
	@Override
	public Dimension2D preferredSize(Graphics2D g2, Rectangle2D bounds, 
			Map<String, Object> constraints) {
		Insets insets = getInsets();
		double[][] cellDimensions = findCellDimensions(g2, bounds);
		double[] widths = cellDimensions[0];
		double[] heights = cellDimensions[1];
		double w = insets.left + insets.right;
		for (int i = 0; i < widths.length; i++) {
			w = w + widths[i];
		}
		double h = insets.top + insets.bottom;
		for (int i = 0; i < heights.length; i++) {
			h = h + heights[i];
		}
		return new Dimension((int) w, (int) h);
	}

	/**
	 * Performs a layout of this table element, returning a list of bounding
	 * rectangles for the element and its subelements.
	 * 
	 * @param g2  the graphics target.
	 * @param bounds  the bounds.
	 * @param constraints  the constraints (if any).
	 * 
	 * @return A list of bounding rectangles. 
	 */
	@Override
	public List<Rectangle2D> layoutElements(Graphics2D g2, Rectangle2D bounds, 
			Map<String, Object> constraints) {
//		System.out.println(bounds);
		
//		double[][] cellDimensions = findCellDimensions(g2, bounds);
//		
//		double[] widths = cellDimensions[0];
//		double[] heights = cellDimensions[1];
//		List<Rectangle2D> result = new ArrayList<>(
//				this.elements.getRowCount() * this.elements.getColumnCount());
//		double y = bounds.getY() + getInsets().top;
//		for (int r = 0; r < elements.getRowCount(); r++) {
//			double x = bounds.getX() + getInsets().left;
//			for (int c = 0; c < this.elements.getColumnCount(); c++) {
//				Rectangle2D cellBounds = new Rectangle2D.Double(x, y, widths[c], heights[r]);
//				TableElement element = this.elements.getValue(r, c);
//				element.layoutElements(g2, cellBounds, null);
//				result.add(cellBounds);
//				x += widths[c];
//			}
//			y = y + heights[r];
//		}

		int bufferEach = 10;
		double totWidth = bounds.getWidth() - 2*bufferEach;
		double wdithEach = totWidth / elements.getColumnCount();
		
		List<Rectangle2D> result = new ArrayList<>(
				this.elements.getRowCount() * this.elements.getColumnCount());
		double y = bounds.getY() + getInsets().top;
		for (int r = 0; r < elements.getRowCount(); r++) {
			double x = bounds.getX() + bufferEach;
			double maxHeight = 0d;
			for (int c = 0; c < this.elements.getColumnCount(); c++) {
				TableElement element = this.elements.getValue(r, c);
				double height = element.preferredSize(g2, bounds).getHeight();
				maxHeight = Math.max(maxHeight, height);
				Rectangle2D cellBounds = new Rectangle2D.Double(x, y, wdithEach, height);
				element.layoutElements(g2, cellBounds, null);
				result.add(cellBounds);
				x += wdithEach;
			}
			y = y + maxHeight;
		}
		return result;
	}

	/**
	 * Draws the element within the specified bounds.
	 * 
	 * @param g2  the graphics target.
	 * @param bounds  the bounds.
	 */
	@Override
	public void draw(Graphics2D g2, Rectangle2D bounds) {
		draw(g2, bounds, null);
	}

	/**
	 * Draws the element within the specified bounds.  If the 
	 * {@code recordBounds} flag is set, this element and each of its
	 * children will have their {@code BOUNDS_2D} property updated with 
	 * the current bounds.
	 * 
	 * @param g2  the graphics target ({@code null} not permitted).
	 * @param bounds  the bounds ({@code null} not permitted).
	 * @param onDrawHandler  an object that will receive notification before 
	 *     and after the element is drawn ({@code null} permitted).
	 * 
	 * @since 1.3
	 */
	@Override
	public void draw(Graphics2D g2, Rectangle2D bounds, 
			TableElementOnDraw onDrawHandler) {
		if (onDrawHandler != null) {
			onDrawHandler.beforeDraw(this, g2, bounds);
		}
		if (getBackground() != null) {
			getBackground().fill(g2, bounds);
		}
		List<Rectangle2D> positions = layoutElements(g2, bounds, null);
		for (int r = 0; r < this.elements.getRowCount(); r++) {
			for (int c = 0; c < this.elements.getColumnCount(); c++) {
				TableElement element = this.elements.getValue(r, c);
				if (element == null) {
					continue;
				}
				Rectangle2D pos = positions.get(r * elements.getColumnCount() 
						+ c);
				element.draw(g2, pos, onDrawHandler);
			}
		}
		if (onDrawHandler != null) {
			onDrawHandler.afterDraw(this, g2, bounds);
		}
	}

	/**
	 * Tests this element for equality with an arbitrary object.
	 * 
	 * @param obj  the object ({@code null} permitted).
	 * 
	 * @return A boolean. 
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof CustomWidthGridElement)) {
			return false;
		}
		CustomWidthGridElement that = (CustomWidthGridElement) obj;
		if (!this.elements.equals(that.elements)) {
			return false;
		}
		return true;
	}

	/**
	 * Returns a string representation of this element, primarily for
	 * debugging purposes.
	 * 
	 * @return A string representation of this element. 
	 */
	@Override
	public String toString() {
		return "GridElement[rowCount=" + this.elements.getRowCount()
		+ ", columnCount=" + this.elements.getColumnCount() + "]";
	}

}
