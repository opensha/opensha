/**
 * 
 */
package org.opensha.sha.earthquake.rupForecastImpl.NewZealand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.estimate.DiscreteValueEstimate;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <p>Title: New Zealand 2010 Eqk Rup Forecast</p>
 * <p>Description: .
 * Earthquake Rupture Forecast for New Zealand using the 2010
 * NSHM (Stirling et al, submitted).  As well as epistemic
 * uncertainties examined as part of EQC project (Bradley et al, in prep)
 * Background sources yet to be included (31 Jan 2011)
 * </p>
 * <p>Copyright: Copyright (c) 2011</p>
 * <p>Company: </p>
 * @author : Brendon Bradley
 * @Date : Jan, 2011
 * @version 1.0
 */

public class NewZealandERF2010_Epistemic  extends AbstractEpistemicListERF{
	public static final String  NAME = new String("NewZealand_ERF_2010_Epistemic");
	protected NewZealandERF2010 newZealand2010ERF = new NewZealandERF2010();
	private final static double DURATION_DEFAULT = 1;
	//
	public final static String FAULT_AND_BACK_SEIS_NAME = new String ("Background and Fault Seismicity");
	public final static String FAULT_AND_BACK_SEIS = new String ("Fault and Background Sources");
	public final static String FAULT_SEIS_ONLY = new String ("Fault Sources Only");
	public final static String BACK_SEIS_ONLY = new String ("Background Sources Only");
	private StringParameter backSeisParam;
	
	// Epistemic uncertainty consideration
	//Overall consideration
	public final static String EPISTEMIC_PARAM_NAME = "Consider Epistemic Uncertainties";
	private final static String EPISTEMIC_PARAM_INFO = "Consideration of Epistemic Uncertainties";
	private final static Boolean EPISTEMIC_PARAM_DEFAULT = new Boolean(true);
	private BooleanParameter epistemicParam;
	//in length, top and bottom
	public final static String EPISTEMIC_GEOMETRY_PARAM_NAME = "Epistemic Uncertainties in fault length, depth";
	private final static String EPISTEMIC_GEOMETRY_PARAM_INFO = "Epistemic Uncertainties in fault length, depth";
	private final static Boolean EPISTEMIC_GEOMETRY_PARAM_DEFAULT = new Boolean(true);
	private BooleanParameter epistemicGeometryParam;
	//in dip
	public final static String EPISTEMIC_DIP_PARAM_NAME = "Epistemic Uncertainties in fault dip";
	private final static String EPISTEMIC_DIP_PARAM_INFO = "Epistemic Uncertainties in fault dip";
	private final static Boolean EPISTEMIC_DIP_PARAM_DEFAULT = new Boolean(true);
	private BooleanParameter epistemicDipParam;
	//in slip rate and coupling coefficient
	public final static String EPISTEMIC_SLIP_PARAM_NAME = "Epistemic Uncertainties in fault slip rate and coupling";
	private final static String EPISTEMIC_SLIP_PARAM_INFO = "Epistemic Uncertainties in fault slip rate and coupling";
	private final static Boolean EPISTEMIC_SLIP_PARAM_DEFAULT = new Boolean(true);
	private BooleanParameter epistemicSlipParam;
	//in magnitude scaling
	public final static String EPISTEMIC_MAGSCALING_PARAM_NAME = "Epistemic Uncertainties in Magnitude Scaling Relations";
	private final static String EPISTEMIC_MAGSCALING_PARAM_INFO = "Epistemic Uncertainties in Magnitude Scaling Relations";
	private final static Boolean EPISTEMIC_MAGSCALING_PARAM_DEFAULT = new Boolean(true);
	private BooleanParameter epistemicMagScalingParam;
	//in correlation between mag scaling on different faults
	public final static String EPISTEMIC_MAGSCALINGCORRELATION_PARAM_NAME = "Correlation of Epistemic Uncertainties in Mw Scaling on faults";
	private final static String EPISTEMIC_MAGSCALINGCORRELATION_PARAM_INFO = "Correlation of Epistemic Uncertainties in Mw Scaling on faults";
	private final static double EPISTEMIC_MAGSCALINGCORRELATION_PARAM_DEFAULT = new Double(0.5);
	private DoubleParameter epistemicMagScalingCorrelationParam;
	//proportion of mag scaling uncertainty considered as aleatory 
	public final static String EPISTEMIC_MAGSCALINGPROPORTION_PARAM_NAME = "Proportion of Mw Scaling Unc considered as epistemic (remaining aleatory)";
	private final static String EPISTEMIC_MAGSCALINGPROPORTION_PARAM_INFO = "Proportion of Mw Scaling Unc considered as epistemic (remaining aleatory)";
	private final static double EPISTEMIC_MAGSCALINGPROPORTION_PARAM_DEFAULT = new Double(0.5);
	private DoubleParameter epistemicMagScalingUncertaintyProportionParam;
	
	//	 For num realizations parameter
	private final static String NUM_REALIZATIONS_PARAM_NAME ="Num Realizations";
	private Integer DEFAULT_NUM_REALIZATIONS_VAL= new Integer(100);
	private int NUM_REALIZATIONS_MIN = 1;
	private int NUM_REALIZATIONS_MAX = 10000;
	private final static String NUM_REALIZATIONS_PARAM_INFO = "Number of Monte Carlo ERF realizations";
	IntegerParameter numRealizationsParam;

	public NewZealandERF2010_Epistemic() {
		
		
		// number of Monte carlo realizations
		numRealizationsParam = new IntegerParameter(NUM_REALIZATIONS_PARAM_NAME,NUM_REALIZATIONS_MIN,
				NUM_REALIZATIONS_MAX, DEFAULT_NUM_REALIZATIONS_VAL);
		numRealizationsParam.setInfo(NUM_REALIZATIONS_PARAM_INFO);

		//Other adjustable parameters from Parent simulation
		initAdjParams();
		newZealand2010ERF.setAdjParams(adjustableParams);
		
		createParamList();
		
		// create the time-ind timespan object with start time and duration in years
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(DURATION_DEFAULT);
	}
	
	/*
	 * Initialize the adjustable parameters
	 */
	private void initAdjParams(){	
		//Background seismicity
		ArrayList<String> backSeisOptionsStrings = new ArrayList<String>();
		backSeisOptionsStrings.add(FAULT_AND_BACK_SEIS);
		backSeisOptionsStrings.add(FAULT_SEIS_ONLY);
		backSeisOptionsStrings.add(BACK_SEIS_ONLY);
		backSeisParam = new StringParameter(FAULT_AND_BACK_SEIS_NAME,backSeisOptionsStrings,FAULT_AND_BACK_SEIS);
		backSeisParam.addParameterChangeListener(this);
		
		//Consideration of epistemic unceratinties - overall
		epistemicParam = new BooleanParameter(EPISTEMIC_PARAM_NAME, EPISTEMIC_PARAM_DEFAULT);
		epistemicParam.setInfo(EPISTEMIC_PARAM_INFO);
		epistemicParam.addParameterChangeListener(this);
		//uncertainties in length, top, bottom
		epistemicGeometryParam = new BooleanParameter(EPISTEMIC_GEOMETRY_PARAM_NAME, EPISTEMIC_GEOMETRY_PARAM_DEFAULT);
		epistemicGeometryParam.setInfo(EPISTEMIC_GEOMETRY_PARAM_INFO);
		epistemicGeometryParam.addParameterChangeListener(this);
		//uncertainty in dip
		epistemicDipParam = new BooleanParameter(EPISTEMIC_DIP_PARAM_NAME, EPISTEMIC_DIP_PARAM_DEFAULT);
		epistemicDipParam.setInfo(EPISTEMIC_DIP_PARAM_INFO);
		epistemicDipParam.addParameterChangeListener(this);
		//uncertainty in slip rate and coupling
		epistemicSlipParam = new BooleanParameter(EPISTEMIC_SLIP_PARAM_NAME, EPISTEMIC_SLIP_PARAM_DEFAULT);
		epistemicSlipParam.setInfo(EPISTEMIC_SLIP_PARAM_INFO);
		epistemicSlipParam.addParameterChangeListener(this);
		//uncertainty in magnitude scaling
		epistemicMagScalingParam = new BooleanParameter(EPISTEMIC_MAGSCALING_PARAM_NAME, EPISTEMIC_MAGSCALING_PARAM_DEFAULT);
		epistemicMagScalingParam.setInfo(EPISTEMIC_MAGSCALING_PARAM_INFO);
		epistemicMagScalingParam.addParameterChangeListener(this);
		//proportion of magnitude scaling uncertainty considered as correlated between faults
		epistemicMagScalingCorrelationParam = new DoubleParameter(EPISTEMIC_MAGSCALINGCORRELATION_PARAM_NAME, EPISTEMIC_MAGSCALINGCORRELATION_PARAM_DEFAULT);
		epistemicMagScalingCorrelationParam.setInfo(EPISTEMIC_MAGSCALINGCORRELATION_PARAM_INFO);
		epistemicMagScalingCorrelationParam.addParameterChangeListener(this);
		//proportion of magnitude scaling uncertainty considered epistemic
		epistemicMagScalingUncertaintyProportionParam = new DoubleParameter(EPISTEMIC_MAGSCALINGPROPORTION_PARAM_NAME, EPISTEMIC_MAGSCALINGPROPORTION_PARAM_DEFAULT);
		epistemicMagScalingUncertaintyProportionParam.setInfo(EPISTEMIC_MAGSCALINGPROPORTION_PARAM_INFO);
		epistemicMagScalingUncertaintyProportionParam.addParameterChangeListener(this);
		
	}
	
	/**
	 * This put parameters in the ParameterList (depending on settings).
	 * This could be smarter in terms of not showing parameters if certain settings
	 */
	protected void createParamList() {
		
		adjustableParams = new ParameterList();
		
		//Parameters always displayed
		adjustableParams.addParameter(numRealizationsParam);
		adjustableParams.addParameter(backSeisParam);
		adjustableParams.addParameter(epistemicParam);
		//If Epistemic uncertainties considered display other values
		if (epistemicParam.getValue()) {
			adjustableParams.addParameter(epistemicGeometryParam);
			adjustableParams.addParameter(epistemicDipParam);
			adjustableParams.addParameter(epistemicSlipParam);
			adjustableParams.addParameter(epistemicMagScalingParam);
			if (epistemicMagScalingParam.getValue()) {
				adjustableParams.addParameter(epistemicMagScalingCorrelationParam);
				adjustableParams.addParameter(epistemicMagScalingUncertaintyProportionParam);
			}
		}
		
	}
	
	@Override
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes() {

		return newZealand2010ERF.getTectonicRegionTypes();
	}
	
	/**
	 *  This is the main function of this interface. Any time a control
	 *  paramater or independent paramater is changed by the user in a GUI this
	 *  function is called, and a paramater change event is passed in.
	 *
	 *  This sets the flag to indicate that the sources need to be updated
	 *
	 * @param  event
	 */
	public void parameterChange(ParameterChangeEvent event) {
		this.parameterChangeFlag = true;
		// Create adjustable parameter list
		createParamList();
		newZealand2010ERF.setAdjParams(adjustableParams);
	}


	/**
	 * Return the name for this class
	 *
	 * @return : return the name for this class
	 */
	public String getName(){
		return NAME;
	}


	/**
	 * get the number of Eqk Rup Forecasts in this list
	 * @return : number of eqk rup forecasts in this list
	 */
	public int getNumERFs() {
		return (Integer)numRealizationsParam.getValue();
	}
	
	/**
	 * Return the vector containing the Double values with
	 * relative weights for each ERF
	 * @return : ArrayList of Double values
	 */
	public ArrayList getRelativeWeightsList() {
		ArrayList<Double> weightList = new ArrayList<Double>();
		int numERFs = getNumERFs();
		for(int i=0; i<numERFs; ++i) weightList.add(getERF_RelativeWeight(i));
		return weightList;
	}


	/**
	 * Get the ERF in the list with the specified index. 
	 * It returns the updated forecast
	 * Index can range from 0 to getNumERFs-1
	 * 
	 * 
	 * @param index : index of Eqk rup forecast to return
	 * @return
	 */
	public ERF getERF(int index) {
		newZealand2010ERF.getTimeSpan().setDuration(this.timeSpan.getDuration());
		newZealand2010ERF.updateForecast();
		return newZealand2010ERF;
	}


	/**
	 * get the weight of the ERF at the specified index. 
	 * It always returns 1 because we are doing Monte Carlo simulations
	 * 
	 * @param index : index of ERF
	 * @return : relative weight of ERF
	 */
	public double getERF_RelativeWeight(int index) {
		return 1;
	}



	public static void main(String[] args) {
		NewZealandERF2010_Epistemic nzEpistemicList = new NewZealandERF2010_Epistemic();
		int numERFs = nzEpistemicList.getNumERFs();
		System.out.println("Num Branches="+numERFs);
		for(int i=0; i<5; ++i) {
			nzEpistemicList.getERF(i);
		}

	}
}
