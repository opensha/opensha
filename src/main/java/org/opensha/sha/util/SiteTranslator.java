package org.opensha.sha.util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.ListIterator;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.AS_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Abrahamson_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CS_2005_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Campbell_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.DahleEtAl_1995_AttenRel;
import org.opensha.sha.imr.attenRelImpl.Field_2000_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SEA_1999_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SadighEtAl_1997_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ShakeMap_2003_AttenRel;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;
import org.opensha.sha.imr.attenRelImpl.ZhaoEtAl_2006_AttenRel;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

/**
 * <p>Title: SiteTranslator</p>
 * <p>Description: This object sets the value of a site parameter from one or more
 * of the following types of site data:<p>
 * <UL>
 * <LI> Vs30 (average shear-wave velocity in the upper 30 meters of a site)
 * <LI> Wills Site Type (Wills et al., 2000, BSSA, v. 90, S187-S208)
 * <LI> Basin-Depth-2.5 (the depth in m where the shear-wave velocity equals 2.5 km/sec)
 * <LI> Basin-Depth-1.0 (the depth in m where the shear-wave velocity equals 1.0 km/sec)
 * </UL>
 * <p>All of these translations were authorized by the attenuation-rlationship authors
 * (except for Sadigh, who used a dataset similar to Abrahamson & Silve (1997) so that
 * translation is applied).  The main method tests the translations of all currently
 * implemented attenuation-relationship site-related parameters.<p>
 * 
 * <p>If you would like to add a new site data type, or new site parameter, see
 * javadocs for <code>createMap</code> and <code>setParameterValue</code> below
 * for instructions.<p>
 *
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author: Ned Field, Nitin Gupta, Vipin Gupta, and Kevin Milner
 * @version 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SiteTranslator
implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private final static boolean D = false;

	public static SiteDataTypeParameterNameMap DATA_TYPE_PARAM_NAME_MAP = createMap();

	
	/**
	 * Constructor
	 */
	public SiteTranslator() {
		
	}
	
	/**
	 * Creates a mapping between site data types and site parameter names. This is useful if you
	 * want to see if a given site data type can be used to set a given parameter.
	 * 
	 * If you are adding a new site parameter or site data type, you must add it to the mapping below
	 * in order for the translator work with it.
	 * 
	 * If you are adding a new parameter, you must then also edit the <code>setParameterValue</code>
	 * method below to call a new function (something like set[PARAM_NAME]Param) which you must also
	 * update.
	 * 
	 * @return
	 */
	private static SiteDataTypeParameterNameMap createMap() {
		SiteDataTypeParameterNameMap map = new SiteDataTypeParameterNameMap();
		
		/*				params that can be set from raw VS 30 only						*/
		// ...NONE!
		
		/*				params that can be set from Wills Classes only					*/
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	Campbell_1997_AttenRel.BASIN_DEPTH_NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	ShakeMap_2003_AttenRel.WILLS_SITE_NAME);
		
		/*				params common to Vs30 and Wills Classes							*/
		map.addMapping(SiteData.TYPE_VS30,			Vs30_Param.NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	Vs30_Param.NAME);
		map.addMapping(SiteData.TYPE_VS30,			Vs30_TypeParam.NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	Vs30_TypeParam.NAME);
		map.addMapping(SiteData.TYPE_VS30,			AS_1997_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	AS_1997_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_VS30,			SadighEtAl_1997_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	SadighEtAl_1997_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_VS30,			Campbell_1997_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	Campbell_1997_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_VS30,			CB_2003_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	CB_2003_AttenRel.SITE_TYPE_NAME);
		map.addMapping(SiteData.TYPE_VS30,			CS_2005_AttenRel.SOFT_SOIL_NAME);
		map.addMapping(SiteData.TYPE_WILLS_CLASS,	CS_2005_AttenRel.SOFT_SOIL_NAME);
		map.addMapping(SiteData.TYPE_VS30,			ZhaoEtAl_2006_AttenRel.SITE_TYPE_NAME);
		
		/*				params that can be set from Depth to Vs = 2.5 KM/sec			*/
		map.addMapping(SiteData.TYPE_DEPTH_TO_2_5,	DepthTo2pt5kmPerSecParam.NAME);
		map.addMapping(SiteData.TYPE_DEPTH_TO_2_5,	Field_2000_AttenRel.BASIN_DEPTH_NAME);
		map.addMapping(SiteData.TYPE_DEPTH_TO_2_5,	Campbell_1997_AttenRel.BASIN_DEPTH_NAME);
		
		/*				params that can be set from Depth to Vs = 1.0 KM/sec			*/
		map.addMapping(SiteData.TYPE_DEPTH_TO_1_0,	DepthTo1pt0kmPerSecParam.NAME);
		
		return map;
	}
	
	/**
	 * Method to set a site parameter from a single site data value
	 * 
	 * @param param
	 * @param datas
	 * @return true if the parameter was set, false otherwise
	 */
	public boolean setParameterValue(Parameter param, SiteDataValue<?> data) {
		ArrayList<SiteDataValue<?>> datas = new ArrayList<SiteDataValue<?>>();
		datas.add(data);
		return setParameterValue(param, datas);
	}

	/**
	 * Method to set a site parameter from a given set of site data. The first site data value
	 * in the set that can be used to set the parameter will be used.
	 * 
	 * If adding a new site data type, first update the mapping above in the <code>createMap</code>
	 * function. Then modify all of set[PARAM_NAME]Param functions that can be set by the new data
	 * type.
	 * 
	 * If adding a new site parameter, first update the mapping above in the <code>createMap</code>
	 * function. Then add a new clause to the if/else statement below to handle your new parameter
	 * name. In doing so you will also have to create a new method, something like set[PARAM_NAME]Param,
	 * which will actually set the parameter for the given site data values. This new method should return
	 * true if the parameter was set successfully, false otherwise.
	 * 
	 * @param param
	 * @param datas
	 * @return true if the parameter was set, false otherwise
	 */
	public boolean setParameterValue(Parameter param, Collection<SiteDataValue<?>> datas) {
		String paramName = param.getName();
		if (D) System.out.println("setSiteParamsForData: Handling parameter: " + paramName);
		
		// first lets make sure there's a valid mapping, which means that the given parameter can be set
		// by at least one of the given site data types
		boolean mapping = false;
		for (SiteDataValue<?> data : datas) {
			if (DATA_TYPE_PARAM_NAME_MAP.isValidMapping(data, paramName)) {
				mapping = true;
				break;
			}
		}
		
		if (mapping) {
			// this means that the given parameter can be set by at least one of the data types
			
			// VS 30/Wills Site Class
			if (paramName.equals(Vs30_Param.NAME)) {
				return setVS30Param(param, datas);
			} else if (paramName.equals(Vs30_TypeParam.NAME)) {
				return setVS30FlagParam(param, datas);
			} else if (paramName.equals(AS_1997_AttenRel.SITE_TYPE_NAME)) {
				return setAS_SiteType(param, datas);
			} else if (paramName.equals(SadighEtAl_1997_AttenRel.SITE_TYPE_NAME)) {
				return setSCEMY_SiteType(param, datas);
			} else if (paramName.equals(Campbell_1997_AttenRel.BASIN_DEPTH_NAME)) {
				return setCampbellBasinDepth(param, datas);
			} else if (paramName.equals(Campbell_1997_AttenRel.SITE_TYPE_NAME)) {
				return setCampbellSiteType(param, datas);
			} else if (paramName.equals(CB_2003_AttenRel.SITE_TYPE_NAME)) {
				return setCB03SiteType(param, datas);
			} else if (paramName.equals(ShakeMap_2003_AttenRel.WILLS_SITE_NAME)) {
				return setWillsSiteTypeName(param, datas);
			} else if (paramName.equals(CS_2005_AttenRel.SOFT_SOIL_NAME)){
				return setCS05SoftSoil(param, datas);
			} else if (paramName.equals(ZhaoEtAl_2006_AttenRel.SITE_TYPE_NAME)){
				return setZhao06SiteType(param, datas);
			}
			
			// BASIN Depth
			if (paramName.equals(DepthTo2pt5kmPerSecParam.NAME)) {
				return setDepthTo2p5Param(param, datas);
			} else if (paramName.equals(DepthTo1pt0kmPerSecParam.NAME)) {
				return setDepthTo1p0Param(param, datas);
			} else if (paramName.equals(Field_2000_AttenRel.BASIN_DEPTH_NAME)){
				return setDepthTo2p5Param(param, datas);
			}
		} else {
			// this means that the parameter can't be set by any of the data values given.
			
			if (D) {
				String typeStr = "";
				for (SiteDataValue<?> data : datas) {
					if (typeStr.length() > 0)
						typeStr += ", ";
					typeStr += data.getDataType();
				}
				System.out.println("setSiteParamsForData: No mapping exists for type(s): " + typeStr);
			}
			return false;
		}
		return false;
	}
	
	/**
	 * Convenience method to set all site params in the given attenuation relationship instance from a single
	 * site data value. Returns true if at least one parameter was set.
	 * 
	 * @param imr IMR for which to set params
	 * @param data data value used to set IMR params
	 * @return true if at least one parameter was set.
	 */
	public boolean setAllSiteParams(ScalarIMR imr, SiteDataValue<?> data) {
		Collection<SiteDataValue<?>> datas = new ArrayList<SiteDataValue<?>>();
		datas.add(data);
		return setAllSiteParams(imr, datas);
	}
	
	/**
	 * Convenience method to set all site params in the given collection of IMRs from a single
	 * site data value. Returns true if at least one parameter was set.
	 * 
	 * @param imrs collection of IMRs for which to set params
	 * @param data data value used to set IMR params
	 * @return true if at least one parameter was set.
	 */
	public boolean setAllSiteParams(Collection<? extends ScalarIMR> imrs,
			SiteDataValue<?> data) {
		Collection<SiteDataValue<?>> datas = new ArrayList<SiteDataValue<?>>();
		datas.add(data);
		return setAllSiteParams(imrs, datas);
	}
	
	/**
	 * Convenience method to set all site params in the given IMR instance from the given
	 * set of data. Returns true if at least one parameter was set.
	 * 
	 * @param imr IMR for which to set params
	 * @param datas collection of data values used to set IMR params
	 * @return true if at least one parameter was set.
	 */
	public boolean setAllSiteParams(ScalarIMR imr, Collection<SiteDataValue<?>> datas) {
		boolean setSomething = false;
		
		ListIterator<Parameter<?>> it = imr.getSiteParamsIterator();
		
		while (it.hasNext()) {
			Parameter param = it.next();
			if (this.setParameterValue(param, datas))
				setSomething = true;
		}
		
		return setSomething;
	}
	
	/**
	 * Convenience method to set all site params in the given collection of IMRs from the given
	 * set of data. Returns true if at least one parameter was set.
	 * 
	 * @param imrs collection of IMRs for which to set params
	 * @param datas collection of data values used to set IMR params
	 * @return true if at least one parameter was set.
	 */
	public boolean setAllSiteParams(Collection<? extends ScalarIMR> imrs,
			Collection<SiteDataValue<?>> datas) {
		boolean setSomething = false;
		
		for (ScalarIMR imr : imrs) {
			if (this.setAllSiteParams(imr, datas))
				setSomething = true;
		}
		
		return setSomething;
	}
	
	/**
	 * Returns the first data value of a given type
	 * 
	 * @param datas
	 * @param type
	 * @return
	 */
	private SiteDataValue<?> getDataForType(Collection<SiteDataValue<?>> datas, String type) {
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(type)) {
				return data;
			}
		}
		return null;
	}
	
	/**
	 * Checks to see if the specified Vs30 value is greater than 0, not null, and not NaN.
	 * 
	 * @param vsValue
	 * @return
	 */
	private boolean isVS30ValueValid(Double vsValue) {
		return vsValue != null && !vsValue.isNaN() && vsValue > 0;
	}
	
	private static void setValueIgnoreWarning(Parameter param, Object value) {
		if (param instanceof WarningParameter)
			((WarningParameter)param).setValueIgnoreWarning(value);
		else
			param.setValue(value);
	}
	
	/**
	 * Set the Vs30 param for the given set to site data. If a Vs30 value is available in the data,
	 * and is highest priority,that is used. Otherwise if a Wills Site Classification is available,
	 * it is translated into Vs30 and used.
	 * 
	 * See <code>getVS30FromWillsClass</code> for Wills translation values.
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setVS30Param(Parameter param, Collection<SiteDataValue<?>> datas) {
		Double vsValue = null;
		
		// iterate over the data finding the first one you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				// this is just a plain VS 30 value
				vsValue = (Double)data.getValue();
				if (D) System.out.println("setSiteParamsForData: Got VS: " + vsValue);
				if (isVS30ValueValid(vsValue)) {
					if (D) System.out.println("setSiteParamsForData: +++ Set VS30 param: " + vsValue);
					setValueIgnoreWarning(param, vsValue);
					return true;
				}
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				// this is a Wills Site Class that needs to be translated
				vsValue = WillsMap2000.getVS30FromWillsClass((String)data.getValue());
				if (D) System.out.println("setSiteParamsForData: Got translated VS: " + vsValue
						+ " from " + data.getValue());
				if (isVS30ValueValid(vsValue)) {
					if (D) System.out.println("setSiteParamsForData: +++ Set VS30 param: " + vsValue);
					setValueIgnoreWarning(param, vsValue);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Sets the Vs30 flag parameter. It figures out what was used to set the Vs30 parameter, and then
	 * uses the measured/inferred flag from that data source.
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setVS30FlagParam(Parameter param, Collection<SiteDataValue<?>> datas) {
		// Follow the same methodology as that used to set the Vs30 param so we make sure we get the flag
		// from the vs data source that was actually used
		
		// iterate over the data finding the first one you can use. once you find a valid Vs30 value
		// or wills class, use the flag from the data to set the parameter
		for (SiteDataValue<?> data : datas) {
			Double vsValue = null;
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				// this is just a plain VS 30 value
				vsValue = (Double)data.getValue();
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				// this is a Wills Site Class that needs to be translated
				vsValue = WillsMap2000.getVS30FromWillsClass((String)data.getValue());
			}
			if (isVS30ValueValid(vsValue) && data.getDataMeasurementType() != null) {
				if (data.getDataMeasurementType().equals(SiteData.TYPE_FLAG_MEASURED)) {
					if (D) System.out.println("setSiteParamsForData: +++ Setting VS measured");
					param.setValue(Vs30_TypeParam.VS30_TYPE_MEASURED); // set it to measured
					return true;
				} else {
					if (D) System.out.println("setSiteParamsForData: +++ Setting VS inferred");
					param.setValue(Vs30_TypeParam.VS30_TYPE_INFERRED); // set it to inferred
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Sets the Depth to Vs = 2.5 KM/sec param if appropriate data is available.
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setDepthTo2p5Param(Parameter param, Collection<SiteDataValue<?>> datas) {
		// this will get the first (highest priority) data that works
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5)) {
				Double val = (Double)data.getValue();
				if (Double.isNaN(val)) {
					continue;
				}
				if (D) System.out.println("setSiteParamsForData: +++ Setting dep 2.5: " + val);
				setValueIgnoreWarning(param, val);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Sets the Depth to Vs = 1.0 KM/sec param if appropriate data is available.
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setDepthTo1p0Param(Parameter param, Collection<SiteDataValue<?>> datas) {
		// this will get the first (highest priority) data that works
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_DEPTH_TO_1_0)) {
				Double val = (Double)data.getValue();
				if (Double.isNaN(val)) {
					continue;
				} else {
					val = val * 1000d;
				}
				if (D) System.out.println("setSiteParamsForData: +++ Setting dep 1.0: " + val);
				setValueIgnoreWarning(param, val);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Sets the AS Site Type param as follows:
	 * 
	 * If using a Wills Site Classification:
	 * <UL>
	 * <LI> NA 						if E
	 * <LI> Deep-Soil				if DE, D, or CD
	 * <LI> Rock/Shallow-Soil		if C, BC, or B
	 * </UL>
	 * 
	 * Else if using a Vs30 value:
	 * <UL>
	 * <LI> NA 						if NaN
	 * <LI> Deep-Soil				if (Vs30 <= 400 AND no depth data) OR (Vs30 <= 400 AND Depth to 2.5 KM/sec > 100 m)
	 * <LI> Rock/Shallow-Soil		if Vs30 > 400 OR Depth to 2.5 KM/sec <= 100 m
	 * </UL>
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setAS_SiteType(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				Double vsVal = (Double)data.getValue();
				if (!isVS30ValueValid(vsVal))
					continue;
				// if we also have depth to 2.5...then use that to confirm soft soil
				SiteDataValue<?> dep2p5Data = getDataForType(datas, SiteData.TYPE_DEPTH_TO_2_5);
				Double dep = null;
				if (dep2p5Data != null)
					dep = (Double)dep2p5Data.getValue();
				// we want to set it to soil if vs <= 400 and:
				//		* we don't have depth to 2.5 data (or the data we have is null/NaN)
				//		* we have depth to 2.5 data, the depth is > 100 meters (0.1 KM)
				if (vsVal <= 400 && (dep == null || (!Double.isNaN(dep) && dep > 0.1))) {
					param.setValue(AS_1997_AttenRel.SITE_TYPE_SOIL);
				} else {
					param.setValue(AS_1997_AttenRel.SITE_TYPE_ROCK);
				}
				return true;
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				String wc = (String)data.getValue();
				if (wc.equals(WillsMap2000.WILLS_DE) || wc.equals(WillsMap2000.WILLS_D) || wc.equals(WillsMap2000.WILLS_CD)) {
					param.setValue(AS_1997_AttenRel.SITE_TYPE_SOIL);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_C) || wc.equals(WillsMap2000.WILLS_BC) || wc.equals(WillsMap2000.WILLS_B)) {
					param.setValue(AS_1997_AttenRel.SITE_TYPE_ROCK);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Sets the SCEMY Site Type param as follows:
	 * 
	 * If using a Wills Site Classification:
	 * <UL>
	 * <LI> NA 						if E
	 * <LI> Deep-Soil				if DE, D, or CD
	 * <LI> Rock/Shallow-Soil		if C, BC, or B
	 * </UL>
	 * 
	 * Else if using a Vs30 value:
	 * <UL>
	 * <LI> NA 					if NaN
	 * <LI> Deep-Soil			if (Vs30 <= 400 AND no depth data) OR (Vs30 <= 400 AND Depth to 2.5 KM/sec > 100 m)
	 * <LI> Rock				if Vs30 > 400 OR Depth to 2.5 KM/sec <= 100 m
	 * </UL>
	 *  
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setSCEMY_SiteType(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				Double vsVal = (Double)data.getValue();
				if (!isVS30ValueValid(vsVal))
					continue;
				// if we also have depth to 2.5...then use that to confirm soft soil
				SiteDataValue<?> dep2p5Data = getDataForType(datas, SiteData.TYPE_DEPTH_TO_2_5);
				Double dep = null;
				if (dep2p5Data != null)
					dep = (Double)dep2p5Data.getValue();
				// we want to set it to soil if vs <= 400 and:
				//		* we don't have depth to 2.5 data (or the data we have is null/NaN)
				//		* we have depth to 2.5 data, the depth is > 100 meters (0.1 KM)
				if (vsVal <= 400 && (dep == null || (!Double.isNaN(dep) && dep > 0.1))) {
					param.setValue(SadighEtAl_1997_AttenRel.SITE_TYPE_SOIL);
				} else {
					param.setValue(SadighEtAl_1997_AttenRel.SITE_TYPE_ROCK);
				}
				return true;
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				String wc = (String)data.getValue();
				if (wc.equals(WillsMap2000.WILLS_DE) || wc.equals(WillsMap2000.WILLS_D) || wc.equals(WillsMap2000.WILLS_CD)) {
					param.setValue(SadighEtAl_1997_AttenRel.SITE_TYPE_SOIL);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_C) || wc.equals(WillsMap2000.WILLS_BC) || wc.equals(WillsMap2000.WILLS_B)) {
					param.setValue(SadighEtAl_1997_AttenRel.SITE_TYPE_ROCK);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * If using a Wills class is available, sets Campbell-Basin-Depth as follows:
	 * 
	 * <UL>
	 * <LI> Campbell-Basin-Depth = NaN      if E
	 * <LI> Campbell-Basin-Depth = 0.0      if B or BC
	 * <LI> Campbell-Basin-Depth = 1.0      if C
	 * <LI> Campbell-Basin-Depth = 5.0      if CD, D, or DE
	 * </UL>
	 * 
	 * Otherwise, if using Depth to Vs=2.5 KM/sec, use that value as an approximate
	 * (even though Campbell-Basin-Depth is for Depth to Vs=3.0 KM/sec)
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setCampbellBasinDepth(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				String wc = (String)data.getValue();
				if (wc.equals(WillsMap2000.WILLS_DE) || wc.equals(WillsMap2000.WILLS_D) || wc.equals(WillsMap2000.WILLS_CD)) {
					setValueIgnoreWarning(param, Double.valueOf(5.0));
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_C)) {
					setValueIgnoreWarning(param, Double.valueOf(1.0));
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_BC) || wc.equals(WillsMap2000.WILLS_B)) {
					setValueIgnoreWarning(param, Double.valueOf(0.0));
					return true;
				}
			} else if (data.getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5)) {
				Double depth = (Double)data.getValue();
				if (depth.isNaN())
					continue;
				setValueIgnoreWarning(param, depth);
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Campbell_1997_AttenRel.SITE_TYPE_NAME (Campbell (1997))<p>
	 * 
	 * If using a Wills class, set it as follows:
	 * 
	 * <UL>
	 * <LI> NA 					if E
	 * <LI> Firm-Soil			if DE, D, or CD
	 * <LI> Soft-Rock			if C
	 * <LI> Hard-Rock			if BC or B
	 * </UL>
	 * 
	 * Otherwise if using a Vs30 value:
	 * 
	 * <UL>
	 * <LI> NA 					if Vs30 <= 180
	 * <LI> Firm-Soil			if 180 > Vs30 <= 400
	 * <LI> Soft-Rock			if 400 > Vs30 <= 500
	 * <LI> Hard-Rock			if 500 > Vs30
	 * </UL>
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setCampbellSiteType(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				Double vsVal = (Double)data.getValue();
				if (!isVS30ValueValid(vsVal))
					continue;
				if(vsVal>180 && vsVal<=400) {
					param.setValue(Campbell_1997_AttenRel.SITE_TYPE_FIRM_SOIL);
					return true;
				} else if(vsVal>400 && vsVal<=500) {
					param.setValue(Campbell_1997_AttenRel.SITE_TYPE_SOFT_ROCK);
					return true;
				} else if(vsVal>500) {
					param.setValue(Campbell_1997_AttenRel.SITE_TYPE_HARD_ROCK);
					return true;
				}
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				String wc = (String)data.getValue();
				if (wc.equals(WillsMap2000.WILLS_DE) || wc.equals(WillsMap2000.WILLS_D) || wc.equals(WillsMap2000.WILLS_CD)) {
					param.setValue(Campbell_1997_AttenRel.SITE_TYPE_FIRM_SOIL);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_C)) {
					param.setValue(Campbell_1997_AttenRel.SITE_TYPE_SOFT_ROCK);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_BC) || wc.equals(WillsMap2000.WILLS_B)) {
					param.setValue(Campbell_1997_AttenRel.SITE_TYPE_HARD_ROCK);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * 
	 * Set Campbell & Bozorgnia 2003 Site Type parameter.
	 * 
	 * If using a Wills class, set it as follows:
	 * 
	 * <UL>
	 * <LI> NA 						if E
	 * <LI> Firm-Soil				if DE, or D
	 * <LI> Very-Firm-Soil			if CD
	 * <LI> BC-Boundary				if BC
	 * <LI> Soft-Rock				if C
	 * <LI> Hard-Rock				if B
	 * </UL>
	 * 
	 * Otherwise if using a Vs30 value:
	 * 
	 * <UL>
	 * <LI> NA 						if Vs30 <= 180
	 * <LI> Firm-Soil				if 180 > Vs30 <= 300
	 * <LI> Very-Firm-Soil			if 300 > Vs30 <= 400
	 * <LI> Soft-Rock				if 400 > Vs30 <= 500
	 * <LI> Hard-Rock				if 500 > Vs30
	 * </UL>
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setCB03SiteType(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				Double vsVal = (Double)data.getValue();
				if(vsVal>180 && vsVal<=300) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_FIRM_SOIL);
					return true;
				} else if(vsVal>300 && vsVal<=400) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_VERY_FIRM_SOIL);
					return true;
				} else if(vsVal >400 && vsVal <=500) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_SOFT_ROCK);
					return true;
				} else if(vsVal >500) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_FIRM_ROCK);
					return true;
				}
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				String wc = (String)data.getValue();
				if (wc.equals(WillsMap2000.WILLS_DE) || wc.equals(WillsMap2000.WILLS_D)) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_FIRM_SOIL);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_CD)) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_VERY_FIRM_SOIL);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_C)) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_SOFT_ROCK);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_BC)) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_NEHRP_BC);
					return true;
				}
				else if (wc.equals(WillsMap2000.WILLS_B)) {
					param.setValue(CB_2003_AttenRel.SITE_TYPE_FIRM_ROCK);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Set Wills Site Type Name parameter (e.g. ShakeMap 2003)
	 * 
	 * If we have a wills value, set the parameter.
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setWillsSiteTypeName(Parameter param, Collection<SiteDataValue<?>> datas) {
		// if we have a wills class, use that
		SiteDataValue<?> willsData = getDataForType(datas, SiteData.TYPE_WILLS_CLASS);
		if (willsData != null) {
			String wc = (String)willsData.getValue();
			if (param.isAllowed(wc)) {
				param.setValue(wc);
				return true;
			}
		}
		
		// TODO: figure out what to do with a VS 30 value here
		return false;
	}
	
	/**
	 * 
	 * Set the CS 2005 Soft Soil parameter
	 * 
	 * If a Wills Site Class is available:
	 * 
	 * <UL>
	 * <LI> True 				if E
	 * <LI> False				otherwise
	 * </UL>
	 * 
	 * Otherwise if we have a Vs30 value:
	 * 
	 * <UL>
	 * <LI> True 				if Vs30 < 180
	 * <LI> False				if 180 >= Vs30
	 * </UL>
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setCS05SoftSoil(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				Double vsVal = (Double)data.getValue();
				if (isVS30ValueValid(vsVal)) {
					param.setValue(Boolean.valueOf(vsVal < 180));
					return true;
				}
				return false;
			} else if (data.getDataType().equals(SiteData.TYPE_WILLS_CLASS)) {
				String wc = (String)data.getValue();
				if (wc.equals(WillsMap2000.WILLS_E))
					param.setValue(Boolean.valueOf(true));
				else
					param.setValue(Boolean.valueOf(false));
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * 
	 * Set Campbell & Bozorgnia 2003 Site Type parameter.
	 * 
	 * If using a Wills class, set it as follows:
	 * 
	 * <UL>
	 * <LI> NA 						if E
	 * <LI> Firm-Soil				if DE, or D
	 * <LI> Very-Firm-Soil			if CD
	 * <LI> BC-Boundary				if BC
	 * <LI> Soft-Rock				if C
	 * <LI> Hard-Rock				if B
	 * </UL>
	 * 
	 * Otherwise if using a Vs30 value:
	 * 
	 * <UL>
	 * <LI> NA 						if Vs30 <= 180
	 * <LI> Firm-Soil				if 180 > Vs30 <= 300
	 * <LI> Very-Firm-Soil			if 300 > Vs30 <= 400
	 * <LI> Soft-Rock				if 400 > Vs30 <= 500
	 * <LI> Hard-Rock				if 500 > Vs30
	 * </UL>
	 * 
	 * @param param
	 * @param datas
	 * @return
	 */
	public boolean setZhao06SiteType(Parameter param, Collection<SiteDataValue<?>> datas) {
		// iterate over the data finding the first one (highest priority) you can use
		for (SiteDataValue<?> data : datas) {
			if (data.getDataType().equals(SiteData.TYPE_VS30)) {
				Double vsVal = (Double)data.getValue();
				if (vsVal <= 200) {
					param.setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_SOFT_SOIL);
					return true;
				} else if (vsVal <= 300) {
					param.setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_MEDIUM_SOIL);
					return true;
				} else if (vsVal <= 600) {
					param.setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_HARD_SOIL);
					return true;
				} else if (vsVal <= 1100) {
					param.setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK);
					return true;
				} else {
					param.setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_HARD_ROCK);
					return true;
				}
			}
		}
		
		return false;
	}

	/**
	 * @param parameter: the parameter object to be set
	 * @param willsClass - a String with one of the folowing ("E", "DE", "D", "CD", "C", "BC", or "B")
	 * @param basinDepth - Depth (in meters) to where Vs = 2.5-km/sec
	 *
	 * @return a boolean to tell if setting the value was successful (if false
	 * it means the parameter value was not changed).  A basinDepth value of NaN is allowed
	 * (it will not cause the returned value to be false).
	 * 
	 * ***NOTE: THIS NEEDS TO FIXED TO HANDLE THE SOFT SOIL CASE FOR CHOI AND STEWART MODEL 
	 * 
	 * @deprecated - This is from the old site translator which was hardcoded to only accept wills
	 * classes and depth to Vs=2.5 KM/sec. It is included for compatibility, but modified to use the
	 * new structure.
	 */
	@Deprecated
	public boolean setParameterValue(Parameter param, String willsClass,
			double basinDepth) {
		
		SiteDataValue<?> willsData = null;
		
		// shorten name for convenience
		String wc = willsClass;
		
		if (WillsMap2000.wills_vs30_map.keySet().contains(wc)) {
			// it's a wills class
			willsData = new SiteDataValue<String>(SiteData.TYPE_WILLS_CLASS, SiteData.TYPE_FLAG_MEASURED, wc);
		} else {
			// lets see if it's a Vs30 value
			try {
				double vs = Double.parseDouble(wc);
				willsData = new SiteDataValue<Double>(SiteData.TYPE_VS30,
						SiteData.TYPE_FLAG_MEASURED, vs);
			} catch (NumberFormatException e) {
				// it's not
			}
		}
		
		SiteDataValue<Double> basinData = null;
		// it's in meters here so we have to convert to KM
		basinData = new SiteDataValue<Double>(SiteData.TYPE_DEPTH_TO_2_5,
					SiteData.TYPE_FLAG_MEASURED, basinDepth / 1000d);
		boolean setWills = this.setParameterValue(param, willsData);
		boolean setBasin = this.setParameterValue(param, basinData);
		
		return setWills || setBasin;
	}



	/**
	 * This will test the translation from all wills categories for the parameter given
	 * @param param
	 */
	public void test(Parameter param) {
		System.out.println(param.getName() + "  Parameter (basin depth = NaN):");
		if (setParameterValue(param, WillsMap2000.WILLS_B, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_B + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_B + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_BC, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_BC + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_BC + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_C, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_C + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_C + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_CD, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_CD + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_CD + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_D, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_D + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_D + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_DE, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_DE + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_DE + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_E, Double.NaN)) {
			System.out.println("\t" + WillsMap2000.WILLS_E + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_E + " --> " + "*** can't set ***");
		}

		System.out.println(param.getName() + "  Parameter (basin depth = 1.0):");
		if (setParameterValue(param, WillsMap2000.WILLS_B, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_B + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_B + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_BC, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_BC + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_BC + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_C, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_C + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_C + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_CD, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_CD + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_CD + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_D, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_D + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_D + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_DE, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_DE + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_DE + " --> " + "*** can't set ***");
		}
		if (setParameterValue(param, WillsMap2000.WILLS_E, 1.0)) {
			System.out.println("\t" + WillsMap2000.WILLS_E + " --> " + param.getValue());
		}
		else {
			System.out.println("\t" + WillsMap2000.WILLS_E + " --> " + "*** can't set ***");
		}
	}
	
	private ArrayList<Parameter> getTableParameters() {
		ArrayList<Parameter> params = new ArrayList<Parameter>();
		
		String attenNames = "";
		
		AttenuationRelationship ar;
		ar = new CB_2008_AttenRel(null);
		attenNames += ",(multiple),(multiple)";
		params.add(ar.getParameter(Vs30_Param.NAME));
		params.add(ar.getParameter(DepthTo2pt5kmPerSecParam.NAME));
		
		ar = new CY_2008_AttenRel(null);
		attenNames += ",(multiple)";
		params.add(ar.getParameter(DepthTo1pt0kmPerSecParam.NAME));
		
		ar = new AS_1997_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(AS_1997_AttenRel.SITE_TYPE_NAME));
		
		ar = new CB_2003_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(CB_2003_AttenRel.SITE_TYPE_NAME));
		
		ar = new CS_2005_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(CS_2005_AttenRel.SOFT_SOIL_NAME));
		
		ar = new Campbell_1997_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "") + "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(Campbell_1997_AttenRel.SITE_TYPE_NAME));
		params.add(ar.getParameter(Campbell_1997_AttenRel.BASIN_DEPTH_NAME));
		
		ar = new DahleEtAl_1995_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(DahleEtAl_1995_AttenRel.SITE_TYPE_NAME));
		
		ar = new Field_2000_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(Field_2000_AttenRel.BASIN_DEPTH_NAME));
		
		ar = new SadighEtAl_1997_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(SadighEtAl_1997_AttenRel.SITE_TYPE_NAME));
		
		ar = new SEA_1999_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(SEA_1999_AttenRel.SITE_TYPE_NAME));
		
		ar = new ShakeMap_2003_AttenRel(null);
		attenNames += "," + ar.getName().replaceAll(",", "");
		params.add(ar.getParameter(ShakeMap_2003_AttenRel.WILLS_SITE_NAME));
		
		params.add(new StringParameter("Atten Rel Names", attenNames));
		
		return params;
	}
	
	private String getTableValLine(ArrayList<Parameter> params, SiteDataValue<?> val) {
		String line = val.getValue() + "";
		for (Parameter param : params) {
			boolean flag = setParameterValue(param, val);
			if (flag)
				line += "," + param.getValue();
			else
				line += ",N/A";
		}
		return line;
	}
	
	private void generateConversionTables() throws IOException {
		ArrayList<Parameter> params = getTableParameters();
		
		// the last one here is just a string param with the names of the atten rels.
		// get the value then remove it from the list.
		
		String attenTitles = (String)(params.remove(params.size() - 1).getValue());
		
		FileWriter fw = new FileWriter("siteTrans.csv");
		
		
		String empty = "";
		String paramNames = "";
		for (Parameter param : params) {
			paramNames += "," + param.getName();
			empty += ",";
		}
		fw.write("Vs30" + empty.substring(1) + "\n");
		fw.write(attenTitles + "\n");
		fw.write(paramNames + "\n");
		
		SiteDataValue<?> val = new SiteDataValue<Double>(SiteData.TYPE_VS30,
				SiteData.TYPE_FLAG_INFERRED, Double.NaN);
		String line = getTableValLine(params, val);
		fw.write(line + "\n");
		for (double vs30=150d; vs30<1000d; vs30+=10d) {
			val = new SiteDataValue<Double>(SiteData.TYPE_VS30,
					SiteData.TYPE_FLAG_INFERRED, vs30);
			line = getTableValLine(params, val);
			fw.write(line + "\n");
		}
		fw.write(empty + "\n");
		
		fw.write("Wills Class" + empty.substring(1) + "\n");
		fw.write(attenTitles + "\n");
		fw.write(paramNames + "\n");
		val = new SiteDataValue<String>(SiteData.TYPE_WILLS_CLASS,
				SiteData.TYPE_FLAG_INFERRED, "NA");
		line = getTableValLine(params, val);
		fw.write(line + "\n");
		for (String wills : WillsMap2000.getSortedWillsValues()) {
			val = new SiteDataValue<String>(SiteData.TYPE_WILLS_CLASS,
					SiteData.TYPE_FLAG_INFERRED, wills);
			line = getTableValLine(params, val);
			fw.write(line + "\n");
		}
		fw.write(empty + "\n");
		
		fw.write("Depth to Vs=2.5" + empty.substring(1) + "\n");
		fw.write(attenTitles + "\n");
		fw.write(paramNames + "\n");
		val = new SiteDataValue<Double>(SiteData.TYPE_DEPTH_TO_2_5,
				SiteData.TYPE_FLAG_INFERRED, Double.NaN);
		line = getTableValLine(params, val);
		fw.write(line + "\n");
		for (double depth2_5=0d; depth2_5<3d; depth2_5+=0.1d) {
			val = new SiteDataValue<Double>(SiteData.TYPE_DEPTH_TO_2_5,
					SiteData.TYPE_FLAG_INFERRED, depth2_5);
			line = getTableValLine(params, val);
			fw.write(line + "\n");
		}
		fw.write(empty + "\n");
		
		fw.write("Depth to Vs=1.0" + empty.substring(1) + "\n");
		fw.write(attenTitles + "\n");
		fw.write(paramNames + "\n");
		val = new SiteDataValue<Double>(SiteData.TYPE_DEPTH_TO_1_0,
				SiteData.TYPE_FLAG_INFERRED, Double.NaN);
		line = getTableValLine(params, val);
		fw.write(line + "\n");
		for (double depth1_0=0d; depth1_0<3d; depth1_0+=0.1d) {
			val = new SiteDataValue<Double>(SiteData.TYPE_DEPTH_TO_1_0,
					SiteData.TYPE_FLAG_INFERRED, depth1_0);
			line = getTableValLine(params, val);
			fw.write(line + "\n");
		}
		fw.write(empty + "\n");
		
		fw.close();
	}

	/**
	 * This main method tests the translation of all currently implemented attenuation
	 * relationship site-dependent parameters.
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String args[]) throws IOException {
		SiteTranslator siteTrans = new SiteTranslator();
		
		siteTrans.generateConversionTables();

		AttenuationRelationship ar;
		ar = new AS_1997_AttenRel(null);
		siteTrans.test(ar.getParameter(AS_1997_AttenRel.SITE_TYPE_NAME));

		ar = new SadighEtAl_1997_AttenRel(null);
		siteTrans.test(ar.getParameter(SadighEtAl_1997_AttenRel.SITE_TYPE_NAME));

		ar = new BJF_1997_AttenRel(null);
		siteTrans.test(ar.getParameter(Vs30_Param.NAME));

		ar = new Campbell_1997_AttenRel(null);
		siteTrans.test(ar.getParameter(Campbell_1997_AttenRel.SITE_TYPE_NAME));
		siteTrans.test(ar.getParameter(Campbell_1997_AttenRel.BASIN_DEPTH_NAME));

		ar = new Field_2000_AttenRel(null);
		siteTrans.test(ar.getParameter(Vs30_Param.NAME));
		siteTrans.test(ar.getParameter(Field_2000_AttenRel.BASIN_DEPTH_NAME));

		ar = new Abrahamson_2000_AttenRel(null);
		siteTrans.test(ar.getParameter(Abrahamson_2000_AttenRel.SITE_TYPE_NAME));

		ar = new CB_2003_AttenRel(null);
		siteTrans.test(ar.getParameter(CB_2003_AttenRel.SITE_TYPE_NAME));

		ar = new ShakeMap_2003_AttenRel(null);
		siteTrans.test(ar.getParameter(ShakeMap_2003_AttenRel.WILLS_SITE_NAME));

		ar = new USGS_Combined_2004_AttenRel(null);
		siteTrans.test(ar.getParameter(Vs30_Param.NAME));

		//  ar = new SEA_1999_AttenRel(null);
		//  siteTrans.test(ar.getParameter(SEA_1999_AttenRel.SITE_TYPE_NAME));


	}

}
