package org.opensha.sra.asset.io;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sra.asset.Asset;
import org.opensha.sra.asset.AssetCategory;
import org.opensha.sra.asset.MonetaryHighLowValue;
import org.opensha.sra.asset.Portfolio;
import org.opensha.sra.asset.Value;
import org.opensha.sra.vulnerability.Vulnerability;
import org.opensha.sra.vulnerability.models.servlet.VulnerabilityServletAccessor;

import com.google.common.base.Preconditions;

public class CSVPortfolioParser {
	
	public static Collection<Portfolio> loadCSV(File file) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(file, true, 17);
		return loadCSV(csv);
	}
	
	public static Portfolio loadSingleCSV(File file) throws IOException {
		Collection<Portfolio> ports = loadCSV(file);
		Preconditions.checkState(ports.size() == 1, "load single called, but file contains multiple portfolios");
		return ports.iterator().next();
	}
	
	public static Collection<Portfolio> loadCSV(CSVFile<String> csv) throws IOException {
		HashMap<String, Portfolio> portfolioMap = new HashMap<String, Portfolio>();
		
		VulnerabilityServletAccessor access = new VulnerabilityServletAccessor();
		HashMap<String, Vulnerability> vulnMap = access.getVulnMap();
		
		// start at 1, skip header
		for (int i=1; i<csv.getNumRows(); i++) {
			String name = csv.get(i, 0);
			
			if (!portfolioMap.containsKey(name))
				portfolioMap.put(name, new Portfolio(name));
			
			Portfolio port = portfolioMap.get(name);
			
			int assetID = Integer.parseInt(csv.get(i, 1));
			String assetName = csv.get(i, 2);
			
			AssetCategory cat = AssetCategory.BUILDING;
			double val = Double.parseDouble(csv.get(i, 14));
			double valHi = Double.parseDouble(csv.get(i, 12));
			double valLo = Double.parseDouble(csv.get(i, 13));
			// TODO value basis year?
			Value value = new MonetaryHighLowValue(val, valHi, valLo, 2011);
			
			String vulnString = csv.get(i, 15);
			Vulnerability vuln = vulnMap.get(vulnString);
			if (vuln == null) {
				// TODO HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK HACK
				vuln = vulnMap.values().iterator().next();
			}
//			Preconditions.checkNotNull(vuln, "Unknown Vulnerability: "+vulnString);
			
			double vs30 = Double.parseDouble(csv.get(i, 16));
			
			double lat = Double.parseDouble(csv.get(i, 9));
			double lon = Double.parseDouble(csv.get(i, 10));
			double dep = -Double.parseDouble(csv.get(i, 8));
			
			Location loc = new Location(lat, lon, dep);
			String siteName = csv.get(i, 7);
			Site site = new Site(loc, siteName);
			Vs30_Param vs30Param = new Vs30_Param();
			vs30Param.setValue(vs30);
			site.addParameter(vs30Param);
			
			port.add(new Asset(assetID, siteName, cat, value, vuln, site));
		}
		
		return portfolioMap.values();
	}

}
