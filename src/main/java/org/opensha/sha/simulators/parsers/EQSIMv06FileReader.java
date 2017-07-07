package org.opensha.sha.simulators.parsers;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.EQSIM_Event;
import org.opensha.sha.simulators.EQSIM_EventRecord;
import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.base.Preconditions;

public class EQSIMv06FileReader {
	
	final static String GEOM_FILE_SIG = "EQSim_Input_Geometry_2";	// signature of the geometry file
	final static int GEOM_FILE_MIN_SPEC_LEVEL = 2;
	final static int GEOM_FILE_SPEC_LEVEL = 4;
	
	final static String EVENT_FILE_SIG = "EQSim_Output_Event_2";
	final static int EVENT_FILE_MIN_SPEC_LEVEL = 2;
	final static int EVENT_FILE_SPEC_LEVEL = 3;
	
	private static final boolean doAreaDebugScatter = false;
	
	public static List<SimulatorElement> readGeometryFile(File geomFile) throws IOException {
		return readGeometryFile(new FileInputStream(geomFile));
	}
	
	public static List<SimulatorElement> readGeometryFile(URL url) throws IOException {
		return readGeometryFile(url.openStream());
	}
	
	public static List<SimulatorElement> readGeometryFile(InputStream is) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		
		// note that the following lists have indices that start from 0 (index = sectionID-1)
		List<SimulatorElement> rectElementsList = new ArrayList<SimulatorElement>();
		List<Vertex> vertexList = new ArrayList<Vertex>();
		List<ArrayList<SimulatorElement>> simElementsListForSections = new ArrayList<ArrayList<SimulatorElement>> ();
		List<ArrayList<Vertex>> vertexListForSections = new ArrayList<ArrayList<Vertex>>();
		List<String> sectionNamesList = new ArrayList<String>();
		List<Integer> sectionIDs_List = new ArrayList<Integer>();
		List<Integer> faultIDs_ForSections = new ArrayList<Integer>();
		List<Double> depthLoForSections = new ArrayList<Double>();
		List<Double> depthHiForSections = new ArrayList<Double>();
		List<Double> aveDipForSections = new ArrayList<Double>();

		// get & check first line (must be the signature line)
		String line = reader.readLine();
		StringTokenizer tok = new StringTokenizer(line);
		int kindOfLine = Integer.parseInt(tok.nextToken());
		String fileSignature = tok.nextToken();
		int fileSpecLevel = Integer.parseInt(tok.nextToken());
		Preconditions.checkState(kindOfLine == 101 && fileSignature.equals(GEOM_FILE_SIG), "Wrong kind of file");
		Preconditions.checkState(fileSpecLevel >= GEOM_FILE_MIN_SPEC_LEVEL && fileSpecLevel <= GEOM_FILE_SPEC_LEVEL,
				"Parser currently supports spec levels "+GEOM_FILE_MIN_SPEC_LEVEL+" through "+GEOM_FILE_SPEC_LEVEL
				+" (given file is "+fileSpecLevel+")");

		int n_section=-1, n_vertex=-1,n_triangle=-1, n_rectangle=-1;
		
		DefaultXY_DataSet areaScatter;
		if (doAreaDebugScatter)
			areaScatter = new DefaultXY_DataSet();

		line = reader.readLine();
		while (line != null) {

			try {
				tok = new StringTokenizer(line);
				kindOfLine = Integer.parseInt(tok.nextToken());

				// read "Fault System Summary Record" (values kept are use as a check later)
				if (kindOfLine == 200) {
					// FORMAT: 200 n_section n_vertex n_triangle n_rectangle
					//		lat_lo lat_hi lon_lo lon_hi depth_lo depth_hi [coord_sys] comment_text
					// coord_sys only exists if spec >= 5

					n_section=Integer.parseInt(tok.nextToken());
					n_vertex=Integer.parseInt(tok.nextToken());
					n_triangle=Integer.parseInt(tok.nextToken());
					n_rectangle=Integer.parseInt(tok.nextToken());
					if (fileSpecLevel >= 5) {
						// make sure that this file is in spherical coordinates, rectangular not yet supported
						tok.nextToken();
						tok.nextToken();
						tok.nextToken();
						tok.nextToken();
						tok.nextToken();
						tok.nextToken();
						int coordSys = Integer.parseInt(tok.nextToken());
						Preconditions.checkState(coordSys == 0, "Currently only spherical coordinates are supported");
					}
				} else if (kindOfLine == 201) {
					// read "Fault Section Information Record"
					int sid = Integer.parseInt(tok.nextToken());  // section ID
					String name = tok.nextToken();
					int n_sect_vertex=Integer.parseInt(tok.nextToken());
					int n_sect_triangle=Integer.parseInt(tok.nextToken());
					int n_sect_rectangle=Integer.parseInt(tok.nextToken());
					tok.nextToken(); // lat_lo
					tok.nextToken(); // lat_hi
					tok.nextToken(); // lon_lo
					tok.nextToken(); // lon_hi
					double depth_lo = Double.parseDouble(tok.nextToken()); // depth_lo
					double depth_hi = Double.parseDouble(tok.nextToken()); // depth_hi
					double das_lo = Double.parseDouble(tok.nextToken()); // das_lo
					double das_hi = Double.parseDouble(tok.nextToken()); // das_hi
					int fault_id = Integer.parseInt(tok.nextToken());
					// the rest of the line contains: comment_text

					// check for triangular elements
					Preconditions.checkState(n_sect_triangle == 0, "Don't yet support triangles");

					sectionNamesList.add(name);
					sectionIDs_List.add(sid);
					faultIDs_ForSections.add(fault_id);
					depthLoForSections.add(depth_lo);
					depthHiForSections.add(depth_hi);

					// read the vertices for this section
					ArrayList<Vertex> verticesForThisSect = new ArrayList<Vertex>();
					for(int v=0; v<n_sect_vertex; v++) {
						line = reader.readLine();
						tok = new StringTokenizer(line);
						kindOfLine = Integer.parseInt(tok.nextToken());
						Preconditions.checkState(kindOfLine == 202, "Problem with file (line should start with 202)");
						int id = Integer.parseInt(tok.nextToken());
						double lat = Double.parseDouble(tok.nextToken());
						double lon = Double.parseDouble(tok.nextToken());
						double depth = -Double.parseDouble(tok.nextToken())/1000; 	// convert to km & change sign
						double das = Double.parseDouble(tok.nextToken())/1000;		// convert to km
						int trace_flag = Integer.parseInt(tok.nextToken());
						// the rest of the line contains:
						// comment_text

						Vertex vertex = new Vertex(lat,lon,depth, id, das, trace_flag); 
						verticesForThisSect.add(vertex);
						vertexList.add(vertex);
					}
					vertexListForSections.add(verticesForThisSect);

					// now read the elements
					double aveDip = 0;
					ArrayList<SimulatorElement> simElemForThisSect = new ArrayList<SimulatorElement>();
					double totArea = 0;
					for(int r=0; r<n_sect_rectangle; r++) {
						line = reader.readLine();
						tok = new StringTokenizer(line);
						kindOfLine = Integer.parseInt(tok.nextToken());
						Preconditions.checkState(kindOfLine == 204, "Problem with file (line should start with 204)");
						int id = Integer.parseInt(tok.nextToken());
						int vertex_1_ID = Integer.parseInt(tok.nextToken());
						int vertex_2_ID = Integer.parseInt(tok.nextToken());
						int vertex_3_ID = Integer.parseInt(tok.nextToken());
						int vertex_4_ID = Integer.parseInt(tok.nextToken());
						double rake = Double.parseDouble(tok.nextToken());
						// convert to meters per year
						double slip_rate = Double.parseDouble(tok.nextToken())*General_EQSIM_Tools.SECONDS_PER_YEAR;
						double aseis_factor = Double.parseDouble(tok.nextToken());
						double strike = Double.parseDouble(tok.nextToken());
						double dip = Double.parseDouble(tok.nextToken());
						aveDip+=dip/n_sect_rectangle;
						int perfect_flag = Integer.parseInt(tok.nextToken());
						// the rest of the line contains: comment_text
						boolean perfectBoolean = false;
						if(perfect_flag == 1) perfectBoolean = true;
						Vertex[] vertices = new Vertex[4];

						vertices[0] = vertexList.get(vertex_1_ID-1);  // vertex index is one minus vertex ID
						vertices[1] = vertexList.get(vertex_2_ID-1);
						vertices[2] = vertexList.get(vertex_3_ID-1);
						vertices[3] = vertexList.get(vertex_4_ID-1);
						int numAlongStrike = -1;// unknown
						int numDownDip = -1;	// unknown
						FocalMechanism focalMechanism = new FocalMechanism(strike,dip,rake);
						RectangularElement rectElem = new RectangularElement(id, vertices, name, fault_id, sid, numAlongStrike, 
								numDownDip, slip_rate, aseis_factor, focalMechanism, perfectBoolean);
						simElemForThisSect.add(rectElem);
						rectElementsList.add(rectElem);
						totArea += rectElem.getArea();
					}
					simElementsListForSections.add(simElemForThisSect);
					aveDipForSections.add(aveDip);

					// test areas (make sure they are within 1%)
					double areaTest = Math.abs((das_hi-das_lo)*(depth_hi-depth_lo)/Math.sin(aveDip*Math.PI/180.0));
					if (doAreaDebugScatter)
						areaScatter.set(areaTest, totArea);
					double test = areaTest/totArea;
					double testTol = 0.01; // ORIGINAL VAL
					//							double testTol = 0.05; // NEW VAL // TODO
					if(test<(1-testTol) || test > (1+testTol)) {
						System.out.println(sid+"\t"+name+"\t"+(float)aveDip+"\t"+totArea
								+"\t"+areaTest+"\t"+areaTest/totArea);
						// throw new IllegalStateException("Error: area discrepancy"); // TODO reinstate check
					}
				}
			} catch (RuntimeException e) {
				System.out.println("Offending Line: "+line);
				System.out.flush();
				throw e;
			}
			line = reader.readLine();
		}

		if (doAreaDebugScatter) {
			GraphWindow gw = new GraphWindow(areaScatter, "Area Debug",
					new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
			gw.setX_AxisLabel("From entire fault min/max");
			gw.setY_AxisLabel("From Sum of Element Areas");
			gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
		}

		// check the numbers of things:  n_sction, n_vertex, n_triangle, n_rectangle
		Preconditions.checkState(n_section == sectionNamesList.size(), "something wrong with number of sections");
		Preconditions.checkState(n_vertex == vertexList.size(), "something wrong with number of vertices");
		Preconditions.checkState(n_rectangle == rectElementsList.size(), "something wrong with number of elements");

		System.out.println("namesOfSections.size()="+sectionNamesList.size()+"\tvertexList.size()="
				+vertexList.size()+"\trectElementsList.size()="+rectElementsList.size());

		// check that indices are in order, and that index is one minus the ID:
		for(int i=0;i<vertexList.size();i++)
			Preconditions.checkState(i == vertexList.get(i).getID()-1, "vertexList index problem at "+i);
		for(int i=0;i<rectElementsList.size();i++)
			Preconditions.checkState(i == rectElementsList.get(i).getID()-1, "rectElementsList index problem at "+i);

		return rectElementsList;
	}
	
	public static List<EQSIM_Event> readEventsFile(URL url, List<SimulatorElement> rectElementsList)
			throws IOException {
		return readEventsFile(url, rectElementsList, null);
	}

	public static List<EQSIM_Event> readEventsFile(URL url, List<SimulatorElement> rectElementsList,
			Collection<? extends RuptureIdentifier> rupIdens) throws IOException {
		URLConnection uc = url.openConnection();
		return readEventsFile(new InputStreamReader((InputStream) uc.getContent()), rectElementsList, rupIdens);
	}
	
	public static List<EQSIM_Event> readEventsFile(File file, List<SimulatorElement> rectElementsList)
			throws IOException {
		return readEventsFile(file, rectElementsList, null);
	}
	
	public static List<EQSIM_Event> readEventsFile(File file, List<SimulatorElement> rectElementsList,
			Collection<? extends RuptureIdentifier> rupIdens) throws IOException {
		return readEventsFile(new FileReader(file), rectElementsList, rupIdens);
	}
	
	/**
	 * Reads the given EQSIM events file. Events can be filtered by the given list of rupture identifiers
	 * to cut down on memory requirements.
	 * @param reader
	 * @param rupIdens
	 * @throws IOException
	 */
	private static List<EQSIM_Event> readEventsFile(
			Reader reader, List<SimulatorElement> rectElementsList, Collection<? extends RuptureIdentifier> rupIdens)
			throws IOException {
		
		BufferedReader buffRead;
		
		if (reader instanceof BufferedReader)
			buffRead = (BufferedReader)reader;
		else
			buffRead = new BufferedReader(reader);
		
		// get & check first line (must be the signature line)
		String line = buffRead.readLine();
		StringTokenizer tok = new StringTokenizer(line);
		int kindOfLine = Integer.parseInt(tok.nextToken());
		String fileSignature = tok.nextToken();
		int fileSpecLevel = Integer.parseInt(tok.nextToken());
//		if(kindOfLine != 101 || !fileSignature.equals(EVENT_FILE_SIG) || fileSpecLevel < EVENT_FILE_SPEC_LEVEL)
		if(kindOfLine != 101 || !fileSignature.equals(EVENT_FILE_SIG) || fileSpecLevel < EVENT_FILE_MIN_SPEC_LEVEL)
			throw new IllegalStateException("wrong type of event input file; your first file line is:\n\n\t"+line+"\n");

		List<EQSIM_Event> eventList = new ArrayList<EQSIM_Event>();
		EQSIM_Event currEvent = null;
		EQSIM_EventRecord evRec = null; // this one never used, but created to compile
		int numEventRecs=0;
		line = buffRead.readLine();
		while (line != null) {
			tok = new StringTokenizer(line);
			kindOfLine = Integer.parseInt(tok.nextToken());
			if(kindOfLine ==200) {	// event record
				try {
					evRec = new EQSIM_EventRecord(line, rectElementsList);
				} catch (Exception e) {
					System.err.println("Unable to parse line: "+line.trim()+" (error: "+e.getMessage()+")");
					line = buffRead.readLine();
					// skip lines until we get to 200 type line (otherwise 201 lines get mapped to previous event)
					StringTokenizer tok2 = new StringTokenizer(line);
					while(Integer.parseInt(tok2.nextToken()) != 200) {
// System.out.println("\nSkipping: "+ line);
						line = buffRead.readLine();
						tok2 = new StringTokenizer(line);						
					}
					continue;
				}
				numEventRecs+=1;
				
				// check whether this is the first event in the list
				if(currEvent == null) {
					EQSIM_Event event = new EQSIM_Event(evRec);
					currEvent = event;
				} else { // check whether this is part of currEvent (same ID)
					if(currEvent.isSameEvent(evRec)) {
						currEvent.addEventRecord(evRec);
					}
					else { // it's a new event
// if(currEvent.getID()==1214) System.out.println(currEvent.getID()+"\t"+currEvent.getAllElementIDs().length);
						// see if we should keep the previous event
						boolean keep = rupIdens == null || rupIdens.isEmpty();
						if (!keep) {
							for (RuptureIdentifier rupIden : rupIdens) {
								if (rupIden.isMatch(currEvent)) {
									keep = true;
									break;
								}
							}
						}
						if (keep)
							eventList.add(currEvent);

						EQSIM_Event event = new EQSIM_Event(evRec);
						currEvent = event;
					}
				}
			}
			else if(kindOfLine == 201) {	// Slip map record
// if(currEvent.getID()==1214) System.out.println(line);
				evRec.addSlipAndElementData(line); // add to the last event record created
			}
			else if(kindOfLine ==202)
				evRec.addType202_Line(line);
			line = buffRead.readLine();
		}
		
		if (currEvent != null) {
			// now add the last one if applicable
			boolean keep = rupIdens == null || rupIdens.isEmpty();
			if (!keep) {
				for (RuptureIdentifier rupIden : rupIdens) {
					if (rupIden.isMatch(currEvent)) {
						keep = true;
						break;
					}
				}
			}
			if (keep)
				eventList.add(currEvent);
		}
		
		System.out.println("Num Events = "+eventList.size()+"\tNum Event Records = "+numEventRecs);
		int numEventsKept = eventList.size();
		int numEventsInFile = currEvent.getID();
		if(numEventsKept != numEventsInFile) {
			System.out.println("Warning: "+(numEventsInFile-numEventsKept)+" were not able to be read from the input file");
		}
		
		return eventList;
	}

}
