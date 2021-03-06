package org.opensha.commons.gui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.util.ShapeUtils;

/**
 * <p>Title: GriddedSubsetXYItemRenderer</p>
 * <p>Description: JFreeChart subclass - used in FaultDemo Applet.</p>
 *
 * @author
 * @version 1.0
 */

public class GriddedSubsetXYItemRenderer
    extends AdjustableScaleXYItemRenderer
{



    /** A working line (to save creating thousands of instances). */
    private Line2D line;


    /**
     * Constructs a new renderer.
     */
    public GriddedSubsetXYItemRenderer() {
        super();
        this.line = new Line2D.Double(0.0, 0.0, 0.0, 0.0);
        scale = 6;
    }

    /**
     * Constructs a new renderer.
     * <p>
     * To specify the type of renderer, use one of the constants: SHAPES, LINES or SHAPES_AND_LINES.
     *
     * @param type The type of renderer.
     * @param toolTipGenerator The tooltip generator.
     */
    public GriddedSubsetXYItemRenderer(int type, XYToolTipGenerator toolTipGenerator) {
        super(type, toolTipGenerator);
        scale = 7;
    }


    /**
     * Is used to determine if a shape is filled when drawn or not
     *
     * @param plot The plot (can be used to obtain standard color information etc).
     * @param series The series index
     * @param item The item index
     * @param x The x value of the item
     * @param y The y value of the item
     *
     * @return True if the shape used to draw the data item should be filled, false otherwise.
     */
    protected boolean isShapeFilled(Plot plot, int series, int item, double x, double y) {
      return true;
    }


    /**
     * Draws the visual representation of a single data item.
     *
     * @param g2 The graphics device.
     * @param dataArea The area within which the data is being drawn.
     * @param info Collects information about the drawing.
     * @param plot The plot (can be used to obtain standard color information etc).
     * @param horizontalAxis The horizontal axis.
     * @param verticalAxis The vertical axis.
     * @param data The dataset.
     * @param series The series index.
     * @param item The item index.
     */
    public void drawItem(Graphics2D g2, Rectangle2D dataArea, ChartRenderingInfo info,
                         XYPlot plot, ValueAxis horizontalAxis, ValueAxis verticalAxis,
                         XYDataset data, int datasetIndex, int series, int item,
                         CrosshairState crosshairInfo) {

        // setup for collecting optional entity info...
        Shape entityArea = null;
        EntityCollection entities = null;
        if (info!=null) {
            entities = info.getEntityCollection();
        }

        Paint seriesPaint = fillColor;
        Stroke seriesStroke = this.getItemStroke(datasetIndex, series);
        g2.setPaint(seriesPaint);
        g2.setStroke(seriesStroke);

        // get the data point...
        Number x1 = data.getXValue(series, item);
        Number y1 = data.getYValue(series, item);
        if (y1!=null) {
            double transX1 = horizontalAxis.valueToJava2D(x1.doubleValue(), dataArea,RectangleEdge.TOP);
            double transY1 = verticalAxis.valueToJava2D(y1.doubleValue(), dataArea, RectangleEdge.LEFT);

            Paint paint = getItemPaint(series, item);
            if (paint != null) {
              g2.setPaint(paint);
            }

            if (getPlotLines()) {

                if (item>0) {
                    // get the previous data point...
                    Number x0 = data.getXValue(series, item-1);
                    Number y0 = data.getYValue(series, item-1);
                    if (y0!=null) {
                        double transX0 = horizontalAxis.valueToJava2D(x0.doubleValue(), dataArea,RectangleEdge.TOP);
                        double transY0 = verticalAxis.valueToJava2D(y0.doubleValue(), dataArea,RectangleEdge.LEFT);

                        line.setLine(transX0, transY0, transX1, transY1);
                        if (line.intersects(dataArea)) {
                            g2.draw(line);
                        }
                    }
                }
            }
            
            if (getPlotImages()) {

              Shape shape = getItemShape(series, item);
              shape = ShapeUtils.createTranslatedShape(shape, transY1, 
                      transX1);
              if (isShapeFilled(plot, series, item, transX1, transY1)) {
                if (shape.intersects(dataArea)) g2.fill(shape);
              } else {
                if (shape.intersects(dataArea)) g2.draw(shape);
              }
                entityArea = shape;

            }

            if (getPlotImages()) {
              // use shape scale with transform??
              double shapeScale = getShapeScale(plot, series, item, transX1, transY1);
              Image image = getImage(plot, series, item, transX1, transY1);
              if (image != null) {
                Point hotspot = getImageHotspot(plot, series, item, transX1, transY1, image);
                g2.drawImage(image,(int)(transX1-hotspot.getX()),(int)(transY1-hotspot.getY()),(ImageObserver)null);
              }
              // tooltipArea = image; not sure how to handle this yet
            }

            // add an entity for the item...
            if (entities!=null) {
              if (entityArea==null) {
                entityArea = new Rectangle2D.Double(transX1-2, transY1-2, 4, 4);
              }
              String tip = "";
              if (this.getToolTipGenerator(series,item)!=null) {
                tip = getToolTipGenerator(series,item).generateToolTip(data, series, item);
                }
                XYItemEntity entity = new XYItemEntity(entityArea,data, item, series, tip,null);
                entities.add(entity);
            }

            // do we need to update the crosshair values?
            double distance = 0.0;
            if (plot.isDomainCrosshairLockedOnData()) {
            	if (plot.isRangeCrosshairLockedOnData()) {
            		// both axes
            		// had to add datasetIndex to catch up with JFreeChart 1.5 changes, TODO verify
            		crosshairInfo.updateCrosshairPoint(x1.doubleValue(), y1.doubleValue(), datasetIndex,
            				transX1,transY1,PlotOrientation.HORIZONTAL);
            	}
            	else {
            		// just the horizontal axis...
            		// had to add datasetIndex to catch up with JFreeChart 1.5 changes, TODO verify
            		crosshairInfo.updateCrosshairX(x1.doubleValue(), transX1, datasetIndex);
            	}
            }
            else {
            	if (plot.isRangeCrosshairLockedOnData()) {
            		// just the vertical axis...
            		// had to add datasetIndex to catch up with JFreeChart 1.5 changes, TODO verify
            		crosshairInfo.updateCrosshairY(y1.doubleValue(), transY1, datasetIndex);
            	}
            }
        }

    }

    public void setFillColor(java.awt.Color fillColor) {
        this.fillColor = fillColor;
    }
    public java.awt.Color getFillColor() {
        return fillColor;
    }
    private java.awt.Color fillColor;

}
