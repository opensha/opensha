package org.opensha.sha.simulators.stiffness.patches;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.simulators.stiffness.patches.GenericSubSectPatchBuilder.ReferenceFrameType;

/** Optional comparison exporter. Exports preferred-frame choices and records any failures; metrics identify planar fallback. */
public class ReferenceFrameComparison {
	public static void main(String[] args) throws Exception {
		Path root=Path.of(args[0]); Files.createDirectories(root);
		var cases=new LinkedHashMap<String,EvenlyGriddedSurface>();
		cases.put("rectangle",GenericSubSectPatchBuilderTest.surface(12,new double[] {8,8,8,8,8},new double[] {45,45,45,45},5));
		cases.put("triangle",GenericSubSectPatchBuilderTest.surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5));
		cases.put("undulating",GenericSubSectPatchBuilderTest.surface(20,new double[] {10,10,2,2,10,10},new double[] {60,60,60,60},5));
		cases.put("skewed",GenericSubSectPatchBuilderTest.skewedSurface());
		cases.put("listric",GenericSubSectPatchBuilderTest.surface(12,new double[] {8,8,8,8,8},new double[] {80,60,40,20},5));
		cases.putAll(PathologicalSurfaceFixtures.examples());
		cases.put("curved_ribbon",RibbonSurfaceReferenceFrameTest.curvedRibbon());
		// Optional real geometry is deliberately supplied by the caller, not required by tests.
		if (args.length > 1) for (var section : GeoJSONFaultReader.readFaultSections(Path.of(args[1]).toFile()))
			if (section.getSectionId() == 175 || section.getSectionId() == 148)
				cases.put(section.getSectionId() == 175 ? "bare_mountain_175" : "aubrey_148",(EvenlyGriddedSurface)section.getFaultSurface(0.2,false,false));
		double smoothingMultiplier=args.length > 2 ? Double.parseDouble(args[2]) : GenericSubSectPatchBuilder.DEFAULT_RIBBON_SMOOTHING_MULTIPLIER;
		try (PrintWriter status=new PrintWriter(Files.newBufferedWriter(root.resolve("status.txt")))) {
			for (var type : ReferenceFrameType.values()) {
				Path directory=root.resolve(type.name().toLowerCase()); Files.createDirectories(directory);
				for (var entry : cases.entrySet()) {
					try {
						GenericPatchDiagnostics.write(directory,entry.getKey(),entry.getValue(),
								new GenericSubSectPatchBuilder(2,11,'S',type,smoothingMultiplier),8);
						status.println(type+" / "+entry.getKey()+": OK");
					} catch (RuntimeException e) {
						status.println(type+" / "+entry.getKey()+": "+e);
						System.err.println(type+" / "+entry.getKey()+": "+e);
					}
					status.flush();
				}
			}
		}
	}
}
