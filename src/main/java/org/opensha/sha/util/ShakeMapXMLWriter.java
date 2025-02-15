package org.opensha.sha.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.XMLUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.imr.attenRelImpl.calc.Wald_MMI_Calc;

import com.google.common.base.Preconditions;

public class ShakeMapXMLWriter {
	
	public static void writeXML(File outputFile, double mag, String name, String eventID,
			GriddedGeoDataSet pga, GriddedGeoDataSet pgv, GriddedGeoDataSet sa03, GriddedGeoDataSet sa10, GriddedGeoDataSet sa30)
					throws IOException {
		Preconditions.checkState(pga.size() == pgv.size());
		Preconditions.checkState(pga.size() == sa03.size());
		Preconditions.checkState(pga.size() == sa10.size());
		Preconditions.checkState(sa30 == null || pga.size() == sa30.size());
		GriddedRegion region = pga.getRegion();
		Preconditions.checkState(region.isRectangular(), "Region must be rectangular");
		
		// use actual gridding, can't trust getNum*Nodes() or getMaxGrid*()
		MinMaxAveTracker gridLatTrack = new MinMaxAveTracker();
		MinMaxAveTracker gridLonTrack = new MinMaxAveTracker();
		for (Location loc : region.getNodeList()) {
			gridLatTrack.addValue(loc.getLatitude());
			gridLonTrack.addValue(loc.getLongitude());
		}
		double minGridLat = gridLatTrack.getMin();
		double maxGridLat = gridLatTrack.getMax();
		double latSpacing = region.getLatSpacing();
		int numLat = (int)((maxGridLat - minGridLat)/latSpacing + 1.4);
		
		double minGridLon = gridLonTrack.getMin();
		double maxGridLon = gridLonTrack.getMax();
		double lonSpacing = region.getLonSpacing();
		int numLon = (int)((maxGridLon - minGridLon)/lonSpacing + 1.4);
//		int numLon = region.getNumLonNodes();
//		int numLat = region.getNumLatNodes();
		int expectedSize = numLon * numLat;
		Preconditions.checkState(expectedSize == pga.size(),
				"Coudn't calculate size: %s * %s = %s != %s", numLon, numLat, expectedSize, pga.size());
		
		Document doc = XMLUtils.createDocumentWithRoot("shakemap_grid");
		Element root = doc.getRootElement();
		root.addAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		root.addAttribute("xmlns", "http://earthquake.usgs.gov/eqcenter/shakemap");
		root.addAttribute("xsi:schemaLocation", "http://earthquake.usgs.gov http://earthquake.usgs.gov/eqcenter/shakemap/xml/schemas/shakemap.xsd");
		root.addAttribute("event_id", eventID);
		root.addAttribute("shakemap_id", eventID);
		root.addAttribute("shakemap_version", "1");
		root.addAttribute("code_version", "3.5.1543");
		root.addAttribute("process_timestamp", dateFormat.format(new Date()));
		root.addAttribute("shakemap_originator", "us");
		root.addAttribute("map_status", "RELEASED");
		root.addAttribute("shakemap_event_type", "SCENARIO");
		
		Element eventEl = root.addElement("event");
		eventEl.addAttribute("event_id", eventID);
		eventEl.addAttribute("magnitude", valDF.format(mag));
		eventEl.addAttribute("event_description", name);
		
		Element gridSpecEl = root.addElement("grid_specification");
		gridSpecEl.addAttribute("lon_min", (float)pga.getMinLon()+"");
		gridSpecEl.addAttribute("lat_min", (float)pga.getMinLat()+"");
		gridSpecEl.addAttribute("lon_max", (float)pga.getMaxLon()+"");
		gridSpecEl.addAttribute("lat_max", (float)pga.getMaxLat()+"");
		gridSpecEl.addAttribute("nominal_lon_spacing", (float)region.getLonSpacing()+"");
		gridSpecEl.addAttribute("nominal_lat_spacing", (float)region.getLatSpacing()+"");
		gridSpecEl.addAttribute("nlon", numLon+"");
		gridSpecEl.addAttribute("nlat", numLat+"");
		
		addGridFieldEl(root, 1, "LON", "dd");
		addGridFieldEl(root, 2, "LAT", "dd");
		addGridFieldEl(root, 3, "PGA", "pctg");
		addGridFieldEl(root, 4, "PGV", "cms");
		addGridFieldEl(root, 5, "MMI", "intensity");
		addGridFieldEl(root, 6, "PSA03", "pctg");
		addGridFieldEl(root, 7, "PSA10", "pctg");
		addGridFieldEl(root, 8, "PSA30", "pctg");
		
		StringBuilder gridText = new StringBuilder().append("\n");
		// lon in inner loop, increasing
		// lat in outer loop, decreasing 
		for (int y=numLat; --y>=0;) {
			double lat = minGridLat + y*region.getLatSpacing();
			String latStr = degreeDF.format(lat);
			for (int x=0; x<numLon; x++) {
				double lon = minGridLon + x*region.getLonSpacing();
				int index = region.indexForLocation(new Location(lat, lon));
				gridText.append(degreeDF.format(lon)+" ");
				gridText.append(latStr+" ");
				double pgaVal = pga.get(index);
				Preconditions.checkState(pgaVal >= 0d, "Bad PGA: %s", pgaVal);
				// convert G to percent G
				gridText.append(valDF.format(pgaVal*100d)+" ");
				double pgvVal = pgv.get(index);
				Preconditions.checkState(pgvVal >= 0d, "Bad PGV: %s", pgvVal);
				gridText.append(valDF.format(pgvVal)+" ");
				double mmi = Wald_MMI_Calc.getMMI(pga.get(index), pgv.get(index));
				Preconditions.checkState(mmi >= 0d && Double.isFinite(mmi),
						"Bad MMI=%s with pga=%s and pgv=%s", mmi, pgaVal, pgvVal);
				gridText.append(valDF.format(mmi)+" ");
				double sa03val = sa03.get(index);
				Preconditions.checkState(sa03val >= 0d, "Bad SA03: %s", sa03val);
				// convert G to percent G
				gridText.append(valDF.format(sa03val*100d)+" ");
				double sa10val = sa10.get(index);
				Preconditions.checkState(sa10val >= 0d, "Bad SA10: %s", sa10val);
				// convert G to percent G
				gridText.append(valDF.format(sa10val*100d)+" ");
				if (sa30 == null)
					gridText.append("0.0");
				else
					gridText.append(valDF.format(sa30.get(index)*100d));
				gridText.append("\n");
			}
		}
		Element gridEl = root.addElement("grid_data");
		gridEl.addText(gridText.toString());
		
		OutputFormat format = OutputFormat.createPrettyPrint();
		format.setTrimText(false); // preserve newlines in grid data
		
		XMLWriter writer = new XMLWriter(new FileWriter(outputFile), format);
		writer.write(doc);
		writer.close();
	}
	
	private static final DecimalFormat degreeDF = new DecimalFormat("0.0000");
	private static final DecimalFormat valDF = new DecimalFormat("0.00");
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	
	private static void addGridFieldEl(Element root, int index, String name, String units) {
		Element el = root.addElement("grid_field");
		el.addAttribute("index", index+"");
		el.addAttribute("name", name);
		el.addAttribute("units", units);
	}
	
	public static void main(String[] args) throws IOException {
//		GriddedRegion reg = new GriddedRegion(new Location(34, -118), new Location(35, -119), 0.5, null);
//		
//		GriddedGeoDataSet data = new GriddedGeoDataSet(reg, false);
//		for (int i=0; i<data.size(); i++)
//			data.set(i, i);
//		
//		writeXML(new File("/tmp/output.xml"), 7d, "Test Event", "ID", data, data, data, data, null);
		
////		File dir = new File("/home/kevin/CyberShake/caloes_shakemaps/consolidated");
//		File dir = new File("/home/kevin/CyberShake/caloes_shakemaps/consolidated_gmpe");
//		String prefix = "newport-inglewood";
//		String name = "Newport-Inglewood Scenario";
//		double mag = 7.25;
		
//		File dir = new File("/home/kevin/CyberShake/caloes_shakemaps/consolidated");
		File dir = new File("/home/kevin/CyberShake/caloes_shakemaps/consolidated_gmpe");
		String prefix = "palos-verdes";
		String name = "Palos Verdes Scenario";
		double mag = 7.05;
		
		GriddedGeoDataSet pga = GriddedGeoDataSet.loadXYZFile(new File(dir, prefix+"_pga.xyz"), true);
		GriddedGeoDataSet pgv = GriddedGeoDataSet.loadXYZFile(new File(dir, prefix+"_pgv.xyz"), true);
		GriddedGeoDataSet sa03 = GriddedGeoDataSet.loadXYZFile(new File(dir, prefix+"_0.3s.xyz"), true);
		GriddedGeoDataSet sa10 = GriddedGeoDataSet.loadXYZFile(new File(dir, prefix+"_1.0s.xyz"), true);
		GriddedGeoDataSet sa30 = GriddedGeoDataSet.loadXYZFile(new File(dir, prefix+"_3.0s.xyz"), true);
		
		writeXML(new File(dir, prefix+"_grid.xml"), mag, name, prefix, pga, pgv, sa03, sa10, sa30);
	}

}
