package org.opensha.commons.gui.plot.jfreechart;

import static junit.framework.Assert.*;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.PlotState;
import org.jfree.chart.plot.ValueAxisPlot;
import org.jfree.data.Range;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class JFreeLogarithmicAxisTest {

    static class MockPlot extends Plot implements ValueAxisPlot{

        final Range range;

        public MockPlot(Range range) {
            this.range = range;
        }

        @Override
        public Range getDataRange(ValueAxis axis) {
            return range;
        }

        @Override
        public String getPlotType() {
            return "";
        }

        @Override
        public void draw(Graphics2D g2, Rectangle2D area, Point2D anchor, PlotState parentState, PlotRenderingInfo info) {
            throw new RuntimeException("not implemented");
        }
    }

    @Test
    public void testSmallMinMax() {
        JFreeLogarithmicAxis axis = new JFreeLogarithmicAxis("the axis");
        axis.setAutoRange(false); // prevent configure() from being called when we set the Plot
        axis.setAutoRangeMinimumSize(1, false);
        Range range = new Range(0.00004, 0.00004);
        axis.setPlot(new MockPlot(range));
        axis.autoAdjustRange();

        assertTrue(axis.getRange().getLowerBound() >= JFreeLogarithmicAxis.SMALL_LOG_VALUE);
        assertTrue(axis.getRange().getUpperBound() > axis.getRange().getLowerBound());

    }

    public static void main(String[] args) {
        new JFreeLogarithmicAxisTest().testSmallMinMax();
    }

}
