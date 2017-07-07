package org.opensha.nshmp2.util;

import static org.opensha.nshmp2.util.SiteType.*;
import static org.opensha.nshmp2.util.SourceRegion.*;
import static org.opensha.sha.util.TectonicRegionType.*;
import java.util.EnumSet;
import java.util.List;

import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.nshmp.NEHRP_TestCity;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Utility class that creates instances of common <code>Parameter</code>s.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class Params {

	/**
	 * Creates a new <code>Parameter</code> for <code>SiteType</code>'s with
	 * <code>FIRM_ROCK</code> and <code>HARD_ROCK</code> choices.
	 * @return the paramater
	 * @see SiteType
	 */
	public static EnumParameter<SiteType> createSiteType() {
		return new EnumParameter<SiteType>("Site Type", EnumSet.of(FIRM_ROCK,
			HARD_ROCK), FIRM_ROCK, null);
	}

	/**
	 * Creates a new <code>Parameter</code> for a subduction event with
	 * <code>SUBDUCTION_INTERFACE</code> and <code>SUBDUCTION_SLAB</code>
	 * choices.
	 * @return the paramater
	 * @see TectonicRegionType
	 */
	public static EnumParameter<TectonicRegionType> createSubType() {
		return new EnumParameter<TectonicRegionType>("Subduction Type",
			EnumSet.of(SUBDUCTION_INTERFACE, SUBDUCTION_SLAB),
			SUBDUCTION_INTERFACE, null);
	}
	
	/**
	 * Creates a new <code>Parameter</code> for the Atkinson &amp; Boore (2003)
	 * attenuation relationship to distinguish between global and Cascadia
	 * specific coefficients.
	 * @return the paramater
	 */
	public static BooleanParameter createGlobalFlag() {
		BooleanParameter b = new BooleanParameter("Global", true);
		b.setValueAsDefault();
		return b;
	}
	
	/**
	 * Creates a new <code>Parameter</code> to filter different NSHMP source
	 * regions.
	 * @return the paramater
	 * @see SourceRegion
	 */
	public static EnumParameter<SourceRegion> createSourceRegion() {
		return new EnumParameter<SourceRegion>("Filter by region",
				EnumSet.allOf(SourceRegion.class), null, "All");
	}

	/**
	 * Creates a new <code>Parameter</code> to filter different NSHMP source
	 * types.
	 * @return the paramater
	 * @see SourceType
	 */
	public static EnumParameter<SourceType> createSourceType() {
		return new EnumParameter<SourceType>("Filter by type",
				EnumSet.allOf(SourceType.class), null, "All");
	}
	
	/**
	 * Creates a new <code>Parameter</code> to filter different NSHMP source
	 * types.
	 * @return the paramater
	 * @see SourceType
	 */
	public static StringParameter createFileParam(List<String> names, String value) {
		StringConstraint sc = new StringConstraint(names);
		value = names.contains(value) ? value : names.get(0);
		StringParameter param = new StringParameter("File", sc, value);
		return param;
	}
	
	public static StringParameter createSourceDataParam() {
		StringParameter param = new StringParameter("Info");
		param.getEditor().setEnabled(false);
		param.setNonEditable();
		return param;
	}
	
	public static EnumParameter<NEHRP_TestCity> createCityParam() {
		return new EnumParameter<NEHRP_TestCity>("Select City",
			EnumSet.allOf(NEHRP_TestCity.class), NEHRP_TestCity.MEMPHIS, null);
	}
	
	public static EnumParameter<Period> createPeriodParam() {
		return new EnumParameter<Period>("Select Period", Period.getCEUS(),
			Period.GM0P00, null);
	}	


}
