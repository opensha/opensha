package org.opensha.sha.simulators.utils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.PlaneUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.EvenlyGridCenteredSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.parsers.EQSIMv06FileReader;
import org.opensha.sha.simulators.writers.EQSimv06FileWriter;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

public class RectElemFromPrefDataBuilder {
	
	private static final boolean D = false;

	public static List<SimulatorElement> build(List<? extends FaultSection> allFaultSectionPrefData,
			boolean aseisReducesArea, double maxDiscretization) {
		return build(allFaultSectionPrefData, aseisReducesArea, maxDiscretization, null);
	}
	
	public static List<SimulatorElement> build(List<? extends FaultSection> allFaultSectionPrefData,
			boolean aseisReducesArea, double maxDiscretization, Map<Integer, Integer> parentSectToFaultIDMap) {
		// put in new list since we're about to sort
		allFaultSectionPrefData = Lists.newArrayList(allFaultSectionPrefData);

		List<SimulatorElement> rectElementsList = new ArrayList<SimulatorElement>();
		List<Vertex> vertexList = new ArrayList<Vertex>();
		List<ArrayList<SimulatorElement>> rectElementsListForSections = new ArrayList<ArrayList<SimulatorElement>> ();
		List<ArrayList<Vertex>> vertexListForSections = new ArrayList<ArrayList<Vertex>>();
		List<String> sectionNamesList = new ArrayList<String>();

		//Alphabetize:
		Collections.sort(allFaultSectionPrefData, new SubSectNameComparator());

		/*		  
				  // write sections IDs and names
				  for(int i=0; i< this.allFaultSectionPrefData.size();i++)
						System.out.println(allFaultSectionPrefData.get(i).getSectionId()+"\t"+allFaultSectionPrefData.get(i).getName());
		 */

		// remove those with no slip rate
		if (D)System.out.println("Removing the following due to NaN slip rate:");
		for(int i=allFaultSectionPrefData.size()-1; i>=0;i--)
			if(Double.isNaN(allFaultSectionPrefData.get(i).getOrigAveSlipRate())) {
				if(D) System.out.println("\t"+allFaultSectionPrefData.get(i).getSectionName());
				allFaultSectionPrefData.remove(i);
			}	 

		// Loop over sections and create the simulator elements
		int elementID =0;
		int numberAlongStrike = 0;
		int numberDownDip;
		int faultNumber = -1; // unknown for now // TODO fill in?
		int sectionNumber =0;
		double elementSlipRate=0;
		double elementAseis;
		double elementStrike=0, elementDip=0, elementRake=0;
		String sectionName;
		//				System.out.println("allFaultSectionPrefData.size() = "+allFaultSectionPrefData.size());
		for(int i=0;i<allFaultSectionPrefData.size();i++) {
			ArrayList<SimulatorElement> sectionElementsList = new ArrayList<SimulatorElement>();
			ArrayList<Vertex> sectionVertexList = new ArrayList<Vertex>();
			sectionNumber +=1; // starts from 1, not zero
			FaultSection faultSectionPrefData = allFaultSectionPrefData.get(i);
			RuptureSurface surface = faultSectionPrefData.getFaultSurface(maxDiscretization, false, aseisReducesArea);
			Preconditions.checkState(surface instanceof EvenlyGriddedSurface);
			EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surface;
			EvenlyGridCenteredSurface gridCenteredSurf = new EvenlyGridCenteredSurface(gridSurf);
			double elementLength = gridCenteredSurf.getGridSpacingAlongStrike();
			double elementDDW = gridCenteredSurf.getGridSpacingDownDip(); // down dip width
			if (i == 256)
				System.out.println("Detected len="+elementLength+", ddw="+elementDDW);
			boolean adjustedLenghts = true && faultSectionPrefData.getAveDip() < 90d && gridSurf.getNumCols()>1;
			boolean doColSpecific = true;
			double[] colSpecificDDW = null;
			
			// deal with fault ID
			if (parentSectToFaultIDMap != null
					&& parentSectToFaultIDMap.containsKey(faultSectionPrefData.getParentSectionId()))
				faultNumber = parentSectToFaultIDMap.get(faultSectionPrefData.getParentSectionId());
			else if (faultSectionPrefData.getParentSectionId() > 0)
				faultNumber = faultSectionPrefData.getParentSectionId();
			else
				faultNumber = faultSectionPrefData.getSectionId();
			Preconditions.checkState(faultNumber > 0, "Fault ID not provided and parent and reg sect "
					+ "IDs not positive ints");
			
			if (adjustedLenghts) {
				// use distance to line to work with skewed sections

				if (doColSpecific)
					colSpecificDDW = new double[gridCenteredSurf.getNumCols()];
				//						for (int j=0; j<gridCenteredSurf.getNumCols(); j++) {
				//							Location testPt = gridCenteredSurf.get(1, j);
				List<Double> candidates = Lists.newArrayList();
				for (int j=0; j<gridSurf.getNumCols()-1; j++) {
					Location t1 = gridSurf.get(1, j);
					Location t2 = gridSurf.get(1, j+1);
					Location testPt = new Location((t1.getLatitude()+t2.getLatitude())/2d, (t1.getLongitude()+t2.getLongitude())/2d, (t1.getDepth()+t2.getDepth())/2d);
					//							Location testPt = t1;
					double myMinDDW = Double.POSITIVE_INFINITY;
					for (int k=0; k<gridSurf.getNumCols()-1; k++) {
						Location topL = gridSurf.get(0, k);
						Location topR = gridSurf.get(0, k+1);

						if (doColSpecific) {
							// just use that column
							if (k != j)
								continue;
						} else {
							// now make sure it's contained within the given points
							double distBetweenTop = LocationUtils.linearDistanceFast(topL, topR);
							double distToL = LocationUtils.linearDistanceFast(testPt, topL);
							double distToR = LocationUtils.linearDistanceFast(testPt, topR);
							// these are the 3 lenghts of a triangle. if obtuse, then the point is outside
							// test via pythagorean theorem
							double a2 = distToL*distToL;
							double b2 = distBetweenTop*distBetweenTop;
							double c2 = distToR*distToR;
							//									System.out.println("a2+b2="+(a2+b2)+", c2="+c2);
							if (a2+b2<c2)
								continue;
						}

						double vertDelta = testPt.getDepth() - topR.getDepth();
						// this ignores depth
						double ddw = Math.abs(LocationUtils.distanceToLineFast(topL, topR, testPt));
						// now reincorporate depth
						ddw = Math.sqrt(ddw*ddw + vertDelta*vertDelta);

						myMinDDW = Math.min(myMinDDW, ddw);
					}
					if (Double.isInfinite(myMinDDW))
						continue;
					//							Preconditions.checkState(!Double.isInfinite(myMinDDW), "Infinite after "+tests+" tests for "+faultSectionPrefData.getName());
					Preconditions.checkState(myMinDDW > 0);
					if (doColSpecific)
						colSpecificDDW[j] = myMinDDW;
					candidates.add(myMinDDW);
				}
				Preconditions.checkState(!candidates.isEmpty());
				double[] candidatesArray = Doubles.toArray(candidates);

				// average
				//						elementDDW = StatUtils.mean(candidatesArray);

				// min
				//						elementDDW = StatUtils.min(candidatesArray);

				// max
				elementDDW = StatUtils.max(candidatesArray);

				// first
				//						elementDDW = candidatesArray[0];

				if (i == 256)
					System.out.println("Modified len="+elementLength+", ddw="+elementDDW);
			}
			//					if (i == 256)
			//						System.exit(0);
			elementRake = faultSectionPrefData.getAveRake();
			elementSlipRate = faultSectionPrefData.getOrigAveSlipRate()/1000;
			elementAseis = faultSectionPrefData.getAseismicSlipFactor();
			sectionName = faultSectionPrefData.getName().replaceAll("\\W+", "_");
			for(int col=0; col<gridCenteredSurf.getNumCols();col++) {
				if (colSpecificDDW != null)
					elementDDW = colSpecificDDW[col];
				numberAlongStrike += 1;
				for(int row=0; row<gridCenteredSurf.getNumRows();row++) {
					elementID +=1; // starts from 1, not zero
					numberDownDip = row+1;
					Location centerLoc = gridCenteredSurf.get(row, col);
					Location top1 = gridSurf.get(row, col);
					Location top2 = gridSurf.get(row, col+1);
					Location bot1 = gridSurf.get(row+1, col);
					double[] strikeAndDip = PlaneUtils.getStrikeAndDip(top1, top2, bot1);
					elementStrike = strikeAndDip[0];
					elementDip = strikeAndDip[1];	

					double hDistAlong = elementLength/2;
					double dipRad = Math.PI*elementDip/180;
					double vDist = (elementDDW/2)*Math.sin(dipRad);
					double hDist = (elementDDW/2)*Math.cos(dipRad);

					//							System.out.println(elementID+"\telementDDW="+elementDDW+"\telementDip="+elementDip+"\tdipRad="+dipRad+"\tvDist="+vDist+"\thDist="+hDist);

					LocationVector vect = new LocationVector(elementStrike+180, hDistAlong, 0);
					Location newMid1 = LocationUtils.location(centerLoc, vect);  // half way down the first edge
					vect.set(elementStrike-90, hDist, -vDist); // up-dip direction
					Location newTop1 = LocationUtils.location(newMid1, vect);
					vect.set(elementStrike+90, hDist, vDist); // down-dip direction
					Location newBot1 = LocationUtils.location(newMid1, vect);

					vect.set(elementStrike, hDistAlong, 0);
					Location newMid2 = LocationUtils.location(centerLoc, vect); // half way down the other edge
					vect.set(elementStrike-90, hDist, -vDist); // up-dip direction
					Location newTop2 = LocationUtils.location(newMid2, vect);
					vect.set(elementStrike+90, hDist, vDist); // down-dip direction
					Location newBot2 = LocationUtils.location(newMid2, vect);

					// @param traceFlag - tells whether is on the fault trace  (0 means no; 1 means yes, but not
					// 		              the first or last point; 2 means yes & it's the first; and 3 means yes 
					//                    & it's the last point)


					// set DAS
					double das1 = col*elementLength;	// this is in km
					double das2 = das1+elementLength;	// this is in km
					// set traceFlag - tells whether is on the fault trace  (0 means no; 1 means yes, but not the 
					// first or last point; 2 means yes & it's the first; and 3 means yes & it's the last point)
					int traceFlagBot = 0;
					int traceFlagTop1 = 0;
					int traceFlagTop2 = 0;
					if(row ==0) {
						traceFlagTop1 = 1;
						traceFlagTop2 = 1;
					}
					if(row==0 && col==0) traceFlagTop1 = 2;
					if(row==0 && col==gridCenteredSurf.getNumCols()-1) traceFlagTop2 = 3;

					Vertex[] elementVertices = new Vertex[4];
					elementVertices[0] = new Vertex(newTop1,vertexList.size()+1, das1, traceFlagTop1);  
					elementVertices[1] = new Vertex(newBot1,vertexList.size()+2, das1, traceFlagBot);
					elementVertices[2] = new Vertex(newBot2,vertexList.size()+3, das2, traceFlagBot);
					elementVertices[3] = new Vertex(newTop2,vertexList.size()+4, das2, traceFlagTop2);

					FocalMechanism focalMech = new FocalMechanism(elementStrike, elementDip, elementRake);

					SimulatorElement simSurface =
							new RectangularElement(elementID, elementVertices, sectionName,
									faultNumber, sectionNumber, numberAlongStrike, numberDownDip,
									elementSlipRate, elementAseis, focalMech, true);

					rectElementsList.add(simSurface);
					vertexList.add(elementVertices[0]);
					vertexList.add(elementVertices[1]);
					vertexList.add(elementVertices[2]);
					vertexList.add(elementVertices[3]);

					sectionElementsList.add(simSurface);
					sectionVertexList.add(elementVertices[0]);
					sectionVertexList.add(elementVertices[1]);
					sectionVertexList.add(elementVertices[2]);
					sectionVertexList.add(elementVertices[3]);


					//							String line = elementID + "\t"+
							//								numberAlongStrike + "\t"+
					//								numberDownDip + "\t"+
					//								faultNumber + "\t"+
					//								sectionNumber + "\t"+
					//								(float)elementSlipRate + "\t"+
					//								(float)elementStrength + "\t"+
					//								(float)elementStrike + "\t"+
					//								(float)elementDip + "\t"+
					//								(float)elementRake + "\t"+
					//								(float)newTop1.getLatitude() + "\t"+
					//								(float)newTop1.getLongitude() + "\t"+
					//								(float)newTop1.getDepth()*-1000 + "\t"+
					//								(float)newBot1.getLatitude() + "\t"+
					//								(float)newBot1.getLongitude() + "\t"+
					//								(float)newBot1.getDepth()*-1000 + "\t"+
					//								(float)newBot2.getLatitude() + "\t"+
					//								(float)newBot2.getLongitude() + "\t"+
					//								(float)newBot2.getDepth()*-1000 + "\t"+
					//								(float)newTop2.getLatitude() + "\t"+
					//								(float)newTop2.getLongitude() + "\t"+
					//								(float)newTop2.getDepth()*-1000 + "\t"+
					//								sectionName;
					//
					//							System.out.println(line);
				}
			}
			rectElementsListForSections.add(sectionElementsList);
			vertexListForSections.add(sectionVertexList);
			// strip out any whitespace and special characters
			String strippedName = faultSectionPrefData.getName().replaceAll("\\W+", "_");
			sectionNamesList.add(strippedName);
		}
		System.out.println("rectElementsList.size()="+rectElementsList.size());
		System.out.println("vertexList.size()="+vertexList.size());

		/*
				for(int i=0;i<allFaultSectionPrefData.size();i++) {
					ArrayList<RectangularElement> elList = rectElementsListForSections.get(i);
					ArrayList<Vertex> verList = vertexListForSections.get(i);;
					System.out.println(allFaultSectionPrefData.get(i).getName());
					System.out.println("\tEl Indices:  "+elList.get(0).getID()+"\t"+elList.get(elList.size()-1).getID());
//					System.out.println("\tVer Indices:  "+verList.get(0).getID()+"\t"+verList.get(verList.size()-1).getID());
				}
		 */
		
		return rectElementsList;
	}
	
	private static class SubSectNameComparator implements Comparator<FaultSection> {
		
		private NamedComparator nameComp = new NamedComparator();

		@Override
		public int compare(FaultSection o1, FaultSection o2) {
			if (o1.getParentSectionId() != o2.getParentSectionId())
				return nameComp.compare(o1, o2);
			return new Integer(o1.getSectionId()).compareTo(o2.getSectionId());
		}
		
	}
	
	public static void main(String[] args) throws IOException {
//		// write UCERF3 DMs
//		
//		FaultModels fm = FaultModels.FM3_1;
//		DeformationModels dm = DeformationModels.ZENGBB;
//		double defaultAseisVal = 0.1;
//		File scratchDir = UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR;
//		File outputDir = new File("/tmp/ucerf3_dm_eqsim_files");
//		if (!outputDir.exists())
//			outputDir.mkdir();
//		boolean aseisReducesArea = false; // TODO
//		double maxDiscretization = 4.0; // TODO
//		DateFormat df = new SimpleDateFormat("MMM d, yyyy");
//		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm, scratchDir, defaultAseisVal);
//		List<SimulatorElement> elems = build(dmFetch.getSubSectionList(), aseisReducesArea, maxDiscretization);
//		File outputFile = new File(outputDir, fm.encodeChoiceString()+"_"+dm.encodeChoiceString()+"_EQSIM.txt");
//		EQSimv06FileWriter.writeGeometryFile(elems, outputFile, null, fm.name()+", "+dm.name()+" output",
//				"Kevin Milner", EQSimv06FileWriter.getCurDateString());
//		
//		// now try opening it
//		EQSIMv06FileReader.readGeometryFile(outputFile);
	}

}
