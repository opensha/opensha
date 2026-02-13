package org.opensha.commons.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(ApplicationVersion.Adapter.class)
public class ApplicationVersion implements Comparable<ApplicationVersion> {
	
	private int major;
	private int minor;
	private int build;
	
	public ApplicationVersion(int major, int minor, int build) {
		this.major = major;
		this.minor = minor;
		this.build = build;
	}
	
	public int getMajor() {
		return major;
	}

	public void setMajor(int major) {
		this.major = major;
	}

	public int getMinor() {
		return minor;
	}

	public void setMinor(int minor) {
		this.minor = minor;
	}

	public int getBuild() {
		return build;
	}

	public void setBuild(int build) {
		this.build = build;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + build;
		result = prime * result + major;
		result = prime * result + minor;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ApplicationVersion other = (ApplicationVersion) obj;
		if (build != other.build)
			return false;
		if (major != other.major)
			return false;
		if (minor != other.minor)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + build;
	}
	
	public String getDisplayString() {
		String ret = toString();
		if (ServerPrefUtils.SERVER_PREFS.getBuildType() != DevStatus.PRODUCTION)
			ret += " ("+ServerPrefUtils.SERVER_PREFS.getBuildType()+")";
		return ret;
	}
	
	public static ApplicationVersion fromString(String str) {
		String[] split = str.split("\\.");
		if (split.length < 2 || split.length > 3)
			throw new IllegalArgumentException("Version string must have 2 or 3 period separated version numbers");
		int major = Integer.parseInt(split[0]);
		int minor = Integer.parseInt(split[1]);
		int build = 0;
		if (split.length == 3)
			build = Integer.parseInt(split[2]);
		return new ApplicationVersion(major, minor, build);
	}
	
	public static class Adapter extends TypeAdapter<ApplicationVersion> {

		@Override
		public void write(JsonWriter out, ApplicationVersion value) throws IOException {
			out.value(value.toString());
		}

		@Override
		public ApplicationVersion read(JsonReader in) throws IOException {
			return fromString(in.nextString());
		}
		
	}
	
	@Override
	public int compareTo(ApplicationVersion o) {
		int val;
		val = ((Integer)getMajor()).compareTo(o.getMajor());
		if (val != 0)
			return val;
		val = ((Integer)getMinor()).compareTo(o.getMinor());
		if (val != 0)
			return val;
		val = ((Integer)getBuild()).compareTo(o.getBuild());
		return val;
	}
	
	/**
	 * Returns true if the current version is greater than the supplied (other) version
	 * @param other
	 * @return
	 */
	public boolean isGreaterThan(ApplicationVersion other) {
		return compareTo(other) > 0;
	}
	
	/**
	 * Returns true if the current version is less than the supplied (other) version
	 * @param other
	 * @return
	 */
	public boolean isLessThan(ApplicationVersion other) {
		return compareTo(other) < 0;
	}
	
	private static final String[] possible_version_files = 
			{	"ant/include/build.version",
				"build.version",
				"../opensha/build.version"};
	private static final String[] possible_version_resources = 
		{	"/ant/include/build.version",
			"/build.version"};

	private static URL getVersionFile()
			throws FileNotFoundException {
		return locateURL(possible_version_files, possible_version_resources);
	}

	private static URL locateURL(String[] possible_files, String[] possible_resources)
			throws FileNotFoundException {
		URL url = null;
		for (String fileName : possible_files) {
			try {
				url = new URL("file:"+fileName);
				if (new File(fileName.replace('/', File.separatorChar)).exists()) {
					return url;
				} else
					url = null;
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		for (String resource : possible_resources) {
			try {
				url = ApplicationVersion.class.getResource(resource);
			} catch (Throwable t) {}
			if (url != null)
				return url;
		}
		throw new FileNotFoundException("Couldn't locate build version file!");
	}
	
	public static ApplicationVersion loadBuildVersion() throws IOException {
		return loadBuildVersion(getVersionFile());
	}
	
	public static ApplicationVersion loadBuildVersion(URL versionFile) throws IOException {
//		System.out.println("Loading version from: " + versionFile);
		int major = 0;
		int minor = 0;
		int build = 0;
		for (String line : FileUtils.loadFile(versionFile)) {
			if (line.startsWith("#"))
				continue;
			if (!line.contains("="))
				continue;
			line = line.trim();
			String[] split = line.split("=");
			if (split.length != 2) {
				System.err.println("Incorrectly formatted line: " + line);
				continue;
			}
			if (split[0].equals("major.version"))
				major = Integer.parseInt(split[1]);
			else if (split[0].equals("minor.version"))
				minor = Integer.parseInt(split[1]);
			else if (split[0].equals("build.number"))
				build = Integer.parseInt(split[1]);
			else {
				System.err.println("Unknown key: " + split[0]);
				continue;
			}
		}
		return new ApplicationVersion(major, minor, build);
	}

	private static void testCompare(ApplicationVersion v1, ApplicationVersion v2) {
		System.out.println("Comparing " + v1 + " to " + v2);
		System.out.println(v1 + " > " + v2 + " ? " + v1.isGreaterThan(v2));
		System.out.println(v1 + " < " + v2 + " ? " + v1.isLessThan(v2));
		System.out.println(v1 + " == " + v2 + " ? " + v1.equals(v2));
	}
	
	public static void main(String args[]) throws IOException {
		System.exit(0);
		System.out.println(loadBuildVersion());
		System.out.println(fromString("0.4.2"));
		System.out.println(fromString("0.4"));
		System.out.println("---sort test---");
		ArrayList<ApplicationVersion> versions = new ArrayList<ApplicationVersion>();
		versions.add(fromString("0.4.2"));
		versions.add(fromString("0.4"));
		versions.add(fromString("2.4.2"));
		versions.add(fromString("0.0.0"));
		versions.add(fromString("0.3.1"));
		versions.add(fromString("0.57.2"));
		versions.add(fromString("1.57.2"));
		
		Collections.sort(versions);
		for (ApplicationVersion version : versions) {
			System.out.println(version);
		}
		testCompare(versions.get(0), versions.get(1));
		testCompare(versions.get(1), versions.get(0));
		testCompare(versions.get(1), versions.get(4));
		testCompare(versions.get(4), versions.get(1));
		testCompare(versions.get(0), versions.get(0));
		testCompare(versions.get(4), versions.get(4));
	}

}
