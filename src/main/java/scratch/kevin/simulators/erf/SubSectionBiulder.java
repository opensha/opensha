package scratch.kevin.simulators.erf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FourPointEvenlyGriddedSurface;
import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Builds subsection approximations for EQSim rectangular elements. Each column of rectangular elements is
 * combined into one sub section.
 * 
 * @author kevin
 *
 */
public class SubSectionBiulder {
	
	private List<SimulatorElement> elements;
	private List<FaultSectionPrefData> subSectsList;
	private Map<Integer, Integer> elemIDToSubSectsMap;
	
	public SubSectionBiulder(List<SimulatorElement> elements) {
		this.elements = elements;
		
		// first build mapping from fault section ID to faults
		Map<Integer, List<RectangularElement>> sectsMap = Maps.newHashMap();
		for (SimulatorElement e : elements) {
			List<RectangularElement> elemsForSect = sectsMap.get(e.getSectionID());
			if (elemsForSect == null) {
				elemsForSect = Lists.newArrayList();
				sectsMap.put(e.getSectionID(), elemsForSect);
			}
			Preconditions.checkState(e instanceof RectangularElement, "Only rectangular supported (for now)");
			elemsForSect.add((RectangularElement)e);
		}
		
		checkAssignNAS(sectsMap);
		
		subSectsList = Lists.newArrayList();
		elemIDToSubSectsMap = Maps.newHashMap();
		
		int sectIndex = 0;
		
		for (Integer sectID : sectsMap.keySet()) {
			// for each fault section
			List<RectangularElement> elemsForSect = sectsMap.get(sectID);
			String sectName = elemsForSect.get(0).getSectionName();
			
			// now organize elements for each fault into rows and columns
			List<List<RectangularElement>> organized = organizeElemsIntoColumns(elemsForSect);
			
			int subSectIndex = 0;
			for (List<RectangularElement> column : organized) {
				String subSectName = sectName+", Subsection "+(subSectIndex++);
				
				RectangularElement top = column.get(0);
				RectangularElement bottom = column.get(column.size()-1);
				FocalMechanism mech = top.getFocalMechanism();
				
				// average the dip
				double[] dips = new double[column.size()];
				for (int i=0; i<column.size(); i++)
					dips[i] = column.get(i).getFocalMechanism().getDip();
				double avgDip = Math.abs(StatUtils.mean(dips));
				
				// calculate dip direvtion as azimuth from middle of top to middle of bottom
				double dipDir = LocationUtils.azimuth(top.getCenterLocation(), bottom.getCenterLocation());
				
				// upper depth
				double upperDepth = getMinDepth(top.getSurface());
				double lowerDepth = getMaxDepth(bottom.getSurface());
				
				// fault trace
				FaultTrace trace = new FaultTrace(subSectName);
				trace.addAll(top.getSurface().getRowAsTrace(0));
				
				// build FSD object
				FaultSectionPrefData fsd = new FaultSectionPrefData();
				fsd.setSectionId(sectIndex);
				fsd.setSectionName(subSectName);
				fsd.setShortName(subSectName);
				// TODO: currently using top as long term slip rate. average? (they taper)
				fsd.setAveSlipRate(top.getSlipRate());
				fsd.setAveDip(avgDip);
				fsd.setAveRake(mech.getRake());
				fsd.setAveUpperDepth(upperDepth);
				fsd.setAveLowerDepth(lowerDepth);
				fsd.setConnector(false);
				fsd.setAseismicSlipFactor(top.getAseisFactor());
				fsd.setFaultTrace(trace);
				fsd.setDipDirection((float)dipDir);
				fsd.setParentSectionName(sectName);
				fsd.setParentSectionId(sectID);
				
				subSectsList.add(fsd);
				
				// populate mapping from element ID to sub section index
				for (SimulatorElement e : column)
					elemIDToSubSectsMap.put(e.getID(), sectIndex);
				
				sectIndex++;
				Preconditions.checkState(sectIndex == subSectsList.size());
			}
		}
	}
	
	public List<SimulatorElement> getElements() {
		return elements;
	}
	
	/**
	 * List of sub sections. Each sub section represents a column of recangular elements
	 * @return
	 */
	public List<FaultSectionPrefData> getSubSectsList() {
		return subSectsList;
	}

	/**
	 * Mapping from element ID to subsections. Note that there are multiple elements for each subsection,
	 * so this is an MxN map with M elements and N subsections, N<M.
	 * @return
	 */
	public Map<Integer, Integer> getElemIDToSubSectsMap() {
		return elemIDToSubSectsMap;
	}

	private static double getMinDepth(EvenlyGriddedSurface surf) {
		return getAveDepth(surf, 0);
	}
	
	private static double getMaxDepth(EvenlyGriddedSurface surf) {
		return getAveDepth(surf, surf.getNumRows()-1);
	}
	
	private static double getAveDepth(EvenlyGriddedSurface surf, int row) {
		double[] vals = new double[surf.getNumCols()];
		for (int i=0; i<vals.length; i++)
			vals[i] = surf.getLocation(row, i).getDepth();
		return StatUtils.mean(vals);
	}
	
	/**
	 * Organizes a list of elements for a specified fault into rows and columns using the getNumAlongStrike and
	 * getNumDownDip methods on each element.
	 * @param elementsForFault
	 * @return Array organized as a list of columns along strike, each of which is a list of rows (from top to bottom)
	 */
	private static List<List<RectangularElement>> organizeElemsIntoColumns(Collection<RectangularElement> elementsForFault) {
		List<RectangularElement> sortedAlongStrike = Lists.newArrayList(elementsForFault);
		
		Collections.sort(sortedAlongStrike, alongStrikeComparator);
		
		List<List<RectangularElement>> organized = Lists.newArrayList();
		
		int curAlongStrike = -1;
		List<RectangularElement> curAlongStrikeList = null;
		
		for (RectangularElement elem : sortedAlongStrike) {
			Preconditions.checkState(elem.getNumAlongStrike() >= 0, "Uh oh, NAS: "+elem.getNumAlongStrike());
			
			if (curAlongStrike != elem.getNumAlongStrike()) {
				if (curAlongStrikeList != null) {
					Collections.sort(curAlongStrikeList, downDipComparator);
					for (int i=1; i<curAlongStrikeList.size(); i++)
						// verify that down dip list is complete and sorted
						Preconditions.checkState(curAlongStrikeList.get(i-1).getNumDownDip()
								== (curAlongStrikeList.get(i).getNumDownDip()-1));
					
					organized.add(curAlongStrikeList);
				}
				curAlongStrikeList = Lists.newArrayList();
				// verify that along strike list is complete and sorted
				if (curAlongStrike == -1)
					Preconditions.checkState(elem.getNumAlongStrike() == 0);
				else
					Preconditions.checkState(elem.getNumAlongStrike() == curAlongStrike+1);
				curAlongStrike = elem.getNumAlongStrike();
			}
			
			curAlongStrikeList.add(elem);
		}
		
		if (!curAlongStrikeList.isEmpty())
			organized.add(curAlongStrikeList);
		
		return organized;
	}
	
	private static AlongStrikeComparator alongStrikeComparator = new AlongStrikeComparator();
	private static DownDipComparator downDipComparator = new DownDipComparator();
	
	private static class AlongStrikeComparator implements Comparator<SimulatorElement> {

		@Override
		public int compare(SimulatorElement o1, SimulatorElement o2) {
			return new Integer(o1.getNumAlongStrike()).compareTo(o2.getNumAlongStrike());
		}
		
	}
	
	private static class DownDipComparator implements Comparator<SimulatorElement> {

		@Override
		public int compare(SimulatorElement o1, SimulatorElement o2) {
			return new Integer(o1.getNumDownDip()).compareTo(o2.getNumDownDip());
		}
		
	}
	
	/**
	 * If the elements don't have the Num Along Strike/Down Dip numbers set, we'll do it based on DAS and depth. A little
	 * dirty but checks are made to make sure that everything lines up well. Only works for rectangular faults where
	 * the number of elements down dip is constant along strike.
	 * @param sectsMap
	 */
	private static void checkAssignNAS(Map<Integer, List<RectangularElement>> sectsMap) {
		DASComparator dasCompare = new DASComparator();
		for (Integer sectID : sectsMap.keySet()) {
			List<RectangularElement> elems = sectsMap.get(sectID);
			if (elems.get(0).getNumAlongStrike()>=0)
				continue;
			
			// we need to set the NAS a NDD numbers
			
			// detect element size
			FourPointEvenlyGriddedSurface surf = elems.get(0).getSurface();
			double elemDeltaDepth = surf.getLocation(1,0).getDepth() - surf.getLocation(0,0).getDepth();
			
			// first bin by depth (simplest)
			MinMaxAveTracker depthTrack = new MinMaxAveTracker();
			for (SimulatorElement elem : elems)
				depthTrack.addValue(elem.getCenterLocation().getDepth());
			
			double minDepth = depthTrack.getMin();
			double maxDepth = depthTrack.getMax();
			double deltaDepth = maxDepth - minDepth;
			double numElemsNoRound = deltaDepth / elemDeltaDepth + 1;
			int numElemsDD = (int)(numElemsNoRound+0.5);
			double depthError = numElemsNoRound - Math.floor(numElemsNoRound);
			if (depthError > 0.5)
				depthError = 1 - depthError;
			Preconditions.checkState(depthError < 0.1, "DDW error too big, may be unstable: min="+minDepth+", max="+maxDepth
					+", delta="+deltaDepth+", elemDelta="+elemDeltaDepth+", numElems="+numElemsNoRound);
			
			List<List<SimulatorElement>> elemsByDepth = Lists.newArrayList();
			for (int i=0; i<numElemsDD; i++)
				elemsByDepth.add(new ArrayList<SimulatorElement>());
			
			for (SimulatorElement elem : elems) {
				double depth = elem.getCenterLocation().getDepth();
				double depthIndexNoRound = (depth - minDepth)/elemDeltaDepth;
				double depthIndexError = depthIndexNoRound - Math.floor(depthIndexNoRound);
				if (depthIndexError > 0.5)
					depthIndexError = 1 - depthIndexError;
				Preconditions.checkState(depthIndexError < 0.1, "DDW depth for elem too big, may be unstable: " +
						"min="+minDepth+", max="+maxDepth+", depth="+deltaDepth
						+", elemDelta="+elemDeltaDepth+", depthIndexNoRound="+depthIndexNoRound);
				int depthIndex = (int)(depthIndexNoRound+0.5);
				elemsByDepth.get(depthIndex).add(elem);
			}
			
			// make sure each depth bin is the same length
			for (int i=1; i<elemsByDepth.size(); i++)
				Preconditions.checkState(elemsByDepth.get(i-1).size() == elemsByDepth.get(i).size(), "Error binning by depth");
			
			// now bin by DAS
			for (int i=0; i<elemsByDepth.size(); i++) {
				List<SimulatorElement> elemsForDepth = elemsByDepth.get(i);
				
				// sort by DAS
				Collections.sort(elemsForDepth, dasCompare);
				
				for (int j=0; j<elemsForDepth.size(); j++) {
					SimulatorElement elem = elemsForDepth.get(j);
					elem.setNumAlongStrike(j);
					elem.setNumDownDip(i);
				}
			}
		}
	}
	
	private static class DASComparator implements Comparator<SimulatorElement> {

		@Override
		public int compare(SimulatorElement o1, SimulatorElement o2) {
			return Double.compare(o1.getAveDAS(), o2.getAveDAS());
		}
		
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/Simulators");
		File geomFile = new File(dir, "ALLCAL2_1-7-11_Geometry.dat");
		System.out.println("Loading geometry...");
		General_EQSIM_Tools tools = new General_EQSIM_Tools(geomFile);
		SubSectionBiulder builder = new SubSectionBiulder(tools.getElementsList());
		System.out.println("Elems: "+tools.getElementsList().size());
		System.out.println("Sub Sects: "+builder.getSubSectsList().size());
		System.out.println("Mappings: "+builder.getElemIDToSubSectsMap().size());
	}

}
