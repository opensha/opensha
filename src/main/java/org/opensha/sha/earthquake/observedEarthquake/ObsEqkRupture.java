package org.opensha.sha.earthquake.observedEarthquake;

import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;

/**
 * <p>Title: ObsEqkRupture </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2002</p>
 *
 * <p>Company: </p>
 *
 * @author rewritten by Ned Field
 * @version 1.0
 */
public class ObsEqkRupture extends EqkRupture implements java.io.Serializable{

	protected String eventId;
	protected long originTimeInMillis;	

	public ObsEqkRupture(){}

	
	/**
	 * This constructor sets the rupture surface as a point source at the given hypocenter
	 * @param eventId
	 * @param originTimeInMillis
	 * @param hypoLoc
	 * @param mag
	 */
	public ObsEqkRupture(String eventId, long originTimeInMillis, 
			Location hypoLoc, double mag) {
		super(mag,0,null,hypoLoc);
		//making the Obs Rupture Surface to just be the hypocenter location.
		PointSurface surface = new PointSurface(hypoLoc);
		this.setRuptureSurface(surface);
		this.eventId = eventId;
		this.originTimeInMillis = originTimeInMillis;
	}

	public String getEventId() {
		return eventId;
	}
	
	public void setEventId(String eventId) {
		this.eventId=eventId;
	}

	/**
	 * This returns the origin time of this event as a GregorianCalendar for UTC time zone
	 */
	public GregorianCalendar getOriginTimeCal() {
		// GregorianCalendar cal = new GregorianCalendar();
		GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(originTimeInMillis);
		return cal;
	}	

	/**
	 * This sets the origin time of this event from the given GregorianCalendar
	 */
	public void setOriginTimeCal(GregorianCalendar origTimeCal) {
		originTimeInMillis = origTimeCal.getTimeInMillis();
	}

	/**
	 * This returns the origin time of this event in UTC milliseconds from the epoch
	 * (see GregorianCalendar.setTimeInMillis() for details)
	 * @return 
	 */
	public long getOriginTime() {
		return this.originTimeInMillis;
	}
	
	/**
	 * This sets the origin time of this event in UTC milliseconds from the epoch
	 * (see GregorianCalendar.setTimeInMillis() for details)
	 */
	public void setOriginTime(long originTimeInMillis) {
		this.originTimeInMillis=originTimeInMillis;
	}

	/**
	 * Checks whether eventId and mag of the given ObsEqkRupture 
	 * is the same as this instance; nothing else is checked!
	 * @return boolean
	 */
	public boolean equalsObsEqkRupEvent(ObsEqkRupture obsRupEvent){

		if(!eventId.equals(obsRupEvent.getEventId()) ||
				getMag() != obsRupEvent.getMag())
			return false;
		else
			return true;
	}
	

	/**
	 * this calls equalsObsEqkRupEvent() if the Object is of type ObsEqkRupture

	 */
	public boolean equals(Object obj) {
		if (obj instanceof ObsEqkRupture) 
			return equalsObsEqkRupEvent((ObsEqkRupture) obj);
		return false;
	}


	/**
	 * Returns an info String for the Observed EqkRupture
	 * @return String
	 */
	public String getInfo(){
		String obsEqkInfo = super.getInfo();
		obsEqkInfo += "EventId ="+eventId+"\n";
		obsEqkInfo += "OriginTimeInMillis ="+originTimeInMillis+"\n";
		return obsEqkInfo;
	}


	/**
	 * Clones the eqk rupture and returns the new cloned object
	 * @return
	 */
	public Object clone() {
		ObsEqkRupture eqkEventClone=new ObsEqkRupture();
		eqkEventClone.setEventId(eventId);
		eqkEventClone.setMag(mag);
		eqkEventClone.setRuptureSurface(getRuptureSurface());
		eqkEventClone.setHypocenterLocation(hypocenterLocation);
		eqkEventClone.setOriginTime(originTimeInMillis);
		eqkEventClone.setAveRake(aveRake);
		return eqkEventClone;
	}


}
