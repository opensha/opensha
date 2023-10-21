package org.opensha.sha.imr.attenRelImpl.nshmp;

import static java.lang.Math.sin;
import static org.opensha.commons.geo.GeoTools.TO_RAD;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.util.FaultUtils;
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
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceX_Parameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.SedimentThicknessParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.Gmm.Type;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Builder;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Field;
import gov.usgs.earthquake.nshmp.tree.Branch;
import gov.usgs.earthquake.nshmp.tree.LogicTree;
import gov.usgs.earthquake.nshmp.gmm.GroundMotion;
import gov.usgs.earthquake.nshmp.gmm.GroundMotionModel;
import gov.usgs.earthquake.nshmp.gmm.Imt;

/**
 * This wraps Peter's NGA implementation to conform to the AttenuationRelationship construct
 * 
 * @author kevin
 *
 */
public class NSHMP_GMM_WrapperFullParam extends AttenuationRelationship implements ParameterChangeListener {
	
	public final static String C = "NSHMP_GMM_WrapperFullParam";
	
	// inputs
	private Gmm gmm;
	private String shortName;
	private Constraints constraints;
	private EnumSet<Field> fields;
	
	// instances
	private Imt imt;
	private GmmInput gmmInput;
	private EnumMap<Imt, GroundMotionModel> instanceMap;
	private LogicTree<GroundMotion> gmTree;
	
	// params not in parent class
	private DistanceX_Parameter distanceXParam;
	private SedimentThicknessParam zSedParam;
	
	/**
	 * Supported fields. Currently all except:
	 * Currently all of them, but may be useful if/when new fields are added
	 */
	private static final EnumSet<Field> SUPPORTED_FIELDS = EnumSet.of(
			Field.DIP, Field.MW, Field.RAKE, Field.RJB, Field.RRUP, Field.RX, Field.VS30, Field.WIDTH, Field.Z1P0,
			Field.Z2P5, Field.ZHYP, Field.ZSED, Field.ZTOR);
	
	private String defaultIMT = null;
	
	public NSHMP_GMM_WrapperFullParam(Gmm gmm) {
		this(gmm, gmm.name());
	}
	
	public NSHMP_GMM_WrapperFullParam(Gmm gmm, String shortName) {
		this.gmm = gmm;
		this.shortName = shortName;
		this.constraints = gmm.constraints();
		
		instanceMap = new EnumMap<>(Imt.class);
		
		// figure out which fields are actually used by this GMM
		List<Field> fieldsUsed = new ArrayList<>();
		for (Field field : Field.values()) {
			if (constraints.get(field).isPresent()) {
				// this field is used
				
				// make sure we support this field
				Preconditions.checkState(SUPPORTED_FIELDS.contains(field),
						"Field '%s' used by '%s' is not supported by this wrapper", field, gmm);
				fieldsUsed.add(field);
			}
		}
		this.fields = EnumSet.copyOf(fieldsUsed);
		
		initSupportedIntensityMeasureParams();
		initSiteParams();
		initEqkRuptureParams();
		initPropagationEffectParams();
		initOtherParams();
		
		initIndependentParamLists();
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
	
	public synchronized LogicTree<GroundMotion> getGroundMotionTree() {
		if (gmTree != null)
			// already built for these inputs
			return gmTree;
		
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
		GroundMotionModel gmmInstance = getBuildGMM(imt);
		
		if (gmmInput == null) {
			// an input has changed, rebuild
			Builder inputBuilder = GmmInput.builder().withDefaults();
			
			// rup params
			if (fields.contains(Field.RAKE))
				inputBuilder.rake(rakeParam.getValue());
			if (fields.contains(Field.MW))
				inputBuilder.mag(magParam.getValue());
			if (fields.contains(Field.RJB))
				inputBuilder.rJB(distanceJBParam.getValue());
			if (fields.contains(Field.RRUP))
				inputBuilder.rRup(distanceRupParam.getValue());
			if (fields.contains(Field.RX))
				inputBuilder.rX(distanceXParam.getValue());
			if (fields.contains(Field.DIP))
				inputBuilder.dip(dipParam.getValue());
			if (fields.contains(Field.WIDTH))
				inputBuilder.width(rupWidthParam.getValue());
			if (fields.contains(Field.ZTOR))
				inputBuilder.zTor(rupTopDepthParam.getValue());
			if (fields.contains(Field.ZHYP))
				inputBuilder.zHyp(focalDepthParam.getValue());
			
			// site params
			if (fields.contains(Field.VS30))
				inputBuilder.vs30(vs30Param.getValue());
			if (fields.contains(Field.Z1P0)) {
				Double val = depthTo1pt0kmPerSecParam.getValue();
				if (val == null)
					val = Double.NaN;
				else
					val *= 1e-3; // m -> km
				inputBuilder.z1p0(val);
			}
			if (fields.contains(Field.Z2P5)) {
				Double val = depthTo2pt5kmPerSecParam.getValue();
				if (val == null)
					val = Double.NaN;
				inputBuilder.z2p5(val);
			}
			if (fields.contains(Field.ZSED)) {
				Double val = zSedParam.getValue();
				if (val == null)
					val = Double.NaN;
				inputBuilder.zSed(val);
			}
			gmmInput = inputBuilder.build();
		}
		
		gmTree = gmmInstance.calc(gmmInput);

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
			valWeightSum += branch.weight()*branch.value().mean();
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
			valWeightSum += branch.weight()*branch.value().sigma();
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
		if (gmTree.size() == 1)
			return super.getExceedProbabilities(intensityMeasureLevels);
		
		double weightSum = 0d;
		boolean first = true;
		for (Branch<GroundMotion> branch : gmTree) {
			double weight = branch.weight();
			weightSum += weight;
			double mean = branch.value().mean();
			double stdDev = branch.value().sigma();
			for (int i=0; i<intensityMeasureLevels.size(); i++) {
				double x = intensityMeasureLevels.getX(i);
				double y = getExceedProbability(mean, stdDev, x)*weight;
				if (first)
					intensityMeasureLevels.set(i, y);
				else
					intensityMeasureLevels.set(i, intensityMeasureLevels.getY(i) + y);
			}
			first = false;
		}
		if (weightSum != 1d)
			intensityMeasureLevels.scale(1d/weightSum);
		
		return intensityMeasureLevels;
	}

	@Override
	public double getExceedProbability(double iml) throws ParameterException,
			IMRException {
		double weightSum = 0d;
		double weightValSum = 0d;
		for (Branch<GroundMotion> branch : gmTree) {
			double weight = branch.weight();
			weightSum += weight;
			double mean = branch.value().mean();
			double stdDev = branch.value().sigma();
			double prob = getExceedProbability(mean, stdDev, iml)*weight;
			weightValSum += prob*weight;
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

		double exceedProb = ( (Double) ( (Parameter) exceedProbParam).getValue()).doubleValue();
		
		double weightSum = 0d;
		double weightValSum = 0d;
		for (Branch<GroundMotion> branch : gmTree) {
			double weight = branch.weight();
			weightSum += weight;
			double mean = branch.value().mean();
			double stdDev = branch.value().sigma();
			double val = getIML_AtExceedProb(mean, stdDev, exceedProb, sigmaTruncTypeParam, sigmaTruncLevelParam);
			weightValSum += val*weight;
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
		
		// Create saParam:
		Set<Imt> imts = gmm.supportedImts();
		boolean hasSA = false;
		for (Imt imt : imts) {
			if (imt.isSA()) {
				hasSA = true;
				break;
			}
		}
		if (hasSA) {
			
			DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
			for (Imt imt : imts) {
				if (imt.isSA())
					periodConstraint.addDouble(imt.period());
			}
			periodConstraint.setNonEditable();
			saPeriodParam = new PeriodParam(periodConstraint);
			saPeriodParam.setValueAsDefault();
			saPeriodParam.addParameterChangeListener(this);
			saDampingParam = new DampingParam();
			saDampingParam.setValueAsDefault();
			saDampingParam.addParameterChangeListener(this);
			saParam = new SA_Param(saPeriodParam, saDampingParam);
			saParam.setNonEditable();
			saParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(saParam);
			
			defaultIMT = SA_Param.NAME;
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
		siteParams.clear();
		if (fields.contains(Field.VS30)) {
			Range<Double> range = getConstraintRange(Field.VS30, 150d, 1500d);
			vs30Param = new Vs30_Param(Field.VS30.defaultValue, range.lowerEndpoint(), range.upperEndpoint());
			siteParams.addParameter(vs30Param);
		}
		if (fields.contains(Field.Z1P0)) {
			// they use km, we use m
			Range<Double> range = getConstraintRange(Field.Z1P0,
					DepthTo1pt0kmPerSecParam.MIN*1e-3, DepthTo1pt0kmPerSecParam.MAX*1e-3);
			depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(
					Double.isNaN(Field.Z1P0.defaultValue) ? null : Field.Z1P0.defaultValue,
					range.lowerEndpoint()*1e3, range.upperEndpoint()*1e3, true);
			siteParams.addParameter(depthTo1pt0kmPerSecParam);
		}
		if (fields.contains(Field.Z2P5)) {
			Range<Double> range = getConstraintRange(Field.Z2P5,
					DepthTo2pt5kmPerSecParam.MIN, DepthTo2pt5kmPerSecParam.MAX);
			depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(
					Double.isNaN(Field.Z2P5.defaultValue) ? null : Field.Z2P5.defaultValue,
					range.lowerEndpoint(), range.upperEndpoint(), true);
			siteParams.addParameter(depthTo2pt5kmPerSecParam);
		}
		
		if (fields.contains(Field.ZSED)) {
			Range<Double> range = getConstraintRange(Field.ZSED);
			Double defaultValue = Double.isNaN(Field.ZSED.defaultValue) ? null : Field.ZSED.defaultValue;
			if (range == null)
				zSedParam = new SedimentThicknessParam(defaultValue, true);
			else
				zSedParam = new SedimentThicknessParam(defaultValue, range.lowerEndpoint(), range.upperEndpoint(), true);
			siteParams.addParameter(zSedParam);
		}
		
		for (Parameter<?> param : siteParams)
			param.addParameterChangeListener(this);
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
		if (constraintRange instanceof Range && ((Range<?>)constraintRange).lowerEndpoint() instanceof Double)
			return (Range<Double>)constraintRange;
		return defaultRange;
	}

	@Override
	protected void initEqkRuptureParams() {
		eqkRuptureParams.clear();
		
		if (fields.contains(Field.MW)) {
			Range<Double> range = getConstraintRange(Field.MW, 4d, 9d);
			magParam = new MagParam(range.lowerEndpoint(), range.upperEndpoint(), Field.MW.defaultValue);
			eqkRuptureParams.addParameter(magParam);
		}
		
		if (fields.contains(Field.DIP)) {
			Range<Double> range = getConstraintRange(Field.DIP, 15d, 90d);
			dipParam = new DipParam(range.lowerEndpoint(), range.upperEndpoint(), Field.DIP.defaultValue);
			eqkRuptureParams.addParameter(dipParam);
		}
		
		if (fields.contains(Field.WIDTH)) {
			Range<Double> range = getConstraintRange(Field.WIDTH, 0d, 500d);
			rupWidthParam = new RupWidthParam(range.lowerEndpoint(), range.upperEndpoint(), Field.WIDTH.defaultValue);
			eqkRuptureParams.addParameter(rupWidthParam);
		}
		
		if (fields.contains(Field.RAKE)) {
			rakeParam = new RakeParam(Field.RAKE.defaultValue, true);
			eqkRuptureParams.addParameter(rakeParam);
		}
		
		if (fields.contains(Field.ZTOR)) {
			Range<Double> range = getConstraintRange(Field.ZTOR, 0d, 15d);
			rupTopDepthParam = new RupTopDepthParam(range.lowerEndpoint(), range.upperEndpoint(), Field.ZTOR.defaultValue);
			eqkRuptureParams.addParameter(rupTopDepthParam);
		}
		
		if (fields.contains(Field.ZHYP)) {
			Range<Double> range = getConstraintRange(Field.ZHYP, 0d, 15d);
			focalDepthParam = new FocalDepthParam(range.lowerEndpoint(), range.upperEndpoint(), Field.ZHYP.defaultValue);
			eqkRuptureParams.addParameter(focalDepthParam);
		}
		
		for (Parameter<?> param : eqkRuptureParams)
			param.addParameterChangeListener(this);
	}

	@Override
	protected void initPropagationEffectParams() {
		propagationEffectParams.clear();
		
		if (fields.contains(Field.RJB)) {
			Range<Double> range = getConstraintRange(Field.RJB, 0d, 400d);
			distanceJBParam = new DistanceJBParameter(
					new DoubleConstraint(range.lowerEndpoint(), range.upperEndpoint()), Field.RJB.defaultValue);
			propagationEffectParams.addParameter(distanceJBParam);
		}
		
		if (fields.contains(Field.RRUP)) {
			Range<Double> range = getConstraintRange(Field.RRUP, 0d, 400d);
			distanceRupParam = new DistanceRupParameter(
					new DoubleConstraint(range.lowerEndpoint(), range.upperEndpoint()), Field.RRUP.defaultValue);
			propagationEffectParams.addParameter(distanceRupParam);
		}
		
		if (fields.contains(Field.RX)) {
			Range<Double> range = getConstraintRange(Field.RX, -400d, 400d);
			distanceXParam = new DistanceX_Parameter(
					new DoubleConstraint(range.lowerEndpoint(), range.upperEndpoint()), Field.RX.defaultValue);
			propagationEffectParams.addParameter(distanceXParam);
		}
		
		for (Parameter<?> param : propagationEffectParams)
			param.addParameterChangeListener(this);
	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		
		componentParam = new ComponentParam(Component.RotD50, Component.RotD50);
		componentParam.setValueAsDefault();
		componentParam.addParameterChangeListener(this);
		otherParams.addParameter(componentParam);
		
		// tectonic region type
		Type type = gmm.type();
		if (type != null) {
			String typeStr;
			switch (type) {
			case ACTIVE_CRUST:
				typeStr = TectonicRegionType.ACTIVE_SHALLOW.toString();
				break;
			case STABLE_CRUST:
				typeStr = TectonicRegionType.STABLE_SHALLOW.toString();
				break;
			case SUBDUCTION_INTERFACE:
				typeStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
				break;
			case SUBDUCTION_SLAB:
				typeStr = TectonicRegionType.SUBDUCTION_SLAB.toString();
				break;

			default:
				throw new IllegalStateException("Unexpected TRT: "+type);
			}
			StringConstraint options = new StringConstraint();
			options.addString(typeStr);
			tectonicRegionTypeParam.setConstraint(options);
		    tectonicRegionTypeParam.setDefaultValue(typeStr);
		    tectonicRegionTypeParam.setValueAsDefault();
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
	}

	@Override
	public String getName() {
		return gmm.toString();
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public void setSite(Site site) {
		super.setSite(site);
		if (fields.contains(Field.VS30))
			this.vs30Param.setValueIgnoreWarning(site.getParameter(Double.class, Vs30_Param.NAME).getValue());
		if (fields.contains(Field.Z1P0))
			this.depthTo1pt0kmPerSecParam.setValueIgnoreWarning(site.getParameter(Double.class,
					DepthTo1pt0kmPerSecParam.NAME).getValue());
		if (fields.contains(Field.Z2P5))
			this.depthTo2pt5kmPerSecParam.setValueIgnoreWarning(site.getParameter(Double.class,
					DepthTo2pt5kmPerSecParam.NAME).getValue());
		if (fields.contains(Field.ZSED))
			this.zSedParam.setValueIgnoreWarning(site.getParameter(Double.class, SedimentThicknessParam.NAME).getValue());
		
		setPropagationEffectParams();
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		super.setEqkRupture(eqkRupture);
		
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		
		if (fields.contains(Field.MW))
			magParam.setValueIgnoreWarning(eqkRupture.getMag());
		if (fields.contains(Field.RAKE))
			rakeParam.setValue(eqkRupture.getAveRake());
		if (fields.contains(Field.DIP))
			dipParam.setValueIgnoreWarning(surf.getAveDip());
		double width = surf.getAveWidth();
		if (width == 0d)
			width = 0.1; // must be positive
		if (fields.contains(Field.WIDTH))
			rupWidthParam.setValueIgnoreWarning(width);
		if (fields.contains(Field.ZTOR))
			rupTopDepthParam.setValueIgnoreWarning(surf.getAveRupTopDepth());
		if (fields.contains(Field.ZHYP)) {
			double zHyp;
			if (eqkRupture.getHypocenterLocation() != null) {
				zHyp = eqkRupture.getHypocenterLocation().getDepth();
			} else {
				zHyp = surf.getAveRupTopDepth() +
					Math.sin(surf.getAveDip() * TO_RAD) * width/2.0;
			}
			focalDepthParam.setValueIgnoreWarning(zHyp);
		}
		
		setPropagationEffectParams();
	}

	@Override
	protected void setPropagationEffectParams() {
		if (site != null && eqkRupture != null) {
			Location siteLoc = site.getLocation();
			RuptureSurface surf = eqkRupture.getRuptureSurface();
			
			if (fields.contains(Field.RJB))
				distanceJBParam.setValueIgnoreWarning(surf.getDistanceJB(siteLoc));
			if (fields.contains(Field.RRUP))
				distanceRupParam.setValueIgnoreWarning(surf.getDistanceRup(siteLoc));
			if (fields.contains(Field.RX))
				distanceXParam.setValueIgnoreWarning(surf.getDistanceX(siteLoc));
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		gmmInput = null;
		gmTree = null;
	}
	
	@Override
	public void setIntensityMeasure(String intensityMeasureName) throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		imt = null;
		gmTree = null;
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
		meanIndependentParams.addParameter(componentParam);

		// params that the stdDev depends upon TODO
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameterList(meanIndependentParams);

		// params that the exceed. prob. depends upon TODO
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(stdDevIndependentParams);
		exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		imlAtExceedProbIndependentParams.addParameterList(
				exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
	}
	
}
