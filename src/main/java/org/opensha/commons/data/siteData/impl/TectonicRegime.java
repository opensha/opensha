package org.opensha.commons.data.siteData.impl;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public enum TectonicRegime {

	/*
	 * These ones are from STREC
	 */
	ANSR_DEEPCON("ACR (deep)", "ANSR-DEEPCON"),
	ANSR_HOTSPOT("ACR (hot spot)", "ANSR-HOTSPOT"),
	ANSR_OCEANBD("ACR (oceanic boundary)", "ANSR-OCEANBD"),
	ANSR_SHALCON("ACR (shallow)", "ANSR-SHALCON"),
	ANSR_ABSLDEC("ACR deep (above slab)", "ANSR-ABSLDEC"),
	ANSR_ABSLOCB("ACR oceanic boundary (above slab)", "ANSR-ABSLOCB"),
	ANSR_ABSLSHC("ACR shallow (above slab)", "ANSR-ABSLSHC"),
	ANSR_ABVSLAB("SCR (above slab)", "SCR-ABVSLAB"),
	ACR_GENERIC("SCR (generic)", "SCR-GENERIC"),
	SOR_ABVSLAB("SOR (above slab)", "SOR-ABVSLAB"),
	SOR_GENERIC("SOR (generic)", "SOR-GENERIC"),
	SZ_GENERIC("SZ (generic)", "SZ-GENERIC"),
	SZ_INLBACK("SZ (inland/back-arc)", "SZ-INLBACK"),
	SZ_ONSHORE("SZ (on-shore)", "SZ-ONSHORE"),
	SZ_OUTERTR("SZ (outer-trench)", "SZ-OUTERTR"),
	/*
	 * Other hardcoded/misc regimes
	 */
	CALIFORNIA("California");

	private static Map<String, TectonicRegime> mappings = null;
	
	private String[] names;

	private TectonicRegime(String... names) {
		this.names = names;
	}
	
	public static synchronized TectonicRegime forName(String name) {
		if (mappings == null) {
			mappings = Maps.newHashMap();
			for (TectonicRegime regime : values()) {
				for (String rName : regime.names) {
					Preconditions.checkState(!mappings.containsKey(rName));
					mappings.put(rName, regime);
				}
			}
		}
		return mappings.get(name);
	}

}
