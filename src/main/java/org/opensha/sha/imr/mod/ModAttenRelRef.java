package org.opensha.sha.imr.mod;

import java.lang.reflect.Constructor;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.imr.mod.impl.BaylessSomerville2013DirectivityModifier;
import org.opensha.sha.imr.mod.impl.DemoSiteSpecificMod;
import org.opensha.sha.imr.mod.impl.SimpleScaleMod;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.ErgodicFromRefIMRMod;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.NonErgodicSiteResponseMod;

/**
 * Enum which supplies references to AbstractAteenRelMod's. Add values here for them to show up in
 * the ModAttenuationRelationship.
 * 
 * @author kevin
 *
 */
public enum ModAttenRelRef {
	
	SIMPLE_SCALE(SimpleScaleMod.class, SimpleScaleMod.NAME, SimpleScaleMod.SHORT_NAME),
	DEMO_SITE_SPECIFIC(DemoSiteSpecificMod.class, DemoSiteSpecificMod.NAME, DemoSiteSpecificMod.SHORT_NAME),
	BAYLESS_SOMERVILLE_2013_DIRECTIVITY(BaylessSomerville2013DirectivityModifier.class,
			BaylessSomerville2013DirectivityModifier.NAME, BaylessSomerville2013DirectivityModifier.SHORT_NAME),
	STEWART_SITE_SPECIFIC(NonErgodicSiteResponseMod.class, NonErgodicSiteResponseMod.NAME, NonErgodicSiteResponseMod.SHORT_NAME),
	ERGODIC_FROM_REF(ErgodicFromRefIMRMod.class, ErgodicFromRefIMRMod.NAME, ErgodicFromRefIMRMod.SHORT_NAME);
	
	private Class<? extends AbstractAttenRelMod> clazz;
	private String name, shortName;
	
	private ModAttenRelRef(Class<? extends AbstractAttenRelMod> clazz,
		String name, String shortName) {
		this.clazz = clazz;
		this.name = name;
		this.shortName = shortName;
	}

	/**
	 * Returns a new instance of the attenuation relationship mod represented by
	 * this reference.
	 * @return a new <code>AbstractAttenRelMod</code> instance
	 */
	public AbstractAttenRelMod instance() {
		try {
			Object[] args = new Object[] {};
			Class<?>[] params = new Class[] {};
			Constructor<? extends AbstractAttenRelMod> con = clazz
				.getConstructor(params);
			return con.newInstance(args);
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getShortName() {
		return shortName;
	}
}
