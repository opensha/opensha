package org.opensha.sha.gcim;

import java.util.HashMap;
import java.util.Map;

import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Thuese utilities had been added to TRTUtils but have been removed to
 * consolidate with GCIM package. They may be re-added at some future time.
 */
public class Utils {

	/**
	 * This wraps a single IMCorrRel in a HashMap with a single TRT, Active Shallow.
	 * 
	 * @param imr
	 * @return
	 */
	public static HashMap<TectonicRegionType, ImCorrelationRelationship>
			wrapInHashMap(ImCorrelationRelationship imCorrRel) {
		HashMap<TectonicRegionType, ImCorrelationRelationship> imCorrRelMap =
			new HashMap<TectonicRegionType, ImCorrelationRelationship>();
		// The type of tectonic region here is of no consequence (it just a dummy value)
		imCorrRelMap.put(TectonicRegionType.ACTIVE_SHALLOW, imCorrRel);
		return imCorrRelMap;
	}

	
	/**
	 * This will return the IMR for the given Tectonic Region Type. If the map has only
	 * a single mapping, the first (and only) IMR in the map is returned without checking
	 * that the Tectonic Region Types match.
	 * 
	 * @param imrMap
	 * @param trt
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static ImCorrelationRelationship getIMCorrRelForTRT(
			Map<TectonicRegionType, ImCorrelationRelationship> imCorrRelMap,
			TectonicRegionType trt) {
		
		if (trt == null)
			// TODO maybe figure out another way to handle this?
			throw new IllegalArgumentException("Tectonic Region Type cannot be null!");
		
		ImCorrelationRelationship imCorrRel;
		if(imCorrRelMap.size()>1) {
			imCorrRel = imCorrRelMap.get(trt);
			// now set the tectonic region in the imr if it supports this type 
			// (because it might support multiple Tectonic Regions), otherwise
			// do nothing to force it to take the ruptures anyway (and to avoid an exception)
			// what if it support two other types, but not this one?????????????????????
			if(imCorrRel.isTectonicRegionSupported(trt.toString()))  {
				imCorrRel.getParameter(TectonicRegionTypeParam.NAME).setValue(trt.toString());					  
			} else { // set to the default value
				imCorrRel.getParameter(TectonicRegionTypeParam.NAME).setValueAsDefault();
			}

		} else {  // only one IMR, so force all sources to be used with this one and assume the TectonicRegionTypeParam has already been set (e.g., in the gui)
			imCorrRel = getFirstIMCorrRel(imCorrRelMap);		// long winded way of getting the single imr		  
		}
		return imCorrRel;
	}

	
	/**
	 * Simply returns the first IMCorrRel in the map by calling <code>imrMap.values().iterator().next()</code>.
	 * This is useful for returning the only IMCorrRel out of a map with a single TRT.
	 * 
	 * @param imrMap
	 * @return
	 */
	public static ImCorrelationRelationship getFirstIMCorrRel(
			Map<TectonicRegionType, ImCorrelationRelationship> imCorrRelMap) {
		return imCorrRelMap.values().iterator().next();
	}

}
