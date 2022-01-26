package org.opensha.sha.earthquake;

import java.io.Serializable;

/**
 * <p>Title: FocalMechanism</p>
 *
 * <p>Description: This class allows to set the Focal Mechanism</p>
 * @author Nitin Gupta
 * @version 1.0
 */
public class FocalMechanism implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private double strike,dip,rake;

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
		this.rake = rake;
		this.dip = dip;
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

	public void setFocalMechanism(double dip, double rake, double strike){
		this.dip = dip;
		this.rake = rake;
		this.strike = strike;
	}

	public FocalMechanism copy() {
		return new FocalMechanism(strike,dip,rake);
	}


}
