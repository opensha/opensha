package org.opensha.sha.calc.disaggregation.chart3d;

import java.awt.Color;

import org.jfree.chart3d.Chart3DFactory;
import org.jfree.chart3d.axis.CategoryAxis3D;
import org.jfree.chart3d.axis.ValueAxis3D;
import org.jfree.chart3d.data.KeyedValues3DItemKey;
import org.jfree.chart3d.data.Range;
import org.jfree.chart3d.data.category.CategoryDataset3D;
import org.jfree.chart3d.graphics3d.Dimension3D;
import org.jfree.chart3d.graphics3d.Face;
import org.jfree.chart3d.graphics3d.Object3D;
import org.jfree.chart3d.graphics3d.Point3D;
import org.jfree.chart3d.graphics3d.World;
import org.jfree.chart3d.graphics3d.internal.TaggedFace;
import org.jfree.chart3d.internal.Args;
import org.jfree.chart3d.plot.CategoryPlot3D;
import org.jfree.chart3d.renderer.category.CategoryColorSource;
import org.jfree.chart3d.renderer.category.StackedBarRenderer3D;

/**
 * A renderer that can be used with the {@link CategoryPlot3D} class to create
 * 3D stacked bar charts from data in a {@link CategoryDataset3D}.  The 
 * {@code createStackedBarChart()} method in the {@link Chart3DFactory} 
 * class will construct a chart that uses this renderer.  Here is a sample:
 * <div>
 * <img src="../../../../../../doc-files/StackedBarChart3DDemo1.svg"  
 * alt="StackedBarChart3DDemo1.svg" width="500" height="359">
 * </div>
 * (refer to {@code StackedBarChart3DDemo1.java} for the code to generate 
 * the above chart).
 * <br><br> 
 * There is a factory method to create a chart using this renderer - see
 * {@link Chart3DFactory#createStackedBarChart(String, String, CategoryDataset3D, String, String, String)}.
 * <br><br>
 * NOTE: This class is serializable, but the serialization format is subject 
 * to change in future releases and should not be relied upon for persisting 
 * instances of this class.
 */
@SuppressWarnings("serial")
class DisaggBarRenderer3D extends StackedBarRenderer3D {

	/**
	 * Creates a default constructor.
	 */
	public DisaggBarRenderer3D() {
		super();
	}

	/**
     * Performs the actual work of composing a bar to represent one item in the
     * dataset.  This method is reused by the {@link StackedBarRenderer3D}
     * subclass.
     * 
     * @param value  the data value (top of the bar).
     * @param barBase  the base value for the bar.
     * @param dataset  the dataset.
     * @param series  the series index.
     * @param row  the row index.
     * @param column  the column index.
     * @param world  the world.
     * @param dimensions  the plot dimensions.
     * @param xOffset  the x-offset.
     * @param yOffset  the y-offset.
     * @param zOffset  the z-offset.
     */
    @SuppressWarnings("unchecked")
    protected void composeItem(double value, double barBase, 
            CategoryDataset3D dataset, int series, int row, int column,
            World world, Dimension3D dimensions, double xOffset, 
            double yOffset, double zOffset) {

        Comparable<?> seriesKey = dataset.getSeriesKey(series);
        Comparable<?> rowKey = dataset.getRowKey(row);
        Comparable<?> columnKey = dataset.getColumnKey(column);
        
        double vlow = Math.min(barBase, value);
        double vhigh = Math.max(barBase, value);

        CategoryPlot3D plot = getPlot();
        CategoryAxis3D rowAxis = plot.getRowAxis();
        CategoryAxis3D columnAxis = plot.getColumnAxis();
        ValueAxis3D valueAxis = plot.getValueAxis();
        Range range = valueAxis.getRange();
        if (!range.intersects(vlow, vhigh)) {
            return; // the bar is not visible for the given axis range
        }
        
        double vbase = range.peggedValue(vlow);
        double vtop = range.peggedValue(vhigh);
        boolean inverted = barBase > value;
        
        double rowValue = rowAxis.getCategoryValue(rowKey);
        double columnValue = columnAxis.getCategoryValue(columnKey);

        double width = dimensions.getWidth();
        double height = dimensions.getHeight();
        double depth = dimensions.getDepth();
        double xx = columnAxis.translateToWorld(columnValue, width) + xOffset;
        double yy = valueAxis.translateToWorld(vtop, height) + yOffset;
        double zz = rowAxis.translateToWorld(rowValue, depth) + zOffset;

        double xw = getBarXWidth() * columnAxis.getCategoryWidth();
        double zw = getBarZWidth() * rowAxis.getCategoryWidth();
        double xxw = columnAxis.translateToWorld(xw, width);
        double xzw = rowAxis.translateToWorld(zw, depth);
        double basew = valueAxis.translateToWorld(vbase, height) + yOffset;
    
        Color color = getColorSource().getColor(series, row, column);
        Color baseColor = null;
        CategoryColorSource baseColorSource = getBaseColorSource();
        if (baseColorSource != null && !range.contains(getBase())) {
            baseColor = baseColorSource.getColor(series, row, column);
        }
        if (baseColor == null) {
            baseColor = color;
        }

        Color topColor = null;
        CategoryColorSource topColorSource = getTopColorSource();
        if (topColorSource != null && !range.contains(value)) {
            topColor = topColorSource.getColor(series, row, column);
        }
        if (topColor == null) {
            topColor = color;
        }
        
        double customZ = column*100000d + row*10000d + series*1000d;
        
        // create our own custom bar
        Object3D bar = createBar(xxw, xzw, xx, yy, zz, basew, 
                color, baseColor, topColor, inverted, customZ);
        KeyedValues3DItemKey itemKey = new KeyedValues3DItemKey(seriesKey, 
                rowKey, columnKey);
        bar.setProperty(Object3D.ITEM_KEY, itemKey);
        world.add(bar);
        drawItemLabels(world, dataset, itemKey, xx, yy, zz, basew, inverted);   
    }
    
    public static Object3D createBar(double xWidth, double zWidth, double x, 
            double y, double z, double zero, Color barColor, Color baseColor,
            Color topColor, boolean inverted, double customZ) {
        Args.nullNotPermitted(barColor, "barColor");
        Color c0 = baseColor;
        Color c1 = topColor;
        if (inverted) {
            Color cc = c1;
            c1 = c0;
            c0 = cc;
        }
        Object3D bar = new Object3D(barColor);
        if (c0 != null) {
            bar.setProperty(Object3D.COLOR_PREFIX + "c0", c0);
        }
        if (c1 != null) {
            bar.setProperty(Object3D.COLOR_PREFIX + "c1", c1);
        }
        double xdelta = xWidth / 2.0;
        double zdelta = zWidth / 2.0;
        bar.addVertex(new Point3D(x - xdelta, zero, z - zdelta));
        bar.addVertex(new Point3D(x + xdelta, zero, z - zdelta));
        bar.addVertex(new Point3D(x + xdelta, zero, z + zdelta));
        bar.addVertex(new Point3D(x - xdelta, zero, z + zdelta));
        bar.addVertex(new Point3D(x - xdelta, y, z - zdelta));
        bar.addVertex(new Point3D(x + xdelta, y, z - zdelta));
        bar.addVertex(new Point3D(x + xdelta, y, z + zdelta));
        bar.addVertex(new Point3D(x - xdelta, y, z + zdelta));

        bar.addFace(new ZSortOverrideFace(bar, new int[] {0, 1, 5, 4}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {4, 5, 1, 0}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {1, 2, 6, 5}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {5, 6, 2, 1}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {2, 3, 7, 6}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {6, 7, 3, 2}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {0, 4, 7, 3}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {3, 7, 4, 0}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {4, 5, 6, 7}, customZ));
        bar.addFace(new ZSortOverrideFace(bar, new int[] {3, 2, 1, 0}, customZ));
        if (c1 != null) {
            bar.addFace(new ZSortOverrideTaggedFace(bar, new int[] {7, 6, 5, 4}, "c1", customZ));
        } else {
            bar.addFace(new ZSortOverrideFace(bar, new int[] {7, 6, 5, 4}, customZ));
        }
        if (c0 != null) {
            bar.addFace(new ZSortOverrideTaggedFace(bar, new int[] {0, 1, 2, 3}, "c0", customZ));    
        } else {
            bar.addFace(new ZSortOverrideFace(bar, new int[] {0, 1, 2, 3}, customZ));                
        }
        
        return bar;      
    }
    
    private static class ZSortOverrideFace extends Face {

		private double zOverride;
		private int[] vertices;

		public ZSortOverrideFace(Object3D owner, int[] vertices, double zOverride) {
			super(owner, vertices);
			this.vertices = vertices;
			this.zOverride = zOverride;
		}

		@Override
		public float calculateAverageZValue(Point3D[] points) {
			float total = 0.0f;
			int offset = getOffset();
			for (int i = 0; i < this.vertices.length; i++) {
				total = total + (float) points[this.vertices[i] + offset].z;
			}
	        return (float)zOverride + total / (float)this.getVertexCount();
		}
    	
    }
    
    private static class ZSortOverrideTaggedFace extends TaggedFace {

		private int[] vertices;
		private double zOverride;

		public ZSortOverrideTaggedFace(Object3D owner, int[] vertices, String tag, double zOverride) {
			super(owner, vertices, tag);
			// TODO Auto-generated constructor stub
			this.vertices = vertices;
			this.zOverride = zOverride;
		}

		@Override
		public float calculateAverageZValue(Point3D[] points) {
			float total = 0.0f;
			int offset = getOffset();
			for (int i = 0; i < this.vertices.length; i++) {
				total = total + (float) points[this.vertices[i] + offset].z;
			}
	        return (float)zOverride + total / (float)this.getVertexCount();
		}
    	
    }

}