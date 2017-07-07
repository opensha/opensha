package org.opensha.sha.imr.attenRelImpl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParamLinker;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.EnumConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.commons.param.impl.WeightedListParameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.gui.beans.IMT_NewGuiBean;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.IA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.MMI_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class MultiIMR_Averaged_AttenRel extends AttenuationRelationship {
	
	public static final String NAME = "Averaged Multi IMR";
	public static final String SHORT_NAME = "MultiIMR";
	
	private static final String C = ClassUtils.getClassNameWithoutPackage(MultiIMR_Averaged_AttenRel.class);
	
	private static final boolean D = false;
	
	private List<? extends ScalarIMR> imrs;
	private WeightedList<ScalarIMR> weights;
	
	public static final String IMR_WEIGHTS_PARAM_NAME = "IMR Weights";
	private WeightedListParameter<ScalarIMR> weightsParam;
	
	public MultiIMR_Averaged_AttenRel(List<? extends ScalarIMR> imrs) {
		this(imrs, null);
	}
	
	public MultiIMR_Averaged_AttenRel(List<? extends ScalarIMR> imrs,
			ArrayList<Double> weights) {
		
		if (imrs == null)
			throw new NullPointerException("imrs cannot be null!");
		if (imrs.size() == 0)
			throw new IllegalArgumentException("imrs must contain at least one IMR");
		
		if (D) System.out.println(SHORT_NAME+": const called with " + imrs.size() + " imrs");
		if (D)
			for (ScalarIMR imr : imrs)
				System.out.println(" * " + imr.getName());
		
		this.imrs = imrs;
		setWeights(weights);

		initSupportedIntensityMeasureParams();
		initEqkRuptureParams();
		initPropagationEffectParams();
		initSiteParams();
		initOtherParams();
		initIndependentParamLists(); // Do this after the above
	}
	
	public void setWeights(ArrayList<Double> newWeights) {
		if (weights == null) {
			weights = new WeightedList<ScalarIMR>();
			for (ScalarIMR imr : imrs)
				weights.add(imr, 1.0d);
		}
		if (newWeights == null) {
			weights.normalize();
		} else {
			weights.setWeights(newWeights);
		}
		if (weightsParam != null && weightsParam.isParameterEditorBuilt()) {
			weightsParam.getEditor().refreshParamEditor();
		}
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("MultiIMR\n");
		for (int i=0; i<imrs.size(); i++)
			s.append("\t").append(imrs.get(i).getShortName()).append(": ").append(weights.getWeight(i)).append("\n");
		return s.toString();
	}

	@Override
	protected void initEqkRuptureParams() {
		// do nothing // TODO validate this assumption
	}

	@Override
	protected void initPropagationEffectParams() {
		// do nothing // TODO validate this assumption
	}

	@Override
	protected void initSiteParams() {
		HashMap<String, ArrayList<ScalarIMR>> paramNameIMRMap =
			new HashMap<String, ArrayList<ScalarIMR>>();
		for (ScalarIMR imr : imrs) {
			ListIterator<Parameter<?>> siteParamsIt = imr.getSiteParamsIterator();
			while (siteParamsIt.hasNext()) {
				Parameter<?> siteParam = siteParamsIt.next();
				String name = siteParam.getName();
				if (!paramNameIMRMap.containsKey(name))
					paramNameIMRMap.put(name, new ArrayList<ScalarIMR>());
				ArrayList<ScalarIMR> imrsForParam = paramNameIMRMap.get(name);
				imrsForParam.add(imr);
			}
		}
		siteParams.clear();
		
		for (String paramName : paramNameIMRMap.keySet()) {
			if (D) System.out.println(SHORT_NAME+": initializing site param: " + paramName);
			// if it's a special case, lets use the param we already have
			ArrayList<ScalarIMR> imrs = paramNameIMRMap.get(paramName);
			Object defaultVal = imrs.get(0).getParameter(paramName).getDefaultValue();
			if (defaultVal == null)
				defaultVal = imrs.get(0).getParameter(paramName).getValue();
			Parameter masterParam = imrs.get(0).getParameter(paramName);
//			if (paramName.equals(Vs30_Param.NAME)) {
//				vs30Param = masterParam;
//			} else if (paramName.equals(Vs30_TypeParam.NAME)) {
//				vs30_TypeParam = new Vs30_TypeParam();
//				masterParam = vs30_TypeParam;
//			} else if (paramName.equals(DepthTo2pt5kmPerSecParam.NAME)) {
//				depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam();
//				masterParam = depthTo2pt5kmPerSecParam;
//			} else if (paramName.equals(DepthTo1pt0kmPerSecParam.NAME)) {
//				depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam();
//				masterParam = depthTo1pt0kmPerSecParam;
//			} else {
//				// it's a custom param not in the atten rel abstract class
//				if (D) System.out.println(SHORT_NAME+": " + paramName + " is a custom param!");
//			}
			
			for (int i=1; i<imrs.size(); i++) {
				ScalarIMR imr = imrs.get(i);
				Parameter imrParam = imr.getParameter(paramName);
				trySetDefault(defaultVal, imrParam);
				// link the master param to this imr's param
				new ParamLinker(masterParam, imrParam);
			}
			siteParams.addParameter(masterParam);
		}
	}
	
	/**
	 * This will remove any parameters from the given ParameterList which do not exist in the given iterator.
	 * If params is null (such as the first call for this method), all params will be added to the list.
	 * 
	 * @param params
	 * @param it
	 * @return
	 */
	private static ParameterList removeNonCommonParams(ParameterList params, ListIterator<Parameter<?>> it) {
		if (params == null) {
			params = new ParameterList();
			while (it.hasNext())
				params.addParameter(it.next());
			return params;
		}
		
		ParameterList paramsToKeep = new ParameterList();
		while (it.hasNext()) {
			Parameter<?> param = it.next();
			paramsToKeep.addParameter(param);
		}
		ParameterList paramsToRemove = new ParameterList();
		
		for (Parameter<?> param : params) {
			if (!paramsToKeep.containsParameter(param))
				paramsToRemove.addParameter(param);
		}
		
		for (Parameter<?> param : paramsToRemove)
			params.removeParameter(param);
		
		return params;
	}

	@Override
	protected void initSupportedIntensityMeasureParams() {
		ParameterList imrTempList = null;
		for (ScalarIMR imr : imrs) {
			imrTempList = removeNonCommonParams(imrTempList, imr.getSupportedIntensityMeasuresIterator());
		}
		
		saPeriodParam = null;
		saDampingParam = null;
		ArrayList<Double> commonPeriods = null;
		
		if (imrTempList.containsParameter(SA_Param.NAME) || imrTempList.containsParameter(SA_InterpolatedParam.NAME)) {
			saDampingParam = new DampingParam();
			saDampingParam.setNonEditable();
		}
		
		if (imrTempList.containsParameter(SA_Param.NAME)) {
			commonPeriods = IMT_NewGuiBean.getCommonPeriods(imrs);
			if (D) System.out.println(SHORT_NAME+": " + commonPeriods.size() + " common periods found!");
			if (commonPeriods.size() == 0) {
				System.err.println("WARNING: All IMRS have SA, but no common periods! Skipping SA.");
				imrTempList.removeParameter(SA_Param.NAME);
			} else {
				DoubleDiscreteConstraint periodList = new DoubleDiscreteConstraint(commonPeriods);
				Double defaultPeriod = 1.0;
				if (!periodList.isAllowed(defaultPeriod))
					defaultPeriod = periodList.getAllowedDoubles().get(0);
				saPeriodParam = new PeriodParam(periodList, defaultPeriod, false);
				saPeriodParam.setValueAsDefault();
			}
		}
		
		supportedIMParams.clear();
		// now init the params
		for (Parameter<?> imrParam : imrTempList) {
			String name = imrParam.getName();
			if (D) System.out.println(SHORT_NAME+": initializing IM param: " + name);
			if (name.equals(PGA_Param.NAME)) {
				pgaParam = new PGA_Param();
				pgaParam.setNonEditable();
				supportedIMParams.addParameter(pgaParam);
			} else if (name.equals(PGV_Param.NAME)) {
				pgvParam = new PGV_Param();
				pgvParam.setNonEditable();
				supportedIMParams.addParameter(pgvParam);
			} else if (name.equals(PGD_Param.NAME)) {
				pgdParam = new PGD_Param();
				pgdParam.setNonEditable();
				supportedIMParams.addParameter(pgdParam);
			} else if (name.equals(MMI_Param.NAME)) {
				MMI_Param mmiParam = new MMI_Param();
				mmiParam.setNonEditable();
				supportedIMParams.addParameter(mmiParam);
			} else if (name.equals(IA_Param.NAME)) {
				IA_Param iaParam = new IA_Param();
				iaParam.setNonEditable();
				supportedIMParams.addParameter(iaParam);
			} else if (name.equals(SA_Param.NAME)) {
				saParam = new SA_Param(saPeriodParam, saDampingParam);
				saParam.setNonEditable();
				supportedIMParams.addParameter(saParam);
				for (ScalarIMR imr : imrs) {
					Parameter<Double> imrPeriodParam = imr.getParameter(PeriodParam.NAME);
					trySetDefault(saPeriodParam, imrPeriodParam);
					new ParamLinker<Double>(saPeriodParam, imrPeriodParam);
				}
			} else if (name.equals(SA_InterpolatedParam.NAME)) {
				double greatestMin = Double.MIN_VALUE;
				double smallestMax = Double.MAX_VALUE;
				for (ScalarIMR imr: imrs) {
					SA_InterpolatedParam interParam = 
						(SA_InterpolatedParam)imr.getParameter(SA_InterpolatedParam.NAME);
					try {
						double min = interParam.getPeriodInterpolatedParam().getMin();
						double max = interParam.getPeriodInterpolatedParam().getMax();
						if (min > greatestMin)
							greatestMin = min;
						if (max < smallestMax)
							smallestMax = max;
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (smallestMax <= greatestMin)
					throw new RuntimeException("Period ranges don't overlap for interpolated SA");
				double defaultPeriod = 1.0;
				if (defaultPeriod < greatestMin || defaultPeriod > smallestMax)
					defaultPeriod = greatestMin;
				PeriodInterpolatedParam periodInterpParam =
					new PeriodInterpolatedParam(greatestMin, smallestMax,
							defaultPeriod, false);
				periodInterpParam.setValueAsDefault();
				for (ScalarIMR imr : imrs) {
					Parameter<Double> imrPeriodParam = imr.getParameter(PeriodInterpolatedParam.NAME);
					trySetDefault(periodInterpParam, imrPeriodParam);
					new ParamLinker<Double>(periodInterpParam, imrPeriodParam);
				}
				SA_InterpolatedParam saInterParam = new SA_InterpolatedParam(periodInterpParam, saDampingParam);
				supportedIMParams.addParameter(saInterParam);
			} else {
				throw new RuntimeException(SHORT_NAME+" cannot yet handle param of type '" + name + "'");
			}
		}
	}
	
	/**
	 * This creates the lists of independent parameters that the various dependent
	 * parameters (mean, standard deviation, exceedance probability, and IML at
	 * exceedance probability) depend upon. NOTE: these lists do not include anything
	 * about the intensity-measure parameters or any of thier internal
	 * independentParamaters.
	 */
	protected void initIndependentParamLists() {

		// params that the mean depends upon
		meanIndependentParams.clear();
		for (Parameter<?> siteParam : siteParams) {
			meanIndependentParams.addParameter(siteParam);
		}
		if (componentParam != null)
			meanIndependentParams.addParameter(componentParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		if (stdDevTypeParam != null)
			stdDevIndependentParams.addParameter(stdDevTypeParam);
		if (componentParam != null)
			stdDevIndependentParams.addParameter(componentParam);

		// params that the exceed. prob. depends upon
		exceedProbIndependentParams.clear();
		for (Parameter<?> siteParam : siteParams) {
			exceedProbIndependentParams.addParameter(siteParam);
		}
		if (componentParam != null)
			exceedProbIndependentParams.addParameter(componentParam);
		if (stdDevTypeParam != null)
			exceedProbIndependentParams.addParameter(stdDevTypeParam);
		if (sigmaTruncTypeParam != null)
			exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		if (sigmaTruncLevelParam != null)
			exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		imlAtExceedProbIndependentParams.addParameterList(
				exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);

	}
	
	private static void trySetDefault(Parameter master, Parameter child) {
		trySetDefault(master.getDefaultValue(), child);
	}
	
	private static void trySetDefault(Object defaultVal, Parameter param) {
		try {
			param.setDefaultValue(defaultVal);
		} catch (EditableException e) {}
	}

	@Override
	protected void initOtherParams() {
		 // we actually shouldn't call this here. Instead just use the params from the IMRs.
//		super.initOtherParams();
		// link up default params
//		linkParams(otherParams);
		
		weightsParam = new WeightedListParameter<ScalarIMR>(IMR_WEIGHTS_PARAM_NAME, null);
		weightsParam.setValue(weights);
		otherParams.addParameter(weightsParam);
		
		HashMap<String, ArrayList<Parameter<?>>> newParams = new HashMap<String, ArrayList<Parameter<?>>>();
		// now gather new params from IMRs
		for (ScalarIMR imr : imrs) {
			for (Parameter<?> param : imr.getOtherParams()) {
				if (otherParams.containsParameter(param))
					continue;
				if (!newParams.containsKey(param.getName()))
					newParams.put(param.getName(), new ArrayList<Parameter<?>>());
				ArrayList<Parameter<?>> params = newParams.get(param.getName());
				params.add(param);
				
//				if (componentParam == null && param instanceof ComponentParam) {
//					componentParam = (ComponentParam) param;
//				}
//				if (stdDevTypeParam == null && param instanceof StdDevTypeParam) {
//					stdDevTypeParam = (StdDevTypeParam) param;
//				}
//				newParams.addParameter(param);
//				otherParams.addParameter(param);
			}
		}
		for (String paramName : newParams.keySet()) {
			ArrayList<Parameter<?>> params = newParams.get(paramName);
			// if string constraint we need a constraint with all common values
			StringConstraint sconst = null;
			String sDefault = null;
			// likewise if enum constraint we need constraint with all common values
			List<? extends Enum> enumVals = null;
			Enum enumDefault = null;
			String nullOption = null;
			Parameter masterParam = params.get(0);
			if (params.size() > 1) {
				// this param is common to multiple IMRs
				if (params.get(0) instanceof StringParameter) {
					// hack to make string params consistant
					boolean allCommon = true;
					ArrayList<String> commonVals = null;
					for (Parameter<?> param : params) {
						StringConstraint sconst_temp = (StringConstraint)param.getConstraint();
						ArrayList<String> myVals = sconst_temp.getAllowedValues();
						if (commonVals == null)
							commonVals = myVals;
						for (int i=commonVals.size()-1; i>=0; i--) {
							String commonVal = commonVals.get(i);
							if (!myVals.contains(commonVal)) {
								// this param isn't common after all
								allCommon = false;
								commonVals.remove(i);
							}
						}
						allCommon = allCommon && (commonVals.size() == myVals.size());
					}
					if (!allCommon) {
						if (D) System.out.println("Param '"+paramName+"' has "+commonVals.size()+" common vals");
						if (D)
							for (String val : commonVals)
								System.out.println(" * " + val);
						if (commonVals.size() == 0)
							continue;
						sconst = new StringConstraint(commonVals);
						sDefault = (String) masterParam.getDefaultValue();
						if (sDefault == null || !sconst.isAllowed(sDefault))
							sDefault = commonVals.get(0);
						if (D) System.out.println("NEW DEFAULT: " + sDefault);
					}
					// end string hack
				} else if (params.get(0) instanceof EnumParameter<?>) {
					nullOption = ((EnumParameter<?>)params.get(0)).getNullOption();
					// hack to make enum constraint
					boolean allCommon = true;
					List commonVals = null;
					for (Parameter<?> param : params) {
						EnumConstraint<?> econst_temp = (EnumConstraint<?>)param.getConstraint();
						// clear null option if not common
						if (nullOption != null && !nullOption.equals(((EnumParameter<?>)param).getNullOption()))
							nullOption = null;
						List myVals = econst_temp.getAllowedValues();
						if (commonVals == null)
							commonVals = Lists.newArrayList(myVals);
						for (int i=commonVals.size()-1; i>=0; i--) {
							Object commonVal = commonVals.get(i);
							if (!myVals.contains(commonVal)) {
								// this param isn't common after all
								allCommon = false;
								commonVals.remove(i);
							}
						}
						allCommon = allCommon && (commonVals.size() == myVals.size());
					}
					if (!allCommon) {
						if (D) System.out.println("Param '"+paramName+"' has "+commonVals.size()+" common vals");
						if (D)
							for (Object val : commonVals)
								System.out.println(" * " + val);
						if (commonVals.size() == 0)
							continue;
						enumVals = commonVals;
						enumDefault = (Enum) masterParam.getDefaultValue();
						if (!commonVals.contains(enumDefault))
							enumDefault= enumVals.get(0);
						if (D) System.out.println("NEW DEFAULT: " + enumDefault);
					}
					// end enum hack
				}
			}
			if (masterParam instanceof ComponentParam) {
				if (enumVals != null)
					masterParam = new ComponentParam((Component)enumDefault, (List<Component>)enumVals);
				componentParam = (ComponentParam) masterParam;
			} else if (masterParam instanceof StdDevTypeParam) {
				if (sconst != null)
					if (masterParam.isEditable())
						masterParam.setConstraint(sconst);
					else
						masterParam = new StdDevTypeParam(sconst, sDefault);
				stdDevTypeParam = (StdDevTypeParam) masterParam;
			} else if (sconst != null) {
				if (masterParam.isEditable())
					masterParam.setConstraint(sconst);
				else {
					StringParameter newSParam = new StringParameter(masterParam.getName(), sconst,
						masterParam.getUnits(), sDefault);
					masterParam = newSParam;
				}
			}
			if (sDefault != null)
				masterParam.setValue(sDefault);
			masterParam.setValueAsDefault();
			for (Parameter<?> param : params) {
				if (masterParam == param)
					continue;
				new ParamLinker(masterParam, param);
			}
			otherParams.addParameter(masterParam);
		}
	}
	
	private void linkParams(Iterable<Parameter<?>> params) {
		for (Parameter masterParam : params) {
			masterParam.setValueAsDefault();
			for (ScalarIMR imr : imrs) {
				try {
					Parameter imrParam = imr.getParameter(masterParam.getName());
					trySetDefault(masterParam, imrParam);
					// link them
					new ParamLinker(masterParam, imrParam);
				} catch (ParameterException e) {
					// this imr doesn't have it
					continue;
				}
			}
		}
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		// Set the eqkRupture
		this.eqkRupture = eqkRupture;
		for (ScalarIMR imr : imrs) {
			imr.setEqkRupture(eqkRupture);
		}
	}

	@Override
	public void setSite(Site site) {
		this.site = site;
		for (Parameter param : siteParams) {
			if (param instanceof WarningDoubleParameter)
				((WarningDoubleParameter)param).setValueIgnoreWarning((Double)site.getParameter(param.getName()).getValue());
			else
				param.setValue(site.getParameter(param.getName()).getValue());
		}
		for (ScalarIMR imr : imrs) {
			imr.setSite(site);
		}
	}

	@Override
	protected void setPropagationEffectParams() {
		// do nothing // TODO validate this assumption
		throw new UnsupportedOperationException("setPropagationEffectParams is not supported by "+C);
	}

	@Override
	public void setIntensityMeasure(Parameter intensityMeasure)
			throws ParameterException, ConstraintException {
		super.setIntensityMeasure(intensityMeasure);
		for (ScalarIMR imr : imrs) {
			imr.setIntensityMeasure(intensityMeasure);
		}
	}

	@Override
	public void setIntensityMeasure(String intensityMeasureName)
			throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		for (ScalarIMR imr : imrs) {
			imr.setIntensityMeasure(intensityMeasureName);
		}
	}
	
	/**
	 * Returns true if the IMR at index i can be skipped during calculation (zero weight)
	 * 
	 * @param i index of the IMR to check
	 * @return true if it can be skipped (zero weight)
	 */
	private boolean canSkipIMR(ScalarIMR imr) {
		return canSkipIMR(imrs.indexOf(imr));
	}
	
	/**
	 * Returns true if the IMR at index i can be skipped during calculation (zero weight)
	 * 
	 * @param i index of the IMR to check
	 * @return true if it can be skipped (zero weight)
	 */
	private boolean canSkipIMR(int i) {
		return weights.getWeight(i) == 0;
	}
	
	private double getWeightedValue(double[] vals) {
		if (!weights.isNormalized()) {
			weights.normalize();
			if (weightsParam != null && weightsParam.isParameterEditorBuilt())
				weightsParam.getEditor().refreshParamEditor();
		}
		double weighted = weights.getWeightedAverage(vals);
		if (D && Double.isNaN(weighted)) {
			System.out.println("Got a NaN!");
			for (int i=0; i<vals.length; i++) {
				System.out.println(imrs.get(i).getShortName() + ": " + vals[i]);
				if (Double.isNaN(vals[i]))
					System.out.println(imrs.get(i).getAllParamMetadata());
			}
			throw new RuntimeException();
		}
		return weighted;
	}

	@Override
	public double getMean() {
		double[] means = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			means[i] = imr.getMean();
		}
		return getWeightedValue(means);
	}

	@Override
	public double getStdDev() {
		double[] std = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			std[i] = imr.getStdDev();
		}
		return getWeightedValue(std);
	}

	@Override
	public double getEpsilon() {
		double[] vals = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			vals[i] = imr.getEpsilon();
		}
		return getWeightedValue(vals);
	}

	@Override
	public double getEpsilon(double iml) {
		double[] vals = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			vals[i] = imr.getEpsilon(iml);
		}
		return getWeightedValue(vals);
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(
			DiscretizedFunc intensityMeasureLevels)
			throws ParameterException {
		ArrayList<DiscretizedFunc> funcs = new ArrayList<DiscretizedFunc>();
		for (ScalarIMR imr : imrs) {
			if (canSkipIMR(imr)) {
				funcs.add(null);
			} else {
				funcs.add(imr.getExceedProbabilities((DiscretizedFunc)intensityMeasureLevels.deepClone()));
			}
		}
		for (int i=0; i<intensityMeasureLevels.size(); i++) {
			double[] vals = new double[imrs.size()];
			for (int j=0; j<funcs.size(); j++) {
				DiscretizedFunc func = funcs.get(j);
				if (func != null)
					vals[j] = func.getY(i);
			}
			intensityMeasureLevels.set(i, getWeightedValue(vals));
		}
		return intensityMeasureLevels;
	}

	@Override
	public double getExceedProbability() throws ParameterException,
			IMRException {
		// all IMLs should be the same
		Double iml = null;
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			Double myIML = (Double)imr.getIntensityMeasureLevel();
			Preconditions.checkNotNull(myIML, "Sub IMR has null IML");
			if (iml == null)
				iml = myIML;
			else
				Preconditions.checkState(iml.equals(myIML), "IML mismatch: %s != %s", iml, myIML);
		}
//		double iml = (Double) getIntensityMeasure().getValue();
		return getExceedProbability(iml);
	}

	@Override
	protected double getExceedProbability(double mean, double stdDev, double iml)
			throws ParameterException, IMRException {
		// TODO implement ??
		throw new UnsupportedOperationException("getExceedProbability(mean, stdDev, iml) is unsupported for "+C);
	}

	@Override
	public double getExceedProbability(double iml) throws ParameterException,
			IMRException {
		double[] vals = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			vals[i] = imr.getExceedProbability(iml);
		}
		return getWeightedValue(vals);
	}

	@Override
	public double getIML_AtExceedProb() throws ParameterException {
		double[] vals = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			vals[i] = imr.getIML_AtExceedProb();
		}
		return getWeightedValue(vals);
	}

	@Override
	public double getIML_AtExceedProb(double exceedProb)
			throws ParameterException {
		double[] vals = new double[imrs.size()];
		for (int i=0; i<imrs.size(); i++) {
			if (canSkipIMR(i))
				continue;
			ScalarIMR imr = imrs.get(i);
			vals[i] = imr.getIML_AtExceedProb(exceedProb);
		}
		return getWeightedValue(vals);
	}

	@Override
	public DiscretizedFunc getSA_ExceedProbSpectrum(double iml)
			throws ParameterException, IMRException {
		// TODO implement
		throw new UnsupportedOperationException("getSA_IML_AtExceedProbSpectrum is unsupported for "+C);
	}

	@Override
	public DiscretizedFunc getSA_IML_AtExceedProbSpectrum(double exceedProb)
			throws ParameterException, IMRException {
		// TODO implement
		throw new UnsupportedOperationException("getSA_IML_AtExceedProbSpectrum is unsupported for "+C);
	}

	@Override
	public double getTotExceedProbability(PointEqkSource ptSrc, double iml) {
		throw new UnsupportedOperationException("getTotExceedProbability is unsupported for "+C);
	}

	@Override
	public void setIntensityMeasureLevel(Double iml) throws ParameterException {
		for (ScalarIMR imr : imrs) {
			imr.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasureLevel(Object iml) throws ParameterException {
		for (ScalarIMR imr : imrs) {
			imr.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setSiteLocation(Location loc) {
		for (ScalarIMR imr : imrs) {
			imr.setSiteLocation(loc);
		}
	}

	@Override
	public void setUserMaxDistance(double maxDist) {
		for (ScalarIMR imr : imrs) {
			imr.setUserMaxDistance(maxDist);
		}
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void setParamDefaults() {
		if (weightsParam.getValue() == null)
			weightsParam.setValue(weights);
//			throw new IllegalStateException("weights param value can't be null!");
		for (ScalarIMR imr : imrs) {
			imr.setParamDefaults();
		}
	}

}
