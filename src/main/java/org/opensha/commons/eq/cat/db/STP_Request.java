package org.opensha.commons.eq.cat.db;

import static org.opensha.commons.eq.cat.CatTools.MAG_MAX;
import static org.opensha.commons.eq.cat.CatTools.MAG_MIN;
import static org.opensha.commons.geo.GeoTools.DEPTH_MAX;
import static org.opensha.commons.geo.GeoTools.DEPTH_MIN;
import static org.opensha.commons.geo.GeoTools.LAT_MAX;
import static org.opensha.commons.geo.GeoTools.LAT_MIN;
import static org.opensha.commons.geo.GeoTools.LON_MAX;
import static org.opensha.commons.geo.GeoTools.LON_MIN;
import static org.opensha.commons.geo.GeoTools.validateDepth;
import static org.opensha.commons.geo.GeoTools.validateLat;
import static org.opensha.commons.geo.GeoTools.validateLon;

import java.util.Calendar;
import java.util.TimeZone;

import org.opensha.commons.eq.cat.CatTools;
import org.opensha.commons.eq.cat.util.EventType;

/**
 * STP request manager. See the <a
 * href="http://www.scecdc.scec.org/STP/stp.html">STP Guide</a> for details on
 * request formatting.
 * 
 * @author Peter Powers
 * @version $Id: STP_Request.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class STP_Request {

	/** Preset request that can be used to get earthquakes in the past week. */
	public static final String RECENT_EQ_WEEK = "event -t0 -7d +7d -type le";

	private Long dateMax;
	private Long dateMin;
	private Double latitudeMin;
	private Double latitudeMax;
	private Double longitudeMin;
	private Double longitudeMax;
	private Double depthMin;
	private Double depthMax;
	private Double magnitudeMin;
	private Double magnitudeMax;
	private EventType eventType;

	// TODO clean up CatUtils refs in jdocs

	// TODO delete ////
	private static String exampleReq = "event -t0 2002/09/01 2002/09/30 -mag 3.5 7.0 -type le";

	public static void main(String[] args) {
		STP_Request req = new STP_Request();
		req.setDateMin(new Long(1237880234));
		req.setMagnitudeMax(new Double(6));
		req.setLatitudeMin(new Double(23));
		req.setLatitudeMax(new Double(30));
		req.setEventType(EventType.NUCLEAR);
		req.setDepthMax(new Double(10));
		req.setLongitudeMin(new Double(-120));

		System.out.println(req.toString());
	}

	// ////////////////

	/**
	 * Returns the current request parameters in STP format.
	 */
	@Override
	public String toString() {
		return "event " + getDateParams() + getLongitudeParams()
			+ getLatitudeParams() + getDepthParams() + getMagnitudeParams()
			+ getTypeParam() + "\n";
	}

	/*
	 * Class uses the STP option of formatting time in seconds; the
	 * implmentation below drops fractional second information. If only one date
	 * value is set the null value is set as follows: minDate = 0 (January 1,
	 * 1970, 00:00:00 UTC), maxDate = current time in milliseconds UTC.
	 */
	private String getDateParams() {
		if (dateMax == null && dateMin == null) return "";
		String min = (dateMin != null) ? String.valueOf(dateMin / 1000) : "0";
		String max = (dateMax != null) ? String.valueOf(dateMax / 1000)
			: String.valueOf(Calendar.getInstance(TimeZone.getTimeZone("UTC"))
				.getTimeInMillis());
		return "-t0 " + min + "u " + max + "u ";
	}

	private String getLongitudeParams() {
		if (longitudeMax == null && longitudeMin == null) return "";
		String min = (longitudeMin != null) ? longitudeMin.toString() : String
			.valueOf(LON_MIN);
		String max = (longitudeMax != null) ? longitudeMax.toString() : String
			.valueOf(LON_MAX);
		return "-lon " + min + " " + max + " ";
	}

	private String getLatitudeParams() {
		if (latitudeMax == null && latitudeMin == null) return "";
		String min = (latitudeMin != null) ? latitudeMin.toString() : String
			.valueOf(LAT_MIN);
		String max = (latitudeMax != null) ? latitudeMax.toString() : String
			.valueOf(LAT_MAX);
		return "-lat " + min + " " + max + " ";
	}

	private String getDepthParams() {
		if (depthMax == null && depthMin == null) return "";
		String min = (depthMin != null) ? depthMin.toString() : String
			.valueOf(DEPTH_MIN);
		String max = (depthMax != null) ? depthMax.toString() : String
			.valueOf(DEPTH_MAX);
		return "-depth " + min + " " + max + " ";
	}

	private String getMagnitudeParams() {
		if (magnitudeMax == null && magnitudeMin == null) return "";
		String min = (magnitudeMin != null) ? magnitudeMin.toString() : String
			.valueOf(MAG_MIN);
		String max = (magnitudeMax != null) ? magnitudeMax.toString() : String
			.valueOf(MAG_MAX);
		return "-mag " + min + " " + max + " ";
	}

	private String getTypeParam() {
		if (eventType == null) return "";
		return "-type " + eventType.id() + " ";
	}

	/**
	 * Returns the maximum (later) cutoff date in UTC milliseconds.
	 * 
	 * @return the maximum date; may be <code>null</code>
	 */
	public long getDateMax() {
		return dateMax;
	}

	/**
	 * Sets the maximum cutoff date in UTC milliseconds.
	 * 
	 * @param dateMax the maximum date to set; can be <code>null</code>
	 * @throws IllegalArgumentException if minimum is not <code>null</code> and
	 *         <code>dateMax</code> is smaller than minimum
	 */
	public void setDateMax(Long dateMax) {
		if (dateMax != null) {
			if (dateMin != null && dateMax < dateMin) {
				throw new IllegalArgumentException("STP request: Max < Min");
			}
		}
		this.dateMax = dateMax;
	}

	/**
	 * Returns the minimum (earlier) cutoff date in UTC milliseconds.
	 * 
	 * @return the minimum date; may be <code>null</code>
	 */
	public long getDateMin() {
		return dateMin;
	}

	/**
	 * Sets the minimum (earlier) cutoff date in UTC milliseconds.
	 * 
	 * @param dateMin the minimum date to set; can be <code>null</code>
	 * @throws IllegalArgumentException if maximum is not <code>null</code> and
	 *         <code>dateMin</code> is larger than maximum
	 */
	public void setDateMin(Long dateMin) {
		if (dateMin != null) {
			if (dateMax != null && dateMin > dateMax) {
				throw new IllegalArgumentException("STP request: Min > Max");
			}
		}
		this.dateMin = dateMin;
	}

	/**
	 * Returns the maximum cutoff depth (Depth values are positive).
	 * 
	 * @return the maximum depth; may be <code>null</code>
	 */
	public double getDepthMax() {
		return depthMax;
	}

	/**
	 * Sets the maximum cutoff depth (Depth values are positive).
	 * 
	 * @param depthMax the maximum depth to set; can be <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if minimum
	 *         is not <code>null</code> and <code>depthMax</code> is smaller
	 *         than minimum
	 * @see CatTools for range values
	 */
	public void setDepthMax(Double depthMax) {
		if (depthMax != null) {
			validateDepth(depthMax);
			if (depthMin != null && depthMax < depthMin) {
				throw new IllegalArgumentException("STP request: Max < Min");
			}
		}
		this.depthMax = depthMax;
	}

	/**
	 * Returns the minimum cutoff depth (Depth values are positive).
	 * 
	 * @return the minimum depth; may be <code>null</code>
	 */
	public double getDepthMin() {
		return depthMin;
	}

	/**
	 * Sets the minimum cutoff depth (Depth values are positive).
	 * 
	 * @param depthMin the minimum depth to set; can be <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if maximum
	 *         is not <code>null</code> and <code>depthMin</code> is larger than
	 *         maximum
	 * @see CatTools for range values
	 */
	public void setDepthMin(Double depthMin) {
		if (depthMin != null) {
			validateDepth(depthMin);
			if (depthMax != null && depthMin > depthMax) {
				throw new IllegalArgumentException("STP request: Min > Max");
			}
		}
		this.depthMin = depthMin;
	}

	/**
	 * Returns the event type to select.
	 * 
	 * @return the event type; may be <code>null</code>
	 */
	public EventType getEventType() {
		return eventType;
	}

	/**
	 * Sets the event type to select.
	 * 
	 * @param eventType the event type to set; can be <code>null</code>
	 */
	public void setEventType(EventType eventType) {
		this.eventType = eventType;
	}

	/**
	 * Returns the maximum cutoff latitude.
	 * 
	 * @return the maximum latitude; may be <code>null</code>
	 */
	public double getLatitudeMax() {
		return latitudeMax;
	}

	/**
	 * Sets the maximum cutoff latitude.
	 * 
	 * @param latitudeMax the maximum latitude to set; can be <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if minimum
	 *         is not <code>null</code> and <code>latitudeMax</code> is smaller
	 *         than minimum
	 * @see CatTools for range values
	 */
	public void setLatitudeMax(Double latitudeMax) {
		if (latitudeMax != null) {
			validateLat(latitudeMax);
			if (latitudeMin != null && latitudeMax < latitudeMin) {
				throw new IllegalArgumentException("STP request: Max < Min");
			}
		}
		this.latitudeMax = latitudeMax;
	}

	/**
	 * Returns the minimum cutoff latitude.
	 * 
	 * @return the mimimum latitude; may be <code>null</code>
	 */
	public double getLatitudeMin() {
		return latitudeMin;
	}

	/**
	 * Sets the minimum cutoff latitude.
	 * 
	 * @param latitudeMin the minimum latitude to set; can be <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if maximum
	 *         is not <code>null</code> and <code>latitudeMin</code> is larger
	 *         than maximum
	 * @see CatTools for range values
	 */
	public void setLatitudeMin(Double latitudeMin) {
		if (latitudeMin != null) {
			validateLat(latitudeMin);
			if (latitudeMax != null && latitudeMin > latitudeMax) {
				throw new IllegalArgumentException("STP request: Min > Max");
			}
		}
		this.latitudeMin = latitudeMin;
	}

	/**
	 * Returns the maximum cutoff longitude.
	 * 
	 * @return the maximum longitude; may be <code>null</code>
	 */
	public double getLongitudeMax() {
		return longitudeMax;
	}

	/**
	 * Sets the maximum cutoff longitude.
	 * 
	 * @param longitudeMax the maximum longitude to set; can be
	 *        <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if minimum
	 *         is not <code>null</code> and <code>longitudeMax</code> is smaller
	 *         than minimum
	 * @see CatTools for range values
	 */
	public void setLongitudeMax(Double longitudeMax) {
		if (longitudeMax != null) {
			validateLon(longitudeMax);
			if (longitudeMin != null && longitudeMax < longitudeMin) {
				throw new IllegalArgumentException("STP request: Max < Min");
			}
		}
		this.longitudeMax = longitudeMax;
	}

	/**
	 * Returns the minimum cutoff longitude.
	 * 
	 * @return the minimum longitude; may be <code>null</code>
	 */
	public double getLongitudeMin() {
		return longitudeMin;
	}

	/**
	 * Sets the minimum cutoff longitude.
	 * 
	 * @param longitudeMin the minimum longitude to set; can be
	 *        <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if maximum
	 *         is not <code>null</code> and <code>longitudeMin</code> is larger
	 *         than maximum
	 * @see CatTools for range values
	 */
	public void setLongitudeMin(Double longitudeMin) {
		if (longitudeMin != null) {
			validateLon(longitudeMin);
			if (longitudeMax != null && longitudeMin > longitudeMax) {
				throw new IllegalArgumentException("STP request: Min > Max");
			}
		}
		this.longitudeMin = longitudeMin;
	}

	/**
	 * Returns the maximum cutoff magnitude.
	 * 
	 * @return the maximum magnitude; may be <code>null</code>
	 */
	public Double getMagnitudeMax() {
		return magnitudeMax;
	}

	/**
	 * Sets the maximum cutoff magnitude.
	 * 
	 * @param magnitudeMax the maximum magnitude to set; can be
	 *        <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if minimum
	 *         is not <code>null</code> and <code>magnitudeMax</code> is smaller
	 *         than minimum
	 * @see CatTools for range values
	 */
	public void setMagnitudeMax(Double magnitudeMax) {
		if (magnitudeMax != null) {
			CatTools.validateMag(magnitudeMax);
			if (magnitudeMin != null && magnitudeMax < magnitudeMin) {
				throw new IllegalArgumentException("STP request: Max < Min");
			}
		}
		this.magnitudeMax = magnitudeMax;
	}

	/**
	 * Returns the minimum cutoff magnitude.
	 * 
	 * @return the minimum magnitude; may be <code>null</code>
	 */
	public Double getMagnitudeMin() {
		return magnitudeMin;
	}

	/**
	 * Sets the the minimum cutoff magnitude.
	 * 
	 * @param magnitudeMin the minimum magnitude to set; can be
	 *        <code>null</code>
	 * @throws IllegalArgumentException if value is out of range or if maximum
	 *         is not <code>null</code> and <code>magnitudeMin</code> is larger
	 *         than maximum
	 * @see CatTools for range values
	 */
	public void setMagnitudeMin(Double magnitudeMin) {
		if (magnitudeMin != null) {
			CatTools.validateMag(magnitudeMin);
			if (magnitudeMax != null && magnitudeMin > magnitudeMax) {
				throw new IllegalArgumentException("STP request: Min > Max");
			}
		}
		this.magnitudeMin = magnitudeMin;
	}
}
