package scratch.UCERF3.erf.ETAS;

import org.opensha.commons.data.TimeSpan;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

/**
 * This overrides subclass to:
 * 
 * 1) add more start-time precision (for both poisson and time-dep probability model options)
 * @author field
 *
 */
public class FaultSystemSolutionERF_ETAS extends FaultSystemSolutionERF {
	
	protected final static int START_TIME_MAX = FaultSystemSolutionERF.START_TIME_MAX+(int)DURATION_MAX;
	
	public static final GriddedSeismicitySettings GRID_SEIS_SETTINGS =
			GriddedSeismicitySettings.DEFAULT.forSurfaceType(BackgroundRupType.POINT)
			.forDistanceCorrection(PointSourceDistanceCorrections.NONE.get())
			.forMinimumMagnitude(2.55)
			.forSupersamplingSettings(null);
	
	public FaultSystemSolutionERF_ETAS() {
		this(null);
	}

	public FaultSystemSolutionERF_ETAS(FaultSystemSolution faultSysSolution) {
		super(faultSysSolution);
		
		// sets to point with mMin=2.55
		setGriddedSeismicitySettings(GRID_SEIS_SETTINGS);
	}
	
	/**
	 * This initiates the timeSpan.
	 */
	@Override
	protected void initTimeSpan() {
		if(tdTimeSpanCache == null) {
			tdTimeSpanCache = new TimeSpan(TimeSpan.MILLISECONDS, TimeSpan.YEARS);
			tdTimeSpanCache.setDuractionConstraint(DURATION_MIN, DURATION_MAX);
			tdTimeSpanCache.setDuration(DURATION_DEFAULT);
			tdTimeSpanCache.setStartTimeConstraint(TimeSpan.START_YEAR, START_TIME_MIN, START_TIME_MAX);
			tdTimeSpanCache.setStartTime(START_TIME_DEFAULT, 1, 1, 0, 0, 0, 0);
			tdTimeSpanCache.addParameterChangeListener(this);		
		}
		timeSpan = tdTimeSpanCache;
	}


	@Override
	protected boolean isRateGainValid(double rateGain, int fltSystRupIndex, double duration) {
		// TODO: couldn't figure out a sanity check the actually worked to validate 0 rate gains, revisit zeros
		// when building new ETAS implementation
		return rateGain >= 0d;
//		if (rateGain == 0d) {
//			// make sure that time since last is effectively zero for all sections involved
//			long startMillis = timeSpan.getStartTimeMillisecond();
//			// test that it's at least within the past month
//			long testMillis = startMillis - 1000l*60l*60l*24l*30l;
//			for (FaultSection sect : getSolution().getRupSet().getFaultSectionDataForRupture(fltSystRupIndex))
//				if (sect.getDateOfLastEvent() < testMillis)
//					return false;
//			return true;
//		} else {
//			return rateGain > 0d;
//		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

	}

}
