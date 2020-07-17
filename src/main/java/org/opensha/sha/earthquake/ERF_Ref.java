package org.opensha.sha.earthquake;

import static org.opensha.commons.util.DevStatus.DEPRECATED;
import static org.opensha.commons.util.DevStatus.DEVELOPMENT;
import static org.opensha.commons.util.DevStatus.EXPERIMENTAL;
import static org.opensha.commons.util.DevStatus.PRODUCTION;

import java.lang.reflect.Constructor;
import java.util.EnumSet;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.opensha.commons.util.DevStatus;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.sha.earthquake.rupForecastImpl.FloatingPoissonFaultERF;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceERF;
import org.opensha.sha.earthquake.rupForecastImpl.PoissonFaultERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.NSHMP_CEUS08.NSHMP08_CEUS_ERF;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_AreaForecast;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_LogicTreeERF_List;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_MultiSourceForecast;
import org.opensha.sha.earthquake.rupForecastImpl.PEER_TestCases.PEER_NonPlanarFaultForecast;
import org.opensha.sha.earthquake.rupForecastImpl.Point2MultVertSS_Fault.Point2MultVertSS_FaultERF;
import org.opensha.sha.earthquake.rupForecastImpl.Point2MultVertSS_Fault.Point2MultVertSS_FaultERF_List;
import org.opensha.sha.earthquake.rupForecastImpl.WG02.WG02_ERF_Epistemic_List;
import org.opensha.sha.earthquake.rupForecastImpl.WG02.WG02_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WG02.WG02_FortranWrappedERF_EpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF1.WGCEP_UCERF1_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeIndependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.YuccaMountain.YuccaMountainERF;
import org.opensha.sha.earthquake.rupForecastImpl.YuccaMountain.YuccaMountainERF_List;
import org.opensha.sha.earthquake.rupForecastImpl.step.STEP_AlaskanPipeForecast;

import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.erf.UCERF3_CompoundSol_ERF;
import scratch.UCERF3.erf.epistemic.UCERF3EpistemicListERF;
import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt1;

public enum ERF_Ref {
	
	
	/* 
	 * ********************
	 * LOCAL ERFS
	 * ********************
	 */
	
	// PRODUCTION
	
	/** Frankel/USGS 1996 Adjustable ERF */
	FRANKEL_ADJUSTABLE_96(Frankel96_AdjustableEqkRupForecast.class,
			Frankel96_AdjustableEqkRupForecast.NAME, PRODUCTION, false),
	
	// should we include this?
			//			erf_Classes.add(POINT_SRC_TO_LINE_ERF_CLASS_NAME);
			//			erf_Classes.add(POINT_SRC_TO_LINE_ERF_LIST_TEST_CLASS_NAME);
	
	/** Frankel/USGS 1996 ERF */
	FRANKEL_96(Frankel96_EqkRupForecast.class,
			Frankel96_EqkRupForecast.NAME, PRODUCTION, false),

	/** Frankel/USGS 2002 Adjustable ERF */
	FRANKEL_02(Frankel02_AdjustableEqkRupForecast.class,
			Frankel02_AdjustableEqkRupForecast.NAME, PRODUCTION, false),
	
	/** WGCEP 2002 ERF */
	WGCEP_02(WG02_EqkRupForecast.class, WG02_EqkRupForecast.NAME, PRODUCTION, false),
	
	/** WGCEP 2002 ERF Epistemic List */
	WGCEP_02_LIST(WG02_ERF_Epistemic_List.class, WG02_ERF_Epistemic_List.NAME, PRODUCTION, true),
	
	/** WGCEP 2002 Fortran Wrapped ERF */
	WGCEP_02_WRAPPED_LIST(WG02_FortranWrappedERF_EpistemicList.class,
			WG02_FortranWrappedERF_EpistemicList.NAME, PRODUCTION, true),
	
	/** WGCEP UCERF 1 */
	WGCEP_UCERF_1(WGCEP_UCERF1_EqkRupForecast.class, WGCEP_UCERF1_EqkRupForecast.NAME, PRODUCTION, false),

	/** PEER Area Forecast */
	PEER_AREA(PEER_AreaForecast.class, PEER_AreaForecast.NAME, PRODUCTION, false),

	/** PEER Non Planar Fault Forecast */
	PEER_NON_PLANAR_FAULT(PEER_NonPlanarFaultForecast.class, PEER_NonPlanarFaultForecast.NAME, PRODUCTION, false),

	/** PEER Multi Source Forecast */
	PEER_MULTI_SOURCE(PEER_MultiSourceForecast.class, PEER_MultiSourceForecast.NAME, PRODUCTION, false),

	/** PEER Logic Tree Forecast */
	PEER_LOGIC_TREE(PEER_LogicTreeERF_List.class, PEER_LogicTreeERF_List.NAME, PRODUCTION, false),

	// include this?
		//erf_Classes.add(STEP_FORECAST_CLASS_NAME);
	
	/** Floating Poisson Fault ERF */
	POISSON_FLOATING_FAULT(FloatingPoissonFaultERF.class, FloatingPoissonFaultERF.NAME, PRODUCTION, false),
	
	/** Poisson Fault ERF */
	POISSON_FAULT(PoissonFaultERF.class, PoissonFaultERF.NAME, PRODUCTION, false),
	
	/**  Point Source ERF */
	POINT_SOURCE(PointSourceERF.class, PointSourceERF.NAME, PRODUCTION, false),
	
	/**  Point Source Multi Vert ERF */
	POINT_SOURCE_MULTI_VERT(Point2MultVertSS_FaultERF.class, Point2MultVertSS_FaultERF.NAME, PRODUCTION, false),

	/**  Point Source Multi Vert ERF */
	POINT_SOURCE_MULTI_VERT_LIST(Point2MultVertSS_FaultERF_List.class,
			Point2MultVertSS_FaultERF_List.NAME, PRODUCTION, true),

	/** WGCEP UCERF 2 ERF */
	UCERF_2(UCERF2.class, UCERF2.NAME, PRODUCTION, false),
	
	/** WGCEP UCERF 2 Time Independent Epistemic List */
	UCERF_2_TIME_INDEP_LIST(UCERF2_TimeIndependentEpistemicList.class,
			UCERF2_TimeIndependentEpistemicList.NAME, PRODUCTION, true),
	
	/** WGCEP Mean UCERF 2 */
	MEAN_UCERF_2(MeanUCERF2.class, MeanUCERF2.NAME, PRODUCTION, false),
	
	/** WGCEP Mean UCERF 2 */
	MEAN_UCERF_2_Mod(ModMeanUCERF2_FM2pt1.class, ModMeanUCERF2_FM2pt1.NAME, PRODUCTION, false),
	
	/** Fault System Solution ERF */
	INVERSION_SOLUTION_ERF(FaultSystemSolutionERF.class, FaultSystemSolutionERF.NAME,
			PRODUCTION, false),
	
	/** WGCEP Mean UCERF 3 */
	MEAN_UCERF3(MeanUCERF3.class, MeanUCERF3.NAME, PRODUCTION, false),
	
	/** WGCEP Single Branch UCERF 3 */
	UCERF3_COMPOUND(UCERF3_CompoundSol_ERF.class, UCERF3_CompoundSol_ERF.NAME, PRODUCTION, false),

	/** Yucca Mountain ERF */
	YUCCA_MOUNTAIN(YuccaMountainERF.class, YuccaMountainERF.NAME, PRODUCTION, false),
	
	/** Yucca Mountain ERF List */
	YUCCA_MOUNTAIN_LIST(YuccaMountainERF_List.class, YuccaMountainERF_List.NAME, PRODUCTION, true),
	
	// DEVELOPMENT
	
	/** WGCEP UCERF3 Epistemic List */
	UCERF3_EPISTEMIC(UCERF3EpistemicListERF.class, UCERF3EpistemicListERF.NAME, DEVELOPMENT, true),
	
	/** NSHMP CEUS 2008 ERF */
	NSHMP_CEUS_08(NSHMP08_CEUS_ERF.class, NSHMP08_CEUS_ERF.NAME, DEVELOPMENT, false),
	
	/** STEP Alaska Forecast */
	STEP_ALASKA(STEP_AlaskanPipeForecast.class, STEP_AlaskanPipeForecast.NAME, DEVELOPMENT, false);
		
	// EXPERIMENTAL
		
//	TEST_ETAS1_ERF(TestModel1_ERF.class, TestModel1_ERF.NAME, EXPERIMENTAL, false);

	
	private Class<? extends BaseERF> clazz;
	private String name;
	private DevStatus status;
	private boolean erfList;

	private ERF_Ref(Class<? extends BaseERF> clazz,
		String name, DevStatus status, boolean erfList) {
		this.clazz = clazz;
		this.name = name;
		this.status = status;
		this.erfList = erfList;
	}

	@Override
	public String toString() {
		return name;
	}

	/**
	 * Returns the development status of the referenced
	 * <code>EqkRupForecastBaseAPI</code>.
	 * @return the development status
	 */
	public DevStatus status() {
		return status;
	}
	
	/**
	 * @return true if this is an ERF Epistemic List, false otherwise
	 */
	public boolean isERFList() {
		return erfList;
	}

	/**
	 * Returns a new instance of the ERF represented by
	 * this reference.
	 * @return a new <code>EqkRupForecastBaseAPI</code> instance
	 */
	public BaseERF instance() {
		try {
			Constructor<? extends BaseERF> con = clazz
				.getConstructor();
			return con.newInstance();
		} catch (Exception e) {
			// TODO init logging
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public Class<? extends BaseERF> getERFClass() {
		return clazz;
	}

	/**
	 * Convenience method to return references for all
	 * <code>EqkRupForecastBaseAPI</code> implementations that are currently
	 * production quality (i.e. fully tested and documented), under development,
	 * or experimental. The <code>Set</code> of references returned does not
	 * include deprecated references.
	 * @param includeListERFs if true, Epistemic List ERFs will be included, otherwise
	 * they will be excluded
	 * @return reference <code>Set</code> of all non-deprecated
	 *         <code>EqkRupForecastBaseAPI</code>s
	 * @see DevStatus
	 */
	public static Set<ERF_Ref> get(boolean includeListERFs) {
		return get(includeListERFs, PRODUCTION, DEVELOPMENT, EXPERIMENTAL);
	}
	
	/**
	 * Convenience method to return references for all
	 * <code>EqkRupForecastBaseAPI</code> implementations that should be included
	 * in applications with the given ServerPrefs. Production applications only include
	 * production IMRs, and development applications include everything but
	 * deprecated IMRs.
	 * @param includeListERFs if true, Epistemic List ERFs will be included, otherwise
	 * they will be excluded
	 * @param prefs <code>ServerPrefs</code> instance for which IMRs should be selected
	 * @return
	 */
	public static Set<ERF_Ref> get(boolean includeListERFs, ServerPrefs prefs) {
		if (prefs == ServerPrefs.DEV_PREFS)
			return get(includeListERFs, PRODUCTION, DEVELOPMENT, EXPERIMENTAL);
		else if (prefs == ServerPrefs.PRODUCTION_PREFS)
			return get(includeListERFs, PRODUCTION);
		else
			throw new IllegalArgumentException("Unknown ServerPrefs instance: "+prefs);
	}

	/**
	 * Convenience method to return references to
	 * <code>EqkRupForecastBaseAPI</code> implementations at the specified
	 * levels of development.
	 * @param includeListERFs if true, Epistemic List ERFs will be included, otherwise
	 * they will be excluded
	 * @param stati the development level(s) of the
	 *        <code>EqkRupForecastBaseAPI</code> references to be retrieved
	 * @return a <code>Set</code> of <code>EqkRupForecastBaseAPI</code>
	 *         references
	 * @see DevStatus
	 */
	public static Set<ERF_Ref> get(boolean includeListERFs, DevStatus... stati) {
		EnumSet<ERF_Ref> erfSet = EnumSet.allOf(ERF_Ref.class);
		for (ERF_Ref erf : erfSet) {
			if (!ArrayUtils.contains(stati, erf.status))
				erfSet.remove(erf);
			if (erf.isERFList() && !includeListERFs)
				erfSet.remove(erf);
		}
		return erfSet;
	}

}
