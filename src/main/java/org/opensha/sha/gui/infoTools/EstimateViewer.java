package org.opensha.sha.gui.infoTools;

import java.awt.Color;
import java.util.ArrayList;

import org.opensha.commons.data.estimate.DiscreteValueEstimate;
import org.opensha.commons.data.estimate.Estimate;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotColorAndLineTypeSelectorControlPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;

/**
 * <p>Title: EstimateViewer.java </p>
 * <p>Description: This class helps in viewing the various estimates </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Vipin Gupta, Nitin Gupta
 * @version 1.0
 */

public class EstimateViewer {
  private final static String X_AXIS_LABEL = "X Values";
  private final static String Y_AXIS_LABEL = "Probability";
  private Estimate estimate;
  private String xAxisLabel, yAxisLabel;
  private GraphWindow graphWindow;
  private final PlotCurveCharacterstics PDF_PLOT_CHAR_HISTOGRAM = new PlotCurveCharacterstics(
		  PlotLineType.HISTOGRAM, 2f, null, 4f, Color.RED);
  private final PlotCurveCharacterstics CDF_PLOT_CHAR = new PlotCurveCharacterstics(
		  PlotLineType.SOLID, 2f, null, 4f, Color.BLUE);
  private final PlotCurveCharacterstics CDF_USING_FRACTILE_PLOT_CHAR = new PlotCurveCharacterstics(
		  PlotLineType.SOLID, 2f, null, 4f, Color.BLACK);
  private final PlotCurveCharacterstics DISCRETE_VAL_CDF_USING_FRACTILE_PLOT_CHAR = new PlotCurveCharacterstics(
		  null, 2f, PlotSymbol.CROSS, 6f, Color.BLACK);


  public EstimateViewer(Estimate estimate) {
    setEstimate(estimate);
    graphWindow = new GraphWindow(getCurveFunctionList(), estimate.getName(), getPlottingFeatures());
    graphWindow.setX_AxisLabel(X_AXIS_LABEL);
    graphWindow.setY_AxisLabel(Y_AXIS_LABEL);
    //graphWindow.pack();
    graphWindow.setVisible(true);
  }

  public void setEstimate(Estimate estimate) {
    this.estimate = estimate;
  }

  public ArrayList getCurveFunctionList() {
   ArrayList list = new ArrayList();
   AbstractDiscretizedFunc func = estimate.getPDF_Test();
   list.add(func); // draw the histogram for PDF
   list.add(estimate.getCDF_Test());
   list.add(estimate.getCDF_TestUsingFractile());
   //list.add(estimate.getCDF());
   return list;
 }

  public ArrayList getPlottingFeatures() {
    ArrayList list = new ArrayList();
    list.add(PDF_PLOT_CHAR_HISTOGRAM);
    list.add(CDF_PLOT_CHAR);
    if(estimate instanceof DiscreteValueEstimate)
      list.add(DISCRETE_VAL_CDF_USING_FRACTILE_PLOT_CHAR);
    else list.add(CDF_USING_FRACTILE_PLOT_CHAR);
    return list;
  }

}
