package org.opensha.sha.gui.infoTools;

/**
 * <p>Title: HazardCurveDisaggregationWindowAPI</p>
 *
 * <p>Description: This allows the main application to listen to the events
 * that occur on the HazardCurve Disaggregation window.</p>
 *
 * @author Nitin Gupta, Vipin Gupta, Ned Field
 * @since Nov 28, 2005
 * @version 1.0
 */
public interface HazardCurveDisaggregationWindowAPI {

    /**
     * Returns the Sorted Sources Disaggregation list based on
     * @return String
     */
    public String getSourceDisaggregationInfo();

    /**
     * Returns the Disaggregation plot image webaddr.
     * @return String
     */
    public String getDisaggregationPlot();

    //gets the Parameters info as HTML for which Disaggregation generated.
    public String getMapParametersInfoAsHTML();

}
