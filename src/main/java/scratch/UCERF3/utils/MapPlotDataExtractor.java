package scratch.UCERF3.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.commons.data.xyz.AbstractGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.util.FileUtils;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.CompoundFSSPlots.MapBasedPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.MapPlotData;

public class MapPlotDataExtractor {
	
	/**
	 * Extracts all of the gridded data/cpt files from the given XML file and writes to the given directory.
	 * 
	 * @param writeDir
	 * @param mapDataFile
	 * @throws IOException
	 * @throws DocumentException
	 */
	public static void extractMapData(File writeDir, File mapDataFile) throws IOException, DocumentException {
		List<MapPlotData> plotDatas = MapBasedPlot.loadPlotData(mapDataFile);
		
		for (MapPlotData plotData : plotDatas) {
			String fileName = plotData.getFileName();
			GeoDataSet griddedData = plotData.getGriddedData();
			if (griddedData == null) {
				System.out.println("Warning: skipping "+plotData.getFileName()
						+" because it is fault based (no gridded data)");
				continue;
			}
			File myWriteDir;
			if (plotData.getSubDirName() == null) {
				myWriteDir = writeDir;
			} else {
				myWriteDir = new File(writeDir, plotData.getSubDirName());
				if (!myWriteDir.exists())
					myWriteDir.mkdir();
			}
			
			File txtFile = new File(myWriteDir, fileName+".txt");
			File cptFile = new File(myWriteDir, fileName+".cpt");
			
			System.out.println("Writing txt/cpt for "+fileName);
			
			AbstractGeoDataSet.writeXYZFile(griddedData, txtFile);
			plotData.getCPT().writeCPTFile(cptFile);
		}
	}
	
	/**
	 * Downloads the XML file from the given URL and writes all of the gridded data/cpt files from
	 * the given XML file to the given directory.
	 * 
	 * @param writeDir
	 * @param url
	 * @throws IOException
	 * @throws DocumentException 
	 */
	public static void extractMapDataFromURL(File writeDir, String url) throws IOException, DocumentException {
		extractMapDataFromURL(writeDir, new URL(url));
	}
	
	/**
	 * Downloads the XML file from the given URL and writes all of the gridded data/cpt files from
	 * the given XML file to the given directory.
	 * 
	 * @param writeDir
	 * @param url
	 * @throws IOException
	 * @throws DocumentException 
	 */
	public static void extractMapDataFromURL(File writeDir, URL url) throws IOException, DocumentException {
		File dataFile;
		// try to detect file name from the URL
		String[] split = url.getPath().split("/");
		if (split.length > 1)
			dataFile = new File(writeDir, split[split.length-1]);
		else
			dataFile = new File(writeDir, "downloaded_xml_file.xml");
		FileUtils.downloadURL(url, dataFile);
		
		extractMapData(writeDir, dataFile);
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		if (args.length > 0) {
			// CLI version
			// USAGE:
			//	<xml-file>						// this will write data files to same dir as XML file
			//	<xml-file> <write-dir>			// this will write data files to the given dir
			//	<url> <write-dir>				// this is detected by cheking for string starting with "http"
			try {
				Preconditions.checkArgument(args.length <= 2, "Must supply no more than 2 arguments");
				if (args[0].toLowerCase().startsWith("http")) {
					Preconditions.checkArgument(args.length == 2, "Detected URL but no write dir specified");
					File writeDir = new File(args[1]);
					Preconditions.checkArgument(writeDir.exists(), "Write dir doesn't exist: "+writeDir.getPath());
					extractMapDataFromURL(writeDir, args[0]);
				} else {
					File xmlFile = new File(args[0]);
					Preconditions.checkArgument(xmlFile.exists() && xmlFile.isFile(),
							"XML file doesn't exist or is a directory: "+xmlFile.getPath());
					File writeDir;
					if (args.length == 2)
						writeDir = new File(args[1]);
					else
						writeDir = xmlFile.getParentFile();
					Preconditions.checkArgument(writeDir.exists(), "Write dir doesn't exist: "+writeDir.getPath());
					extractMapDataFromURL(writeDir, args[0]);
				}
			} catch (Throwable t) {
				t.printStackTrace();
				System.exit(1);
			}
		}
		
		// otherwise hardcoded paths
		
		// URL based
		String url = "http://opensha.usc.edu/ftp/kmilner/ucerf3/2013_01_14-stampede_3p2_production_runs_combined/" +
				"gridded_participation_plots.xml";
		File writeDir = new File("/tmp");
		extractMapDataFromURL(writeDir, url);
		
		// File based
//		File xmlFile = new File("/path/to/file");
//		File writeDir = new File("/tmp");
//		extractMapData(writeDir, xmlFile);
	}

}
