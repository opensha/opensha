package org.opensha.sha.faultSurface;

import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.Named;
import org.opensha.commons.geo.Region;
import org.opensha.commons.metadata.XMLSaveable;

public interface FaultSection extends Named, XMLSaveable {
	
	/**
	 * This returns the date of last event in UTC milliseconds from the epoch
	 * (see GregorianCalendar.setTimeInMillis() for details).  A value of
	 * Long.MIN_VALUE means it's not available (this is the default).
	 * @param dateOfLastEventMillis
	 */
	public long getDateOfLastEvent();
	
	/**
	 * This sets the date of last event in UTC milliseconds from the epoch
	 * (see GregorianCalendar.setTimeInMillis() for details).
	 * @param dateOfLastEventMillis
	 */
	public void setDateOfLastEvent(long dateOfLastEventMillis);
	
	/**
	 * Defined as a reduction of area between the upper and lower seismogenic depths
	 * @return
	 */
	public double getAseismicSlipFactor();
	
	/**
	 * Defined as a reduction of area between the upper and lower seismogenic depths
	 * @return
	 */
	public double getCouplingCoeff();
	
	/**
	 * This returns the average dip (degrees)
	 * @return
	 */
	public double getAveDip();
	
	/**
	 * This returns the slip rate (mm/yr) unmodified by the coupling coefficient
	 * @return
	 */
	public double getOrigAveSlipRate();
	
	/**
	 * This returns the product of the slip rate (mm/yr) times the coupling coefficient
	 * @return
	 */
	public default double getReducedAveSlipRate() {
		return getOrigAveSlipRate()*getCouplingCoeff();
	}
	
	/**
	 * This returns the average lower seismogenic depth in km
	 * @return
	 */
	public double getAveLowerDepth();
	
	/**
	 * This gets the average rake in degrees
	 * @return
	 */
	public double getAveRake();
	
	/**
	 * This returns the upper seismogenic (km) depth that has not been modified
	 * by the aseismicity factor
	 * @return
	 */
	public double getOrigAveUpperDepth();
	
	/**
	 * This returns the upper seismogenic (km) depth that has been modified
	 * by the aseismicity factor
	 * @return
	 */
	public default double getReducedAveUpperDepth() {
		double depthToReduce = getAseismicSlipFactor()*(getAveLowerDepth() - getOrigAveUpperDepth());
		return getOrigAveUpperDepth() + depthToReduce;
	}
	
	/**
	 * This returns the dip direction (degrees)
	 * @return
	 */
	public float getDipDirection();
	
	/**
	 * This returns the fault trace
	 * @return
	 */
	public FaultTrace getFaultTrace();
	
	/**
	 * This returns the section ID 
	 * @return
	 */
	public int getSectionId();
	
	/**
	 * This is the ID of the parent section if this is a subsection
	 */
	public int getParentSectionId();
	
	/**
	 * This is the name of the parent section if this is a subsection (null otherwise)
	 */
	public String getParentSectionName();
	
	/**
	 * this returns the length of the trace in km.
	 * @return
	 */
	public default double getTraceLength() {
		return getFaultTrace().getTraceLength();
	}
	
	/**
	 * This returns the original down dip width in km (unmodified by the aseismicity factor)
	 * @return
	 */
	public default double getOrigDownDipWidth() {
		return (getAveLowerDepth()-getOrigAveUpperDepth())/Math.sin(getAveDip()*Math.PI/ 180);
	}
	
	/**
	 * This returns the down-dip width (km) reduced by the aseismicity factor
	 * @return
	 */
	public default double getReducedDownDipWidth() {
		return getOrigDownDipWidth()*(1.0-getAseismicSlipFactor());
	}

	/**
	 * Get a list of all sub sections.  This version makes the subsection names the same as the parent plus " Subsection: #+1" and
	 * subsection IDs = 1000*parentId+#, where # is the ith subsection
	 * 
	 * @param maxSubSectionLen
	 * @return
	 */
	public default List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen) {
		return getSubSectionsList(maxSubSectionLen, 1000*getSectionId());
	}
	
	/**
	 * Get a list of all sub sections.  This version makes the subsection names the same as the parent plus " Subsection: #+1" and
	 * subsection IDs = startId+#, where # is the ith subsection
	 * 
	 * @param maxSubSectionLen
	 * @param startId - the index of the first subsection
	 * @return
	 */
	public default List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen, int startId) {
		return getSubSectionsList(maxSubSectionLen, startId, 1);
	}
	
	/**
	 * Get a list of all sub sections.  This version makes the subsection names the same as the parent plus " Subsection: #+1" and
	 * subsection IDs = startId+#, where # is the ith subsection
	 * 
	 * @param maxSubSectionLen
	 * @param startId - the index of the first subsection
	 * @param minSubSections minimum number of sub sections to generate
	 * @return
	 */
	public List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen, int startId, int minSubSections);

	/**
	 * This returns the slip rate standard deviation (mm/yr) (not modified by the coupling coefficient)
	 * @return
	 */
	public double getOrigSlipRateStdDev();

	/**
	 * This returns the product of the slip rate standard deviation (mm/yr) times the coupling coefficient
	 * @return
	 */
	public default double getReducedSlipRateStdDev() {
		return getOrigSlipRateStdDev()*getCouplingCoeff();
	}

	/**
	 * @return true if this is a connector fault
	 */
	public boolean isConnector();

	public Region getZonePolygon();
	
	public Element toXMLMetadata(Element root, String name);
	
	/**
	 * 
	 * @param creepReduced 0 whether or not to apply aseismicity
	 * @return area of this section in square meters
	 */
	public double getArea(boolean creepReduced);
	
	/**
	 * This calculates the moment rate of this fault section
	 * @param creepReduced - whether or not to apply aseismicity and coupling coefficient
	 * @return moment rate in SI units (Newton-Meters/year)
	 */
	public default double calcMomentRate(boolean creepReduced) {
		double area = getArea(creepReduced);
		double slipRate = creepReduced ? getReducedAveSlipRate() : getOrigAveSlipRate();
			
		return FaultMomentCalc.getMoment(area, slipRate*1e-3);
	}
}
