package org.opensha.commons.data.siteData.impl;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public enum TectonicRegime {

	/*
	 * These ones are from STREC
	 */
	ANSR_DEEPCON("ACR (deep)", "ANSR-DEEPCON", "ANSR_DEEPCON"),
	ANSR_HOTSPOT("ACR (hot spot)", "ANSR-HOTSPOT", "ANSR_HOTSPOT"),
	ANSR_OCEANBD("ACR (oceanic boundary)", "ANSR-OCEANBD", "ANSR_OCEANBD"),
	ANSR_SHALCON("ACR (shallow)", "ANSR-SHALCON", "ANSR_SHALCON"),
	ANSR_ABSLDEC("ACR deep (above slab)", "ANSR-ABSLDEC", "ANSR_ABSLDEC"),
	ANSR_ABSLOCB("ACR oceanic boundary (above slab)", "ANSR-ABSLOCB", "ANSR_ABSLOCB"),
	ANSR_ABSLSHC("ACR shallow (above slab)", "ANSR-ABSLSHC", "ANSR_ABSLSHC"),
	SCR_ABVSLAB("SCR (above slab)", "SCR-ABVSLAB", "SCR_ABVSLAB"),
	SCR_GENERIC("SCR (generic)", "SCR-GENERIC", "SCR_GENERIC"),
	SOR_ABVSLAB("SOR (above slab)", "SOR-ABVSLAB", "SOR_ABVSLAB"),
	SOR_GENERIC("SOR (generic)", "SOR-GENERIC", "SOR_GENERIC"),
	SZ_GENERIC("SZ (generic)", "SZ-GENERIC", "SZ_GENERIC"),
	SZ_INLBACK("SZ (inland/back-arc)", "SZ-INLBACK", "SZ_INLBACK"),
	SZ_ONSHORE("SZ (on-shore)", "SZ-ONSHORE", "SZ_ONSHORE"),
	SZ_OUTERTR("SZ (outer-trench)", "SZ-OUTERTR", "SZ_OUTERTR"),
	/*
	 * Other hardcoded/misc regimes
	 */
	CALIFORNIA("California", "CALIFORNIA"),
	GLOBAL_AVERAGE("Global Average", "GLOBAL-AVERAGE");

	private static Map<String, TectonicRegime> mappings = null;
	
	private String[] names;

	private TectonicRegime(String... names) {
		this.names = names;
	}
	
	public String getPreferredName() {
		return names[0];
	}
	
	public String[] getNames() {
		return names;
	}
	
	@Override
	public String toString() {
		return getPreferredName();
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
