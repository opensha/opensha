package org.opensha.sha.simulators.stiffness.patches;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.sha.faultSurface.ApproxEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.simulators.stiffness.StiffnessCalc;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

/** Synthetic Cartesian fixtures intentionally hide all abstract-surface helpers. */
public class GenericSubSectPatchBuilderTest {
	private static final int ZONE=11;
	private static final char LETTER='S';

	static Location location(double east, double north, double depth) {
		WGS84 wgs=new WGS84(new UTM(ZONE,LETTER,500000+east*1000,3760000+north*1000));
		return new Location(wgs.getLatitude(),wgs.getLongitude(),depth);
	}

	static EvenlyGriddedSurface surface(double length, double[] widths, double[] dips, double topDepth) {
		Location[][] nodes=new Location[dips.length+1][widths.length];
		for (int c=0; c<widths.length; c++) {
			double east=0,depth=topDepth;
			nodes[0][c]=location(east,length*c/(widths.length-1),depth);
			for (int r=0; r<dips.length; r++) {
				double step=widths[c]/dips.length;
				east+=step*Math.cos(Math.toRadians(dips[r]));
				depth+=step*Math.sin(Math.toRadians(dips[r]));
				nodes[r+1][c]=location(east,length*c/(widths.length-1),depth);
			}
		}
		return nodes(nodes);
	}

	static EvenlyGriddedSurface nodes(Location[][] nodes) {
		return (EvenlyGriddedSurface)Proxy.newProxyInstance(EvenlyGriddedSurface.class.getClassLoader(),
				new Class<?>[] {EvenlyGriddedSurface.class},(proxy,method,args)-> {
					switch (method.getName()) {
					case "getNumRows": return nodes.length;
					case "getNumCols": return nodes[0].length;
					case "getLocation": return nodes[(int)args[0]][(int)args[1]];
					default: throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	static EvenlyGriddedSurface skewedSurface() {
		Location[][] nodes=new Location[5][7];
		for (int r=0; r<5; r++) for (int c=0; c<7; c++) {
			double v=r/4d,u=c/6d;
			nodes[r][c]=location(8*v*Math.cos(Math.PI/4),12*u+4*v,5+8*v*Math.sin(Math.PI/4));
		}
		return nodes(nodes);
	}

	static FaultSection section(EvenlyGriddedSurface surface) {
		FaultTrace trace=new FaultTrace("test");
		for (int c=0; c<surface.getNumCols(); c++) trace.add(surface.getLocation(0,c));
		return (FaultSection)Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
				new Class<?>[] {FaultSection.class},(proxy,method,args)-> {
					switch (method.getName()) {
					case "getSectionId": return 0;
					case "getSectionName": case "getName": return "synthetic";
					case "getAveRake": return 90d;
					case "getFaultTrace": return trace;
					case "getFaultSurface": return surface;
					case "getArea": return 24e6;
					case "hashCode": return System.identityHashCode(proxy);
					case "equals": return proxy == args[0];
					case "toString": return "synthetic";
					default: throw new UnsupportedOperationException(method.getName());
					}
				});
	}

	@Test public void intervalConventions() {
		assertArrayEquals(new double[] {7d/6,3.5,35d/6},PatchIntervalPlacement.centers(7,2,PatchAlignment.CENTER),1e-12);
		assertArrayEquals(new double[] {1,8d/3,13d/3,6},PatchIntervalPlacement.centers(7,2,PatchAlignment.FILL_OVERLAP),1e-12);
		for (PatchAlignment alignment : PatchAlignment.values())
			assertArrayEquals(new double[] {0.25},PatchIntervalPlacement.centers(0.5,2,alignment),0);
	}

	@Test public void interfaceOnlyPlanarGeometry() {
		EvenlyGriddedSurface surface=surface(6,new double[] {4,4,4,4},new double[] {45,45,45,45},5);
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(surface,ZONE,LETTER);
		GriddedSurfaceGeometry.SurfacePoint sample=geometry.interpolate(1.5,1.5);
		assertEquals(45,sample.dip,0.002);
		assertEquals(0,Math.min(sample.strike,360-sample.strike),0.002);
		assertEquals(6*4,java.util.stream.IntStream.range(0,4).mapToDouble(r ->
				java.util.stream.IntStream.range(0,3).mapToDouble(c -> geometry.cellArea(r,c)).sum()).sum(),0.001);
		for (PatchAlignment alignment : PatchAlignment.values()) {
			List<PatchLocation> patches=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(surface,90,alignment);
			assertFalse(patches.isEmpty());
			for (PatchLocation patch : patches) {
				assertEquals(4e6,patch.patch.area,0);
				assertEquals(45,Math.toDegrees(patch.patch.dip),0.002);
				assertEquals(90,Math.toDegrees(patch.patch.rake),1e-12);
				for (Location corner : patch.corners) assertTrue(corner.getDepth() >= 0);
			}
			double[] stress=StiffnessCalc.calcStiffness(30000,30000,patches.get(0).patch,patches.get(patches.size()-1).patch);
			assertNotNull(stress);
			for (double value : stress) assertTrue(Double.isFinite(value));
		}
	}

	@Test public void reversingColumnsPreservesDippingPatchMechanismsAndStiffness() {
		EvenlyGriddedSurface original=surface(6,new double[] {4,4,4,4},new double[] {45,45,45,45},5);
		Location[][] reversed=new Location[original.getNumRows()][original.getNumCols()];
		for (int r=0; r<reversed.length; r++) for (int c=0; c<reversed[0].length; c++)
			reversed[r][c]=original.getLocation(r,reversed[0].length-1-c);
		EvenlyGriddedSurface backward=nodes(reversed);
		assertEquals(45,new GriddedSurfaceGeometry(backward,ZONE,LETTER).interpolate(1.5,1.5).dip,0.002);
		for (PatchAlignment alignment : PatchAlignment.values()) {
			GenericSubSectPatchBuilder builder=new GenericSubSectPatchBuilder(2,ZONE,LETTER);
			List<PatchLocation> a=new java.util.ArrayList<>(builder.buildPatches(original,25,alignment));
			List<PatchLocation> b=new java.util.ArrayList<>(builder.buildPatches(backward,25,alignment));
			java.util.Comparator<PatchLocation> order=java.util.Comparator
					.comparingLong((PatchLocation p)->Math.round(p.patch.center[2]/0.01))
					.thenComparingDouble(p->p.patch.center[1]);
			a.sort(order); b.sort(order);
			assertEquals(a.size(),b.size());
			for (int i=0; i<a.size(); i++) {
				assertArrayEquals(a.get(i).patch.center,b.get(i).patch.center,0.02);
				assertEquals(a.get(i).patch.strike,b.get(i).patch.strike,1e-6);
				assertEquals(a.get(i).patch.dip,b.get(i).patch.dip,1e-6);
				assertEquals(a.get(i).patch.rake,b.get(i).patch.rake,0);
			}
			assertArrayEquals(StiffnessCalc.calcStiffness(30000,30000,a.get(0).patch,a.get(a.size()-1).patch),
					StiffnessCalc.calcStiffness(30000,30000,b.get(0).patch,b.get(b.size()-1).patch),1e-6);
		}
	}

	@Test public void reversedWindingStillRejectsFoldedCells() {
		Location[][] bowtie={{location(0,0,1),location(0,4,1)},
				{location(2,4,3),location(2,0,3)}};
		assertThrows(IllegalArgumentException.class,()->new GriddedSurfaceGeometry(nodes(bowtie),ZONE,LETTER));
		Location[][] reversed={{bowtie[0][1],bowtie[0][0]},{bowtie[1][1],bowtie[1][0]}};
		assertThrows(IllegalArgumentException.class,()->new GriddedSurfaceGeometry(nodes(reversed),ZONE,LETTER));
	}

	@Test public void rowsTerminateAndRestart() {
		EvenlyGriddedSurface surface=surface(20,new double[] {10,10,2,2,10,10},new double[] {60,60,60,60},5);
		SurfaceReferenceFrame frame=new SurfaceReferenceFrame(new GriddedSurfaceGeometry(surface,ZONE,LETTER));
		List<double[]> intervals=frame.intervals(4,true);
		assertEquals(2,intervals.size());
		assertTrue(intervals.get(0)[1] < intervals.get(1)[0]);
		for (double[] interval : intervals) {
			double x=(interval[0]+interval[1])*0.5;
			assertTrue(frame.contains(x,4));
			frame.sample(x,4);
		}
		assertFalse(new GenericSubSectPatchBuilder(2,ZONE,LETTER)
				.buildPatches(surface,0,PatchAlignment.FILL_OVERLAP).isEmpty());
	}

	@Test public void intermediateRowsControlLocalDip() {
		EvenlyGriddedSurface surface=surface(10,new double[] {8,8,8},new double[] {80,60,40,20},5);
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(surface,ZONE,LETTER);
		for (int r=0; r<4; r++) assertEquals(80-20*r,geometry.interpolate(r+0.5,1).dip,0.002);
		List<PatchLocation> patches=new GenericSubSectPatchBuilder(2,ZONE,LETTER)
				.buildPatches(surface,0,PatchAlignment.FILL_OVERLAP);
		double min=patches.stream().mapToDouble(p->p.patch.dip).min().getAsDouble();
		double max=patches.stream().mapToDouble(p->p.patch.dip).max().getAsDouble();
		assertTrue(Math.toDegrees(max-min) > 40);
	}

	@Test public void approximateUpperLowerTraces() {
		FaultTrace upper=new FaultTrace("upper"),lower=new FaultTrace("lower");
		upper.add(location(0,0,5)); upper.add(location(0,8,5));
		lower.add(location(4,-2,9)); lower.add(location(4,10,9));
		ApproxEvenlyGriddedSurface surface=new ApproxEvenlyGriddedSurface(upper,lower,0.2);
		for (PatchAlignment alignment : PatchAlignment.values())
			assertFalse(new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(surface,90,alignment).isEmpty());
	}

	@Test public void shallowPatchesStayBelowFreeSurface() {
		EvenlyGriddedSurface surface=surface(6,new double[] {4,4,4},new double[] {90,90},0);
		for (PatchLocation p : new GenericSubSectPatchBuilder(2,ZONE,LETTER)
				.buildPatches(surface,0,PatchAlignment.FILL_OVERLAP))
			for (double[] corner : p.patch.corner) assertTrue(corner[2] <= 1e-6);
	}

	@Test public void cacheKeyIncludesGeometryAndStrategy() {
		SubSectStiffnessCalculator first=calculator(surface(6,new double[] {4,4,4},new double[] {45,45},5));
		SubSectStiffnessCalculator second=calculator(surface(6,new double[] {4,4,4},new double[] {60,60},5));
		String a=first.getAggregationCache(SubSectStiffnessCalculator.StiffnessType.CFF).getCacheFileName();
		String b=second.getAggregationCache(SubSectStiffnessCalculator.StiffnessType.CFF).getCacheFileName();
		assertTrue(a.contains("genGENERIC"));
		assertNotEquals(a,b);
		assertEquals(a,first.getAggregationCache(SubSectStiffnessCalculator.StiffnessType.CFF).getCacheFileName());
	}

	@Test public void taperedTipsInBothDirections() {
		for (double[] widths : new double[][] {{0,2,4,6,8}, {8,6,4,2,0}}) {
			EvenlyGriddedSurface surface=surface(12,widths,new double[] {45,45,45,45},5);
			for (PatchAlignment alignment : PatchAlignment.values()) {
				GenericSubSectPatchBuilder.Layout layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER)
						.buildLayout(surface,90,alignment);
				assertFalse(layout.patches.isEmpty());
				for (double[] xy : layout.referenceCenters)
					assertTrue(layout.frame.contains(xy[0],xy[1]));
			}
		}
	}

	@Test public void stirlingMatchesOriginalPlacement() {
		org.opensha.refFaultParamDb.vo.FaultSectionPrefData section=new org.opensha.refFaultParamDb.vo.FaultSectionPrefData();
		section.setSectionId(0);
		section.setSectionName("Stirling fixture");
		FaultTrace trace=new FaultTrace("upper");
		trace.add(location(0,0,5)); trace.add(location(0,7,5));
		section.setFaultTrace(trace);
		section.setAveDip(45);
		section.setAveUpperDepth(5);
		section.setAveLowerDepth(5+5*Math.sin(Math.PI/4));
		section.setAveRake(90);
		org.opensha.sha.faultSurface.StirlingGriddedSurface surface=(org.opensha.sha.faultSurface.StirlingGriddedSurface)
				section.getFaultSurface(0.2,false,false);
		for (PatchAlignment alignment : PatchAlignment.values()) {
			SubSectStiffnessCalculator calc=new SubSectStiffnessCalculator(List.of(section),2,30000,30000,0.5,alignment,0);
			List<PatchLocation> actual=calc.buildPatches(section);
			double[] along=PatchIntervalPlacement.centers(surface.getAveLength(),2,alignment);
			double[] down=PatchIntervalPlacement.centers(surface.getAveWidth(),2,alignment);
			assertEquals(along.length*down.length,actual.size());
			int index=0;
			for (double x : along) for (double y : down) {
				Location expected=surface.getInterpolatedLocation(x,y);
				assertEquals(0,org.opensha.commons.geo.LocationUtils.linearDistanceFast(expected,actual.get(index++).center),1e-7);
			}
		}
	}

	@Test public void stirlingRejectsOtherSurfacesForBothAlignments() {
		FaultSection section=section(surface(6,new double[] {4,4,4},new double[] {45,45},5));
		for (PatchAlignment alignment : PatchAlignment.values()) {
			try {
				new SubSectStiffnessCalculator(List.of(section),2,30000,30000,0.5,alignment,0,PatchGenerationStrategy.STIRLING).buildPatches(section);
				fail("Expected STIRLING type check");
			} catch (IllegalStateException expected) {
				assertTrue(expected.getMessage().contains("Use GENERIC"));
			}
		}
	}

	@Test public void configurationRoundTripAndReuse() throws Exception {
		List<FaultSection> sections=List.of(section(surface(7,new double[] {5,5,5},new double[] {45,45},5)));
		java.lang.reflect.Method method=org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration.class
				.getDeclaredMethod("buildGson",List.class);
		method.setAccessible(true);
		com.google.gson.Gson gson=(com.google.gson.Gson)method.invoke(null,sections);
		SubSectStiffnessCalculator center=new SubSectStiffnessCalculator(sections,2,30000,30000,0.5,
				PatchAlignment.CENTER,1,PatchGenerationStrategy.GENERIC);
		SubSectStiffnessCalculator fill=new SubSectStiffnessCalculator(sections,2,30000,30000,0.5,
				PatchAlignment.FILL_OVERLAP,1,PatchGenerationStrategy.GENERIC);
		SubSectStiffnessCalculator a=gson.fromJson(gson.toJson(center),SubSectStiffnessCalculator.class);
		SubSectStiffnessCalculator b=gson.fromJson(gson.toJson(fill),SubSectStiffnessCalculator.class);
		assertEquals(PatchGenerationStrategy.GENERIC,a.getPatchGenerationStrategy());
		assertEquals(PatchAlignment.CENTER,a.getPatchAlignment());
		assertEquals(PatchAlignment.FILL_OVERLAP,b.getPatchAlignment());
		assertNotSame(a,b);
		com.google.gson.JsonObject oldObject=gson.toJsonTree(fill).getAsJsonObject();
		oldObject.remove("patchGenerationStrategy");
		String oldJson=oldObject.toString();
		SubSectStiffnessCalculator old=gson.fromJson(oldJson,SubSectStiffnessCalculator.class);
		assertEquals(PatchGenerationStrategy.AUTO,old.getPatchGenerationStrategy());
		assertNotSame(b,old);
		assertEquals(PatchGenerationStrategy.AUTO,gson.fromJson(gson.toJson(old),SubSectStiffnessCalculator.class).getPatchGenerationStrategy());
		oldObject.add("patchGenerationStrategy",com.google.gson.JsonNull.INSTANCE);
		assertEquals(PatchGenerationStrategy.AUTO,gson.fromJson(oldObject,SubSectStiffnessCalculator.class).getPatchGenerationStrategy());
		oldObject.addProperty("patchGenerationStrategy","STIRLING");
		assertEquals(PatchGenerationStrategy.STIRLING,gson.fromJson(oldObject,SubSectStiffnessCalculator.class).getPatchGenerationStrategy());
	}

	@Test public void alignmentInvalidatesSelfStiffness() {
		EvenlyGriddedSurface surface=surface(7,new double[] {5,5,5},new double[] {45,45},5);
		SubSectStiffnessCalculator calc=calculator(surface);
		calc.setPatchAlignment(PatchAlignment.CENTER);
		calc.calcStiffnessDistribution(0,0);
		calc.setPatchAlignment(PatchAlignment.FILL_OVERLAP);
		double[][] actual=calc.calcStiffnessDistribution(0,0).get(SubSectStiffnessCalculator.StiffnessType.CFF);
		double[][] expected=calculator(surface).calcStiffnessDistribution(0,0).get(SubSectStiffnessCalculator.StiffnessType.CFF);
		assertEquals(expected.length,actual.length);
		for (int i=0; i<actual.length; i++) assertArrayEquals(expected[i],actual[i],1e-10);
	}

	@Test public void interpolationIncludesAlongRowDepthAndFourthCorner() {
		EvenlyGriddedSurface base=surface(6,new double[] {4,4},new double[] {45},5);
		EvenlyGriddedSurface warped=(EvenlyGriddedSurface)Proxy.newProxyInstance(EvenlyGriddedSurface.class.getClassLoader(),
				new Class<?>[] {EvenlyGriddedSurface.class},(proxy,method,args)-> {
					if (method.getName().equals("getLocation")) {
						int r=(int)args[0],c=(int)args[1];
						Location p=base.getLocation(r,c);
						return new Location(p.getLatitude(),p.getLongitude(),p.getDepth()+c+2*r*c);
					}
					return method.invoke(base,args);
				});
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(warped,ZONE,LETTER);
		double expected=0;
		for (int r=0; r<2; r++) for (int c=0; c<2; c++) expected-=warped.getLocation(r,c).getDepth()*0.25;
		assertEquals(expected,geometry.point(0.5,0.5).getZ(),1e-12);
		assertTrue(Double.isFinite(geometry.interpolate(0.5,0.5).strike));
	}

	@Test public void sameGeometryDifferentGridResolution() {
		EvenlyGriddedSurface coarse=surface(12,new double[] {8,8,8},new double[] {45,45},5);
		EvenlyGriddedSurface fine=surface(12,new double[] {8,8,8,8,8},new double[] {45,45,45,45},5);
		GenericSubSectPatchBuilder builder=new GenericSubSectPatchBuilder(2,ZONE,LETTER);
		for (PatchAlignment alignment : PatchAlignment.values()) {
			List<PatchLocation> a=builder.buildPatches(coarse,90,alignment);
			List<PatchLocation> b=builder.buildPatches(fine,90,alignment);
			assertEquals(24,a.size());
			assertEquals(a.size(),b.size());
			for (int i=0; i<a.size(); i++)
				assertEquals(0,new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(a.get(i).patch.center)
						.distance(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(b.get(i).patch.center)),0.05);
		}
	}

	@Test public void farFieldCoulombConvergesWithPatchSpacing() {
		EvenlyGriddedSurface source=surface(12,new double[] {8,8,8},new double[] {80,60,40,20},5);
		EvenlyGriddedSurface receiver=(EvenlyGriddedSurface)Proxy.newProxyInstance(EvenlyGriddedSurface.class.getClassLoader(),
				new Class<?>[] {EvenlyGriddedSurface.class},(proxy,method,args)-> {
					if (method.getName().equals("getLocation")) {
						Location p=source.getLocation((int)args[0],(int)args[1]);
						return new Location(p.getLatitude()+0.3,p.getLongitude(),p.getDepth());
					}
					return method.invoke(source,args);
				});
		double[] values=new double[2];
		for (int i=0; i<2; i++) {
			GenericSubSectPatchBuilder builder=new GenericSubSectPatchBuilder(i == 0 ? 2 : 1,ZONE,LETTER);
			List<PatchLocation> sources=builder.buildPatches(source,90,PatchAlignment.FILL_OVERLAP);
			List<PatchLocation> receivers=builder.buildPatches(receiver,90,PatchAlignment.FILL_OVERLAP);
			for (PatchLocation r : receivers) for (PatchLocation s : sources) {
				double[] stress=StiffnessCalc.calcStiffness(30000,30000,s.patch,r.patch);
				assertNotNull(stress);
				values[i]+=StiffnessCalc.calcCoulombStress(stress[1],stress[0],0.5)/receivers.size();
			}
			assertTrue(Double.isFinite(values[i]));
		}
		assertEquals(values[1],values[0],Math.abs(values[1])*0.05);
	}

	@Test public void upperOrLowerTraceCanBeTiny() {
		for (boolean tinyUpper : new boolean[] {true,false}) {
			FaultTrace upper=new FaultTrace("upper"),lower=new FaultTrace("lower");
			double upperLength=tinyUpper ? 0.1 : 12, lowerLength=tinyUpper ? 12 : 0.1;
			upper.add(location(0,-upperLength*0.5,5)); upper.add(location(0,upperLength*0.5,5));
			lower.add(location(4,-lowerLength*0.5,9)); lower.add(location(4,lowerLength*0.5,9));
			ApproxEvenlyGriddedSurface surface=new ApproxEvenlyGriddedSurface(upper,lower,0.2);
			for (PatchAlignment alignment : PatchAlignment.values())
				assertFalse(new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(surface,90,alignment).isEmpty());
		}
	}

	@Test public void singleRowSurfaceRejectedClearly() {
		try {
			new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(
					surface(6,new double[] {4,4},new double[0],5),90,PatchAlignment.FILL_OVERLAP);
			fail("Expected invalid surface");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("two rows and columns"));
		}
	}

	@Test public void stirlingFirstColumnStrikeIsDefined() {
		org.opensha.refFaultParamDb.vo.FaultSectionPrefData section=new org.opensha.refFaultParamDb.vo.FaultSectionPrefData();
		section.setSectionId(0);
		section.setSectionName("short Stirling fixture");
		FaultTrace trace=new FaultTrace("upper");
		trace.add(location(0,0,5)); trace.add(location(0,0.1,5));
		section.setFaultTrace(trace);
		section.setAveDip(45);
		section.setAveUpperDepth(5);
		section.setAveLowerDepth(9);
		section.setAveRake(90);
		for (PatchAlignment alignment : PatchAlignment.values()) {
			List<PatchLocation> patches=new SubSectStiffnessCalculator(List.of(section),2,30000,30000,0.5,alignment,0)
					.buildPatches(section);
			assertFalse(patches.isEmpty());
			for (PatchLocation patch : patches) assertTrue(Double.isFinite(patch.patch.strike));
		}
	}

	@Test public void planarFramesPreserveDistancesAndInverse() {
		for (EvenlyGriddedSurface surface : List.of(skewedSurface(),
				surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5),
				surface(20,new double[] {10,10,2,2,10,10},new double[] {60,60,60,60},5))) {
			GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(surface,ZONE,LETTER);
			SurfaceReferenceFrame frame=new SurfaceReferenceFrame(geometry);
			assertEquals(1,frame.minimumEdgeRatio,1e-7);
			assertEquals(1,frame.minimumAreaRatio,1e-7);
			assertEquals(0,frame.rmsPlaneDistance,1e-5);
			for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++) {
				var a=geometry.point(r+0.3,c+0.4);
				var b=geometry.point(r+0.7,c+0.8);
				double[] xy=frame.project(a), other=frame.project(b);
				assertEquals(a.distance(b),Math.hypot(xy[0]-other[0],xy[1]-other[1]),1e-7);
				assertEquals(0,a.distance(frame.point(xy[0],xy[1])),1e-7);
			}
		}
	}

	@Test public void cutoutDoesNotMoveTheUpperBoundary() {
		EvenlyGriddedSurface surface=surface(20,new double[] {10,10,2,2,10,10},new double[] {60,60,60,60},5);
		SurfaceReferenceFrame frame=new SurfaceReferenceFrame(new GriddedSurfaceGeometry(surface,ZONE,LETTER));
		for (int c=0; c<surface.getNumCols(); c++) assertEquals(0,frame.nodeY[0][c],2e-5);
		assertEquals(2,frame.nodeY[4][2],2e-5);
		assertEquals(10,frame.nodeY[4][0],2e-5);
	}

	@Test public void skewedSidesHaveNeutralBoundaryCounts() {
		EvenlyGriddedSurface surface=skewedSurface();
		GenericSubSectPatchBuilder.Layout layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER)
				.buildLayout(surface,90,PatchAlignment.FILL_OVERLAP);
		assertTrue(Double.isFinite(layout.maxBoundaryCorrection));
		java.util.Set<String> cells=new java.util.HashSet<>();
		for (int i=0; i<layout.gridIndices.size(); i++) {
			int[] index=layout.gridIndices.get(i);
			assertTrue(cells.add(index[0]+","+index[1]));
			double[] xy=layout.referenceCenters.get(i);
			assertTrue(layout.frame.contains(xy[0],xy[1]));
		}
		assertEquals(24,layout.patches.size());
		for (int r=0; r<4; r++) {
			final int row=r;
			assertEquals(6,layout.gridIndices.stream().filter(i -> i[0] == row).count());
		}
	}

	@Test public void fillPrefersOverlapAndAvoidsInteriorGaps() {
		for (EvenlyGriddedSurface surface : List.of(skewedSurface(),
				surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5),
				surface(20,new double[] {10,10,2,2,10,10},new double[] {60,60,60,60},5))) {
			GenericSubSectPatchBuilder.Layout layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER)
					.buildLayout(surface,90,PatchAlignment.FILL_OVERLAP);
			ProjectedPatchCoverage coverage=new ProjectedPatchCoverage(layout,2);
			assertEquals(0,coverage.interiorUncoveredArea,1e-10);
			assertTrue(coverage.uncoveredArea/coverage.surfaceArea < 0.08);
			assertEquals(0,layout.maxBoundaryCorrection,1e-6);
			// Persistent identities and equal spacing along each fitted axis.
			for (int axis=0; axis<2; axis++) {
				java.util.Map<Integer,java.util.List<Integer>> lines=new java.util.TreeMap<>();
				for (int i=0; i<layout.gridIndices.size(); i++)
					lines.computeIfAbsent(layout.gridIndices.get(i)[axis],k -> new java.util.ArrayList<>()).add(i);
				for (var line : lines.values()) {
					Double previousStep=null;
					for (int j=1; j<line.size(); j++) {
						int a=line.get(j-1),b=line.get(j);
						if (layout.gridIndices.get(b)[1-axis]-layout.gridIndices.get(a)[1-axis] != 1) { previousStep=null; continue; }
						double step=layout.referenceCenters.get(b)[axis]-layout.referenceCenters.get(a)[axis];
						assertTrue(step > 0 && step <= 2+1e-5);
						if (previousStep != null) assertEquals(previousStep,step,1e-5);
						previousStep=step;
					}
				}
			}
		}
	}

	@Test public void listricCreaseUsesBothOneSidedNormals() {
		EvenlyGriddedSurface surface=surface(12,new double[] {8,8,8,8,8},new double[] {80,60,40,20},5);
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(surface,ZONE,LETTER);
		for (double col : new double[] {0.3,1,1.7,2.9,3.8}) {
			for (double offset : new double[] {-1e-6,0,1e-6}) {
				var sample=geometry.interpolate(2+offset,col);
				assertEquals(50,sample.dip,0.001);
				assertEquals(0,geometry.point(2+offset,col).distance(sample.center),1e-12);
			}
			assertEquals(60,geometry.interpolate(1.99,col).dip,0.001);
			assertEquals(40,geometry.interpolate(2.01,col).dip,0.001);
		}
		var layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildLayout(surface,90,PatchAlignment.CENTER);
		for (int i=0; i<layout.patches.size(); i++) if (layout.gridIndices.get(i)[0] == 1)
			assertEquals(50,Math.toDegrees(layout.patches.get(i).patch.dip),0.001);
	}

	@Test public void planeFitUsesAreaRatherThanNodeDensity() {
		EvenlyGriddedSurface coarse=surface(12,new double[] {8,8,8},new double[] {80,60,40,20},5);
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(coarse,ZONE,LETTER);
		java.util.List<Location[]> rows=new java.util.ArrayList<>();
		int[] subdivisions={1,7,2,3};
		for (int r=0; r<4; r++) for (int i=0; i<subdivisions[r]; i++) {
			Location[] row=new Location[3];
			for (int c=0; c<3; c++) row[c]=geometry.location(geometry.point(r+(double)i/subdivisions[r],c));
			rows.add(row);
		}
		Location[] last=new Location[3];
		for (int c=0; c<3; c++) last[c]=coarse.getLocation(4,c);
		rows.add(last);
		SurfaceReferenceFrame a=new SurfaceReferenceFrame(geometry);
		SurfaceReferenceFrame b=new SurfaceReferenceFrame(new GriddedSurfaceGeometry(nodes(rows.toArray(Location[][]::new)),ZONE,LETTER));
		for (int r=0; r<5; r++) {
			double[] x=a.project(geometry.point(r,1)),y=b.project(geometry.point(r,1));
			assertArrayEquals(x,y,0.0001);
		}
	}

	@Test public void ambiguousProjectionRejected() {
		Location[][] nodes=new Location[3][9];
		for (int r=0; r<3; r++) for (int c=0; c<9; c++) {
			double angle=1.5*Math.PI*c/8;
			nodes[r][c]=location(5*Math.cos(angle),5*Math.sin(angle),5+2*r);
		}
		try {
			new SurfaceReferenceFrame(new GriddedSurfaceGeometry(nodes(nodes),ZONE,LETTER));
			fail("Expected folded projection to be rejected");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("projection"));
		}
	}

	@Test public void physicalCornersRespectOriginalDepthExtremes() {
		for (EvenlyGriddedSurface surface : List.of(skewedSurface(),
				surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5),
				surface(12,new double[] {8,8,8},new double[] {80,60,40,20},5))) {
			double shallow=Double.POSITIVE_INFINITY,deep=Double.NEGATIVE_INFINITY;
			for (int r=0; r<surface.getNumRows(); r++) for (int c=0; c<surface.getNumCols(); c++) {
				double depth=surface.getLocation(r,c).getDepth();
				shallow=Math.min(shallow,depth); deep=Math.max(deep,depth);
			}
			for (PatchAlignment alignment : PatchAlignment.values()) {
				var patches=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(surface,90,alignment);
				for (PatchLocation patch : patches) for (double[] corner : patch.patch.corner) {
					assertTrue(-corner[2] >= shallow*1000-1e-5);
					assertTrue(-corner[2] <= deep*1000+1e-5);
				}
			}
		}
	}

	@Test public void exactAndUndersizedDepthFits() {
		EvenlyGriddedSurface exact=surface(6,new double[] {2,2,2},new double[] {90,90},5);
		assertFalse(new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(exact,90,PatchAlignment.FILL_OVERLAP).isEmpty());
		for (double top : new double[] {0,5}) {
			EvenlyGriddedSurface undersized=surface(6,new double[] {1,1,1},new double[] {90,90},top);
			for (PatchAlignment alignment : PatchAlignment.values()) {
				var patches=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildPatches(undersized,90,alignment);
				assertFalse(patches.isEmpty());
				for (PatchLocation patch : patches) {
					assertEquals(4e6,patch.patch.area,0);
					assertEquals(top == 0 ? 1 : top+0.5,patch.center.getDepth(),1e-8);
					assertEquals(patch.center.getDepth(),-patch.patch.center[2]/1000,1e-12);
					for (double[] corner : patch.patch.corner) assertTrue(corner[2] <= 0);
				}
			}
		}
	}

	@Test public void adjacentSkewedSurfacesHaveTouchingPatchEdges() {
		EvenlyGriddedSurface first=skewedSurface();
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(first,ZONE,LETTER);
		Location[][] shifted=new Location[first.getNumRows()][first.getNumCols()];
		for (int r=0; r<shifted.length; r++) for (int c=0; c<shifted[0].length; c++)
			shifted[r][c]=geometry.location(geometry.point(r,c).add(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(0,12,0)));
		GenericSubSectPatchBuilder builder=new GenericSubSectPatchBuilder(2,ZONE,LETTER);
		List<PatchLocation> a=builder.buildPatches(first,90,PatchAlignment.FILL_OVERLAP);
		List<PatchLocation> b=builder.buildPatches(nodes(shifted),90,PatchAlignment.FILL_OVERLAP);
		assertEquals(24,a.size()); assertEquals(24,b.size());
		for (int row=0; row<4; row++) {
			var left=a.get(row*6+5).patch;
			var right=b.get(row*6).patch;
			assertEquals(2000,new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(left.center)
					.distance(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(right.center)),0.05);
			assertEquals(0,new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(left.corner[1])
					.distance(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(right.corner[0])),0.05);
			assertEquals(0,new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(left.corner[2])
					.distance(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(right.corner[3])),0.05);
		}
	}

	@Test public void disconnectedIntervalsStaySeparateEvenWithinOneGridSpacing() {
		var surface=surface(4,new double[] {8,8,6,8,8},new double[] {45,45,45,45},5);
		var layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildLayout(surface,90,PatchAlignment.FILL_OVERLAP);
		java.util.List<Integer> bottom=new java.util.ArrayList<>();
		for (int i=0; i<layout.gridIndices.size(); i++) if (layout.gridIndices.get(i)[0] == 3) bottom.add(i);
		assertEquals(2,bottom.size());
		assertTrue(layout.referenceCenters.get(bottom.get(0))[0] < 1.5);
		assertTrue(layout.referenceCenters.get(bottom.get(1))[0] > 2.5);
		assertNotEquals(layout.gridIndices.get(bottom.get(0))[2],layout.gridIndices.get(bottom.get(1))[2]);
	}

	@Test public void physicalCornersRespectGlobalAlongStrikeExtremes() {
		Location[][] bent=new Location[5][7];
		for (int r=0; r<5; r++) for (int c=0; c<7; c++) {
			double down=2d*r;
			bent[r][c]=location(2*Math.sin(Math.PI*c/6)+down*Math.cos(Math.PI/4),2d*c,5+down*Math.sin(Math.PI/4));
		}
		for (var surface : List.of(skewedSurface(),nodes(bent),
				surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5),
				surface(12,new double[] {8,6,4,2,0},new double[] {45,45,45,45},5))) {
			for (PatchAlignment alignment : PatchAlignment.values()) {
				var layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildLayout(surface,90,alignment);
				for (PatchLocation patch : layout.patches) for (double[] corner : patch.patch.corner) {
					double x=layout.frame.project(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(corner).scalarMultiply(0.001))[0];
					assertTrue(x >= layout.frame.minX-1e-8);
					assertTrue(x <= layout.frame.maxX+1e-8);
				}
			}
		}
	}

	@Test public void triangleSingletonHonorsStraightOuterEdge() {
		for (boolean reverse : new boolean[] {false,true}) {
			double[] widths=reverse ? new double[] {8,6,4,2,0} : new double[] {0,2,4,6,8};
			var layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildLayout(
					surface(12,widths,new double[] {45,45,45,45},5),90,PatchAlignment.FILL_OVERLAP);
			java.util.List<Integer> bottom=new java.util.ArrayList<>();
			for (int i=0; i<layout.gridIndices.size(); i++) if (layout.gridIndices.get(i)[0] == 3) bottom.add(i);
			assertEquals(1,bottom.size());
			int index=bottom.get(0);
			assertEquals(reverse ? 1 : 11,layout.referenceCenters.get(index)[0],1e-5);
			double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
			for (double[] corner : layout.patches.get(index).patch.corner) {
				double x=layout.frame.project(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(corner).scalarMultiply(0.001))[0];
				min=Math.min(min,x); max=Math.max(max,x);
			}
			assertEquals(reverse ? layout.frame.minX : layout.frame.maxX,reverse ? min : max,1e-5);
		}
	}

	@Test public void exactAndUndersizedAlongStrikeFits() {
		for (double length : new double[] {0.5,2}) for (PatchAlignment alignment : PatchAlignment.values()) {
			var layout=new GenericSubSectPatchBuilder(2,ZONE,LETTER).buildLayout(
					surface(length,new double[] {1,1,1},new double[] {90,90},0),90,alignment);
			assertFalse(layout.patches.isEmpty());
			for (PatchLocation patch : layout.patches) {
				assertEquals(4e6,patch.patch.area,0);
				double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
				for (double[] corner : patch.patch.corner) {
					assertTrue(corner[2] <= 0);
					double x=layout.frame.project(new org.apache.commons.math3.geometry.euclidean.threed.Vector3D(corner).scalarMultiply(0.001))[0];
					min=Math.min(min,x); max=Math.max(max,x);
				}
				if (length == 2) {
					assertEquals(layout.frame.minX,min,1e-8);
					assertEquals(layout.frame.maxX,max,1e-8);
				} else {
					assertTrue(min < layout.frame.minX);
					assertTrue(max > layout.frame.maxX);
				}
			}
		}
	}

	static SubSectStiffnessCalculator calculator(EvenlyGriddedSurface surface) {
		return new SubSectStiffnessCalculator(List.of(section(surface)),2,30000,30000,0.5,
				PatchAlignment.FILL_OVERLAP,1,PatchGenerationStrategy.GENERIC);
	}
}
