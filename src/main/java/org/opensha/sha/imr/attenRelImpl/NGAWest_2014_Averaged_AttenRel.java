package org.opensha.sha.imr.attenRelImpl;

import java.util.ArrayList;

import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

import com.google.common.collect.Lists;

public class NGAWest_2014_Averaged_AttenRel extends MultiIMR_Averaged_AttenRel {
	
	public static final String NAME = "NGAWest2 2014 Averaged Attenuation Relationship";
	public static final String SHORT_NAME = "NGAWest_2014";
	
	private static ArrayList<ScalarIMR> buildIMRs(ParameterChangeWarningListener listener,
			boolean idriss) {
		ArrayList<ScalarIMR> imrs = new ArrayList<ScalarIMR>();
		imrs.add(AttenRelRef.ASK_2014.instance(listener));
		imrs.add(AttenRelRef.BSSA_2014.instance(listener));
		imrs.add(AttenRelRef.CB_2014.instance(listener));
		imrs.add(AttenRelRef.CY_2014.instance(listener));
		if (idriss)
			imrs.add(AttenRelRef.IDRISS_2014.instance(listener));
		for (ScalarIMR imr : imrs) {
			imr.setParamDefaults();
		}
		return imrs;
	}
	
	private static ArrayList<Double> getWeights(boolean idriss) {
		if (idriss)
			return Lists.newArrayList(0.22, 0.22, 0.22, 0.22, 0.12);
		return Lists.newArrayList(0.22, 0.22, 0.22, 0.22);
	}
	
	public NGAWest_2014_Averaged_AttenRel(ParameterChangeWarningListener listener) {
		this(listener, true);
	}
	
	public NGAWest_2014_Averaged_AttenRel(ParameterChangeWarningListener listener, boolean idriss) {
		super(buildIMRs(listener, idriss), getWeights(idriss));
	}
	
	public static class NGAWest_2014_Averaged_AttenRel_NoIdriss extends NGAWest_2014_Averaged_AttenRel {
		
		public static final String NAME = "NGAWest2 2014 Averaged No Idriss";
		public static final String SHORT_NAME = "NGAWest_2014_NoIdr";

		public NGAWest_2014_Averaged_AttenRel_NoIdriss(
				ParameterChangeWarningListener listener) {
			super(listener, false);
		}

		@Override
		public String getShortName() {
			return SHORT_NAME;
		}

		@Override
		public String getName() {
			return NAME;
		}
		
		
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

}
