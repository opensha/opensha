package org.opensha.sha.imr.param.IntensityMeasureParams;

/**
 * The interval, expressed as percentiles of total intensity, for which the significant
 * shaking duration is computed.
 * 
 * @author kevin
 *
 */
public enum DurationTimeInterval {
	
	INTERVAL_5_75("5-75%"),
	INTERVAL_5_95("5-95%"),
	INTERVAL_20_80("20-80%");
	
	private String name;

	private DurationTimeInterval(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}

}
