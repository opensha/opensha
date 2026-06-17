package org.opensha.sha.imr.attenRelImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

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
import gov.usgs.earthquake.nshmp.tree.LogicTree.Builder;

/**
 * Experimental GMM for joint crustal-subduction ruptures. Currently assumes hardcoded crustal and interface magnitude
 * scaling as well as fixed GMMs.
 */
public class JointRuptureExperimentalIMR extends NSHMP_GMM_Wrapper {
	
	public static final String NAME = "Joint Rupture Experimental IMR";
	public static final String SHORT_NAME = "JointRupExperimentIMR";
	
	public static final Gmm DEFAULT_CRUSTAL_GMM = Gmm.ASK_14_BASE;
	public static final Gmm DEFAULT_INTERFACE_GMM = Gmm.PSBAH_20_GLOBAL_INTERFACE_NO_EPI;

	public static final String CRUSTAL_GMM_PROP_NAME = "JointRupCrustalGMM";
	public static final String INTERFACE_GMM_PROP_NAME = "JointRupInterfaceGMM";
	
	private Gmm crustalGMM;
	private EnumMap<Imt, GroundMotionModel> crustalInstanceMap;
	private Gmm interfaceGMM;
	private EnumMap<Imt, GroundMotionModel> interfaceInstanceMap;
	
	private Constraints[] allConstraints;
	
	public JointRuptureExperimentalIMR() {
		this(getOrDefault(CRUSTAL_GMM_PROP_NAME, DEFAULT_CRUSTAL_GMM), getOrDefault(INTERFACE_GMM_PROP_NAME, DEFAULT_INTERFACE_GMM));
	}
	
	private static Gmm getOrDefault(String propertyName, Gmm defaultValue) {
		String propVal = System.getProperty(propertyName);
		if (propVal == null)
			return defaultValue;
		return Gmm.valueOf(propertyName);
	}
	
	public JointRuptureExperimentalIMR(Gmm crustalGMM, Gmm interfaceGMM) {
		super(NAME, SHORT_NAME, false, null);
		this.crustalGMM = crustalGMM;
		this.crustalInstanceMap = new EnumMap<>(Imt.class);
		this.interfaceGMM = interfaceGMM;
		this.interfaceInstanceMap = new EnumMap<>(Imt.class);
		
		allConstraints = new Constraints[] {
				crustalGMM.constraints(),
				interfaceGMM.constraints()
		};
		
		init();
	}

	@Override
	public boolean isTectonicRegionSupported(TectonicRegionType tectRegion) {
		return tectRegion == null || tectRegion == TectonicRegionType.ACTIVE_SHALLOW
				|| tectRegion == TectonicRegionType.SUBDUCTION_INTERFACE;
	}

	@Override
	public Type getType() {
		// type is TRT, return null (multiple)
		return null;
	}
	
	private GroundMotionModel getBuildGMM(Gmm gmm, EnumMap<Imt, GroundMotionModel> instanceMap, Imt imt) {
		Preconditions.checkNotNull(imt);
		GroundMotionModel gmmInstance = instanceMap.get(imt);
		if (gmmInstance == null) {
			gmmInstance = gmm.instance(imt);
			instanceMap.put(imt, gmmInstance);
		}
		return gmmInstance;
	}

	@Override
	protected LogicTree<GroundMotion> buildGroundMotionTree() {
		GmmInput origInput = getCurrentGmmInput();
		
		EqkRupture eqkRup = getEqkRupture();
		Preconditions.checkNotNull(eqkRup);
		RuptureSurface surf = eqkRup.getRuptureSurface();
		
		double origMag = eqkRup.getMag();
		
		GmmInput crustalInput = null;
		GmmInput interfaceInput = null;
		
		if (surf instanceof CompoundSurface) {
			// possibly joint
			CompoundSurface cSurf = (CompoundSurface)surf;
			List<? extends RuptureSurface> surfs = cSurf.getSurfaceList();
			List<? extends FaultSection> sects = cSurf.getSectionsList();
			Preconditions.checkNotNull(sects);
			
			List<RuptureSurface> crustalSurfs = null;
			List<FaultSection> crustalSects = null;
			
			List<RuptureSurface> interfaceSurfs = null;
			List<FaultSection> interfaceSects = null;
			
			for (int i=0; i<surfs.size(); i++) {
				RuptureSurface subSurf = surfs.get(i);
				FaultSection subSect = sects.get(i);
				TectonicRegionType trt = subSect.getTectonicRegionType();
				switch (trt) {
				case ACTIVE_SHALLOW:
					if (crustalSurfs == null) {
						crustalSurfs = new ArrayList<>();
						crustalSects = new ArrayList<>();
					}
					crustalSurfs.add(subSurf);
					crustalSects.add(subSect);
					break;
				case SUBDUCTION_INTERFACE:
					if (interfaceSurfs == null) {
						interfaceSurfs = new ArrayList<>();
						interfaceSects = new ArrayList<>();
					}
					interfaceSurfs.add(subSurf);
					interfaceSects.add(subSect);
					break;

				default:
					throw new IllegalStateException("Unexpected sub-surf TRT: "+trt);
				}
			}
			
			if (crustalSects == null) {
				// all interface
				interfaceInput = origInput;
			} else if (interfaceSects == null) {
				// all crustal
				crustalInput = origInput;
			} else {
				// joint
//				System.out.println("Calculating for joint rupture; input="+origInput);
				EqkRupture crustalRup = null;
				EqkRupture interfaceRup = null;
				double crustalArea = Double.NaN;
				double interfaceArea = Double.NaN;
				for (boolean crustal : new boolean[] {true,false}) {
					List<RuptureSurface> mySurfs;
					List<FaultSection> mySects;
					if (crustal) {
						mySurfs = crustalSurfs;
						mySects = crustalSects;
					} else {
						mySurfs = interfaceSurfs;
						mySects = interfaceSects;
					}
					double[] sectAreas = new double[mySects.size()];
					double[] sectRakes = new double[mySects.size()];
					double sumArea = 0d;
					for (int i=0; i<sectAreas.length; i++) {
						FaultSection mySect = mySects.get(i);
						RuptureSurface mySurf = mySurfs.get(i);
						sectAreas[i] = mySurf.getArea();
						sectRakes[i] = mySect.getAveRake();
						sumArea += sectAreas[i];
					}
					double subRake = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(sectAreas, sectRakes));
					double subMag = crustal ? getCrustalMag(sumArea) : getInterfaceMag(sumArea);
//					System.out.println("SubArea="+sumArea+" for "+mySects.size()+" sects, mag="+subMag+", crustal="+crustal);
					RuptureSurface subSurf = mySurfs.size() == 1 ? mySurfs.get(0) : CompoundSurface.get(mySurfs, mySects);
					EqkRupture subRup = new EqkRupture(subMag, subRake, subSurf, null);
					if (crustal) {
						crustalRup = subRup;
						crustalArea = sumArea;
					} else {
						interfaceRup = subRup;
						interfaceArea = sumArea;
					}
				}
				
				double calcJointMag = getJointMag(crustalArea, interfaceArea);
//				System.out.println("JointMag="+calcJointMag+", OrigMag="+origMag+", diff="+(calcJointMag - origMag));
				double fractMagDiff = Math.abs(calcJointMag - origMag)/origMag;
				Preconditions.checkState(fractMagDiff < 0.05,
						"Calculated jointMag=%s differs by more than 5% from ERF jointMag=%s, "
						+ "bailing because the ERF isn't compatible with our assumptions", calcJointMag, origMag);
				setEqkRupture(crustalRup);
				crustalInput = getCurrentGmmInput();
				setEqkRupture(interfaceRup);
				interfaceInput = getCurrentGmmInput();
				// set back to the original rupture
				setEqkRupture(eqkRup);
			}
		} else {
			// either point surface, or single-section
			// must be fully one or othe other
			double area = surf.getArea(); // km^2
			Preconditions.checkState(Double.isFinite(area) && area > 0d,
					"Can't determine single-sect TRT without valid area; area=%s", area);
			double magIfCrustal = getCrustalMag(area);
			double magIfInterface = getCrustalMag(area);
			boolean crustal = Math.abs(origMag - magIfCrustal) < Math.abs(origMag - magIfInterface);
			if (crustal)
				crustalInput = origInput;
			else
				interfaceInput = origInput;
		}
		Imt imt = getCurrentIMT();
		LogicTree<GroundMotion> crustalTree = null;
		if (crustalInput != null)
			crustalTree = getBuildGMM(crustalGMM, crustalInstanceMap, imt).calc(crustalInput);
		LogicTree<GroundMotion> interfaceTree = null;
		if (interfaceInput != null)
			interfaceTree = getBuildGMM(interfaceGMM, interfaceInstanceMap, imt).calc(interfaceInput);
		
		setCurrentGmmInput(origInput);
		
		if (crustalTree == null)
			return interfaceTree;
		if (interfaceTree == null)
			return crustalTree;
		// we have both, convolve
		Builder<GroundMotion> builder = LogicTree.builder("joint-gmms");
		for (Branch<GroundMotion> crustalBranch : crustalTree) {
			for (Branch<GroundMotion> interfaceBranch : interfaceTree) {
				double weight = crustalBranch.weight() * interfaceBranch.weight();
				String id = "joint-"+crustalBranch.id()+"-"+interfaceBranch.id();
				
				GroundMotion value = calcJointGroundMotion(crustalBranch.value(), interfaceBranch.value());
				builder.addBranch(id, value, weight);
			}
		}
		
		return builder.build();
	}
	
//	private static final double RHO = 0.5;
	private static final double RHO = 1;
	
	/**
	 * Combines separate crustal and subduction-interface GMM results into a single
	 * joint-rupture distribution.
	 *
	 *   median:  SRSS of the linear (median) ground motions
	 *   sigma:   energy-weighted combination of component sigmas with an assumed
	 *            effective residual correlation rho
	 *
	 *     w_i   = m_i^2 / (m1^2 + m2^2)            (fractional power contribution)
	 *     s_J^2 = w1^2*s1^2 + w2^2*s2^2 + 2*rho*w1*w2*s1*s2
	 *
	 * rho ~ 0.5 is the suggested base ("shared event + shared site, independent
	 * paths"); bracket with 0.3 / 0.7 for sensitivity.
	 */
	public static GroundMotion calcJointGroundMotion(GroundMotion crustalGM, GroundMotion interfaceGM) {
		double mc = Math.exp(crustalGM.mean());
		double mi = Math.exp(interfaceGM.mean());
		double mc2 = mc * mc;
		double mi2 = mi * mi;
		double power = mc2 + mi2;
		
		double sc = crustalGM.sigma();
		double si = interfaceGM.sigma();

		// SRSS median; guard the degenerate zero-motion case.
		double linearMean = Math.sqrt(power);
		Preconditions.checkState(linearMean > 0d, "linearMean=%s for crustal=%s and interface=%s", linearMean, crustalGM, interfaceGM);
		double lnMean = Math.log(linearMean);

		// Energy (squared-median) weights; these slide with IM, M, R.
		double wc = mc2 / power;
		double wi = mi2 / power;

//		double varJ = wc * wc * sc * sc
//				+ wi * wi * si * si
//				+ 2.0 * RHO * wc * wi * sc * si;
//
//		// First-order var is non-negative for rho in [-1,1]; clamp for safety.
//		double sigma = Math.sqrt(Math.max(varJ, 0.0));
		
		double sigma = wc * sc + wi * si;
		GroundMotion jointGM = GroundMotion.create(lnMean, sigma);
		System.out.println("JointGM:\t"+jointGM);
		System.out.println("\tCrustal:\t"+crustalGM);
		System.out.println("\tInterface:\t"+interfaceGM);
		System.out.println();
		return jointGM;
	}
	
	public static double getCrustalMag(double area_km) {
		return Math.log10(area_km) + 4.2;
	}
	
	public static double getInterfaceMag(double area_km) {
		return Math.log10(area_km) + 4.0;
	}
	
	private static final double CRUSTAL_LOG_SCALAR = Math.pow(10, 4.2);
	private static final double INTERFACE_LOG_SCALAR = Math.pow(10, 4.0);
	public static double getJointMag(double crustalArea_km, double interfaceArea_km) {
		return Math.log10(crustalArea_km * CRUSTAL_LOG_SCALAR + interfaceArea_km * INTERFACE_LOG_SCALAR);
	}

	@Override
	protected Set<Imt> getSupportedIMTs() {
		HashSet<Imt> imts = new HashSet<>(crustalGMM.supportedImts());
		imts.retainAll(interfaceGMM.supportedImts());
		Preconditions.checkState(!imts.isEmpty(), "No common IMTs supported by both crustal and interface GMMs");
		return imts;
	}

	@Override
	protected ImmutableList<Field> initFieldsUsed() {
		ImmutableList.Builder<Field> builder = ImmutableList.builder();
		for (Field field : Field.values()) {
			for (Constraints constraints : allConstraints) {
				if (constraints.get(field).isPresent()) {
					// this field is used by at least one Gmm
					
					builder.add(field);
					break;
				}
			}
		}
		return builder.build();
	}

	@Override
	protected Object getCustomConstraintRange(Field field) {
		// return the first match
		for (Constraints constraints : allConstraints) {
			Optional<?> optional = constraints.get(field);
			if (optional.isPresent())
				return optional.get();
		}
		throw new IllegalStateException("No Gmms use field "+field);
	}
	
	public static void main(String[] args) throws IOException {
		Site site = new Site(new Location(-41.3, 174.8));
		JointRuptureExperimentalIMR gmm = new JointRuptureExperimentalIMR();
		gmm.setParamDefaults();
		gmm.setIntensityMeasure(PGA_Param.NAME);
		site.addParameterList(gmm.getSiteParams());
		
		BaseFaultSystemSolutionERF erf = new BaseFaultSystemSolutionERF();
		erf.setSolution(FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nz_nshm/joint_ruptures/2026_06-tests/inversionSolution.zip")));
		erf.updateForecast();
		
		ArbitrarilyDiscretizedFunc xVals = new IMT_Info().getDefaultHazardCurve(gmm.getIntensityMeasure());
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<xVals.size(); i++)
			logXVals.set(Math.log(xVals.getX(i)), 0d);
		
		HazardCurveCalculator calc = new HazardCurveCalculator();
		
		calc.getHazardCurve(logXVals, site, gmm, erf);
	}

}
