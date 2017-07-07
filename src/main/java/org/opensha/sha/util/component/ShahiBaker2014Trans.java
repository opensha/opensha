package org.opensha.sha.util.component;

import java.awt.geom.Point2D;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.imr.param.OtherParams.Component;

/**
 * Component translation from RotD50 to RotD100 from Table 1 of Shahi & Baker 2014.
 * 
 * @author kevin
 *
 */
public class ShahiBaker2014Trans extends ComponentTranslation {
	
	/**
	 * RotD100/RotD50 factors as a function of period from Table 1 of Shahi & Baker 2014.
	 * 
	 * We convert from the natural log values provided in the table because they ahve more
	 * significant digits.
	 */
	private static DiscretizedFunc rotD100_over_RotD50;
	
	static {
		DiscretizedFunc ln_rotd100_over_rotd50 = new ArbitrarilyDiscretizedFunc();
		
		ln_rotd100_over_rotd50.set(0.01,	0.176);
		ln_rotd100_over_rotd50.set(0.02,	0.175);
		ln_rotd100_over_rotd50.set(0.03,	0.172);
		ln_rotd100_over_rotd50.set(0.05,	0.171);
		ln_rotd100_over_rotd50.set(0.075,	0.172);
		ln_rotd100_over_rotd50.set(0.1,		0.172);
		ln_rotd100_over_rotd50.set(0.15,	0.182);
		ln_rotd100_over_rotd50.set(0.2,		0.187);
		ln_rotd100_over_rotd50.set(0.25,	0.196);
		ln_rotd100_over_rotd50.set(0.3,		0.198);
		ln_rotd100_over_rotd50.set(0.4,		0.206);
		ln_rotd100_over_rotd50.set(0.5,		0.206);
		ln_rotd100_over_rotd50.set(0.75,	0.213);
		ln_rotd100_over_rotd50.set(1.0,		0.216);
		ln_rotd100_over_rotd50.set(1.5,		0.217);
		ln_rotd100_over_rotd50.set(2.0,		0.218);
		ln_rotd100_over_rotd50.set(3.0,		0.221);
		ln_rotd100_over_rotd50.set(4.0,		0.231);
		ln_rotd100_over_rotd50.set(5.0,		0.235);
		ln_rotd100_over_rotd50.set(7.5,		0.251);
		ln_rotd100_over_rotd50.set(10.0,	0.258);
		
		rotD100_over_RotD50 = new ArbitrarilyDiscretizedFunc();
		
		for (Point2D pt : ln_rotd100_over_rotd50)
			rotD100_over_RotD50.set(pt.getX(), Math.exp(pt.getY()));
	}

	@Override
	public Component getFromComponent() {
		return Component.RotD50;
	}

	@Override
	public Component getToComponent() {
		return Component.RotD100;
	}

	@Override
	public double getScalingFactor(double period)
			throws IllegalArgumentException {
		assertValidPeriod(period);
		return rotD100_over_RotD50.getInterpolatedY(period);
	}

	@Override
	public double getMinPeriod() {
		return rotD100_over_RotD50.getMinX();
	}

	@Override
	public double getMaxPeriod() {
		return rotD100_over_RotD50.getMaxX();
	}

	@Override
	public String getName() {
		return "Shahi & Baker 2014";
	}

}
