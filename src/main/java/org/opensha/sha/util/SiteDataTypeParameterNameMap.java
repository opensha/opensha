package org.opensha.sha.util;

import java.util.Collection;
import java.util.ListIterator;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.NtoNMap;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.Campbell_1997_AttenRel;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * This class represents an N to N mapping between site data types and parameter names. If
 * a mapping exists, then the given parameter can be set by the given site data type. 
 * 
 * @author Kevin Milner
 *
 */
public class SiteDataTypeParameterNameMap extends NtoNMap<String, String> {
	
	public SiteDataTypeParameterNameMap() {
		super();
	}
	
	/**
	 * Add a mapping
	 * 
	 * @param type
	 * @param paramName
	 */
	public void addMapping(String type, String paramName) {
		this.put(type, paramName);
	}
	
	/**
	 * Returns a list of all site data types that can set this parameter
	 * 
	 * @param paramName
	 * @return
	 */
	public Collection<String> getTypesForParameterName(String paramName) {
		return this.getLefts(paramName);
	}
	
	/**
	 * Returns a list of all of the parameter names that can be set from this
	 * site data type
	 * 
	 * @param type
	 * @return
	 */
	public Collection<String> getParameterNamesForType(String type) {
		return this.getRights(type);
	}
	
	/**
	 * Returns true if the specified mapping exists
	 * 
	 * @param type
	 * @param paramName
	 * @return
	 */
	public boolean isValidMapping(String type, String paramName) {
		return this.containsMapping(type, paramName);
	}
	
	/**
	 * Returns true if the specified mapping exists
	 * 
	 * @param type
	 * @param paramName
	 * @return
	 */
	public boolean isValidMapping(SiteDataValue<?> value, String paramName) {
		return this.containsMapping(value.getDataType(), paramName);
	}
	
	/**
	 * Returns true if the given attenuation relationship has a parameter that can be set by
	 * this type.
	 * 
	 * @param type
	 * @param attenRel
	 * @return
	 */
	public boolean isTypeApplicable(String type, ScalarIMR attenRel) {
		ListIterator<Parameter<?>> it = attenRel.getSiteParamsIterator();
		while (it.hasNext()) {
			Parameter param = it.next();
			if (isValidMapping(type, param.getName()))
				return true;
		}
		return false;
	}
	
	/**
	 * Returns true if the given IMR/Tectonic Region mapping has a parameter that can be set by
	 * this type.
	 * 
	 * @param type
	 * @param imrMap
	 * @return
	 */
	public boolean isTypeApplicable(String type,
			Collection<? extends ScalarIMR> imrs) {
		for (ScalarIMR imr : imrs) {
			if (isTypeApplicable(type, imr))
				return true;
		}
		return false;
	}
	
	/**
	 * Returns true if the given attenuation relationship has a parameter that can be set by
	 * this type.
	 * 
	 * @param type
	 * @param attenRel
	 * @return
	 */
	public boolean isTypeApplicable(SiteDataValue<?> value, ScalarIMR attenRel) {
		return isTypeApplicable(value.getDataType(), attenRel);
	}
	
	/**
	 * Returns true if the given IMR/Tectonic Region mapping has a parameter that can be set by
	 * this type.
	 * 
	 * @param type
	 * @param imrMap
	 * @return
	 */
	public boolean isTypeApplicable(SiteDataValue<?> value,
			Collection<ScalarIMR> imrs) {
		return isTypeApplicable(value.getDataType(), imrs);
	}
	
	private void printParamsForType(String type) {
		System.out.println("***** Type: " + type);
		Collection<String> names = this.getParameterNamesForType(type);
		if (names == null) {
			System.out.println("- <NONE>");
		} else {
			for (String name : names) {
				System.out.println("- " + name);
			}
		}
	}
	
	private void printTypesForParams(String paramName) {
		System.out.println("***** Param Name: " + paramName);
		Collection<String> types = this.getTypesForParameterName(paramName);
		if (types == null) {
			System.out.println("- <NONE>");
		} else {
			for (String name : types) {
				System.out.println("- " + name);
			}
		}
	}
	
	private void printValidTest(String type, String paramName) {
		System.out.println(type + " : " + paramName + " ? " + this.isValidMapping(type, paramName));
	}
	
	public static void main(String args[]) {
		SiteDataTypeParameterNameMap map = SiteTranslator.DATA_TYPE_PARAM_NAME_MAP;
		
		map.printParamsForType(SiteData.TYPE_VS30);
		map.printParamsForType(SiteData.TYPE_WILLS_CLASS);
		map.printParamsForType(SiteData.TYPE_DEPTH_TO_2_5);
		map.printParamsForType(SiteData.TYPE_DEPTH_TO_1_0);
		
		map.printTypesForParams(Vs30_Param.NAME);
		
		map.printValidTest(SiteData.TYPE_VS30, Vs30_Param.NAME);
		map.printValidTest(SiteData.TYPE_WILLS_CLASS, Campbell_1997_AttenRel.SITE_TYPE_NAME);
		map.printValidTest(SiteData.TYPE_VS30, Campbell_1997_AttenRel.SITE_TYPE_NAME);
		map.printValidTest(SiteData.TYPE_DEPTH_TO_2_5, Vs30_Param.NAME);
		
		System.out.println("Size: " + map.size());
	}

}
