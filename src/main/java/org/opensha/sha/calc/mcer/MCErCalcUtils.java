package org.opensha.sha.calc.mcer;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.sha.cybershake.calc.HazardCurveComputation;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.util.component.ComponentConverter;
import org.opensha.sha.util.component.ComponentTranslation;

import com.google.common.base.Preconditions;

public class MCErCalcUtils {

	public static double calcMCER(double dVal, double pVal, double dLowVal) {
		double val = Math.min(pVal, Math.max(dVal, dLowVal));
		Preconditions.checkState(val > 0d, "It's zero???? pVal="+pVal+", dVal="+dVal+", dLowVal="+dLowVal);
		return val;
	}

	public static double saToPsuedoVel(double sa, double period) {
		sa *= HazardCurveComputation.CONVERSION_TO_G; // convert to cm/sec^2
		return (period/MCErCalcUtils.twoPi)*sa;
	}
	
	public static void main(String[] args) {
		System.out.println(saToPsuedoVel(1.075, 1d));
	}
	
	public static double psuedoVelToSA(double psv, double period) {
		double sa = MCErCalcUtils.twoPi*psv/period;
		sa /= HazardCurveComputation.CONVERSION_TO_G; // convert to g
		return sa;
	}

	private static final double twoPi = 2d*Math.PI;

	public static DiscretizedFunc saToPsuedoVel(DiscretizedFunc saFunc) {
		ArbitrarilyDiscretizedFunc velFunc = new ArbitrarilyDiscretizedFunc(saFunc.getName());
		
		for (int i=0; i<saFunc.size(); i++) {
			double period = saFunc.getX(i);
			double sa = saFunc.getY(i);
			double vel = saToPsuedoVel(sa, period);
			velFunc.set(period, vel);
		}
		
		return velFunc;
	}
	
	public static Component getSupportedTranslationComponent(ScalarIMR gmpe, Component[] tos) {
		ComponentTranslation trans = getSupportedComponentConverter(gmpe, tos);
		if (trans == null)
			return null;
		return trans.getToComponent();
	}
	
	public static ComponentTranslation getSupportedComponentConverter(ScalarIMR gmpe, Component[] tos) {
		try {
			ComponentParam compParam = (ComponentParam)gmpe.getParameter(ComponentParam.NAME);
			Component gmpeComp = compParam.getValue();
			Component to = getSupportedTranslationComponent(gmpeComp, tos);
			if (to == null)
				return null;
			return ComponentConverter.getConverter(gmpeComp, to);
		} catch (ParameterException e) {
			return null;
		}
	}
	
	public static Component getSupportedTranslationComponent(Component from, Component[] tos) {
		for (Component to : tos)
			if (from == to)
				return to;
		for (Component to : tos) {
			if (ComponentConverter.isConversionSupported(from, to))
				return to;
		}
		return null;
	}

}
