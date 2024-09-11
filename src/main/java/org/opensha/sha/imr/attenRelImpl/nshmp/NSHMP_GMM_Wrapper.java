package org.opensha.sha.imr.attenRelImpl.nshmp;

import static org.opensha.commons.geo.GeoTools.TO_RAD;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.calc.GaussianDistCalc;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.earthquake.DistCachedERFWrapper.DistCacheWrapperRupture;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gcim.imr.param.EqkRuptureParams.FocalDepthParam;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RakeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupWidthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceX_Parameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.SedimentThicknessParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Gmm.Type;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;
import gov.usgs.earthquake.nshmp.gmm.GroundMotion;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;
import gov.usgs.earthquake.nshmp.tree.Branch;
import gov.usgs.earthquake.nshmp.tree.LogicTree;

/**
 * This wraps the Gmm implementations in nshmp-lib: https://code.usgs.gov/ghsc/nshmp/nshmp-lib
 * <br>
 * If supplied with the 'parameterize' flag, it will conform to the full AttenuationRelationship specification, filling
 * in relevant parameter values. If not, parameters will be skipped for computational efficiency except for site
 * parameters (those are needed so that calculators know which site parameters are required).
 * <br>
 * If the Gmm passed in is null, this can still be used to build fully populated {@link GmmInput} instances, but
 * attempts to calculate exceedance probabilities or GMM values will throw exceptions. Otherwise, {@link GmmInput}
 * instances will be built only for relevant fields.
 * 
 * @author kevin
 *
 */
public class NSHMP_GMM_Wrapper extends AttenuationRelationship implements ParameterChangeListener {
	
	public final static String C = "NSHMP_GMM_WrapperFullParam";
	
	// inputs
	private Gmm gmm;
	private String name;
	private String shortName;
	private Constraints constraints;
	private ImmutableList<Field> fieldsUsedList;
	private EnumSet<Field> fields;
	private final boolean parameterize;
	private Component component;
	
	private GroundMotionLogicTreeFilter treeFilter;
	
	// instances/caches
	// most recently used IMT, reset whenever IMT changes
	private Imt imt;
	// thi
	private FieldParameterValueManager valueManager;
	// current GmmInput, reset to null whenever anything changes
	private GmmInput gmmInput;
	private EnumMap<Imt, GroundMotionModel> instanceMap;
	private LogicTree<GroundMotion> gmTree;
	
	// if enabled, will cache GmmInputs on a per-rupture basis
	private boolean cacheInputsPerRupture = false;
	private Map<EqkRupture, GmmInput> perRuptureInputCache;
	
	// params not in parent class
	private DistanceX_Parameter distanceXParam;
	private SedimentThicknessParam zSedParam;
	
	private String defaultIMT = null;
	private Double defaultPeriod = null; // if SA
	
	public NSHMP_GMM_Wrapper(Gmm gmm) {
		this(gmm, gmm == null ? null : gmm.name());
	}
	
	public NSHMP_GMM_Wrapper(Gmm gmm, boolean parameterize) {
		this(gmm, gmm == null ? null : gmm.name(), parameterize);
	}
	
	public NSHMP_GMM_Wrapper(Gmm gmm, String shortName) {
		this(gmm, shortName, true);
	}
	
	public NSHMP_GMM_Wrapper(Gmm gmm, String shortName, boolean parameterize) {
		this(gmm, shortName, parameterize, null);
	}
	
	public NSHMP_GMM_Wrapper(Gmm gmm, String shortName, boolean parameterize, Component component) {
		this(gmm, gmm == null ? null : gmm.toString(), shortName, parameterize, component);
	}
	
	public NSHMP_GMM_Wrapper(Gmm gmm, String name, String shortName, boolean parameterize, Component component) {
		this.gmm = gmm;
		this.name = name;
		this.shortName = shortName;
		this.component = component;
		this.parameterize = parameterize;
		
		instanceMap = new EnumMap<>(Imt.class);
		
		if (gmm != null) {
			this.constraints = gmm.constraints();
			// figure out which fields are actually used by this GMM
			ImmutableList.Builder<Field> fieldsUsedListBuilder = ImmutableList.builder();
			for (Field field : Field.values()) {
				if (constraints.get(field).isPresent()) {
					// this field is used
					
					// make sure we support this field
					FieldParameterValueManager.ensureSupported(field);
					fieldsUsedListBuilder.add(field);
				}
			}
			this.fieldsUsedList = fieldsUsedListBuilder.build();
			this.fields = EnumSet.copyOf(fieldsUsedList);
		} else {
			// create inputs for all fields
			this.constraints = Constraints.defaults();
			this.fieldsUsedList = ImmutableList.copyOf(Field.values());
			this.fields = EnumSet.allOf(Field.class);
		}
		this.valueManager = new FieldParameterValueManager(this);
		
		initSupportedIntensityMeasureParams();
		initSiteParams();
		initEqkRuptureParams();
		initPropagationEffectParams();
		initOtherParams();
		
		initIndependentParamLists();
	}
	
	/**
	 * @return immutable list of {@link Field}s used by this GMM
	 */
	public List<Field> getFieldsUsed() {
		return fieldsUsedList;
	}
	
	/**
	 * @return the current nshmp-lib {@link Imt} in use
	 */
	public Imt getCurrentIMT() {
		if (imt == null) {
			// IMT has changed
			String imName = im.getName();
			if (imName.equals(SA_Param.NAME))
				imt = Imt.fromPeriod(SA_Param.getPeriodInSA_Param(im));
			else if (imName.equals(PGA_Param.NAME))
				imt = Imt.PGA;
			else if (imName.equals(PGV_Param.NAME))
				imt = Imt.PGV;
			else if (imName.equals(PGD_Param.NAME))
				imt = Imt.PGD;
			else
				throw new IllegalStateException("Unexpected IM: "+imName);
		}
		return imt;
	}
	
	private GroundMotionModel getBuildGMM(Imt imt) {
		Preconditions.checkNotNull(imt);
		GroundMotionModel gmmInstance = instanceMap.get(imt);
		if (gmmInstance == null) {
			gmmInstance = gmm.instance(imt);
			instanceMap.put(imt, gmmInstance);
		}
		return gmmInstance;
	}
	
	/**
	 * @return a {@link GroundMotionModel} instance parameterized for the current IMT
	 */
	public GroundMotionModel getCurrentGMM_Instance() {
		return getBuildGMM(getCurrentIMT());
	}
	
	/**
	 * @return a {@link GmmInput} for the current parameters, site, and rupture
	 */
	public GmmInput getCurrentGmmInput() {
		if (gmmInput == null)
			gmmInput = valueManager.getGmmInput();
		return gmmInput;
	}
	
	/**
	 * Sets the passed in {@link GmmInput} as the current input for the GMM.
	 * @param gmmInput
	 */
	public void setCurrentGmmInput(GmmInput gmmInput) {
		clearCachedGmmInputs();
		this.gmmInput = gmmInput;
		valueManager.setGmmInput(gmmInput);
	}
	
	/**
	 * Enables or disables caching of {@link GmmInput} values on a per-rupture basis; that cache will be cleared
	 * whenever a parameter is set externally, or the {@link Site} is changed.
	 */
	public void setCacheInputsPerRupture(boolean cacheInputsPerRupture) {
		this.cacheInputsPerRupture = cacheInputsPerRupture;
	}
	
	/**
	 * Copies the current per-rupture {@link GmmInput} cache from the given GMM. This is a shallow copy, so any updates
	 * made on either GMM will affect the other (until they are cleared by a site or parameter change) 
	 * @param other
	 */
	public void copyPerRuptureCacheFrom(NSHMP_GMM_Wrapper other) {
		this.perRuptureInputCache = other.perRuptureInputCache;
	}
	
	/**
	 * Set a custom {@link GroundMotionLogicTreeFilter} to filter the nshmp-haz logic tree to only contain certain elements.
	 * Useful for isolating a sub-model.
	 * @param treeFilter
	 */
	public void setGroundMotionTreeFilter(GroundMotionLogicTreeFilter treeFilter) {
		this.treeFilter = treeFilter;
		clearCachedGmmInputs();
	}
	
	/**
	 * @return the custom {@link GroundMotionLogicTreeFilter} used to filter the nshmp-haz logic tree to only contain 
	 * certain elements, if set, otherwise null.
	 */
	public GroundMotionLogicTreeFilter getGroundMotionTreeFilter() {
		return treeFilter;
	}
	
	/**
	 * @return nshmp-lib ground motion logic tree for the current IMT and inputs
	 */
	public LogicTree<GroundMotion> getGroundMotionTree() {
		if (gmTree != null)
			// already built for these inputs and IMT
			return gmTree;
		
		GroundMotionModel gmmInstance = getCurrentGMM_Instance();
		
		LogicTree<GroundMotion> gmTree = gmmInstance.calc(getCurrentGmmInput());
		if (treeFilter != null)
			gmTree = treeFilter.filter(gmTree);
		this.gmTree = gmTree;

		return gmTree;
	}

	@Override
	public double getMean() {
		LogicTree<GroundMotion> gmTree = getGroundMotionTree();
		if (gmTree.size() == 1)
			return gmTree.get(0).value().mean();
		
		double weightSum = 0d;
		double valWeightSum = 0d;
		for (Branch<GroundMotion> branch : gmTree) {
			weightSum += branch.weight();
			valWeightSum = Math.fma(branch.weight(), branch.value().mean(), valWeightSum);
		}

		if (weightSum == 1d)
			return valWeightSum;
		return valWeightSum/weightSum;
	}

	@Override
	public double getStdDev() {
		LogicTree<GroundMotion> gmTree = getGroundMotionTree();
		if (gmTree.size() == 1)
			return gmTree.get(0).value().sigma();
		
		double weightSum = 0d;
		double valWeightSum = 0d;
		for (Branch<GroundMotion> branch : gmTree) {
			weightSum += branch.weight();
			valWeightSum = Math.fma(branch.weight(), branch.value().sigma(), valWeightSum);
		}
		
		if (weightSum == 1d)
			return valWeightSum;
		return valWeightSum/weightSum;
	}
	
	@Override
	public DiscretizedFunc getExceedProbabilities(
			DiscretizedFunc intensityMeasureLevels)
			throws ParameterException {
		LogicTree<GroundMotion> gmTree = getGroundMotionTree();
		
		// compute exceedance probability based on truncation type
		final int sigmaTruncType;
		if (sigmaTruncTypeParam == null ||
				sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_NONE))
			sigmaTruncType = 0; // none
		else if (sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED))
			sigmaTruncType = 1;
		else if (sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_2SIDED))
			sigmaTruncType = 2;
		else
			throw new IllegalStateException();
		double numSig = sigmaTruncLevelParam.getValue();
		
		final int size = intensityMeasureLevels.size();
		double weightSum = 0d;
		boolean first = true;
		double weight, mean, stdDev, x, y, stRndVar;
		int i;
		
		double[] xVals, yVals;
		if (intensityMeasureLevels instanceof LightFixedXFunc) {
			xVals = ((LightFixedXFunc)intensityMeasureLevels).getXVals();
			yVals = ((LightFixedXFunc)intensityMeasureLevels).getYVals();
		} else {
			xVals = new double[size];
			yVals = new double[size];
			for (i=0; i<size; i++)
				xVals[i] = intensityMeasureLevels.getX(i);
		}
		
		for (Branch<GroundMotion> branch : gmTree) {
			weight = branch.weight();
			weightSum += weight;
			mean = branch.value().mean();
			stdDev = branch.value().sigma();
			for (i=0; i<size; i++) {
				stRndVar = (xVals[i] - mean) / stdDev;
				if (sigmaTruncType == 0)
					y = GaussianDistCalc.getExceedProb(stRndVar);
				else
					y = GaussianDistCalc.getExceedProb(stRndVar, sigmaTruncType, numSig);
				if (first)
					yVals[i] = y*weight;
				else
					yVals[i] = Math.fma(y, weight, yVals[i]);
			}
			first = false;
		}
		if (weightSum != 1d)
			for (i=0; i<size; i++)
				yVals[i] /= weightSum;
		if (!(intensityMeasureLevels instanceof LightFixedXFunc))
			// this means we didn't modify in place, need to set
			for (i=0; i<size; i++)
				intensityMeasureLevels.set(i, yVals[i]);
		
		return intensityMeasureLevels;
	}

	@Override
	public double getExceedProbability(double iml) throws ParameterException,
			IMRException {
		double weightSum = 0d;
		double weightValSum = 0d;
		for (Branch<GroundMotion> branch : getGroundMotionTree()) {
			double weight = branch.weight();
			weightSum += weight;
			double mean = branch.value().mean();
			double stdDev = branch.value().sigma();
			double prob = getExceedProbability(mean, stdDev, iml)*weight;
			weightValSum = Math.fma(prob, weight, weightValSum);
		}
		if (weightSum == 1d)
			return weightValSum;
		
		return weightValSum/weightSum;
	}

	@Override
	public double getIML_AtExceedProb() throws ParameterException {
		if (exceedProbParam.getValue() == null) {
			throw new ParameterException(C +
					": getExceedProbability(): " +
					"exceedProbParam or its value is null, unable to run this calculation."
			);
		}

		double exceedProb = ( (Double) ( (Parameter<Double>) exceedProbParam).getValue()).doubleValue();
		
		double weightSum = 0d;
		double weightValSum = 0d;
		for (Branch<GroundMotion> branch : getGroundMotionTree()) {
			double weight = branch.weight();
			weightSum += weight;
			double mean = branch.value().mean();
			double stdDev = branch.value().sigma();
			double val = getIML_AtExceedProb(mean, stdDev, exceedProb, sigmaTruncTypeParam, sigmaTruncLevelParam);
			weightValSum = Math.fma(val, weight, weightValSum);
		}
		if (weightSum == 1d)
			return weightValSum;
		
		return weightValSum/weightSum;
	}

	@Override
	public DiscretizedFunc getSA_ExceedProbSpectrum(double iml)
			throws ParameterException, IMRException {
		// TODO implement?
		throw new UnsupportedOperationException("getSA_IML_AtExceedProbSpectrum is unsupported for "+C);
	}

	@Override
	public DiscretizedFunc getSA_IML_AtExceedProbSpectrum(double exceedProb)
			throws ParameterException, IMRException {
		// TODO implement?
		throw new UnsupportedOperationException("getSA_IML_AtExceedProbSpectrum is unsupported for "+C);
	}

	@Override
	public double getTotExceedProbability(PointEqkSource ptSrc, double iml) {
		throw new UnsupportedOperationException("getTotExceedProbability is unsupported for "+C);
	}

	@Override
	protected void initSupportedIntensityMeasureParams() {
		supportedIMParams.clear();
		
		if (gmm == null)
			return;
		
		// Create SA Parameter
		Set<Imt> imts = gmm.supportedImts();
		boolean hasSA = false;
		for (Imt imt : imts) {
			if (imt.isSA()) {
				hasSA = true;
				break;
			}
		}
		if (hasSA) {
			// we support SA
			DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
			Double firstPeriod = null;
			for (Imt imt : imts) {
				if (imt.isSA()) {
					periodConstraint.addDouble(imt.period());
					if (firstPeriod == null)
						firstPeriod = imt.period();
					if (imt.period() == 1d)
						defaultPeriod = imt.period();
				}
			}
			periodConstraint.setNonEditable();

			defaultIMT = SA_Param.NAME;
			if (defaultPeriod == null)
				defaultPeriod = firstPeriod;
				
			saPeriodParam = new PeriodParam(periodConstraint, defaultPeriod, false);
			saPeriodParam.setValueAsDefault();
			saPeriodParam.addParameterChangeListener(this);
			saDampingParam = new DampingParam();
			saDampingParam.setValueAsDefault();
			saDampingParam.addParameterChangeListener(this);
			saParam = new SA_Param(saPeriodParam, saDampingParam);
			saParam.setNonEditable();
			saParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(saParam);
		}

		if (imts.contains(Imt.PGA)) {
			//  Create PGA Parameter (pgaParam):
			pgaParam = new PGA_Param();
			pgaParam.setNonEditable();
			pgaParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(pgaParam);
			
			if (defaultIMT == null)
				defaultIMT = PGA_Param.NAME;
		}

		if (imts.contains(Imt.PGV)) {
			//  Create PGV Parameter (pgvParam):
			pgvParam = new PGV_Param();
			pgvParam.setNonEditable();
			pgvParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(pgvParam);
			
			if (defaultIMT == null)
				defaultIMT = PGV_Param.NAME;
		}

		if (imts.contains(Imt.PGD)) {
			//  Create PGD Parameter (pgdParam):
			pgdParam = new PGD_Param();
			pgdParam.setNonEditable();
			pgdParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(pgdParam);
			
			if (defaultIMT == null)
				defaultIMT = PGD_Param.NAME;
		}
		
		Preconditions.checkNotNull(defaultIMT, "No supported IMTs found for %s", getName());
	}

	@Override
	protected void initSiteParams() {
		// always need these, even if unparameterized, so that calculators know that we depend on them
		vs30Param = null;
		vs30_TypeParam = null;
		depthTo1pt0kmPerSecParam = null;
		depthTo2pt5kmPerSecParam = null;
		zSedParam = null;
		
		siteParams.clear();
		if (fields.contains(Field.VS30)) {
			Range<Double> range = getConstraintRange(Field.VS30, 150d, 1500d);
			vs30Param = new Vs30_Param(safeDefault(Field.VS30, range), range.lowerEndpoint(), range.upperEndpoint());
			vs30Param.setValueAsDefault();
			siteParams.addParameter(vs30Param);
			valueManager.addParameterMapping(Field.VS30, vs30Param);
		}
		if (fields.contains(Field.Z1P0)) {
			Range<Double> range = getConstraintRange(Field.Z1P0,
					DepthTo1pt0kmPerSecParam.MIN, DepthTo1pt0kmPerSecParam.MAX);
			depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(
					safeDefault(Field.Z1P0, range, true),
					range.lowerEndpoint(), range.upperEndpoint(), true);
			depthTo1pt0kmPerSecParam.setValueAsDefault();
			siteParams.addParameter(depthTo1pt0kmPerSecParam);
			valueManager.addParameterMapping(Field.Z1P0, depthTo1pt0kmPerSecParam);
		}
		if (fields.contains(Field.Z2P5)) {
			Range<Double> range = getConstraintRange(Field.Z2P5,
					DepthTo2pt5kmPerSecParam.MIN, DepthTo2pt5kmPerSecParam.MAX);
			depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(
					safeDefault(Field.Z2P5, range, true),
					range.lowerEndpoint(), range.upperEndpoint(), true);
			depthTo2pt5kmPerSecParam.setValueAsDefault();
			siteParams.addParameter(depthTo2pt5kmPerSecParam);
			valueManager.addParameterMapping(Field.Z2P5, depthTo2pt5kmPerSecParam);
		}
		if (fields.contains(Field.ZSED)) {
			Range<Double> range = getConstraintRange(Field.ZSED);
			Double defaultValue = safeDefault(Field.ZSED, range, true);
			if (range == null)
				zSedParam = new SedimentThicknessParam(defaultValue, true);
			else
				zSedParam = new SedimentThicknessParam(defaultValue, range.lowerEndpoint(), range.upperEndpoint(), true);
			zSedParam.setValueAsDefault();
			siteParams.addParameter(zSedParam);
			valueManager.addParameterMapping(Field.ZSED, zSedParam);
		}
	}
	
	private Range<Double> getConstraintRange(Field field) {
		return getConstraintRange(field, null);
	}
	
	private Range<Double> getConstraintRange(Field field, double min, double max) {
		return getConstraintRange(field, Range.closed(min, max));
	}
	
	@SuppressWarnings("unchecked")
	private Range<Double> getConstraintRange(Field field, Range<Double> defaultRange) {
		Object constraintRange = constraints.get(field).get();
		if (constraintRange instanceof Range && ((Range<?>)constraintRange).lowerEndpoint() instanceof Double) {
			// apply any translations (e.g., units)
			Range<Double> range = (Range<Double>)constraintRange;
			range = Range.closed(FieldParameterValueManager.nshmpToOpenSHA(field, range.lowerEndpoint()),
					FieldParameterValueManager.nshmpToOpenSHA(field, range.upperEndpoint()));
			return range;
		}
		return defaultRange;
	}
	
	private double safeDefault(Field field, Range<Double> range) {
		return safeDefault(field, range, false);
	}
	
	private Double safeDefault(Field field, Range<Double> range, boolean nanAsNull) {
		if (Double.isNaN(field.defaultValue))
			return nanAsNull ? null : Double.NaN;
		if (range != null && !range.contains(field.defaultValue))
			return range.lowerEndpoint()+0.5*(range.upperEndpoint() - range.lowerEndpoint());
		return field.defaultValue;
	}

	@Override
	protected void initEqkRuptureParams() {
		eqkRuptureParams.clear();
		
		magParam = null;
		dipParam = null;
		rupWidthParam = null;
		rakeParam = null;
		rupTopDepthParam = null;
		focalDepthParam = null;
		
		if (!parameterize)
			// unused
			return;
		
		if (fields.contains(Field.MW)) {
			Range<Double> range = getConstraintRange(Field.MW, 4d, 9d);
			magParam = new MagParam(range.lowerEndpoint(), range.upperEndpoint(), safeDefault(Field.MW, range));
			magParam.setValueAsDefault();
			eqkRuptureParams.addParameter(magParam);
			valueManager.addParameterMapping(Field.MW, magParam);
		}
		
		if (fields.contains(Field.DIP)) {
			Range<Double> range = getConstraintRange(Field.DIP, 15d, 90d);
			dipParam = new DipParam(range.lowerEndpoint(), range.upperEndpoint(), safeDefault(Field.DIP, range));
			dipParam.setValueAsDefault();
			eqkRuptureParams.addParameter(dipParam);
			valueManager.addParameterMapping(Field.DIP, dipParam);
		}
		
		if (fields.contains(Field.WIDTH)) {
			Range<Double> range = getConstraintRange(Field.WIDTH, 0d, 500d);
			rupWidthParam = new RupWidthParam(range.lowerEndpoint(), range.upperEndpoint(), safeDefault(Field.WIDTH, range));
			rupWidthParam.setValueAsDefault();
			eqkRuptureParams.addParameter(rupWidthParam);
			valueManager.addParameterMapping(Field.WIDTH, rupWidthParam);
		}
		
		if (fields.contains(Field.RAKE)) {
			rakeParam = new RakeParam(Field.RAKE.defaultValue, true);
			rakeParam.setValueAsDefault();
			eqkRuptureParams.addParameter(rakeParam);
			valueManager.addParameterMapping(Field.RAKE, rakeParam);
		}
		
		if (fields.contains(Field.ZTOR)) {
			Range<Double> range = getConstraintRange(Field.ZTOR, 0d, 15d);
			rupTopDepthParam = new RupTopDepthParam(range.lowerEndpoint(), range.upperEndpoint(), safeDefault(Field.ZTOR, range));
			rupTopDepthParam.setValueAsDefault();
			eqkRuptureParams.addParameter(rupTopDepthParam);
			valueManager.addParameterMapping(Field.ZTOR, rupTopDepthParam);
		}
		
		if (fields.contains(Field.ZHYP)) {
			Range<Double> range = getConstraintRange(Field.ZHYP, 0d, 15d);
			focalDepthParam = new FocalDepthParam(range.lowerEndpoint(), range.upperEndpoint(), safeDefault(Field.ZHYP, range));
			focalDepthParam.setValueAsDefault();
			eqkRuptureParams.addParameter(focalDepthParam);
			valueManager.addParameterMapping(Field.ZHYP, focalDepthParam);
		}
	}

	@Override
	protected void initPropagationEffectParams() {
		propagationEffectParams.clear();
		
		distanceJBParam = null;
		distanceRupParam = null;
		distanceSeisParam = null;
		distanceXParam = null;
		
		if (!parameterize)
			// unused
			return;
		
		if (fields.contains(Field.RJB)) {
			Range<Double> range = getConstraintRange(Field.RJB, 0d, 400d);
			distanceJBParam = new DistanceJBParameter(
					new DoubleConstraint(range.lowerEndpoint(), range.upperEndpoint()), safeDefault(Field.RJB, range));
			distanceJBParam.setValueAsDefault();
			propagationEffectParams.addParameter(distanceJBParam);
			valueManager.addParameterMapping(Field.RJB, distanceJBParam);
		}
		
		if (fields.contains(Field.RRUP)) {
			Range<Double> range = getConstraintRange(Field.RRUP, 0d, 400d);
			distanceRupParam = new DistanceRupParameter(
					new DoubleConstraint(range.lowerEndpoint(), range.upperEndpoint()), safeDefault(Field.RRUP, range));
			distanceRupParam.setValueAsDefault();
			propagationEffectParams.addParameter(distanceRupParam);
			valueManager.addParameterMapping(Field.RRUP, distanceRupParam);
		}
		
		if (fields.contains(Field.RX)) {
			Range<Double> range = getConstraintRange(Field.RX, -400d, 400d);
			distanceXParam = new DistanceX_Parameter(
					new DoubleConstraint(range.lowerEndpoint(), range.upperEndpoint()), safeDefault(Field.RX, range));
			distanceXParam.setValueAsDefault();
			propagationEffectParams.addParameter(distanceXParam);
			valueManager.addParameterMapping(Field.RX, distanceXParam);
		}
	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		
		if (component != null) {
			componentParam = new ComponentParam(component, component);
			componentParam.setValueAsDefault();
			componentParam.addParameterChangeListener(this);
			otherParams.addParameter(componentParam);
		}
		
		if (gmm != null) {
			// tectonic region type
			Type type = gmm.type();
			if (type != null) {
				String typeStr = trtForType(type).toString();
				StringConstraint options = new StringConstraint();
				options.addString(typeStr);
				tectonicRegionTypeParam.setConstraint(options);
			    tectonicRegionTypeParam.setDefaultValue(typeStr);
			    tectonicRegionTypeParam.setValueAsDefault();
			}
		}
	}
	
	public static TectonicRegionType trtForType(Type type) {
		switch (type) {
		case ACTIVE_CRUST:
			return TectonicRegionType.ACTIVE_SHALLOW;
		case STABLE_CRUST:
			return TectonicRegionType.STABLE_SHALLOW;
		case SUBDUCTION_INTERFACE:
			return TectonicRegionType.SUBDUCTION_INTERFACE;
		case SUBDUCTION_SLAB:
			return TectonicRegionType.SUBDUCTION_SLAB;

		default:
			throw new IllegalStateException("Unexpected TRT: "+type);
		}
	}

	@Override
	public void setParamDefaults() {
		for (Parameter<?> param : siteParams)
			param.setValueAsDefault();
		for (Parameter<?> param : propagationEffectParams)
			param.setValueAsDefault();
		for (Parameter<?> param : eqkRuptureParams)
			param.setValueAsDefault();
		for (Parameter<?> param : otherParams)
			param.setValueAsDefault();
		setIntensityMeasure(defaultIMT);
		
		perRuptureInputCache = null;
		
		clearCachedGmmInputs();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public void setSite(Site site) {
		if (site != this.site)
			perRuptureInputCache = null;
		super.setSite(site);
		clearCachedGmmInputs();
		
		// the 'if (gmm != null || site.containsParameter(<param>.NAME))' checks make things work if we're using
		// this just to pre-cache GmmInputs (gmm == null) and we were passed in a site that doesn't have that parameter
		if (fields.contains(Field.VS30))
			if (gmm != null || site.containsParameter(Vs30_Param.NAME))
				valueManager.setParameterValue(Field.VS30, site.getParameter(Double.class, Vs30_Param.NAME).getValue());
		if (fields.contains(Field.Z1P0))
			if (gmm != null || site.containsParameter(DepthTo1pt0kmPerSecParam.NAME))
				valueManager.setParameterValue(Field.Z1P0, site.getParameter(Double.class, DepthTo1pt0kmPerSecParam.NAME).getValue());
		if (fields.contains(Field.Z2P5))
			if (gmm != null || site.containsParameter(DepthTo2pt5kmPerSecParam.NAME))
				valueManager.setParameterValue(Field.Z2P5, site.getParameter(Double.class, DepthTo2pt5kmPerSecParam.NAME).getValue());
		if (fields.contains(Field.ZSED))
			if (gmm != null || site.containsParameter(SedimentThicknessParam.NAME))
				valueManager.setParameterValue(Field.ZSED, site.getParameter(Double.class, SedimentThicknessParam.NAME).getValue());
		
		setPropagationEffectParams();
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		super.setEqkRupture(eqkRupture);
		clearCachedGmmInputs();
		
		boolean usePerRupCache = cacheInputsPerRupture && site != null;
		EqkRupture rupForCache = eqkRupture;
		if (usePerRupCache) {
			if (perRuptureInputCache == null)
				perRuptureInputCache = new HashMap<>();
			if (eqkRupture instanceof DistCacheWrapperRupture)
				rupForCache = ((DistCacheWrapperRupture)eqkRupture).getOriginalRupture();
			GmmInput cached = perRuptureInputCache.get(rupForCache);
			if (cached != null) {
				setCurrentGmmInput(cached);
				return;
			}
		}
		
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		
		if (fields.contains(Field.MW))
			valueManager.setParameterValue(Field.MW, eqkRupture.getMag());
		if (fields.contains(Field.RAKE))
			valueManager.setParameterValue(Field.RAKE, eqkRupture.getAveRake());
		if (fields.contains(Field.DIP))
			valueManager.setParameterValue(Field.DIP, surf.getAveDip());
		if (fields.contains(Field.WIDTH))
			valueManager.setParameterValue(Field.WIDTH, surf.getAveWidth());
		if (fields.contains(Field.ZTOR))
			valueManager.setParameterValue(Field.ZTOR, surf.getAveRupTopDepth());
		if (fields.contains(Field.ZHYP)) {
			double zHyp;
			if (eqkRupture.getHypocenterLocation() != null) {
				zHyp = eqkRupture.getHypocenterLocation().getDepth();
			} else {
				zHyp = surf.getAveRupTopDepth() +
					Math.sin(surf.getAveDip() * TO_RAD) * surf.getAveWidth()/2.0;
			}
			valueManager.setParameterValue(Field.ZHYP, zHyp);
		}
		
		setPropagationEffectParams();
		
		if (usePerRupCache) {
			// cache the GmmInput for future reuse
			perRuptureInputCache.put(rupForCache, getCurrentGmmInput());
		}
	}

	@Override
	protected void setPropagationEffectParams() {
		clearCachedGmmInputs();
		if (site != null && eqkRupture != null) {
			Location siteLoc = site.getLocation();
			RuptureSurface surf = eqkRupture.getRuptureSurface();
			
			if (fields.contains(Field.RJB))
				valueManager.setParameterValue(Field.RJB, surf.getDistanceJB(siteLoc));
			if (fields.contains(Field.RRUP))
				valueManager.setParameterValue(Field.RRUP, surf.getDistanceRup(siteLoc));
			if (fields.contains(Field.RX))
				valueManager.setParameterValue(Field.RX, surf.getDistanceX(siteLoc));
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == saPeriodParam || event.getParameter() == saPeriodParam) {
			clearCachedImt();
		} else {
			clearCachedGmmInputs();
			perRuptureInputCache = null; // this will only be called when set externally, clear any per-rupture input cache
		}
	}
	
	@Override
	public void setIntensityMeasure(String intensityMeasureName) throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		clearCachedImt();
	}
	
	private void clearCachedImt() {
		imt = null;
		gmTree = null;
	}
	
	private void clearCachedGmmInputs() {
		gmmInput = null;
		gmTree = null;
	}
	
	protected void initIndependentParamLists() {
		// assume that mean/std dev/exceed probs depend on pretty much everything

		// params that the mean depends upon
		meanIndependentParams.clear();
		if (parameterize) {
			if (fields.contains(Field.RRUP))
				meanIndependentParams.addParameter(distanceRupParam);
			if (fields.contains(Field.RJB))
				meanIndependentParams.addParameter(distanceJBParam);
			if (fields.contains(Field.RX))
				meanIndependentParams.addParameter(distanceXParam);
			
			if (fields.contains(Field.VS30))
				meanIndependentParams.addParameter(vs30Param);
			if (fields.contains(Field.Z2P5))
				meanIndependentParams.addParameter(depthTo2pt5kmPerSecParam);
			if (fields.contains(Field.Z1P0))
				meanIndependentParams.addParameter(depthTo1pt0kmPerSecParam);
			if (fields.contains(Field.ZSED))
				meanIndependentParams.addParameter(zSedParam);
			
			if (fields.contains(Field.MW))
				meanIndependentParams.addParameter(magParam);
			if (fields.contains(Field.RAKE))
				meanIndependentParams.addParameter(rakeParam);
			if (fields.contains(Field.DIP))
				meanIndependentParams.addParameter(dipParam);
			if (fields.contains(Field.ZTOR))
				meanIndependentParams.addParameter(rupTopDepthParam);
			if (fields.contains(Field.WIDTH))
				meanIndependentParams.addParameter(rupWidthParam);
			if (fields.contains(Field.ZHYP))
				meanIndependentParams.addParameter(focalDepthParam);
		}
		if (componentParam != null)
			meanIndependentParams.addParameter(componentParam);

		// params that the stdDev depends upon
		// assume the same as mean (likely a subset in reality, but we don't have that info)
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameterList(meanIndependentParams);

		// params that the exceed. prob. depends upon
		// assume the same as mean (likely a subset in reality, but we don't have that info)
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(stdDevIndependentParams);
		// add sigma truncation options
		exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		// assume the same as exceed params
		imlAtExceedProbIndependentParams.addParameterList(
				exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
	}
	
}
