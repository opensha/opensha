package org.opensha.sha.simulators.stiffness;

import java.math.BigDecimal;

/**
 * Internal two-double arithmetic for the triangle correction (about 31 decimal
 * digits). The low component retains rounding residuals from the high component.
 * This is fixed-precision arithmetic, not a replacement for arbitrary precision.
 */
final class DoubleDouble {
	static final DoubleDouble ZERO=new DoubleDouble(0,0), ONE=new DoubleDouble(1,0),
			TWO=new DoubleDouble(2,0), THREE=new DoubleDouble(3,0), FOUR=new DoubleDouble(4,0);
	final double hi, lo;

	private DoubleDouble(double hi, double lo) { this.hi=hi; this.lo=lo; }

	static DoubleDouble of(double value) {
		if (value == 0) return ZERO;
		if (value == 1) return ONE;
		return new DoubleDouble(value,0);
	}

	/** Used for constants only; the evaluation path does not parse decimals. */
	static DoubleDouble parse(String value) {
		BigDecimal decimal=new BigDecimal(value);
		double hi=decimal.doubleValue();
		double lo=decimal.subtract(new BigDecimal(hi)).doubleValue();
		return new DoubleDouble(hi,lo);
	}

	private static DoubleDouble normalized(double a, double b) {
		double high=a+b;
		return new DoubleDouble(high,b-(high-a));
	}

	DoubleDouble add(DoubleDouble b) {
		// TwoSum recovers the rounding residual even if the leading terms cancel.
		double sum=hi+b.hi, virtual=sum-hi;
		double error=(hi-(sum-virtual))+(b.hi-virtual);
		error+=lo+b.lo;
		return normalized(sum,error);
	}

	DoubleDouble subtract(DoubleDouble b) {
		double sum=hi-b.hi, virtual=sum-hi;
		double error=(hi-(sum-virtual))-(b.hi+virtual);
		error+=lo-b.lo;
		return normalized(sum,error);
	}

	DoubleDouble multiply(DoubleDouble b) {
		double product=hi*b.hi;
		// FMA obtains the product residual without another rounding of hi*b.hi.
		double error=Math.fma(hi,b.hi,-product);
		error+=hi*b.lo+lo*b.hi;
		return normalized(product,error);
	}

	DoubleDouble divide(DoubleDouble b) {
		double first=hi/b.hi;
		DoubleDouble remainder=subtract(b.multiply(of(first)));
		double second=remainder.hi/b.hi;
		remainder=remainder.subtract(b.multiply(of(second)));
		double third=remainder.hi/b.hi;
		return of(first).add(of(second).add(of(third)));
	}

	DoubleDouble sqrt() {
		if (hi == 0 && lo == 0) return ZERO;
		double first=Math.sqrt(hi);
		DoubleDouble residual=subtract(of(first).multiply(of(first)));
		return of(first).add(residual.divide(of(2*first)));
	}

	DoubleDouble negate() { return new DoubleDouble(-hi,-lo); }
	int signum() { return hi>0 ? 1 : hi<0 ? -1 : lo>0 ? 1 : lo<0 ? -1 : 0; }
	double doubleValue() { return hi+lo; }
}
