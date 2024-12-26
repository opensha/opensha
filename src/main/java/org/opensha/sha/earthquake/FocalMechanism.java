package org.opensha.sha.earthquake;

import java.io.Serializable;
import java.util.Objects;

/**
 * <p>Title: FocalMechanism</p>
 *
 * <p>Description: This class allows to set the Focal Mechanism. Default values are Double.NaN</p>
 * @author Nitin Gupta & Ned Field
 * @version 1.0
 */
public class FocalMechanism implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private double strike=Double.NaN,dip=Double.NaN,rake=Double.NaN;

	/**
	 * Class default constructor
	 */
	public FocalMechanism() {}

	/**
	 *
	 * @param strike double
	 * @param dip double
	 * @param rake double
	 */
	public FocalMechanism(double strike, double dip, double rake){
		this.strike = strike;
		this.dip = dip;
		this.rake = rake;
	}

	public double getDip() {
		return dip;
	}

	public double getRake() {
		return rake;
	}

	public double getStrike() {
		return strike;
	}

	public void setDip(double dip) {
		this.dip = dip;
	}

	public void setRake(double rake) {
		this.rake = rake;
	}

	public void setStrike(double strike) {
		this.strike = strike;
	}

	public void setAll(double strike, double dip, double rake) {
		this.strike = strike;
		this.dip = dip;
		this.rake = rake;
	}

	public FocalMechanism copy() {
		return new FocalMechanism(strike,dip,rake);
	}
	
	public Unmodifiable unmodifiable() {
		return new Unmodifiable(strike, dip, rake);
	}

	@Override
	public int hashCode() {
		return Objects.hash(dip, rake, strike);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof FocalMechanism)) // can be a subclass
			return false;
		FocalMechanism other = (FocalMechanism) obj;
		return Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip)
				&& Double.doubleToLongBits(rake) == Double.doubleToLongBits(other.rake)
				&& Double.doubleToLongBits(strike) == Double.doubleToLongBits(other.strike);
	}
	
	@Override
	public String toString() {
		return "FocalMechanism [strike=" + strike + ", dip=" + dip + ", rake=" + rake + "]";
	}

	public static class Unmodifiable extends FocalMechanism {

		public Unmodifiable(FocalMechanism mech) {
			super(mech.strike, mech.dip, mech.rake);
		}

		public Unmodifiable(double strike, double dip, double rake) {
			super(strike, dip, rake);
		}

		@Override
		public void setDip(double dip) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setRake(double rake) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setStrike(double strike) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAll(double strike, double dip, double rake) {
			throw new UnsupportedOperationException();
		}
		
	}

}
