package org.opensha.sha.simulators.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.Vertex;

public class WardFileReader {
	
	/**
	 * This loads from Steve Wards file format (at least for the format he sent on Sept 2, 2010).  This
	 * implementation does not put DAS for traceFlag in the vertices, and there are assumptions about the
	 * ordering of things in his file.  Note also that his NAS does not start over for each section, but
	 * rather starts over for each fault.
	 * @param geomFile
	 * @return
	 * @throws IOException
	 */
	public static List<SimulatorElement> readGeometryFile(File geomFile) throws IOException {
		return readGeometryFile(new FileInputStream(geomFile));
	}
	
	/**
	 * This loads from Steve Wards file format (at least for the format he sent on Sept 2, 2010).  This
	 * implementation does not put DAS for traceFlag in the vertices, and there are assumptions about the
	 * ordering of things in his file.  Note also that his NAS does not start over for each section, but
	 * rather starts over for each fault.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static List<SimulatorElement> readGeometryFile(InputStream is) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		
		// now need to fill these lists
		List<SimulatorElement> rectElementsList = new ArrayList<SimulatorElement>();
		List<Vertex> vertexList = new ArrayList<Vertex>();
		List<ArrayList<SimulatorElement>> rectElementsListForSections = new ArrayList<ArrayList<SimulatorElement>> ();
		List<ArrayList<Vertex>> vertexListForSections = new ArrayList<ArrayList<Vertex>>();
		List<String> sectionNamesList = new ArrayList<String>();
		List<Integer> faultIDs_ForSections = new ArrayList<Integer>();

		int lastSectionID = -1;
		ArrayList<SimulatorElement> currentRectElForSection = null;
		ArrayList<Vertex> currVertexListForSection = null;

		int numVertices= 0; // to set vertexIDs

		String line = reader.readLine();
		while (line != null) {
			if (line.isEmpty()) {
				line = reader.readLine();
				continue;
			}

			StringTokenizer tok = new StringTokenizer(line);

			int id = Integer.parseInt(tok.nextToken()); // unique number ID for each element
			int numAlongStrike = Integer.parseInt(tok.nextToken()); // Number along strike
			int numDownDip = Integer.parseInt(tok.nextToken()); // Number down dip
			int faultID = Integer.parseInt(tok.nextToken()); // Fault Number
			int sectionID = Integer.parseInt(tok.nextToken()); // Segment Number
			double slipRate = Double.parseDouble(tok.nextToken()); // Slip Rate in m/y.
			double strength = Double.parseDouble(tok.nextToken()); // Element Strength in Bars (not used).
			double strike = Double.parseDouble(tok.nextToken()); // stike
			double dip = Double.parseDouble(tok.nextToken()); // dip
			double rake = Double.parseDouble(tok.nextToken()); // rake
			FocalMechanism focalMechanism = new FocalMechanism(strike, dip, rake);

			Vertex[] vertices = new Vertex[4];
			// 0th vertex
			double lat = Double.parseDouble(tok.nextToken());
			double lon = Double.parseDouble(tok.nextToken());
			double depth = Double.parseDouble(tok.nextToken()) / -1000d;
			numVertices+=1;
			vertices[0] = new Vertex(lat, lon, depth, numVertices);
			// 1st vertex
			lat = Double.parseDouble(tok.nextToken());
			lon = Double.parseDouble(tok.nextToken());
			depth = Double.parseDouble(tok.nextToken()) / -1000d;
			numVertices+=1;
			vertices[1] = new Vertex(lat, lon, depth, numVertices);
			// 2nd vertex
			lat = Double.parseDouble(tok.nextToken());
			lon = Double.parseDouble(tok.nextToken());
			depth = Double.parseDouble(tok.nextToken()) / -1000d;
			numVertices+=1;
			vertices[2] = new Vertex(lat, lon, depth, numVertices);
			// last vertex
			lat = Double.parseDouble(tok.nextToken());
			lon = Double.parseDouble(tok.nextToken());
			depth = Double.parseDouble(tok.nextToken()) / -1000d;
			numVertices+=1;
			vertices[3] = new Vertex(lat, lon, depth, numVertices);

			String name = null;
			while (tok.hasMoreTokens()) {
				if (name == null)
					name = "";
				else
					name += " ";
				name += tok.nextToken();
			}
			String sectionName = name;

			SimulatorElement rectElem = new RectangularElement(id, vertices, sectionName,faultID, sectionID, 
					numAlongStrike, numDownDip, slipRate, Double.NaN, focalMechanism, true);

			rectElementsList.add(rectElem);

			// check if this is a new section
			if(sectionID != lastSectionID) {
				// encountered a new section
				currentRectElForSection = new ArrayList<SimulatorElement>();
				currVertexListForSection = new ArrayList<Vertex>();
				rectElementsListForSections.add(currentRectElForSection);
				vertexListForSections.add(currVertexListForSection);
				sectionNamesList.add(sectionName);
				faultIDs_ForSections.add(faultID);
			}
			currentRectElForSection.add(rectElem);
			for(int i=0; i<4;i++) {
				vertexList.add(vertices[i]);
				currVertexListForSection.add(vertices[i]);
			}
			line = reader.readLine();
		}

		// check that indices are in order, and that index is one minus the ID:
		for(int i=0;i<vertexList.size();i++) {
			int idMinus1 = vertexList.get(i).getID()-1;
			if(i != idMinus1) throw new RuntimeException("vertexList index problem at index "+i+" (ID-1="+idMinus1+")");
		}
		for(int i=0;i<rectElementsList.size();i++) {
			if(i != rectElementsList.get(i).getID()-1) throw new RuntimeException("rectElementsList index problem at "+i);
		}

		System.out.println("namesOfSections.size()="+sectionNamesList.size()
				+"\tvertexList.size()="+vertexList.size()+"\trectElementsList.size()="+rectElementsList.size());
		
		return rectElementsList;
	}

}
