package org.opensha.sha.util;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.XMLUtils;
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
		int numLon = region.getNumLonNodes();
		int numLat = region.getNumLatNodes();
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
		root.addAttribute("shakemap_event_type", "SCENARIO");
		
		Element eventEl = root.addElement("event");
		eventEl.addAttribute("event_id", eventID);
		eventEl.addAttribute("magnitude", valDF.format(mag));
		eventEl.addAttribute("event_description", name);
		
		Element gridSpecEl = root.addElement("grid_specification");
		gridSpecEl.addAttribute("lon_min", pga.getMinLon()+"");
		gridSpecEl.addAttribute("lat_min", pga.getMinLat()+"");
		gridSpecEl.addAttribute("lon_max", pga.getMaxLon()+"");
		gridSpecEl.addAttribute("lat_max", pga.getMaxLat()+"");
		gridSpecEl.addAttribute("nominal_lon_spacing", region.getLonSpacing()+"");
		gridSpecEl.addAttribute("nominal_lat_spacing", region.getLatSpacing()+"");
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
		
		StringBuilder gridText = new StringBuilder();
		// lon in inner loop, increasing
		// lat in outer loop, decreasing 
		for (int y=numLat; --y>=0;) {
			double lat = region.getMinLat() + y*region.getLatSpacing();
			String latStr = degreeDF.format(lat);
			for (int x=0; x<numLon; x++) {
				double lon = region.getMinLon() + x*region.getLonSpacing();
				int index = region.indexForLocation(new Location(lat, lon));
				gridText.append(degreeDF.format(lon)+" ");
				gridText.append(latStr+" ");
				// convert G to percent G
				gridText.append(valDF.format(pga.get(index)*100d)+" ");
				gridText.append(valDF.format(pgv.get(index))+" ");
				double mmi = Wald_MMI_Calc.getMMI(pga.get(index), pgv.get(index));
				gridText.append(valDF.format(mmi)+" ");
				gridText.append(valDF.format(sa03.get(index)*100d)+" ");
				gridText.append(valDF.format(sa10.get(index)*100d)+" ");
				if (sa30 == null)
					gridText.append("NaN ");
				else
					gridText.append(valDF.format(sa30.get(index)*100d)+" ");
			}
		}
		Element gridEl = root.addElement("grid_data");
		gridEl.setText(gridText.toString());
		
		XMLUtils.writeDocumentToFile(outputFile, doc);
	}
	
	private static final DecimalFormat degreeDF = new DecimalFormat("0.0000");
	private static final DecimalFormat valDF = new DecimalFormat("0.00");
	
	private static void addGridFieldEl(Element root, int index, String name, String units) {
		Element el = root.addElement("grid_field");
		el.addAttribute("index", index+"");
		el.addAttribute("name", name);
		el.addAttribute("units", units);
	}

}
