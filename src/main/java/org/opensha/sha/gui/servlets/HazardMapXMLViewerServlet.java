package org.opensha.sha.gui.servlets;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.calc.hazardMap.MakeXYZFromHazardMapDir;


/**
 * <p>Title: HazardMapViewerServlet</p>
 * <p>Description: This servlet is hosted on web server gravity.usc.edu.
 * This servlet allows application to give all the datasets ids that contains
 * Hazard curves dataset.
 * When user has selected the dataset using which he wants to compute Hazard Map,
 * it is sent back to this servlet which then uses GMT script to create the map image.</p>
 * @author :Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class HazardMapXMLViewerServlet  extends HttpServlet {



	// directory where all the hazard map data sets will be saved
	public static final String GET_DATA = "Get Data";
	public static final String MAKE_MAP = "Make Map";

	//Process the HTTP Get request
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		System.out.println("HazardMapXMLViewerServlet: Handling GET");
		try {

			// get an input stream from the applet
			ObjectInputStream inputFromApplet = new ObjectInputStream(request.getInputStream());

			/**
			 * get the function desired by th user
			 */
			String functionDesired  = (String) inputFromApplet.readObject();

			if(functionDesired.equalsIgnoreCase(GET_DATA)) {
				// if USER WANTS TO LOAD EXISTING DATA SETS
				loadDataSets(new ObjectOutputStream(response.getOutputStream()));
			}else if(functionDesired.equalsIgnoreCase(MAKE_MAP)){ // IF USER WANTS TO MAKE MAP
				// get the set selected by the user
				String selectedSet = (String)inputFromApplet.readObject();
				GMT_MapGenerator map = (GMT_MapGenerator)inputFromApplet.readObject();
				boolean isProbAt_IML = (Boolean)inputFromApplet.readObject();
				double value = (Double)inputFromApplet.readObject();
				String metadata = "TEMP META!";

				String localDir = "/opt/install/apache-tomcat-5.5.20/webapps/OpenSHA/HazardMapXMLDatasets/" + selectedSet;

				String outFile = localDir + "/" + "xyzCurves_inv";
				if (isProbAt_IML)
					outFile = outFile + "_PROB";
				else
					outFile = outFile + "_IML";
				outFile = outFile + "_" + value + ".txt";

				try {
					if (!(new File(outFile).exists())) { // if the file hasn't already been created
						MakeXYZFromHazardMapDir maker = new MakeXYZFromHazardMapDir(localDir + "/curves", false, true);
						maker.writeXYZFile(isProbAt_IML, value, outFile);
					}
					GeoDataSet xyzData = null;
					if(outFile != null){
						try{
							xyzData = new ArbDiscrGeoDataSet(true);
							ArrayList fileLines = fileLines = FileUtils.loadFile(outFile);
							ListIterator it = fileLines.listIterator();
							while(it.hasNext()){
								StringTokenizer st = new StringTokenizer((String)it.next());
								double lat = Double.valueOf(st.nextToken().trim());
								double lon = Double.valueOf(st.nextToken().trim());
								double val = Double.valueOf(st.nextToken().trim());
								xyzData.set(new Location(lat, lon), val);
							}
						}catch(Exception ee){
							ee.printStackTrace();
						}
					}
					String mapLabel = getMapLabel(isProbAt_IML);
					String jpgFileName  = map.makeMapUsingServlet(xyzData,mapLabel,metadata,null);
					ObjectOutputStream outputToApplet =new ObjectOutputStream(response.getOutputStream());
					outputToApplet.writeObject(jpgFileName);
					outputToApplet.close();
					return;
				} catch (IOException e1) {
					e1.printStackTrace();
					ObjectOutputStream outputToApplet =new ObjectOutputStream(response.getOutputStream());
					outputToApplet.writeObject(Boolean.valueOf(false));
					outputToApplet.close();
					return;
				}
			}

		}catch(Exception e) {
			e.printStackTrace();
			ObjectOutputStream outputToApplet =new ObjectOutputStream(response.getOutputStream());
			outputToApplet.writeObject(Boolean.valueOf(false));
			outputToApplet.close();
			return;
		}
	}
	//
	//	boolean makeServerMap(String fileName, ArrayList gmtLines, String scriptName, String metadata) {
	//
	//		XYZ_DataSetAPI xyzData = null;
	//		if(fileName != null){
	//			ArrayList xVals = new ArrayList();
	//			ArrayList yVals = new ArrayList();
	//			ArrayList zVals = new ArrayList();
	//			try{
	//				ArrayList fileLines = fileLines = FileUtils.loadFile(fileName);
	//				ListIterator it = fileLines.listIterator();
	//				while(it.hasNext()){
	//					StringTokenizer st = new StringTokenizer((String)it.next());
	//					xVals.add(Double.valueOf(st.nextToken().trim()));
	//					yVals.add(Double.valueOf(st.nextToken().trim()));
	//					zVals.add(Double.valueOf(st.nextToken().trim()));
	//				}
	//				xyzData = new ArbDiscretizedXYZ_DataSet(xVals,yVals,zVals);
	//			}catch(Exception ee){
	//				ee.printStackTrace();
	//				return false;
	//			}
	//		}
	//		metadata = metadata + "\n\nYou can download the jpg or postscript files for:\n\t"+
	//		fileName+"\n\n"+
	//		"From (respectively):";
	//
	//		try{
	//			FileWriter fw = new FileWriter(scriptName);
	//			BufferedWriter br = new BufferedWriter(fw);
	//			for(int i=0;i<gmtLines.size();++i)
	//				br.write((String) gmtLines.get(i)+"\n");
	//			br.close();
	//		}catch(Exception e){
	//			e.printStackTrace();
	//			return false;
	//		}
	//		
	//		String[] command ={"sh","-c","sh "+scriptName};
	//	    RunScript.runScript(command);
	//
	//	    return true;
	//	}

	//Process the HTTP Post request
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// call the doPost method
		doGet(request,response);
	}


	/**
	 *
	 * @return the Map label based on the selected Map Type( Prob@IML or IML@Prob)
	 */
	private String getMapLabel(boolean isProbAtIML){
		//making the map
		String label;

		if(isProbAtIML)
			label="Prob";
		else
			label="IML";
		return label;
	}

	/**
	 * Read the data sets, their names, their params needed to generate map
	 * and site range
	 * @param metaDataHash : Hashtable to save metadata
	 * @param lonHash : hashtable to save longitude range
	 * @param latHash : hashtable to save latitude range
	 */
	private void loadDataSets(ObjectOutputStream outputToApplet) {

		try {
			File dirs =new File("/opt/install/apache-tomcat-5.5.20/webapps/OpenSHA/HazardMapXMLDatasets");
			File[] dirList=dirs.listFiles(); // get the list of all the data in the parent directory


			ArrayList<Document> docs = new ArrayList<Document>();

			// for each data set, read the meta data and sites info
			for(int i=0;i<dirList.length;++i){
				File dir = dirList[i];
				if(dir.isDirectory()){

					File[] subDirList = dir.listFiles(); 

					for (File file : subDirList) {
						if (file.getName().toLowerCase().endsWith(".xml")) {
							try {
								SAXReader reader = new SAXReader();
								Document document = reader.read(file);

								docs.add(document);
							} catch (Exception e) {
								e.printStackTrace();
							}
							break;
						}
					}
				}
			}

			outputToApplet.writeObject(docs);
			outputToApplet.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method reads the file and generates the final outputfile
	 * for the range of the lat and lon selected by the user . The final output is
	 * generated based on the selcetion made by the user either for the iml@prob or
	 * prob@iml. The data is appended to the end of the until all the list of the
	 * files have been searched for thr input iml or prob value. The final output
	 * file is given as the input to generate the grd file.
	 * @param selectedSet : Selected Hazard dataset
	 * @param isProbAt_IML : what to plot IML@Prob or Prob@IML
	 * @param val : Depending on the above parameter it is either prob val if IML@Prob
	 * or iml val if Prob@IML
	 * @param map : GMT object
	 * @return
	 */
	private GeoDataSet getXYZ_DataSet(String dirName,
			boolean isProbAt_IML,
			double val){

		File masterDir = new File("/opt/install/apache-tomcat-5.5.20/webapps/OpenSHA/HazardMapXMLDatasets/" + dirName);
		File[] dirList=masterDir.listFiles();

		GeoDataSet xyzData = new ArbDiscrGeoDataSet(true);

		// for each file in the list
		for(File dir : dirList){
			// make sure it's a subdirectory
			if (dir.isDirectory() && !dir.getName().endsWith(".")) {
				File[] subDirList=dir.listFiles();
				for(File file : subDirList) {
					//only taking the files into consideration
					if(file.isFile()){
						String fileName = file.getName();
						//files that ends with ".txt"
						if(fileName.endsWith(".txt")){
							int index = fileName.indexOf("_");
							int firstIndex = fileName.indexOf(".");
							int lastIndex = fileName.lastIndexOf(".");
							// Hazard data files have 3 "." in their names
							//And leaving the rest of the files which contains only 1"." in their names
							if(firstIndex != lastIndex){

								//getting the lat and Lon values from file names
								Double latVal = Double.valueOf(fileName.substring(0,index).trim());
								Double lonVal = Double.valueOf(fileName.substring(index+1,lastIndex).trim());
								//System.out.println("Lat: " + latVal + " Lon: " + lonVal);
								// handle the file
								double writeVal = handleFile(latVal, lonVal, file.getAbsolutePath(), isProbAt_IML, val);
								xyzData.set(new Location(latVal, lonVal), writeVal);
							}
						}
					}
				}
			}


		}

		return xyzData;
	}

	public double handleFile(double lat, double lon, String fileName, boolean isProbAt_IML, double val) {
		try {
			ArrayList fileLines = FileUtils.loadFile(fileName);
			String dataLine;
			StringTokenizer st;
			ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();

			for(int i=0;i<fileLines.size();++i) {
				dataLine=(String)fileLines.get(i);
				st=new StringTokenizer(dataLine);
				//using the currentIML and currentProb we interpolate the iml or prob
				//value entered by the user.
				double currentIML = Double.parseDouble(st.nextToken());
				double currentProb= Double.parseDouble(st.nextToken());
				func.set(currentIML, currentProb);
			}

			double interpolatedVal = 0;
			if (isProbAt_IML)
				//final iml value returned after interpolation in log space
				return func.getInterpolatedY_inLogXLogYDomain(val);
			// for  IML_AT_PROB
			else { //interpolating the iml value in log space entered by the user to get the final iml for the
				//corresponding prob.
				double out;
				try {
					out = func.getFirstInterpolatedX_inLogXLogYDomain(val);
					return out;
				} catch (RuntimeException e) {
					System.err.println("WARNING: Probability value doesn't exist, setting IMT to NaN");
					//return 0d;
					return Double.NaN;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Double.NaN;
	}

}



