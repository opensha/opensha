package org.opensha.sha.simulators.stiffness.patches;

import java.lang.reflect.Proxy;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

/** Cartesian synthetic surfaces that expose only the EvenlyGriddedSurface interface. */
final class SurfaceTestFixtures {
	static Location location(double east, double north, double depth) {
		WGS84 wgs = new WGS84(new UTM(11, 'S', 500000 + east * 1000, 3760000 + north * 1000));
		return new Location(wgs.getLatitude(), wgs.getLongitude(), depth);
	}

	static EvenlyGriddedSurface surface(double length, double[] widths, double[] dips, double topDepth) {
		Location[][] nodes = new Location[dips.length + 1][widths.length];
		for (int c = 0; c < widths.length; c++) {
			double east = 0, depth = topDepth;
			nodes[0][c] = location(east, length * c / (widths.length - 1), depth);
			for (int r = 0; r < dips.length; r++) {
				double step = widths[c] / dips.length;
				east += step * Math.cos(Math.toRadians(dips[r]));
				depth += step * Math.sin(Math.toRadians(dips[r]));
				nodes[r + 1][c] = location(east, length * c / (widths.length - 1), depth);
			}
		}
		return nodes(nodes);
	}

	static EvenlyGriddedSurface nodes(Location[][] nodes) {
		return (EvenlyGriddedSurface) Proxy.newProxyInstance(EvenlyGriddedSurface.class.getClassLoader(),
				new Class<?>[] { EvenlyGriddedSurface.class }, (proxy, method, args) -> {
					switch (method.getName()) {
						case "getNumRows" :
							return nodes.length;
						case "getNumCols" :
							return nodes[0].length;
						case "getLocation" :
							return nodes[(int) args[0]][(int) args[1]];
						default :
							throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	static FaultSection section(EvenlyGriddedSurface surface) {
		FaultTrace trace = new FaultTrace("test");
		for (int c = 0; c < surface.getNumCols(); c++)
			trace.add(surface.getLocation(0, c));
		return (FaultSection) Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
				new Class<?>[] { FaultSection.class }, (proxy, method, args) -> {
					switch (method.getName()) {
						case "getSectionId" :
							return 0;
						case "getSectionName" :
						case "getName" :
							return "synthetic";
						case "getAveRake" :
							return 90d;
						case "getFaultTrace" :
							return trace;
						case "getFaultSurface" :
							return surface;
						case "getArea" :
							return 24e6;
						case "hashCode" :
							return System.identityHashCode(proxy);
						case "equals" :
							return proxy == args[0];
						case "toString" :
							return "synthetic";
						default :
							throw new UnsupportedOperationException(method.getName());
					}
				});
	}
}
