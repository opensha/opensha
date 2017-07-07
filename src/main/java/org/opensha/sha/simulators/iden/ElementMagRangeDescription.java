package org.opensha.sha.simulators.iden;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.parsers.EQSIMv06FileReader;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

/**
 * This is the simplest rupture identifier implementation - it defines a match as any rupture that includes
 * the given section and is within the specified magnitude range.
 * @author kevin
 *
 */
public class ElementMagRangeDescription extends AbstractRuptureIdentifier {
	
	
	
	public static final int SAF_MOJAVE_ELEMENT_ID = 1246;
	public static final int SAF_CARRIZO_ELEMENT_ID = 1026;
	public static final int SAF_COACHELLA_ELEMENT_ID = 1602;
	public static final int SAF_CHOLAME_ELEMENT_ID = 966;
	public static final int GARLOCK_WEST_ELEMENT_ID = 6036;
	public static final int SAN_JACINTO__ELEMENT_ID = 1931;
	public static final int ELSINORE_ELEMENT_ID = 2460;
	public static final int PUENTE_HILLS_ELEMENT_ID = 11829;
	public static final int NEWP_INGL_ONSHORE_ELEMENT_ID = 7672;
	public static final int SAF_SANTA_CRUZ = 663;
	public static final int SAF_MID_PENINSULA = 529;
	public static final int CALAVERAS = 3692;
	public static final int HAYWARD = 3334;
	
	private String name;
	private List<Integer> elementIDs;
	private double minMag, maxMag;
	
	public ElementMagRangeDescription(String name, int elementID, double minMag, double maxMag) {
		this(name, Lists.newArrayList(elementID), minMag, maxMag);
	}
	
	public ElementMagRangeDescription(String name, double minMag, double maxMag, int... elementIDs) {
		this(name, Ints.asList(elementIDs), minMag, maxMag);
	}

	public static String smartName(String fault, ElementMagRangeDescription o) {
		return smartName(fault, o.minMag, o.maxMag);
	}
	public static String smartName(String fault, double minMag, double maxMag) {
		if (maxMag < 9) {
			return fault+" "+getRoundedStr(minMag)+"=>"+getRoundedStr(maxMag);
		}
		return fault+" "+getRoundedStr(minMag)+"+";
	}
	private static String getRoundedStr(double val) {
		if (val == Math.floor(val))
			return (int)val + "";
		return (float)val+"";
	}
	
	public ElementMagRangeDescription(String name, List<Integer> elementIDs, double minMag, double maxMag) {
		this.elementIDs = elementIDs;
		this.minMag = minMag;
		this.maxMag = maxMag;
		this.name = name;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		double mag = event.getMagnitude();
		if (mag < minMag || mag >= maxMag)
			return false;
		for (int elementID : elementIDs)
			if (!Ints.contains(event.getAllElementIDs(), elementID))
				return false;
		return true;
	}
	
	public List<Integer> getElementIDs() {
		return elementIDs;
	}

	public void setElementID(int elementID) {
		this.elementIDs = Lists.newArrayList(elementID);
	}

	public void addElementID(int elementID) {
		this.elementIDs.add(elementID);
	}
	
	public int removeElementID(int elementID) {
		int ind = elementIDs.indexOf(elementID);
		if (ind < 0)
			return -1;
		this.elementIDs.remove(ind);
		return ind;
	}

	public void setElementID(List<Integer> elementIDs) {
		this.elementIDs = elementIDs;
	}

	public double getMinMag() {
		return minMag;
	}

	public void setMinMag(double minMag) {
		this.minMag = minMag;
	}

	public double getMaxMag() {
		return maxMag;
	}

	public void setMaxMag(double maxMag) {
		this.maxMag = maxMag;
	}

	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/Simulators");
		File geomFile = new File(dir, "ALLCAL2_1-7-11_Geometry.dat");
		File eventFile = new File(dir, "eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.barall");
		
		Preconditions.checkState(geomFile.exists());
		Preconditions.checkState(eventFile.exists());
		
		General_EQSIM_Tools simTools = new General_EQSIM_Tools(geomFile);
		List<? extends SimulatorEvent> events = EQSIMv06FileReader.readEventsFile(eventFile, simTools.getElementsList());
		
		ElementMagRangeDescription descr = new ElementMagRangeDescription(null, 1267, 7.2, 7.5);
		
		List<? extends SimulatorEvent> matches = descr.getMatches(events);
		
		System.out.println("Got "+matches.size()+" matches!");
		HashSet<Integer> matchIDs = new HashSet<Integer>();
		for (SimulatorEvent match : matches) {
			matchIDs.add(match.getID());
			System.out.println(match.getID()+". mag="+match.getMagnitude()+", years="+match.getTimeInYears());
		}
		
		System.out.println("Quickly Triggered Events (1 day):");
		double day = 24*60*60;
		for (SimulatorEvent e : events) {
			if (matchIDs.contains(e.getID()))
				continue;
			if (e.getMagnitude() < 6.5)
				continue;
			double time = e.getTime();
			for (SimulatorEvent m : matches) {
				double mtime = m.getTime();
				if (time >= mtime && time <= (mtime + day)) {
					System.out.println(e.getID()+". mag="+e.getMagnitude()+", years="+e.getTimeInYears());
					break;
				}
			}
		}
	}

	@Override
	public String getName() {
		return name;
	}

}
