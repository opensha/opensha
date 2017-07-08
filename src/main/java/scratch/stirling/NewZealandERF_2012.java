package scratch.stirling;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.bugReports.DefaultExceptoinHandler;
import org.opensha.nshmp2.erf.source.FaultSource;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.util.IconFetcher;

/**
 * An ERF for New Zealand using updated 2012 sources.
 *
 * @author Mark Stirling
 */
public class NewZealandERF_2012 extends AbstractERF {

	public static final String NAME = "New Zealand ERF 2012";
	
	public final static String BACK_SEIS_NAME = new String ("Background Seismicity");
	public final static String BACK_SEIS_INCLUDE = new String ("Include");
	public final static String BACK_SEIS_EXCLUDE = new String ("Exclude");
	private StringParameter backSeisParam;
	
	private static final double FAULT_SPACING = 1.0;
	private static final double DEFAULT_DURATION = 50.0;
	
	private static NewZealandParser parser = new NewZealandParser();
	
	private List<ProbEqkSource> faultSources;
	private List<ProbEqkSource> gridSources;
	

	public NewZealandERF_2012() {
		
		//create the timespan object with start time and duration in years
		timeSpan = new TimeSpan(TimeSpan.NONE,TimeSpan.YEARS);
		timeSpan.setDuration(DEFAULT_DURATION);
		timeSpan.addParameterChangeListener(this);
		
		initAdjParams();
	}
	
	private void initAdjParams() {
		// background seismicity include/exclude  
		ArrayList<String> backSeisOptionsStrings = new ArrayList<String>();
		backSeisOptionsStrings.add(UCERF2.BACK_SEIS_EXCLUDE);
		backSeisOptionsStrings.add(UCERF2.BACK_SEIS_INCLUDE);
		backSeisOptionsStrings.add(UCERF2.BACK_SEIS_ONLY);
		backSeisParam = new StringParameter(UCERF2.BACK_SEIS_NAME, backSeisOptionsStrings, UCERF2.BACK_SEIS_DEFAULT);
		backSeisParam.setInfo("Background source enabler");
	}

	@Override
	public int getNumSources() {
		return faultSources.size() + gridSources.size();
	}

	@Override
	public ProbEqkSource getSource(int idx) {
		checkElementIndex(idx, getNumSources());
		if (idx < faultSources.size()) {
			return faultSources.get(idx);
		}
		return gridSources.get(idx - faultSources.size());
	}

	@Override
	public void updateForecast() {
		if(parameterChangeFlag) {
			faultSources = parser.getFaultSources(FAULT_SPACING, timeSpan.getDuration());
			gridSources = parser.getGridSources(timeSpan.getDuration());
		}
		parameterChangeFlag = false;
	}

	@Override
	public String getName() {
		return NAME;
	}
	
	public static void main(String[] args) {
//		NewZealandERF_2012 erf = new NewZealandERF_2012();
//		erf.updateForecast();
//		for (ProbEqkSource source : erf) {
//			System.out.println(source.getName() + " " + source.getNumRuptures());
//		}
		
		testApp();
	}
	
	private static void testApp() {
		HazardCurveApplication applet = new HazardCurveApplication("NZ Test");
		applet.init();
		applet.setVisible(true);
	}
}
