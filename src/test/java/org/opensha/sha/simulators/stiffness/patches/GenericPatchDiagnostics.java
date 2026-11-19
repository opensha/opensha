package org.opensha.sha.simulators.stiffness.patches;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.simulators.stiffness.StiffnessCalc;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/** Optional standalone CSV exporter for inspecting the synthetic layouts. */
public class GenericPatchDiagnostics {
	public static void main(String[] args) throws IOException {
		Path directory=Path.of(args[0]);
		Files.createDirectories(directory);
		java.util.Map<String,EvenlyGriddedSurface> cases=new java.util.LinkedHashMap<>();
		String selection=args.length > 1 ? args[1] : "original";
		if (!selection.equals("pathological")) {
			cases.put("rectangle",GenericSubSectPatchBuilderTest.surface(12,new double[] {8,8,8,8,8},new double[] {45,45,45,45},5));
			cases.put("triangle",GenericSubSectPatchBuilderTest.surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5));
			cases.put("undulating",GenericSubSectPatchBuilderTest.surface(20,new double[] {10,10,2,2,10,10},new double[] {60,60,60,60},5));
			cases.put("skewed",GenericSubSectPatchBuilderTest.skewedSurface());
			cases.put("listric",GenericSubSectPatchBuilderTest.surface(12,new double[] {8,8,8,8,8},new double[] {80,60,40,20},5));
		}
		if (selection.equals("pathological") || selection.equals("all")) cases.putAll(PathologicalSurfaceFixtures.examples());
		for (var entry : cases.entrySet()) {
			write(directory,entry.getKey(),entry.getValue());
			writeRefinement(directory,entry.getKey(),entry.getValue());
		}
	}

	/** Same fixed remote receivers at all source spacings; no self-stiffness cap. */
	private static void writeRefinement(Path directory,String name,EvenlyGriddedSurface surface) throws IOException {
		try (PrintWriter out=new PrintWriter(Files.newBufferedWriter(directory.resolve(name+"_refinement.csv")))) {
			out.println("spacing,patches,source_area,receiver_0_cff,receiver_1_cff,receiver_2_cff");
			for (double spacing : new double[] {2,1,0.5}) {
				var patches=new GenericSubSectPatchBuilder(spacing,11,'S').buildPatches(surface,90,PatchAlignment.FILL_OVERLAP);
				double[] values=new double[3];
				for (int r=0; r<3; r++) {
					var receiver=new StiffnessCalc.Patch(new double[] {530000+10000*r,3800000-5000*r,-10000},2000,2000,
							new org.opensha.sha.earthquake.FocalMechanism(10,50,90));
					for (PatchLocation source : patches) {
						double[] stress=StiffnessCalc.calcStiffness(30000,30000,source.patch,receiver);
						values[r]+=StiffnessCalc.calcCoulombStress(stress[1],stress[0],0.5);
					}
				}
				out.printf(Locale.ROOT,"%.8f,%d,%.8f,%.12g,%.12g,%.12g%n",spacing,patches.size(),
						patches.size()*spacing*spacing,values[0],values[1],values[2]);
			}
		}
	}

	private static void write(Path directory,String name,EvenlyGriddedSurface surface) throws IOException {
		write(directory,name,surface,GenericSubSectPatchBuilder.ReferenceFrameType.PLANAR,32);
	}

	static void write(Path directory,String name,EvenlyGriddedSurface surface,
			GenericSubSectPatchBuilder.ReferenceFrameType type,int coverageSamples) throws IOException {
		write(directory,name,surface,new GenericSubSectPatchBuilder(2,11,'S',type),coverageSamples);
	}

	static void write(Path directory,String name,EvenlyGriddedSurface surface,
			GenericSubSectPatchBuilder builder,int coverageSamples) throws IOException {
		for (PatchAlignment alignment : PatchAlignment.values()) {
			String prefix=name+"_"+alignment.name().toLowerCase(Locale.ROOT);
			GenericSubSectPatchBuilder.Layout layout=builder.buildLayout(surface,90,alignment);
			GriddedSurfaceGeometry geometry=layout.frame.geometry();
			double area=0;
			for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++) area+=geometry.cellArea(r,c);
			try (PrintWriter out=new PrintWriter(Files.newBufferedWriter(directory.resolve(prefix+"_nodes.csv")))) {
				out.println("row,col,x,y,east,north,up");
				for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++) {
					Vector3D p=geometry.point(r,c);
					out.printf(Locale.ROOT,"%d,%d,%.8f,%.8f,%.8f,%.8f,%.8f%n",r,c,layout.frame.nodeX(r,c),
							layout.frame.nodeY(r,c),p.getX(),p.getY(),p.getZ());
				}
			}
			// Approximate represented area by assigning quadrature points to the
			// nearest physical center. This exposes density bias beyond total area.
			double[] represented=new double[layout.patches.size()];
			for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++) {
				double weight=geometry.cellArea(r,c)/256d;
				for (int a=0; a<16; a++) for (int b=0; b<16; b++) {
					Vector3D point=geometry.point(r+(a+0.5)/16,c+(b+0.5)/16).scalarMultiply(1000);
					int closest=0;
					double distance=Double.POSITIVE_INFINITY;
					for (int i=0; i<layout.patches.size(); i++) {
						double candidate=point.distance(new Vector3D(layout.patches.get(i).patch.center));
						if (candidate < distance) { distance=candidate; closest=i; }
					}
					represented[closest]+=weight;
				}
			}
			double minNeighbor=Double.POSITIVE_INFINITY;
			try (PrintWriter out=new PrintWriter(Files.newBufferedWriter(directory.resolve(prefix+"_patches.csv")))) {
				out.println("id,x,y,east,north,up,strike,dip,nearest,represented_area,row,col,segment,infill");
				for (int i=0; i<layout.patches.size(); i++) {
					PatchLocation p=layout.patches.get(i);
					double nearest=Double.POSITIVE_INFINITY;
					for (int j=0; j<layout.patches.size(); j++) if (j != i)
						nearest=Math.min(nearest,new Vector3D(p.patch.center).distance(new Vector3D(layout.patches.get(j).patch.center))/1000);
					minNeighbor=Math.min(minNeighbor,nearest);
					double[] xy=layout.referenceCenters.get(i), center=p.patch.center;
					out.printf(Locale.ROOT,"%d,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%d,%d,%d,%d%n",i,xy[0],xy[1],
							center[0]/1000,center[1]/1000,center[2]/1000,Math.toDegrees(p.patch.strike),Math.toDegrees(p.patch.dip),nearest,represented[i],layout.gridIndices.get(i)[0],layout.gridIndices.get(i)[1],layout.gridIndices.get(i)[2],layout.gridIndices.get(i)[0] < 0 ? 1 : 0);
				}
			}
			try (PrintWriter out=new PrintWriter(Files.newBufferedWriter(directory.resolve(prefix+"_corners.csv")))) {
				out.println("id,corner,east,north,up,x,y");
				for (int i=0; i<layout.patches.size(); i++) for (int c=0; c<4; c++) {
					double[] p=layout.patches.get(i).patch.corner[c];
					double[] xy=layout.frame.project(new Vector3D(p).scalarMultiply(0.001));
					out.printf(Locale.ROOT,"%d,%d,%.8f,%.8f,%.8f,%.8f,%.8f%n",i,c,p[0]/1000,p[1]/1000,p[2]/1000,xy[0],xy[1]);
				}
			}
			try (PrintWriter out=new PrintWriter(Files.newBufferedWriter(directory.resolve(prefix+"_footprints.csv")))) {
				out.println("id,point,x,y");
				for (int i=0; i<layout.patches.size(); i++) {
					double[][] boundary=layout.frame.projectPatchBoundary(layout.patches.get(i).patch);
					for (int j=0; j<boundary.length; j++)
						out.printf(Locale.ROOT,"%d,%d,%.8f,%.8f%n",i,j,boundary[j][0],boundary[j][1]);
				}
			}
			ProjectedPatchCoverage coverage=new ProjectedPatchCoverage(layout,2,coverageSamples);
			try (PrintWriter out=new PrintWriter(Files.newBufferedWriter(directory.resolve(prefix+"_metrics.csv")))) {
				out.println("patches,surface_area,patch_area_ratio,uncovered_fraction,interior_uncovered_fraction,max_gap_distance,min_edge_ratio,min_area_ratio,rms_plane_distance,max_boundary_correction,infill_patches,reference_frame,planar_fallback");
				out.printf(Locale.ROOT,"%d,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%d,%s,%s%n",layout.patches.size(),area,
						layout.patches.size()*4/area,coverage.uncoveredArea/area,coverage.interiorUncoveredArea/area,
						coverage.maximumGapDistance,layout.frame.minimumEdgeRatio(),layout.frame.minimumAreaRatio(),
						layout.frame.rmsPlaneDistance(),layout.maxBoundaryCorrection,layout.infillCount,
								layout.frame instanceof RibbonSurfaceReferenceFrame ? "RIBBON" : "PLANAR",layout.planarFallbackReason != null);
			}
			System.out.printf(Locale.ROOT,"%s: %d patches, surface %.3f km^2, patch/surface area %.3f, min center spacing %.3f km, max boundary correction %.6g km, min edge ratio %.3f, min area ratio %.3f, uncovered %.2f%% (interior %.2f%%), %d infill patches%n",
					prefix,layout.patches.size(),area,layout.patches.size()*4/area,minNeighbor,layout.maxBoundaryCorrection,layout.frame.minimumEdgeRatio(),layout.frame.minimumAreaRatio(),100*coverage.uncoveredArea/area,100*coverage.interiorUncoveredArea/area,layout.infillCount);
		}
	}
}
