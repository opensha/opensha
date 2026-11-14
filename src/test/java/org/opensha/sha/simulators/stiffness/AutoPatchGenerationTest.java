package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.ApproxEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

public class AutoPatchGenerationTest {
	private static FaultSection stirlingSection() {
		FaultSectionPrefData section=new FaultSectionPrefData();
		section.setSectionId(0);
		section.setSectionName("Stirling fixture");
		FaultTrace trace=new FaultTrace("upper");
		trace.add(GenericSubSectPatchBuilderTest.location(0,0,5));
		trace.add(GenericSubSectPatchBuilderTest.location(0,7,5));
		section.setFaultTrace(trace);
		section.setAveDip(45); section.setAveRake(90);
		section.setAveUpperDepth(5); section.setAveLowerDepth(9);
		return section;
	}

	private static EvenlyGriddedSurface approximateSurface(double bottomEast) {
		FaultTrace upper=new FaultTrace("upper"), lower=new FaultTrace("lower");
		upper.add(GenericSubSectPatchBuilderTest.location(0,0,5));
		upper.add(GenericSubSectPatchBuilderTest.location(0,8,5));
		lower.add(GenericSubSectPatchBuilderTest.location(bottomEast,-2,9));
		lower.add(GenericSubSectPatchBuilderTest.location(bottomEast,10,9));
		return new ApproxEvenlyGriddedSurface(upper,lower,0.2);
	}

	private static FaultSection withID(FaultSection section,int id) {
		return (FaultSection)Proxy.newProxyInstance(FaultSection.class.getClassLoader(),new Class<?>[] {FaultSection.class},
				(proxy,method,args) -> method.getName().equals("getSectionId") ? id : method.invoke(section,args));
	}

	private static SubSectStiffnessCalculator explicit(List<? extends FaultSection> sections,PatchAlignment alignment,PatchGenerationStrategy strategy) {
		return new SubSectStiffnessCalculator(sections,2,30000,30000,0.5,alignment,0,strategy);
	}

	private static void assertSamePatches(List<PatchLocation> expected,List<PatchLocation> actual) {
		assertEquals(expected.size(),actual.size());
		for (int i=0; i<expected.size(); i++) {
			var a=expected.get(i).patch; var b=actual.get(i).patch;
			assertArrayEquals(a.center,b.center,0);
			assertEquals(a.strike,b.strike,0); assertEquals(a.dip,b.dip,0); assertEquals(a.rake,b.rake,0);
			for (int corner=0; corner<4; corner++) assertArrayEquals(a.corner[corner],b.corner[corner],0);
		}
	}

	@Test public void omittedAndNullStrategiesSelectBySurfaceForBothAlignments() {
		EvenlyGriddedSurface interfaceOnly=GenericSubSectPatchBuilderTest.surface(8,new double[] {6,6,6},new double[] {45,45},5);
		for (FaultSection section : List.of(stirlingSection(),GenericSubSectPatchBuilderTest.section(interfaceOnly),
				GenericSubSectPatchBuilderTest.section(approximateSurface(4)))) {
			boolean stirling=section instanceof FaultSectionPrefData;
			for (PatchAlignment alignment : PatchAlignment.values()) {
				List<FaultSection> sections=List.of(section);
				var auto=new SubSectStiffnessCalculator(sections,2,30000,30000,0.5,alignment,0);
				var nullStrategy=explicit(sections,alignment,null);
				assertEquals(PatchGenerationStrategy.AUTO,auto.getPatchGenerationStrategy());
				assertEquals(PatchGenerationStrategy.AUTO,nullStrategy.getPatchGenerationStrategy());
				var expected=explicit(sections,alignment,stirling ? PatchGenerationStrategy.STIRLING : PatchGenerationStrategy.GENERIC);
				assertSamePatches(expected.buildPatches(section),auto.buildPatches(section));
				assertSamePatches(expected.buildPatches(section),nullStrategy.buildPatches(section));
			}
		}
		assertEquals(PatchGenerationStrategy.AUTO,new SubSectStiffnessCalculator(List.of(stirlingSection()),2,30000,30000,0.5).getPatchGenerationStrategy());
	}

	@Test public void explicitGenericIsHonoredForStirlingSurfaces() {
		FaultSection section=stirlingSection();
		for (PatchAlignment alignment : PatchAlignment.values()) {
			var calc=explicit(List.of(section),alignment,PatchGenerationStrategy.GENERIC);
			var actual=calc.buildPatches(section);
			var source=(EvenlyGriddedSurface)section.getFaultSurface(0.2,false,false);
			// The fixture's common UTM frame is zone 11, letter S.
			var expected=new GenericSubSectPatchBuilder(2,11,'S').buildPatches(source,90,alignment);
			assertSamePatches(expected,actual);
		}
	}

	@Test public void mixedModelsAndAutoCacheKeysIncludeGeometry() {
		FaultSection stirling=stirlingSection();
		FaultSection approximate=withID(GenericSubSectPatchBuilderTest.section(approximateSurface(4)),1);
		List<FaultSection> sections=List.of(stirling,approximate);
		var auto=new SubSectStiffnessCalculator(sections,2,30000,30000,0.5);
		assertSamePatches(explicit(sections,PatchAlignment.FILL_OVERLAP,PatchGenerationStrategy.STIRLING).buildPatches(stirling),auto.getPatches(stirling));
		assertSamePatches(explicit(sections,PatchAlignment.FILL_OVERLAP,PatchGenerationStrategy.GENERIC).buildPatches(approximate),auto.getPatches(approximate));
		String key=auto.getAggregationCache(StiffnessType.CFF).getCacheFileName();
		assertTrue(key.contains("genAUTO")); assertTrue(key.contains("_geom"));
		FaultSection changed=withID(GenericSubSectPatchBuilderTest.section(approximateSurface(5)),1);
		var other=new SubSectStiffnessCalculator(List.of(stirling,changed),2,30000,30000,0.5);
		assertNotEquals(key,other.getAggregationCache(StiffnessType.CFF).getCacheFileName());
		var explicitGeneric=explicit(sections,PatchAlignment.FILL_OVERLAP,PatchGenerationStrategy.GENERIC);
		assertNotEquals(key,explicitGeneric.getAggregationCache(StiffnessType.CFF).getCacheFileName());
	}

	@Test public void selectionAndConstructionUseOneSurfaceRequest() {
		FaultSection delegate=stirlingSection();
		RuptureSurface first=delegate.getFaultSurface(0.2,false,true);
		AtomicInteger requests=new AtomicInteger();
		FaultSection section=(FaultSection)Proxy.newProxyInstance(FaultSection.class.getClassLoader(),new Class<?>[] {FaultSection.class},(proxy,method,args)-> {
			if (method.getName().equals("getFaultSurface")) {
				assertEquals(0.2,(double)args[0],0); assertEquals(false,args[1]); assertEquals(true,args[2]);
				return requests.incrementAndGet() == 1 ? first : approximateSurface(4);
			}
			return method.invoke(delegate,args);
		});
		var calc=new SubSectStiffnessCalculator(List.of(section),2,30000,30000,0.5) {
			@Override public boolean isApplyAseisReduction() { return true; }
		};
		assertFalse(calc.buildPatches(section).isEmpty());
		assertEquals(1,requests.get());
	}

	@Test public void autoRejectsNonGriddedSurfacesClearly() {
		FaultSection delegate=stirlingSection();
		RuptureSurface nongridded=(RuptureSurface)Proxy.newProxyInstance(RuptureSurface.class.getClassLoader(),new Class<?>[] {RuptureSurface.class},
				(proxy,method,args)-> { throw new UnsupportedOperationException(method.getName()); });
		FaultSection section=(FaultSection)Proxy.newProxyInstance(FaultSection.class.getClassLoader(),new Class<?>[] {FaultSection.class},
				(proxy,method,args)-> method.getName().equals("getFaultSurface") ? nongridded : method.invoke(delegate,args));
		try {
			new SubSectStiffnessCalculator(List.of(section),2,30000,30000,0.5).buildPatches(section);
			fail("Expected unsupported surface diagnostic");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getMessage().contains("EvenlyGriddedSurface"));
		}
	}
}
