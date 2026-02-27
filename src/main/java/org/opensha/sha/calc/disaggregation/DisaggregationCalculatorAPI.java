package org.opensha.sha.calc.disaggregation;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.CalculatorAPI;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <p>Title: DisaggregationCalculatorAPI</p>
 * <p>Description: This class defines interface for Disaggregation calculator.
 * Implementation of this interface disaggregates a hazard curve based on the
 * input parameters imr, site and eqkRupforecast.  See Bazzurro and Cornell
 * (1999, Bull. Seism. Soc. Am., 89, pp. 501-520) for a complete discussion
 * of disaggregation.  The Dbar computed here is for rupture distance.</p>
 * @author : Nitin Gupta
 * @created Oct 01,2004
 * @version 1.0
 * TODO: delete this?
 */
public interface DisaggregationCalculatorAPI extends CalculatorAPI {

	/**
	 * Sets the Max Z Axis Range value fro the plotting purposes
	 * @param zMax
	 */
	public void setMaxZAxisForPlot(double zMax); 

	/**
	 * this function performs the disaggregation.
	 * Returns true if it was succesfully able to disaggregate above
	 * a given IML else return false
	 *
	 * @param iml: the intensity measure level to disaggregate
	 * @param site: site parameter
	 * @param imr: selected IMR object
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @param sourceFilters: source filters (e.g., distance)
	 * @param calcParams: calculation parameters from the <code>HazardCurveCalculator</code>
	 * @return boolean
	 */
	public boolean disaggregate(double iml, Site site,
			ScalarIMR imr,
			ERF eqkRupForecast,
			Collection<SourceFilter> sourceFilters,
			ParameterList calcParams);

	/**
	 * this function performs the disaggregation.
	 * Returns true if it was succesfully able to disaggregate above
	 * a given IML else return false
	 *
	 * @param iml: the intensity measure level to disaggregate
	 * @param site: site parameter
	 * @param imrMap: mapping of tectonic regions to IMR objects
	 * @param eqkRupForecast: selected Earthquake rup forecast
	 * @param sourceFilters: source filters (e.g., distance)
	 * @param calcParams: calculation parameters from the <code>HazardCurveCalculator</code>
	 * @return boolean
	 */
	public boolean disaggregate(double iml, Site site,
			Map<TectonicRegionType, ScalarIMR> imrMap,
			ERF eqkRupForecast,
			Collection<SourceFilter> sourceFilters,
			ParameterList calcParams);

	/**
	 * Sets the number of sources to be shown in the Disaggregation.
	 * @param numSources int
	 */
	public void setNumSourcesToShow(int numSources);
	
	public int getNumSourcesToShow();
	
	/**
	 * Enables/disables calculation and display of source distances in source data list.
	 * 
	 * @param showDistances
	 */
	public void setShowDistances(boolean showDistances);

	/**
	 *
	 * Returns the disaggregated source list with following info ( in each line)
	 * 1)Source Id as given by OpenSHA
	 * 2)Name of the Source
	 * 3)Rate Contributed by that source
	 * 4)Percentage Contribution of the source in Hazard at the site.
	 *
	 * @return String
	 */
	public String getDisaggregationSourceInfo();

	/**
	 * gets the number of current rupture being processed
	 * @return
	 */
	public int getCurrRuptures();


	/**
	 * gets the total number of ruptures
	 * @return
	 */
	public int getTotRuptures();


	/**
	 * Checks to see if disaggregation calculation for the selected site
	 * have been completed.
	 * @return
	 */
	public boolean done();

	/**
	 * Creates the disaggregation plot using the GMT and return Disaggregation plot
	 * image web address as the URL string.
	 * @param metadata String
	 * @return String
	 */
	public String getDisaggregationPlotUsingServlet(String metadata);
	
	public DisaggregationPlotData getDisaggPlotData();


	/**
	 * Setting up the Mag Range
	 * @param minMag double
	 * @param numMags int
	 * @param deltaMag double
	 */
	public void setMagRange(double minMag, int numMags, double deltaMag);

	/**
	 * Setting up the Distance Range
	 * @param minDist double
	 * @param numDist int
	 * @param deltaDist double
	 */
	public void setDistanceRange(double minDist, int numDist, double deltaDist);

	/**
	 * Setting up custom distance bins
	 * @param distBinEdges - a double array of the distance-bin edges (in correct order, from low to high)
	 */
	public void setDistanceRange(double[] distBinEdges);

	/**
	 * Returns the Bin Data in the String format
	 * @return String
	 */
	public String getBinData();

	/**
	 *
	 * @return resultant disaggregation in a String format.
	 */
	public String getMeanAndModeInfo();

	/**
	 * @return sonsolidated disaggregation source list, sorted by contribution
	 */
	List<DisaggregationSourceRuptureInfo> getConsolidatedDisaggregationSourceList();

	/**
	 *
	 * Returns the disaggregated source list, consolidated with the given consolidator, with following info ( in each line)
	 * 1)Source Id as given by OpenSHA
	 * 2)Name of the Source
	 * 3)Rate Contributed by that source
	 * 4)Percentage Contribution of the source in Hazard at the site.
	 *
	 * @return String
	 */
	String getConsolidatedDisaggregationSourceInfo();

	/**
	 * @return disaggregation source list, sorted by contribution
	 */
	List<DisaggregationSourceRuptureInfo> getDisaggregationSourceList();
}
