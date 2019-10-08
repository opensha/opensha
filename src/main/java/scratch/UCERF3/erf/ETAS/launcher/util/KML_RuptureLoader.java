package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import com.google.common.base.Preconditions;

public class KML_RuptureLoader {
	
	private static final HashSet<String> elementsToParse;
	static {
		elementsToParse = new HashSet<>();
		elementsToParse.add("Document");
		elementsToParse.add("Folder");
		elementsToParse.add("Placemark");
		elementsToParse.add("MultiGeometry");
		elementsToParse.add("LineString");
	}
	
	public static class KML_Node {
		public final KML_Node parent;
		public final String type;
		public final String name;
		public final List<KML_Node> children;
		public final FaultTrace trace;
		
		private KML_Node(KML_Node parent, String type, String name, List<KML_Node> children, FaultTrace trace) {
			super();
			this.parent = parent;
			this.type = type;
			this.name = name;
			this.children = children;
			this.trace = trace;
		}

		private KML_Node(KML_Node parent, Element element) {
			this.parent = parent;
			this.type = element.getName();
			Element nameEl = element.element("name");
			if (nameEl == null) {
				name = null;
			} else {
				String tempName = nameEl.getTextTrim();
				if (tempName.isEmpty())
					name = null;
				else
					name = tempName;
			}
			if (type.equals("LineString")) {
				children = null;
				trace = new FaultTrace(null);
				Element coordsEl = element.element("coordinates");
				Preconditions.checkNotNull(coordsEl, "LineString element doesn't have coordinates");
				
				String coordsText = coordsEl.getTextTrim();
				String[] coordsSplit = coordsText.split(" ");
				Preconditions.checkState(coordsSplit.length > 0, "Coordinates element is empty!");
				for (String coords : coordsSplit) {
					String[] ptSplit = coords.split(",");
					Preconditions.checkState(ptSplit.length == 2 || ptSplit.length == 3,
							"Expected either 2 or 3 points, have %s", ptSplit.length);
					double lon = Double.parseDouble(ptSplit[0]);
					double lat = Double.parseDouble(ptSplit[1]);
					double depth = ptSplit.length == 3 ? -Double.parseDouble(ptSplit[2]) : 0d;
					trace.add(new Location(lat, lon, depth));
				}
			} else {
				trace = null;
				children = new ArrayList<>();
				for (Element el : XMLUtils.getSubElementsList(element)) {
					if (!elementsToParse.contains(el.getName()))
						continue;
					children.add(new KML_Node(this, el));
				}
			}
		}
	}
	
	public static KML_Node parseKML(File kmlFile) throws IOException, DocumentException {
		String fName = kmlFile.getName().toLowerCase();
		if (fName.endsWith(".kmz")) {
			// need to unzip it
			ZipFile zip = new ZipFile(kmlFile);
			Enumeration<? extends ZipEntry> entries = zip.entries();
			InputStream is = null;
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (name.toLowerCase().endsWith(".kml"))
					is = zip.getInputStream(entry);
			}
			if (is == null) {
				zip.close();
				throw new IllegalStateException("No .kml files found within kmz file");
			}
			KML_Node ret = parseKML(is);
			zip.close();
			return ret;
		} else {
			return parseKML(new FileInputStream(kmlFile));
		}
	}
	
	public static KML_Node parseKML(InputStream is) throws DocumentException, IOException {
		Document doc = XMLUtils.loadDocument(is);
		Element rootEl = doc.getRootElement();
		List<KML_Node> nodes = new ArrayList<>();
		for (Element el : XMLUtils.getSubElementsList(rootEl))
			nodes.add(new KML_Node(null, el));
		is.close();
		if (nodes.size() == 0) {
			return null;
		}
		if (nodes.size() == 1) {
			return nodes.get(0);
		}
		// more than one node, create a parent
		List<KML_Node> children = new ArrayList<>();
		KML_Node parent = new KML_Node(null, "KML", null, children, null);
		for (KML_Node node : nodes)
			children.add(new KML_Node(parent, node.type, node.name, node.children, node.trace));
		return parent;
	}
	
	private static int countChildren(KML_Node node) {
		int children = 0;
		if (node.children != null) {
			for (KML_Node child : node.children) {
				children++;
				children += countChildren(child);
			}
		}
		return children;
	}
	
	private static int countTraces(KML_Node node) {
		int traces = 0;
		if (node.trace != null)
			traces++;
		if (node.children != null) {
			for (KML_Node child : node.children) {
				if (child.trace != null)
					traces++;
				else
					traces += countTraces(child);
			}
		}
		return traces;
	}
	
	public static void printTree(KML_Node node) {
		printTree(node, 0);
	}
	
	public static void printTree(KML_Node node, int maxLevels) {
		doPrintTree(node, "", 0, maxLevels);
	}
	
	private static void doPrintTree(KML_Node node, String indent, int curLevel, int maxLevels) {
		String str = indent+"type='"+node.type+"', name='"+node.name+"'";
		if (node.trace != null)
			str += ", trace with "+node.trace.size()+" points";
		str += ", parent to "+countTraces(node)+" traces across "+countChildren(node)+" children";
		if (node.children != null)
			str += " ("+node.children.size()+" direct children)";
		System.out.println(str);
		if (node.children != null && (maxLevels <= 0 || curLevel < maxLevels)) {
			String subIndent = indent+"\t";
			int nextLevel = curLevel+1;
			for (KML_Node child : node.children)
				doPrintTree(child, subIndent, nextLevel, maxLevels);
		}
		
	}
	
	public static List<FaultTrace> loadTraces(KML_Node node) {
		List<FaultTrace> traces = new ArrayList<>();
		doLoadTracesByName(traces, node, false, null);
		return traces;
	}
	
	public static List<FaultTrace> loadTracesByName(KML_Node node, boolean exactMatch, String... names) {
		Preconditions.checkArgument(names.length > 0, "No names supplied");
		for (String name : names)
			Preconditions.checkState(!name.isEmpty(), "Empty name supplied");
		List<FaultTrace> traces = new ArrayList<>();
		doLoadTracesByName(traces, node, exactMatch, names);
		return traces;
	}
	
	private static void doLoadTracesByName(List<FaultTrace> traces, KML_Node node, boolean exactMatch, String[] names) {
		boolean inside = names == null; // if no names to check, then we're already inside a folder/document/etc to include
		if (node.trace != null) {
			if (inside)
				traces.add(node.trace);
		} else if (node.children != null) {
			for (KML_Node child : node.children) {
				boolean childInside = inside;
				if (!childInside && names != null && child.name != null) {
					// check names
					for (String testName : names) {
						if (exactMatch)
							childInside = childInside || testName.equals(child.name);
						else
							childInside = childInside || child.name.contains(testName);
					}
				}
				doLoadTracesByName(traces, child, exactMatch, childInside ? null : names);
			}
		}
	}
	
	public static List<SimpleFaultData> buildRuptureSFDs(List<FaultTrace> traces, double upperDepth, double lowerDepth,
			double dip, double dipDirection) {
		List<SimpleFaultData> sfds = new ArrayList<>();
		for (FaultTrace trace : traces) {
			double traceUpperDepth;
			if (Double.isNaN(upperDepth)) {
				traceUpperDepth = 0d;
				for (Location loc : trace)
					upperDepth += loc.getDepth();
				upperDepth /= (double)trace.size();
			} else {
				traceUpperDepth = upperDepth;
			}
			SimpleFaultData sfd = new SimpleFaultData(dip, lowerDepth, traceUpperDepth, trace, dipDirection);
			sfds.add(sfd);
		}
		return sfds;
	}
	
	public static RuptureSurface buildRuptureSurface(List<FaultTrace> traces, double upperDepth, double lowerDepth,
			double dip, double dipDirection, double gridSpacing) {
		List<SimpleFaultData> sfds = buildRuptureSFDs(traces, upperDepth, lowerDepth, dip, dipDirection);
		List<RuptureSurface> surfs = new ArrayList<>();
		for (SimpleFaultData sfd : sfds) {
			surfs.add(new StirlingGriddedSurface(sfd, gridSpacing, gridSpacing));
		}
		if (surfs.size() == 1)
			return surfs.get(0);
		return new CompoundSurface(surfs);
	}

	public static void main(String[] args) throws IOException, DocumentException {
		File kmlFile = new File("/tmp/ridgecrest.kmz");
		KML_Node node = parseKML(kmlFile);
		int totChildren = countChildren(node);
		int totTraces = countTraces(node);
		printTree(node, 2);
		System.out.println("Parsed "+totTraces+" traces across "+totChildren+" children");
		System.out.println(loadTraces(node).size()+" total traces");
		System.out.println(loadTracesByName(node, true, "Field Verified Rupture Traces").size()+" field verified traces");
		System.out.println(loadTracesByName(node, false, "Verified").size()+" contains 'Verified' traces");
		System.out.println(loadTracesByName(node, false, "Field").size()+" contains 'Field' traces");
		buildRuptureSurface(loadTraces(node), Double.NaN, 12d, 90d, Double.NaN, 1d);
	}

}
