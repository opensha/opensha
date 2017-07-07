package org.opensha.sha.simulators.writers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

import com.google.common.collect.Lists;

public class EQSimv06FileWriter {
	
	final static String GEOM_FILE_SIG = "EQSim_Input_Geometry_2";	// signature of the geometry file
	final static int GEOM_FILE_SPEC_LEVEL = 4;
	
	private static final DateFormat df = new SimpleDateFormat("MMM d, yyyy");
	
	public static String getCurDateString() {
		return df.format(new Date());
	}
	
	/**
	 * 
	 * @param elems
	 * @param outputFile
	 * @param infoLines each line here should NOT end with a new line char "\n" (this will be added)
	 * @param titleLine
	 * @param author
	 * @param date
	 * @throws IOException 
	 */
	public static void writeGeometryFile(List<SimulatorElement> rectElementsList, File outputFile,
			List<String> infoLines, String titleLine, String author, String date) throws IOException {
		FileWriter efw = new FileWriter(outputFile);
		
		// write the standard file signature info
		efw.write("101 "+GEOM_FILE_SIG +" "+GEOM_FILE_SPEC_LEVEL+ "\n");
		
		// add the file-specific meta data records/lines
		if(titleLine!=null)
			efw.write("111 "+titleLine+ "\n");
		if(author!=null)
			efw.write("112 "+author+ "\n");
		if(date!=null)
			efw.write("113 "+date+ "\n");
		if(infoLines!=null)
			for(int i=0; i<infoLines.size();i++)
				efw.write("110 "+infoLines.get(i)+ "\n");
		
		// add the standard descriptor records/lines for the Geometry file (read from another file)
		String fullPath = "org/opensha/sha/simulators/parsers/eqsim_docs/Sample_File_Headers/"
				+ "Sample_Header_Input_Geometry.txt";
		List<String> lines=null;
		try {
			lines = FileUtils.loadJarFile(fullPath);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		for(int l=0;l<lines.size();l++) {
			String line = lines.get(l).trim();
			if (line.isEmpty())
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			int kindOfLine = Integer.parseInt(tok.nextToken());
			if(kindOfLine==120 || kindOfLine==121 || kindOfLine==103)
				efw.write(line+"\n");
		}
		
		// now add the data records/lines
		List<String> sectionNamesList = Lists.newArrayList();
		String prevName = null;
		List<Vertex> vertexList = Lists.newArrayList();
		List<List<Vertex>> vertexListForSections = Lists.newArrayList();
		List<Vertex> vertexListForSection = null;
		List<List<SimulatorElement>> rectElementsListForSections = Lists.newArrayList();
		List<SimulatorElement> rectElementsListForSection = null;
		for (SimulatorElement elem : rectElementsList) {
			String sectName = elem.getName();
			if (!sectName.equals(prevName)) {
				sectionNamesList.add(sectName);
				prevName = sectName;
				vertexListForSection = Lists.newArrayList();
				rectElementsListForSection = Lists.newArrayList();
				rectElementsListForSections.add(rectElementsListForSection);
				vertexListForSection = Lists.newArrayList();
				vertexListForSections.add(vertexListForSection);
			}
			rectElementsListForSection.add(elem);
			for (Vertex v : elem.getVertices()) {
				vertexList.add(v);
				vertexListForSection.add(v);
			}
		}
		
		// Fault System Summary Record:
		// 200 n_section n_vertex n_triangle n_rectangle lat_lo lat_hi lon_lo lon_hi
		// depth_lo depth_hi coord_sys comment_text
		String coord_sys = "0"; // spherical. rectangular is 1 and not supported by this code
		efw.write("200 "+sectionNamesList.size()+" "+vertexList.size()+" 0 "+rectElementsList.size()+" "+
						getMinMaxFileString(vertexList, false)+" "+coord_sys+"\n");
		// TODO add coordinate system

		// loop over sections
		for(int i=0;i<sectionNamesList.size();i++) {
			List<Vertex> vertListForSect = vertexListForSections.get(i);
			List<SimulatorElement> rectElemForSect = rectElementsListForSections.get(i);
			int fault_id = rectElemForSect.get(0).getFaultID();
			String fault_id_str;
			if (fault_id >= 0)
				fault_id_str = fault_id+"";
			else
				fault_id_str = "NA";
			// Fault Section Information Record:
			// 201 sid name n_vertex n_triangle n_rectangle lat_lo lat_hi lon_lo lon_hi
			// depth_lo depth_hi das_lo das_hi fault_id comment_text
			efw.write("201 "+(i+1)+" "+sectionNamesList.get(i)+" "+vertListForSect.size()+" 0 "+
					rectElemForSect.size()+" "+getMinMaxFileString(vertListForSect, true)+" "+fault_id_str+"\n");
			for(int v=0; v<vertListForSect.size(); v++) {
				Vertex vert = vertListForSect.get(v);
				// Vertex Record: 202 ID lat lon depth das trace_flag comment_text
				efw.write("202 "+vert.getID()+" "+(float)vert.getLatitude()+" "+(float)vert.getLongitude()+" "+
						(float)(vert.getDepth()*-1000)+" "+(float)vert.getDAS()*1000+" "+vert.getTraceFlag()+"\n");
			}
			for(int e=0; e<rectElemForSect.size(); e++) {
				// TODO deal with triangular
				RectangularElement elem = (RectangularElement)rectElemForSect.get(e);
				Vertex[] vert = elem.getVertices();
				FocalMechanism focalMech = elem.getFocalMechanism();
				// Rectangle Record:  204 ID vertex_1 vertex_2 vertex_3 vertex_4 rake slip_rate aseis_factor strike dip perfect_flag comment_text
				efw.write("204 "+elem.getID()+" "+vert[0].getID()+" "+vert[1].getID()+" "+vert[2].getID()+" "+
						vert[3].getID()+" "+(float)focalMech.getRake()+" "
						+(float)(elem.getSlipRate()/General_EQSIM_Tools.SECONDS_PER_YEAR)+" "+
						(float)elem.getAseisFactor()+" "+(float)focalMech.getStrike()+" "+(float)focalMech.getDip()
						+" "+elem.getPerfectInt()+"\n");
			}
		}
		
		// add the last line
		efw.write("999 End\n");

		efw.close();
	}
	
	/**
	 * This produces the string of min and max lat, lon, depth, and (optionally) DAS from the
	 * given list of vertices.  There are no spaces before or after the first and last values,
	 * respectively.  Depth and DAS values are converted to meters (from km).
	 * @param vertexList
	 * @param includeDAS
	 * @return
	 */
	private static String getMinMaxFileString(List<Vertex> vertexList, boolean includeDAS) {
		double minLat=Double.MAX_VALUE, maxLat=-Double.MAX_VALUE;
		double minLon=Double.MAX_VALUE, maxLon=-Double.MAX_VALUE;
		double minDep=Double.MAX_VALUE, maxDep=-Double.MAX_VALUE;
		double minDAS=Double.MAX_VALUE, maxDAS=-Double.MAX_VALUE;
		for(Vertex vertex: vertexList) {
			if(vertex.getLatitude()<minLat) minLat = vertex.getLatitude();
			if(vertex.getLongitude()<minLon) minLon = vertex.getLongitude();
			if(vertex.getDepth()<minDep) minDep = vertex.getDepth();
			if(vertex.getDAS()<minDAS) minDAS = vertex.getDAS();
			if(vertex.getLatitude()>maxLat) maxLat = vertex.getLatitude();
			if(vertex.getLongitude()>maxLon) maxLon = vertex.getLongitude();
//			if(!includeDAS) System.out.println(maxLon);
			if(vertex.getDepth()>maxDep) maxDep = vertex.getDepth();
			if(vertex.getDAS()>maxDAS) maxDAS = vertex.getDAS();
		}
		String string = (float)minLat+" "+(float)maxLat+" "+(float)minLon+" "+(float)maxLon+" "+(float)maxDep*-1000+" "+(float)minDep*-1000;
		if(includeDAS) string += " "+(float)minDAS*1000+" "+(float)maxDAS*1000;
		return string;
	}
	
	/**
	 * We can't currently write output files, but this method will write a new output file that skips all events
	 * below the given magnitude from the given input file.
	 * @param inputFile
	 * @param outputFile
	 * @param minMag
	 * @throws IOException 
	 */
	public static void filterEventFileByMag(File inputFile, File outputFile, double minMag) throws IOException {
		FileWriter fw = new FileWriter(outputFile);
		
		BufferedReader buffRead = new BufferedReader(new FileReader(inputFile));
		boolean skip = false;
		
		String line = buffRead.readLine();
		while (line != null) {
			line = line.trim();
			if (line.startsWith("200 ")) {
				String magStr = line.split(" ")[2];
				skip = magStr.toLowerCase().contains("inf") || Double.parseDouble(magStr) < minMag;
			} else if (!line.startsWith("2")) {
				skip = false;
			}
			
			if (!skip)
				fw.write(line+"\n");
			
			line = buffRead.readLine();
		}
		
		fw.close();
		buffRead.close();
	}
	
	public static void main(String[] args) throws IOException {
		File inputFile = new File("/home/kevin/Simulators/eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.long.barall");
		File outputFile = new File("/home/kevin/Simulators/eqs.ALLCAL2_RSQSim_sigma0.5-5_b=0.015.long.barall.m7");
		filterEventFileByMag(inputFile, outputFile, 7d);
	}

}
