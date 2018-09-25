/**
 * 
 */
package org.opensha.refFaultParamDb.vo;

import java.util.ArrayList;
import java.util.Iterator;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.Named;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.faultSurface.SimpleFaultData;

/**
 * This class contains preferred fault section data (rather than the estimates) from  FaultSectionData.
 * 
 *
 */
public class FaultSectionPrefData  implements Named, java.io.Serializable, XMLSaveable, Cloneable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final String XML_METADATA_NAME = "FaultSectionPrefData";

	private int sectionId=-1;
	private String sectionName;
	private String shortName;
	private double aveLongTermSlipRate;
	private double slipRateStdDev;
	private double aveDip;
	private double aveRake;
	private double aveUpperDepth;
	private double aveLowerDepth;
	private boolean connector;
	private Region zonePolygon;
	/**
	 * aseismicSlipFactor is defined as the reduction of area between the upper and lower seismogenic depths
	 */
	private double aseismicSlipFactor=0;
	/**
	 * couplingCoeff is defined as the reduction of slip rate between the upper and lower seismogenic depths
	 */
	private double couplingCoeff=1;
	private FaultTrace faultTrace;
	private float dipDirection;
	private String parentSectionName;
	private int parentSectionId=-1;
	private long dateOfLastEventMillis = Long.MIN_VALUE;
	private double slipInLastEvent = Double.NaN;
	
	// for the stirling surface:
	double lastGridSpacing = Double.NaN; 
	boolean lastPreserveGridSpacingExactly;
	boolean lastAseisReducesArea;
	StirlingGriddedSurface stirlingGriddedSurface=null;
	
	// or for a quad surface
	QuadSurface quadSurf=null;

	public String getShortName() {
		return this.shortName;
	}

	public void setFaultSectionPrefData(FaultSectionPrefData faultSectionPrefData) {
		sectionId = faultSectionPrefData.getSectionId();
		sectionName= faultSectionPrefData.getSectionName();
		parentSectionId = faultSectionPrefData.getParentSectionId();
		parentSectionName = faultSectionPrefData.getParentSectionName();
		shortName= faultSectionPrefData.getShortName();
		aveLongTermSlipRate= faultSectionPrefData.getOrigAveSlipRate();
		slipRateStdDev=faultSectionPrefData.getOrigSlipRateStdDev();
		aveDip= faultSectionPrefData.getAveDip();
		aveRake= faultSectionPrefData.getAveRake();
		aveUpperDepth= faultSectionPrefData.getOrigAveUpperDepth();
		aveLowerDepth= faultSectionPrefData.getAveLowerDepth();
		aseismicSlipFactor= faultSectionPrefData.getAseismicSlipFactor();
		couplingCoeff= faultSectionPrefData.getCouplingCoeff();
		faultTrace= faultSectionPrefData.getFaultTrace();
		dipDirection= faultSectionPrefData.getDipDirection();
		dateOfLastEventMillis = faultSectionPrefData.getDateOfLastEvent();
		slipInLastEvent = faultSectionPrefData.getSlipInLastEvent();
		connector = faultSectionPrefData.isConnector();
		zonePolygon = faultSectionPrefData.getZonePolygon();
	}

	public String toString() {
		String str = new String();
		str += "sectionId = "+this.getSectionId()+"\n";
		str += "sectionName = "+this.getSectionName()+"\n";
		str += "shortName = "+this.getShortName()+"\n";
		str += "aveLongTermSlipRate = "+this.getOrigAveSlipRate()+"\n";
		str += "slipRateStdDev = "+this.getOrigSlipRateStdDev()+"\n";
		str += "aveDip = "+this.getAveDip()+"\n";
		str += "aveRake = "+this.getAveRake()+"\n";
		str += "aveUpperDepth = "+this.getOrigAveUpperDepth()+"\n";
		str += "aveLowerDepth = "+this.getAveLowerDepth()+"\n";
		str += "aseismicSlipFactor = "+this.getAseismicSlipFactor()+"\n";
		str += "couplingCoeff = "+this.getCouplingCoeff()+"\n";
		str += "dipDirection = "+this.getDipDirection()+"\n";
		str += "dateOfLastEventMillis = "+this.getDateOfLastEvent()+"\n";
		str += "slipInLastEvent = "+this.getSlipInLastEvent()+"\n";
		str += "faultTrace:\n";
		for(int i=0; i <this.getFaultTrace().size();i++) {
			Location loc = this.getFaultTrace().get(i);
			str += "\t"+loc.getLatitude()+", "+loc.getLongitude()+", "+loc.getDepth()+"\n";
		}
		return str;
	}
	
	/**
	 * This sets the date of last event in UTC milliseconds from the epoch
	 * (see GregorianCalendar.setTimeInMillis() for details).
	 * @param dateOfLastEventMillis
	 */
	public void setDateOfLastEvent(long dateOfLastEventMillis) {
		this.dateOfLastEventMillis=dateOfLastEventMillis;
	}

	/**
	 * This returns the date of last event in UTC milliseconds from the epoch
	 * (see GregorianCalendar.setTimeInMillis() for details).  A value of
	 * Long.MIN_VALUE means it's not available (this is the default).
	 * @param dateOfLastEventMillis
	 */
	public long getDateOfLastEvent() {
		return dateOfLastEventMillis;
	}

	/**
	 * Amount of slip (m) on this section in the last event
	 * @param slipInLastEvent - in meters
	 */
	public void setSlipInLastEvent(double slipInLastEvent) {
		this.slipInLastEvent=slipInLastEvent;
	}
	
	
	/**
	 * Amount of slip (m) on this section in the last event.  A value of 
	 * Double.NaN means it is not available (the default).
	 * @return slip in meters
	 */
	public double getSlipInLastEvent() {
		return slipInLastEvent;
	}

	
//	private long dateOfLastEventMillis;
//	private double slipInLastEvent;


	public void setShortName(String shortName) {
		this.shortName = shortName;
	}
	
	public String getName() {return this.getSectionName();}

	/**
	 * Defined as a reduction of area between the upper and lower seismogenic depths
	 * @return
	 */
	public double getAseismicSlipFactor() {
		return aseismicSlipFactor;
	}
	
	/**
	 * Defined as a reduction of area between the upper and lower seismogenic depths
	 * @return
	 */
	public void setAseismicSlipFactor(double aseismicSlipFactor) {
		this.aseismicSlipFactor = aseismicSlipFactor;
	}

	/**
	 * Defined as a reduction of area between the upper and lower seismogenic depths
	 * @return
	 */
	public void setCouplingCoeff(double couplingCoeff) {
		this.couplingCoeff = couplingCoeff;
	}


	/**
	 * Defined as a reduction of area between the upper and lower seismogenic depths
	 * @return
	 */
	public double getCouplingCoeff() {
		return couplingCoeff;
	}

	/**
	 * This returns the average dip (degrees)
	 * @return
	 */
	public double getAveDip() {
		return aveDip;
	}
	
	/**
	 * This sets the average dip (degrees)
	 * @return
	 */
	public void setAveDip(double aveDip) {
		this.aveDip = aveDip;
	}
	
	/**
	 * This returns the slip rate (mm/yr) unmodified by the coupling coefficient
	 * @return
	 */
	public double getOrigAveSlipRate() {
		return aveLongTermSlipRate;
	}
	
	
	/**
	 * This returns the product of the slip rate (mm/yr) times the coupling coefficient
	 * @return
	 */
	public double getReducedAveSlipRate() {
		return aveLongTermSlipRate*couplingCoeff;
	}

	
	/**
	 * This sets the aveLongTermSlipRate (mm/yr), which should not already by modified by any
	 * non-unit coupling coefficient.
	 * @param aveLongTermSlipRate
	 */
	public void setAveSlipRate(double aveLongTermSlipRate) {
		this.aveLongTermSlipRate = aveLongTermSlipRate;
	}
	
	/**
	 * This returns the average lower seismogenic depth in km
	 * @return
	 */
	public double getAveLowerDepth() {
		return aveLowerDepth;
	}
	
	/**
	 * This gets the average lower seismogenic depth in km
	 * @return
	 */
	public void setAveLowerDepth(double aveLowerDepth) {
		this.aveLowerDepth = aveLowerDepth;
	}
	
	/**
	 * This gets the average rake in degrees
	 * @return
	 */
	public double getAveRake() {
		return aveRake;
	}
	
	/**
	 * This sets the average rake in degrees
	 * @return
	 */
	public void setAveRake(double aveRake) {
		this.aveRake = aveRake;
	}
	
	/**
	 * This returns the upper seismogenic (km) depth that has not been modified
	 * by the aseismicity factor
	 * @return
	 */
	public double getOrigAveUpperDepth() {
		return aveUpperDepth;
	}
	
	/**
	 * This sets the upper seismogenic depth (km), which should not have been modified
	 * by the aseismicity factor
	 * @return
	 */
	public void setAveUpperDepth(double aveUpperDepth) {
		this.aveUpperDepth = aveUpperDepth;
	}
	
	/**
	 * This returns the upper seismogenic (km) depth that has been modified
	 * by the aseismicity factor
	 * @return
	 */
	public double getReducedAveUpperDepth() {
		double depthToReduce = aseismicSlipFactor*(getAveLowerDepth() - getOrigAveUpperDepth());
		return getOrigAveUpperDepth() + depthToReduce;
	}
	
	/**
	 * This returns the dip direction (degrees)
	 * @return
	 */
	public float getDipDirection() {
		return dipDirection;
	}

	/**
	 * This sets the dip direction (degrees)
	 * @return
	 */
	public void setDipDirection(float dipDirection) {
		this.dipDirection = dipDirection;
	}
	
	/**
	 * This returns the fault trace
	 * @return
	 */
	public FaultTrace getFaultTrace() {
		
		return faultTrace;
	}
	
	/**
	 * This sets the fault trace
	 * @return
	 */
	public void setFaultTrace(FaultTrace faultTrace) {
		this.faultTrace = faultTrace;
	}
	
	/**
	 * This returns the section ID 
	 * @return
	 */
	public int getSectionId() {
		return sectionId;
	}
	
	/**
	 * This sets the section ID 
	 * @return
	 */
	public void setSectionId(int sectionId) {
		this.sectionId = sectionId;
	}
	
	/**
	 * This is the ID of the parent section if this is a subsection
	 */
	public int getParentSectionId() {
		return parentSectionId;
	}
	
	/**
	 * This is the ID of the parent section if this is a subsection
	 */
	public void setParentSectionId(int parentSectionId) {
		this.parentSectionId = parentSectionId;
	}
	
	/**
	 * this returns the name (string) of the section
	 * @return
	 */
	public String getSectionName() {
		return sectionName;
	}
	
	/**
	 * this sets the name (string) of the section
	 */
	public void setSectionName(String sectionName) {
		this.sectionName = sectionName;
	}
	
	/**
	 * This is the name of the parent section if this is a subsection
	 */
	public String getParentSectionName() {
		return parentSectionName;
	}
	
	/**
	 * This is the name of the parent section if this is a subsection
	 */
	public void setParentSectionName(String parentSectionName) {
		this.parentSectionName = parentSectionName;
	}
	
	/**
	 * this returns the length of the trace in km.
	 * @return
	 */
	public double getTraceLength() {
		return this.faultTrace.getTraceLength();
	}
	
	/**
	 * This returns the original down dip width in km (unmodified by the aseismicity factor)
	 * @return
	 */
	public double getOrigDownDipWidth() {
		return (getAveLowerDepth()-getOrigAveUpperDepth())/Math.sin(getAveDip()*Math.PI/ 180);
	}

	
	/**
	 * This returns the down-dip width (km) reduced by the aseismicity factor
	 * @return
	 */
	public double getReducedDownDipWidth() {
		return getOrigDownDipWidth()*(1.0-aseismicSlipFactor);
	}

	/**
	 * Get a list of all sub sections.  This version makes the subsection names the same as the parent plus " Subsection: #+1" and
	 * subsection IDs = 1000*parentId+#, where # is the ith subsection
	 * 
	 * @param maxSubSectionLen
	 * @return
	 */
	public ArrayList<FaultSectionPrefData> getSubSectionsList(double maxSubSectionLen) {
		ArrayList<FaultTrace> equalLengthSubsTrace = FaultUtils.getEqualLengthSubsectionTraces(this.faultTrace, maxSubSectionLen);
		ArrayList<FaultSectionPrefData> subSectionList = new ArrayList<FaultSectionPrefData>();
		for(int i=0; i<equalLengthSubsTrace.size(); ++i) {
			FaultSectionPrefData subSection = new FaultSectionPrefData();
			subSection.setFaultSectionPrefData(this);
			subSection.setFaultTrace(equalLengthSubsTrace.get(i));
			subSection.setSectionId(sectionId*1000+i);
			subSection.setSectionName(sectionName+", Subsection "+(i));
			subSection.setParentSectionId(sectionId);
			subSection.setParentSectionName(sectionName);
			subSectionList.add(subSection);
		}
		return subSectionList;
	}
	
	/**
	 * Get a list of all sub sections.  This version makes the subsection names the same as the parent plus " Subsection: #+1" and
	 * subsection IDs = startId+#, where # is the ith subsection
	 * 
	 * @param maxSubSectionLen
	 * @param startId - the index of the first subsection
	 * @return
	 */
	public ArrayList<FaultSectionPrefData> getSubSectionsList(double maxSubSectionLen, int startId) {
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
	public ArrayList<FaultSectionPrefData> getSubSectionsList(double maxSubSectionLen, int startId, int minSubSections) {
		ArrayList<FaultTrace> equalLengthSubsTrace =
			FaultUtils.getEqualLengthSubsectionTraces(this.faultTrace, maxSubSectionLen, minSubSections);
		ArrayList<FaultSectionPrefData> subSectionList = new ArrayList<FaultSectionPrefData>();
		for(int i=0; i<equalLengthSubsTrace.size(); ++i) {
			FaultSectionPrefData subSection = new FaultSectionPrefData();
			subSection.setFaultSectionPrefData(this);
			subSection.setFaultTrace(equalLengthSubsTrace.get(i));
			subSection.setSectionId(startId+i);
			subSection.setSectionName(sectionName+", Subsection "+(i));
			subSection.setParentSectionId(sectionId);
			subSection.setParentSectionName(sectionName);
			subSectionList.add(subSection);
		}
		return subSectionList;
	}


	/**
	 * This returns the slip rate standard deviation (mm/yr) (not modified by the coupling coefficient)
	 * @return
	 */
	public double getOrigSlipRateStdDev() {
		return slipRateStdDev;
	}

	/**
	 * This returns the product of the slip rate standard deviation (mm/yr) times the coupling coefficient
	 * @return
	 */
	public double getReducedSlipRateStdDev() {
		return slipRateStdDev*couplingCoeff;
	}

	/**
	 * This sets the slip rate standard deviation (mm/yr) (which should not have been modified 
	 * by the coupling coefficient).
	 * @return
	 */
	public void setSlipRateStdDev(double slipRateStdDev) {
		this.slipRateStdDev = slipRateStdDev;
	}

	public boolean isConnector() {
		return connector;
	}

	public void setConnector(boolean connector) {
		this.connector = connector;
	}

	public Region getZonePolygon() {
		return zonePolygon;
	}

	public void setZonePolygon(Region zonePolygon) {
		this.zonePolygon = zonePolygon;
	}

	/**
	 * This returns a simple fault data object.  This is the old version that reduces down-dip width from non zero
	 * aseismicity factor by modifying both the upper and lower seismogenic depths equally
	 *
	 * @param faultSection
	 * @return
	 */
	public SimpleFaultData getSimpleFaultDataOld(boolean aseisReducesArea) {
		if(!aseisReducesArea) {
			SimpleFaultData simpleFaultData = new SimpleFaultData(getAveDip(), getAveLowerDepth(), 
					getOrigAveUpperDepth(), getFaultTrace(), getDipDirection());
			return simpleFaultData;
		}
		else {
			//adjust the upper & lower seis depth according the aseis factor
			double depthToReduce = aseismicSlipFactor*(getAveLowerDepth() - getOrigAveUpperDepth());
			double lowerDepth = getAveLowerDepth()-depthToReduce/2.0;
			double upperDepth = getOrigAveUpperDepth() + depthToReduce/2.0;
			//System.out.println(depthToReduce+","+lowerDepth+","+upperDepth);
			SimpleFaultData simpleFaultData = new SimpleFaultData(getAveDip(), lowerDepth, upperDepth, getFaultTrace());
			return simpleFaultData;

		}
	}
	

	/**
	 * This returns a simple fault data object.  This version applies aseismicity as in increase of the
	 * upper-lower seismogenic depth only (no change to lower seismogenic depth)
	 *
	 * @param faultSection
	 * @return
	 */
	public SimpleFaultData getSimpleFaultData(boolean aseisReducesArea) {
		double upperDepth = getOrigAveUpperDepth();
		if (aseisReducesArea) {
			upperDepth = getReducedAveUpperDepth();
		}
		return new SimpleFaultData(getAveDip(), getAveLowerDepth(), upperDepth, getFaultTrace(), getDipDirection());
	}

	
	
	/**
	 * This returns a StirlingGriddedSurface with the specified grid spacing, where aseismicSlipFactor
	 * is applied as a reduction of down-dip-width (an increase of the upper seis depth).
	 * @param gridSpacing
	 * @param preserveGridSpacingExactly - if false, this will increase the grid spacing to fit the length 
	 * and ddw exactly (otherwise trimming occurs)
	 * @return
	 */
	public synchronized StirlingGriddedSurface getStirlingGriddedSurface(
			double gridSpacing, boolean preserveGridSpacingExactly,
			boolean aseisReducesArea) {
		// return cached surface?
		if( (gridSpacing==lastGridSpacing && stirlingGriddedSurface != null)
				&& (preserveGridSpacingExactly== lastPreserveGridSpacingExactly)
				&& (aseisReducesArea == lastAseisReducesArea)) {
			return stirlingGriddedSurface;
		}
		else {		// make the surface
			// make sure quad surf is null since things changed
			quadSurf = null;
			if(preserveGridSpacingExactly)
				stirlingGriddedSurface = new StirlingGriddedSurface(getSimpleFaultData(aseisReducesArea), gridSpacing);
			else
				stirlingGriddedSurface = new StirlingGriddedSurface(getSimpleFaultData(aseisReducesArea), gridSpacing, gridSpacing);
			// set the last values used
			lastPreserveGridSpacingExactly = preserveGridSpacingExactly;
			lastAseisReducesArea = aseisReducesArea;
			lastGridSpacing = gridSpacing;
		}
		return stirlingGriddedSurface;
	}
	
	/**
	 * This returns a StirlingGriddedSurface with the specified grid spacing, where aseismicSlipFactor
	 * is applied as a reduction of down-dip-width (an increase of the upper seis depth).
	 * The grid spacing is preserved, meaning the surface will be trimmed at the ends.
	 * @param aseisReducesArea
	 * @param gridSpacing
	 * @return
	 */
	public StirlingGriddedSurface getStirlingGriddedSurface(double gridSpacing) {
		return getStirlingGriddedSurface(gridSpacing, true, true);
	}
	
	public QuadSurface getQuadSurface(boolean aseisReducesArea) {
		return getQuadSurface(aseisReducesArea, 1d);
	}
	
	public synchronized QuadSurface getQuadSurface(boolean aseisReducesArea, double spacingForGridOperations) {
		if (lastAseisReducesArea == aseisReducesArea && quadSurf != null) {
			quadSurf.setAveGridSpacing(spacingForGridOperations);
			return quadSurf;
		}
		if (lastAseisReducesArea != aseisReducesArea)
			// make sure stirling is null since aseis changed
			stirlingGriddedSurface = null;
		lastAseisReducesArea = aseisReducesArea;
		quadSurf = new QuadSurface(this, aseisReducesArea);
		return quadSurf;
	}

	
	public Element toXMLMetadata(Element root) {
		return toXMLMetadata(root, XML_METADATA_NAME);
	}

	public Element toXMLMetadata(Element root, String name) {

		Element el = root.addElement(name);
		el.addAttribute("sectionId", this.getSectionId() + "");
		el.addAttribute("sectionName", this.getSectionName());
		el.addAttribute("shortName", this.getShortName());
		el.addAttribute("aveLongTermSlipRate", this.getOrigAveSlipRate() + "");
		el.addAttribute("slipRateStdDev", this.getOrigSlipRateStdDev() + "");
		el.addAttribute("aveDip", this.getAveDip() + "");
		el.addAttribute("aveRake", this.getAveRake() + "");
		el.addAttribute("aveUpperDepth", this.getOrigAveUpperDepth() + "");
		el.addAttribute("aveLowerDepth", this.getAveLowerDepth() + "");
		el.addAttribute("aseismicSlipFactor", this.getAseismicSlipFactor() + "");
		el.addAttribute("couplingCoeff", this.getCouplingCoeff() + "");
		el.addAttribute("dipDirection", this.getDipDirection() + "");
		String parentSectionName = this.getParentSectionName();
		if (parentSectionName != null)
			el.addAttribute("parentSectionName", parentSectionName);
		el.addAttribute("parentSectionId", getParentSectionId()+"");
		el.addAttribute("connector", isConnector()+"");
		if (getZonePolygon() != null)
			zonePolygon.toXMLMetadata(el, "ZonePolygon");
		if (getDateOfLastEvent() > Long.MIN_VALUE)
			el.addAttribute("dateOfLastEventMillis", getDateOfLastEvent()+"");
		if (!Double.isNaN(getSlipInLastEvent()))
			el.addAttribute("slipInLastEvent", getSlipInLastEvent()+"");

		FaultTrace trace = this.getFaultTrace();

		Element traceEl = el.addElement("FaultTrace");
		traceEl.addAttribute("name", trace.getName());

		for (int j=0; j<trace.getNumLocations(); j++) {
			Location loc = trace.get(j);

			traceEl = loc.toXMLMetadata(traceEl);
		}

		return root;
	}

	@SuppressWarnings("unchecked")
	public static FaultSectionPrefData fromXMLMetadata(Element el) {

		int sectionId = Integer.parseInt(el.attributeValue("sectionId"));
		String sectionName = el.attributeValue("sectionName");
		String shortName = el.attributeValue("shortName");
		double aveLongTermSlipRate = Double.parseDouble(el.attributeValue("aveLongTermSlipRate"));
		double aveDip = Double.parseDouble(el.attributeValue("aveDip"));
		double aveRake = Double.parseDouble(el.attributeValue("aveRake"));
		double aveUpperDepth = Double.parseDouble(el.attributeValue("aveUpperDepth"));
		double aveLowerDepth = Double.parseDouble(el.attributeValue("aveLowerDepth"));
		double aseismicSlipFactor = Double.parseDouble(el.attributeValue("aseismicSlipFactor"));
		float dipDirection = Float.parseFloat(el.attributeValue("dipDirection"));
		
		Attribute parentSectNameAtt = el.attribute("parentSectionName");
		String parentSectionName;
		if (parentSectNameAtt != null)
			parentSectionName = parentSectNameAtt.getStringValue();
		else
			parentSectionName = null;
		
		Attribute parentSectIDAtt = el.attribute("parentSectionId");
		int parentSectionId;
		if (parentSectIDAtt != null)
			parentSectionId = Integer.parseInt(parentSectIDAtt.getStringValue());
		else
			parentSectionId = -1;

		Element traceEl = el.element("FaultTrace");

		String traceName = traceEl.attributeValue("name");

		FaultTrace trace = new FaultTrace(traceName);

		Iterator<Element> traceIt = (Iterator<Element>)traceEl.elementIterator();
		while (traceIt.hasNext()) {
			Element locEl = traceIt.next();

			trace.add(Location.fromXMLMetadata(locEl));
		}
		
		boolean connector = false;
		Attribute connectorAtt = el.attribute("connector");
		if (connectorAtt != null)
			connector = Boolean.parseBoolean(connectorAtt.getStringValue());
		
		Region zonePolygon = null;
		Element zonePolygonEl = el.element("ZonePolygon");
		if (zonePolygonEl != null) {
			zonePolygon = Region.fromXMLMetadata(zonePolygonEl);
		}

		FaultSectionPrefData data = new FaultSectionPrefData();
		data.setSectionId(sectionId);
		data.setSectionName(sectionName);
		data.setShortName(shortName);
		data.setAveSlipRate(aveLongTermSlipRate);
		data.setAveDip(aveDip);
		data.setAveRake(aveRake);
		data.setAveUpperDepth(aveUpperDepth);
		data.setAveLowerDepth(aveLowerDepth);
		data.setAseismicSlipFactor(aseismicSlipFactor);
		Attribute couplingAtt = el.attribute("couplingCoeff");
		if (couplingAtt != null)
			data.setCouplingCoeff(Double.parseDouble(couplingAtt.getStringValue()));
		data.setDipDirection(dipDirection);
		data.setFaultTrace(trace);
		data.setParentSectionName(parentSectionName);
		data.setParentSectionId(parentSectionId);
		data.setConnector(connector);
		data.setZonePolygon(zonePolygon);
		
		Attribute lastEventAtt = el.attribute("dateOfLastEventMillis");
		if (lastEventAtt != null)
			data.setDateOfLastEvent(Long.parseLong(lastEventAtt.getStringValue()));
		Attribute lastSlipAtt = el.attribute("slipInLastEvent");
		if (lastSlipAtt != null)
			data.setSlipInLastEvent(Double.parseDouble(lastSlipAtt.getStringValue()));

		return data;
	}
	
	
	/**
	 * This calculates the moment rate of this fault section
	 * @param creepReduced - whether or not to apply aseismicity and coupling coefficient
	 * @return moment rate in SI units (Newton-Meters/year)
	 */
	public double calcMomentRate(boolean creepReduced) {
		double area, slipRate;
		if(creepReduced) {
			area = this.getReducedDownDipWidth()*this.getTraceLength()*1e6;
			slipRate = this.getReducedAveSlipRate();
		}
		else {
			area = this.getOrigDownDipWidth()*this.getTraceLength()*1e6;
			slipRate = this.getOrigAveSlipRate();
		}
			
		return FaultMomentCalc.getMoment(area, slipRate*1e-3);
	}
	

	public FaultSectionPrefData clone() {

		FaultSectionPrefData section = new FaultSectionPrefData();

		section.setFaultSectionPrefData(this);

		return section;
	}
}
