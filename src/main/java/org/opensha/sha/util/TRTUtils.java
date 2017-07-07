package org.opensha.sha.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.opensha.sha.calc.params.NonSupportedTRT_OptionsParam;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;

public class TRTUtils {

	/**
	 * This wraps a single IMR in a HashMap with a single TRT, Active Shallow.
	 * 
	 * @param imr - Intensity Measure Relationship to wrap
	 * @return mapping of IMR's to TRT's with only a single mapping of active shallow to the 
	 * given IMR.
	 */
	public static HashMap<TectonicRegionType, ScalarIMR>
	wrapInHashMap(ScalarIMR imr) {
		HashMap<TectonicRegionType, ScalarIMR> imrMap =
			new HashMap<TectonicRegionType, ScalarIMR>();
		// The type of tectonic region here is of no consequence (it just a dummy value)
		imrMap.put(TectonicRegionType.ACTIVE_SHALLOW, imr);
		return imrMap;
	}

	/**
	 * This will return the IMR for the given Tectonic Region Type. If the map has only
	 * a single mapping, the first (and only) IMR in the map is returned without checking
	 * that the Tectonic Region Types match.
	 * 
	 * The TRT is NOT set in the IMR. To do this, see setTRTinIMR
	 * 
	 * @param imrMap - Mapping of IMR's to TRT's
	 * @param trt - Tectonic Region Type for which to retrieve an IMR
	 * @return Single IMR with TRT param set
	 * @see setTRTinIMR
	 */
	@SuppressWarnings("unchecked")
	public static ScalarIMR getIMRforTRT(
			Map<TectonicRegionType, ScalarIMR> imrMap,
			TectonicRegionType trt) {

		if (trt == null)
			// TODO maybe figure out another way to handle this?
			throw new IllegalArgumentException("Tectonic Region Type cannot be null!");

		ScalarIMR imr;
		if(imrMap.size()>1) {
			imr = imrMap.get(trt);
		} else {  // only one IMR, so force all sources to be used with this one and assume the TectonicRegionTypeParam has already been set (e.g., in the gui)
			imr = getFirstIMR(imrMap);		// long winded way of getting the single imr		  
		}
		return imr;
	}

	/**
	 * Sets the TRT param in the given IMR. If the IMR doesn't support the TRT (determined by
	 * <code>imr.isTectonicRegionSupported(trt.toString())</code>) then the TRT param is set
	 * according to the <code>NonSupportedTRT_OptionsParam</code> param.
	 * 
	 * @param imr - Intensity Measure Relationship in which to set the TRT
	 * @param trt - Tectonic Region Type to set in the IMR
	 */
	public static void setTRTinIMR(ScalarIMR imr, TectonicRegionType trt,
			NonSupportedTRT_OptionsParam nonSupportedTRT_OptionsParam,
			TectonicRegionType originalTRT) {
		TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam) imr.getParameter(TectonicRegionTypeParam.NAME);
		
		if(imr.isTectonicRegionSupported(trt.toString()))  {  // check whether it's supported
			// simple case, just set it as given
			trtParam.setValue(trt);					  
		} else { // what to do if imr does not support that type
			if(nonSupportedTRT_OptionsParam.getValue().equals(NonSupportedTRT_OptionsParam.USE_DEFAULT))
				trtParam.setValueAsDefault();
			else if (nonSupportedTRT_OptionsParam.getValue().equals(NonSupportedTRT_OptionsParam.THROW))
				throw new RuntimeException("Tectonic Region Type from source ("+trt+") not supported by IMR");
			else if (nonSupportedTRT_OptionsParam.getValue().equals(NonSupportedTRT_OptionsParam.USE_ORIG))
				trtParam.setValue(originalTRT);
		}
	}

	/**
	 * This gets a mapping of the TRTs as set in each IMR. This is keeping track of the user's TRT
	 * settings for each parameter when they might change during a calculation.
	 * 
	 * NOTE: This does NOT get the default value, but the CURRENTLY SET value.
	 * 
	 * @return mapping of IMRs to their currently set TRT
	 */
	public static HashMap<ScalarIMR, TectonicRegionType> getTRTsSetInIMRs (
			Map<TectonicRegionType, ScalarIMR> imrMap) {

		return getTRTsSetInIMR(imrMap.values());
	}

	/**
	 * This gets a mapping of the TRTs as set in each IMR. This is keeping track of the user's TRT
	 * settings for each parameter when they might change during a calculation.
	 * 
	 * If the IMR's TRT param is null, it will first be set as default.
	 * 
	 * NOTE: This does NOT get the default value, but the CURRENTLY SET value.
	 * 
	 * @return mapping of IMRs to their currently set TRT
	 */
	public static HashMap<ScalarIMR, TectonicRegionType> getTRTsSetInIMR(
			Collection<ScalarIMR> imrs) {
		HashMap<ScalarIMR, TectonicRegionType> map =
			new HashMap<ScalarIMR, TectonicRegionType>();

		for (ScalarIMR imr : imrs) {
			TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam) imr.getParameter(TectonicRegionTypeParam.NAME);
			if (trtParam.getValue() == null)
				trtParam.setValueAsDefault();
			TectonicRegionType trt = trtParam.getValueAsTRT();
			map.put(imr, trt);
		}

		return map;
	}
	
	/**
	 * Resets TRTs in each IMR to the values specified. Can be used to restore values recorded with
	 * <code>getTRTsSetInIMR</code>.
	 * 
	 * @param trtValues - IMR to TRT mapping to reset
	 * @see getTRTsSetInIMR
	 */
	public static void resetTRTsInIMRs(Map<ScalarIMR, TectonicRegionType> trtValues) {
		for (ScalarIMR imr : trtValues.keySet()) {
			TectonicRegionTypeParam trtParam = (TectonicRegionTypeParam) imr.getParameter(TectonicRegionTypeParam.NAME);
			trtParam.setValue(trtValues.get(imr));
		}
	}

	/**
	 * Simply returns the first IMR in the map by calling <code>imrMap.values().iterator().next()</code>.
	 * This is useful for returning the only IMR out of a map with a single TRT.
	 * 
	 * @param imrMap - Mapping of IMR's to TRT's
	 * @return first IMR in the map
	 */
	public static ScalarIMR getFirstIMR(
			Map<TectonicRegionType, ScalarIMR> imrMap) {
		return imrMap.values().iterator().next();
	}
}
