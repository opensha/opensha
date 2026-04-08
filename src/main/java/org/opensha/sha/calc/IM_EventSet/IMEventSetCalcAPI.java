package org.opensha.sha.calc.IM_EventSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.sourceFilters.SourceFilter;

/**
 * Core interface defining the data input contract for the IM Event Set Calculator system.
 * Provides a standardized way to supply calculation configuration data to both the
 * calculation engine and output writers.
 * <p>
 * This API serves as the central data bridge between different components:
 * <ul>
 * <li><b>Calculation Engine</b>: The AbstractIMEventSetCalc class uses this API to access
 * site data, locations, and output directories for the core hazard calculation logic</li>
 * <li><b>Output Writers</b>: IM_EventSetOutputWriter implementations like
 * HAZ01Writer and OriginalModCsvWriter use this API to generate
 * formatted output files from the calculated results</li>
 * <li><b>Front-end Implementations</b>: Both CLT (IMEventSetCalculatorCLT) and GUI
 * (IMEventSetCalculatorGUI) provide data through this interface</li>
 * </ul>
 * </p>
 * <p>
 * The API design enables separation of concerns:
 * <ul>
 * <li><b>CLT Extension</b>: The command-line tool extends AbstractIMEventSetCalc
 * because it directly parses configuration files and can implement API methods by
 * reading input data</li>
 * <li><b>GUI Delegation</b>: The GUI uses a separate implementation (GUICalcAPI_Impl)
 * because it collects data interactively and needs to separate Swing components from
 * the calculation engine</li>
 * <li><b>Writer Independence</b>: Output writers remain independent of how data is
 * collected, working solely through this interface</li>
 * </ul>
 * </p>
 */
public interface IMEventSetCalcAPI {

    /**
     * Returns the number of sites for the calculation
     *
     * @return
     */
    int getNumSites();

    /**
     * Returns the Location of the ith site.
     *
     * @param i
     * @return
     */
    Location getSiteLocation(int i);

    /**
     * Returns the user specified (in the input file) site data for the site
     * or null to try to use site data providers
     *
     * @param i ith site user created
     * @return list of parameters holding site data
     */
    ParameterList getUserSiteData(int i);

    /**
     * Returns the output directory for all results
     *
     * @return
     */
    File getOutputDir();

    /**
     * This retrieves the site data parameter list for each site.
     * Each entry in the list corresponds to a single site.
     *
     * @return
     */
    ArrayList<ParameterList> getSitesData();

    ArrayList<Site> getSites();

    List<SourceFilter> getSourceFilters();
}
