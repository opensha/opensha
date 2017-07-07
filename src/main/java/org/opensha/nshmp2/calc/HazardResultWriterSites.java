package org.opensha.nshmp2.calc;

import static com.google.common.base.Charsets.US_ASCII;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.nshmp2.util.Period;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Writer of hazard result for site specific analyses. Does not use close()
 * method.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class HazardResultWriterSites implements HazardResultWriter {

	private static final String N = System.getProperty("line.separator");
	private static final String S = File.separator;
	private static final Joiner JOIN = Joiner.on(',');
	private String outDir;
	private Map<String, Location> siteMap;

	/**
	 * Creates anew local writer instance.
	 * @param outDir output location
	 * @param siteMap
	 * @throws IOException
	 */
	public HazardResultWriterSites(String outDir,
		Map<String, Location> siteMap) throws IOException {
		this.outDir = outDir;
		this.siteMap = siteMap;
	}

	@Override
	public void write(HazardResult result) throws IOException {
		Period period = result.period();
		DiscretizedFunc f = result.curve();
		String siteName = siteNameForLoc(result.location());
		Iterable<String> cityData = createResult(siteName, f);
		String cityLine = JOIN.join(cityData) + N;
		String outPath = outDir + S + period + S + "curves.csv";
		File outFile = new File(outPath);
		Files.createParentDirs(outFile);
		Files.append(cityLine, outFile, US_ASCII);
	}

	@Override
	public void close() {
		// does nothing for this implementation; writers are created as needed
		// by the guava Files utility class
	}

	private String siteNameForLoc(Location loc) {
		for (String name : siteMap.keySet()) {
			if (LocationUtils.areSimilar(siteMap.get(name), loc)) return name;
		}
		return "UnnamedSite";
	}

	private static Iterable<String> createResult(String name,
			DiscretizedFunc curve) {
		Iterable<String> intercepts = Lists.newArrayList(name);
		Iterable<String> values = Collections2.transform(curve.yValues(),
			Functions.toStringFunction());
		return Iterables.concat(intercepts, values);
	}

	/**
	 * Writes the header for the supplied period.
	 * @param period
	 * @throws IOException
	 */
	public void writeHeader(Period period) throws IOException {
		String outPath = outDir + S + period + S + "curves.csv";
		File outFile = new File(outPath);
		Files.createParentDirs(outFile);
		Iterable<String> xValsIt = Collections2.transform(period.getIMLs(),
			Functions.toStringFunction());
		String header = "city," + JOIN.join(xValsIt) + N;
		Files.write(header, outFile, US_ASCII);
	}

}
