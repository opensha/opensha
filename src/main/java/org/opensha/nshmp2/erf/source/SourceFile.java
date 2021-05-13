package org.opensha.nshmp2.erf.source;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.nshmp2.util.SourceRegion;
import org.opensha.nshmp2.util.SourceType;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

/**
 * Wrapper for NSHMP source files
 */
class SourceFile {

	private SourceRegion region;
	private SourceType type;
	private URL url;
	private double weight;
	private String name;

	SourceFile(URL url, SourceRegion region, SourceType type, double weight) {
		this.url = url;
		this.region = region;
		this.type = type;
		this.weight = weight;
		name = StringUtils.substringAfterLast(url.toString(), "/");
	}

	@Override
	public String toString() {
		return new StringBuffer(StringUtils.rightPad(region.toString(), 24))
			.append(StringUtils.rightPad(type.toString(), 12))
			.append(
				StringUtils.rightPad(
					new Double(Precision.round(weight, 7)).toString(), 11))
			.append(name).toString();
	}

	SourceRegion getRegion() {
		return region;
	}

	SourceType getType() {
		return type;
	}

	String getName() {
		return name;
	}

	double getWeight() {
		return weight;
	}
	
	List<String> readLines() {
		List<String> lines = null;
		try {
			lines = Resources.readLines(url, Charsets.US_ASCII);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return lines;
	}

}
