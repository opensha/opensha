package scratch.UCERF3.erf.ETAS.NoFaultsModel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.EventObject;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractNthRupERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.calc.ERF_Calculator;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.MaximumMagnitudeParam;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_NoFaultsGridSourceGenerator;

/**
 * This uses only gridded seismicity sources for the specified adjustable parameters.
 * @author field
 *
 */
public class UCERF3_GriddedSeisOnlyERF_ETAS extends AbstractNthRupERF {
	
	
	private static final long serialVersionUID = 1L;
	
	private static final boolean D = true;

	public static final String NAME = "UCERF3_GriddedSeisOnlyERF_ETAS";
	private String name = NAME;
	
	// Adjustable parameters
	protected ApplyGardnerKnopoffAftershockFilterParam applyAftershockFilterParam;
	protected BackgroundRupParam bgRupTypeParam;
	protected MaximumMagnitudeParam maxMagParam;
	protected EnumParameter<TotalMag5Rate> totalRateParam;
	/**
	 *  options for above are (set in initParams(): 
	 *  						TotalMag5Rate.RATE_6p5
	 *  						TotalMag5Rate.RATE_7p9
	 *  						TotalMag5Rate.RATE_9p6
	 */
	protected EnumParameter<SpatialSeisPDF> spatialSeisPDF_Param;
	/**
	 *  options for above are (set in initParams(): 
	 *  						SpatialSeisPDF.UCERF2
	 *  						SpatialSeisPDF.UCERF3
	 *  						null (for uniform distribution)
	 */
	
	// primitives
	protected boolean applyAftershockFilter = false;
	protected BackgroundRupType bgRupType = BackgroundRupType.POINT;
	protected double maxMag = 8.3;
	protected TotalMag5Rate totalMag5Rate = TotalMag5Rate.RATE_7p9;
	protected SpatialSeisPDF spatialSeisPDF = SpatialSeisPDF.UCERF3;

	// Parameter change flags: (none for bgIncludeParam) 
	protected boolean applyAftershockFilterChanged=true;
	protected boolean bgRupTypeChanged=true;
	protected boolean maxMagChanged=true;
	protected boolean totalMag5RateChanged=true;
	protected boolean spatialSeisPDF_Changed=true;
	
	
	// TimeSpan stuff:
	protected final static double DURATION_DEFAULT = 30;	// years
	protected final static double DURATION_MIN = 0.0001;
	public final static double DURATION_MAX = 100000;
	public final static int START_TIME_DEFAULT = 2014;
	protected final static int START_TIME_MIN = 1800;	// prob model calc now handles case when this is before date of last event
	protected final static int START_TIME_MAX = 2100;
	boolean timeSpanChangeFlag=true;	// this keeps track of time span changes
	
	private GridSourceProvider gridSources;				// grid sources from the FSS
	protected ArrayList<int[]> nthRupIndicesForSource;	// this gives the nth indices for a given source
	
	// THESE AND ASSOCIATED GET/SET METHODS COULD BE ADDED TO ABSRACT ERF:
	protected int totNumRups;
	protected int[] srcIndexForNthRup;
	protected int[] rupIndexForNthRup;


	public UCERF3_GriddedSeisOnlyERF_ETAS() {
		initParams();
		initTimeSpan();
	}

	
	/**
	 * This initiates the timeSpan.
	 */
	protected void initTimeSpan() {
			timeSpan = new TimeSpan(TimeSpan.MILLISECONDS, TimeSpan.YEARS);
			timeSpan.setDuractionConstraint(DURATION_MIN, DURATION_MAX);
			timeSpan.setDuration(DURATION_DEFAULT);
			timeSpan.setStartTimeConstraint(TimeSpan.START_YEAR, START_TIME_MIN, START_TIME_MAX);
			timeSpan.setStartTime(START_TIME_DEFAULT, 1, 1, 0, 0, 0, 0);
			timeSpan.addParameterChangeListener(this);			
	}
	
	
	protected void initParams() {
		applyAftershockFilterParam= new ApplyGardnerKnopoffAftershockFilterParam();  // default is false
		bgRupTypeParam = new BackgroundRupParam();
		maxMagParam = new MaximumMagnitudeParam();
		totalRateParam = new EnumParameter<TotalMag5Rate>("Total Regional Rate", EnumSet
				.of(TotalMag5Rate.RATE_6p5,TotalMag5Rate.RATE_7p9,TotalMag5Rate.RATE_9p6),
				TotalMag5Rate.RATE_7p9, null);
		totalRateParam.setUnits("per year");
		totalRateParam.setInfo("Total regional rate of M≥5 events");
		
		spatialSeisPDF_Param = new EnumParameter<SpatialSeisPDF>("Spatial Seis PDF", EnumSet
				.of(SpatialSeisPDF.UCERF2, SpatialSeisPDF.UCERF3),
				SpatialSeisPDF.UCERF3, "Uniform");
		spatialSeisPDF_Param.setInfo("How seismicity is distribute; use null for uniform");

		
		// set listeners
		applyAftershockFilterParam.addParameterChangeListener(this);
		bgRupTypeParam.addParameterChangeListener(this);
		maxMagParam.addParameterChangeListener(this);
		totalRateParam.addParameterChangeListener(this);
		spatialSeisPDF_Param.addParameterChangeListener(this);

		// set parameters to the primitive values
		applyAftershockFilterParam.setValue(applyAftershockFilter);
		bgRupTypeParam.setValue(bgRupType);
		maxMagParam.setValue(maxMag);
		totalRateParam.setValue(totalMag5Rate);
		spatialSeisPDF_Param.setValue(spatialSeisPDF);

		createParamList();
	}
	
	/**
	 * Put parameters in theParameterList
	 */
	protected void createParamList() {
		adjustableParams = new ParameterList();
		adjustableParams.addParameter(applyAftershockFilterParam);
		adjustableParams.addParameter(bgRupTypeParam);
		adjustableParams.addParameter(maxMagParam);
		adjustableParams.addParameter(totalRateParam);
		adjustableParams.addParameter(spatialSeisPDF_Param);
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public void updateForecast() {
		
		if (D) System.out.println("Updating forecast");
		
		if(spatialSeisPDF_Changed || totalMag5RateChanged || maxMagChanged) {
			double[] seisPDF = null;
			if(spatialSeisPDF  != null)
				seisPDF = spatialSeisPDF.getPDF();
			
			if(D) {
				System.out.println(spatialSeisPDF+"\n"+totalMag5Rate.getRateMag5()+"\n"+maxMag);
			}
			gridSources = new UCERF3_NoFaultsGridSourceGenerator(seisPDF, totalMag5Rate.getRateMag5(), maxMag);
		}
		
				
		if(bgRupTypeChanged) {	// this will change the number of ruptures
			setAllNthRupRelatedArrays();
		}
		
		// reset change flags (that haven't already been done so)
		applyAftershockFilterChanged = false;
		bgRupTypeChanged = false;	
		spatialSeisPDF_Changed = false;
		totalMag5RateChanged = false;
		maxMagChanged = false;
		timeSpanChangeFlag = false;
				
	}
	
	@Override
	public void parameterChange(ParameterChangeEvent event) {
		super.parameterChange(event);	// sets parameterChangeFlag = true;
		String paramName = event.getParameterName();
		if (paramName.equalsIgnoreCase(applyAftershockFilterParam.getName())) {
			applyAftershockFilter = applyAftershockFilterParam.getValue();
			applyAftershockFilterChanged = true;
		} else if (paramName.equalsIgnoreCase(bgRupTypeParam.getName())) {
			bgRupType = bgRupTypeParam.getValue();
			bgRupTypeChanged = true;
		} else if (paramName.equalsIgnoreCase(maxMagParam.getName())) {
			maxMag = maxMagParam.getValue();
			maxMagChanged = true;
		} else if (paramName.equalsIgnoreCase(totalRateParam.getName())) {
			this.totalMag5Rate = totalRateParam.getValue();
			totalMag5RateChanged = true;
		} else if (paramName.equalsIgnoreCase(spatialSeisPDF_Param.getName())) {
			spatialSeisPDF = spatialSeisPDF_Param.getValue();
			spatialSeisPDF_Changed = true;
		} else {
			throw new RuntimeException("parameter name not recognized");
		}
		
	}

	public GridSourceProvider getGridSourceProvider() {
		return gridSources;
	}

	
	@Override
	public int getNumSources() {
		return gridSources.size();
	}
	
	@Override
	public ProbEqkSource getSource(int iSource) {
		return gridSources.getSource(iSource, timeSpan.getDuration(),
				applyAftershockFilter, bgRupType);
	}

	@Override
	public void timeSpanChange(EventObject event) {
		timeSpanChangeFlag = true;
	}

	



	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;	// TODO do this better

		UCERF3_GriddedSeisOnlyERF_ETAS erf = new UCERF3_GriddedSeisOnlyERF_ETAS();
		erf.getTimeSpan().setDuration(1.0);
		erf.getParameter("Spatial Seis PDF").setValue(null);
		erf.updateForecast();
		ParameterList paramList = erf.getAdjustableParameterList();
		for(int p=0;p<paramList.size();p++){
			System.out.println(paramList.getByIndex(p).getName()+"\t"+paramList.getByIndex(p).getValue());
			
		}
		System.out.println("erf.getNumSources()="+erf.getNumSources());
		
		GriddedGeoDataSet data = ERF_Calculator.getNucleationRatesInRegion(erf, erf.getGridSourceProvider().getGriddedRegion(),0.0, 10.0);
		System.out.println("data.getMaxZ()="+data.getMaxZ());
		if(erf.spatialSeisPDF_Param.getValue() != null) {
			double[] seisPDF = erf.spatialSeisPDF_Param.getValue().getPDF();
			for(int i=0;i<data.size();i++) {
				Location loc = data.getLocation(i);
				System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+data.get(i)+"\t"+seisPDF[i]);
			}			
		}
		else {
			for(int i=0;i<data.size();i++) {
				Location loc = data.getLocation(i);
				System.out.println(loc.getLongitude()+"\t"+loc.getLatitude()+"\t"+data.get(i));
			}

		}
			
		SummedMagFreqDist totMFD = ERF_Calculator.getTotalMFD_ForERF(erf, 2.55, 8.45, 60, true);
//		System.out.println(totMFD);
		System.out.println("totMFD.getCumRate(5.05)="+totMFD.getCumRate(5.05));
//		System.out.println("done");
	}

}
