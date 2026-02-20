package org.opensha.sha.calc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;

import org.opensha.sha.imr.attenRelImpl.BJF_1997_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;

public class FakeTRTBasedIMR extends BJF_1997_AttenRel {

	private EnumSet<TectonicRegionType> tectonicRegionTypes;
	private TectonicRegionType defaultTRT;
	
	public FakeTRTBasedIMR(EnumSet<TectonicRegionType> tectonicRegionTypes, TectonicRegionType defaultTRT) {
		super(null);
		this.tectonicRegionTypes = tectonicRegionTypes;
		this.defaultTRT = defaultTRT;
		setParamDefaults();
		setIntensityMeasure(SA_Param.NAME);
	}
	
	public FakeTRTBasedIMR(TectonicRegionType trt) {
		super(null);
		tectonicRegionTypes = EnumSet.of(trt);
		defaultTRT = trt;
		setParamDefaults();
		setIntensityMeasure(SA_Param.NAME);
	}
	
	@Override
	public void setParamDefaults() {
		super.setParamDefaults();
		otherParams.removeParameter(TectonicRegionTypeParam.NAME);
		tectonicRegionTypeParam = new TectonicRegionTypeParam(tectonicRegionTypes, defaultTRT);
		tectonicRegionTypeParam.setValueAsDefault();
		otherParams.addParameter(tectonicRegionTypeParam);
	}

}
