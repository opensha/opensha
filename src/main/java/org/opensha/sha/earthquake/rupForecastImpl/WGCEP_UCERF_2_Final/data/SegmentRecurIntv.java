package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.data;

import java.util.ArrayList;

/**
 * It saves the low, high and mean recurrence interval for segments on the A-faults
 *  
 * @author vipingupta
 *
 */
public class SegmentRecurIntv {
	private String faultName;
	private ArrayList meanRecurIntv = new ArrayList();
	private ArrayList lowRecurIntv = new ArrayList();
	private ArrayList highRecurIntv= new ArrayList();
	
	public SegmentRecurIntv(String faultName) {
		this.faultName = faultName;
	}
	
	/**
	 * Add mean recurrence interval
	 * @param recurIntv
	 */
	public void addMeanRecurIntv(double recurIntv) {
		meanRecurIntv.add(new Double(recurIntv));
	}
	
	/**
	 * Add low recurrence interval
	 * @param recurIntv
	 */
	public void addLowRecurIntv(double recurIntv) {
		lowRecurIntv.add(new Double(recurIntv));
	}
	
	/**
	 * Add high recurrence interval
	 * @param recurIntv
	 */
	public void addHighRecurIntv(double recurIntv) {
		highRecurIntv.add(new Double(recurIntv));
	}
	
	/**
	 * Get mean recurrence interval
	 * @param recurIntv
	 */
	public double getMeanRecurIntv(int segIndex) {
		return ((Double)meanRecurIntv.get(segIndex)).doubleValue();
	}
	
	/**
	 * Get low recurrence interval
	 * @param recurIntv
	 */
	public double getLowRecurIntv(int segIndex) {
		return ((Double)lowRecurIntv.get(segIndex)).doubleValue();
	}
	
	/**
	 * Get high recurrence interval
	 * @param recurIntv
	 */
	public double getHighRecurIntv(int segIndex) {
		return  ((Double)highRecurIntv.get(segIndex)).doubleValue();
	}
	
	/**
	 * Get fault name
	 * @return
	 */
	public String getFaultName() {
		return this.faultName;
	}
	
}
