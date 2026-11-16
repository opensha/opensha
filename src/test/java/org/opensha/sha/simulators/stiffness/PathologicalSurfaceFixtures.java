package org.opensha.sha.simulators.stiffness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;

/** Analytic shapes sampled independently of the production surface helpers. */
final class PathologicalSurfaceFixtures {
	@FunctionalInterface interface Shape {
		/** East, north, and positive-down depth, in km. */
		double[] point(double u,double v);
	}

	static EvenlyGriddedSurface sample(int rows,int cols,Shape shape) {
		Location[][] nodes=new Location[rows][cols];
		for (int r=0; r<rows; r++) for (int c=0; c<cols; c++) {
			double[] p=shape.point((double)c/(cols-1),(double)r/(rows-1));
			nodes[r][c]=GenericSubSectPatchBuilderTest.location(p[0],p[1],p[2]);
		}
		return GenericSubSectPatchBuilderTest.nodes(nodes);
	}

	static double[] twistedPoint(double u,double v) {
		double dip=Math.toRadians(38+22*u);
		return new double[] {1.2*Math.sin(2*Math.PI*u)+8.6*v*Math.cos(dip)+0.3*v*v,
				13.7*u+0.3*v*Math.sin(Math.PI*u),5+8.6*v*Math.sin(dip)+0.8*v*v};
	}

	static EvenlyGriddedSurface twisted() { return sample(11,17,PathologicalSurfaceFixtures::twistedPoint); }

	static EvenlyGriddedSurface unevenTraces() {
		return sample(9,25,(u,v) -> {
			double upper=1.1*Math.sin(2*Math.PI*u);
			double lower=6.7+Math.sin(4*Math.PI*u+0.4);
			double down=upper+(lower-upper)*v;
			return new double[] {down*Math.cos(Math.PI/4),14*u,5+down*Math.sin(Math.PI/4)};
		});
	}

	static EvenlyGriddedSurface narrowNecks() {
		double[] widths={8,8,1,1,8,8,0.4,0.4,6,6};
		Location[][] nodes=new Location[9][widths.length];
		for (int r=0; r<nodes.length; r++) for (int c=0; c<widths.length; c++) {
			double down=4+widths[c]*((double)r/(nodes.length-1)-0.5);
			nodes[r][c]=GenericSubSectPatchBuilderTest.location(down*Math.cos(Math.PI/4),2.4*c,5+down*Math.sin(Math.PI/4));
		}
		return GenericSubSectPatchBuilderTest.nodes(nodes);
	}

	static EvenlyGriddedSurface thinTongue() {
		// A long tongue only 0.4 km wide lies between the main grid's row centers.
		return sample(9,19,(u,v) -> {
			double width=u <= 0.4 ? 8 : u >= 0.6 ? 0.4 : 8+(0.4-8)*(u-0.4)/0.2;
			double down=4+width*(v-0.5);
			return new double[] {down*Math.cos(Math.PI/4),18*u,5+down*Math.sin(Math.PI/4)};
		});
	}

	static EvenlyGriddedSurface planar(double length,double width,double dip,double topDepth) {
		double radians=Math.toRadians(dip);
		return sample(5,7,(u,v) -> new double[] {width*v*Math.cos(radians),length*u,topDepth+width*v*Math.sin(radians)});
	}

	static Shape seededShape(long seed) {
		Random random=new Random(seed);
		double length=12.7+random.nextDouble(),width=7.3+random.nextDouble();
		double dip=Math.toRadians(25+40*random.nextDouble());
		double bend=0.15+0.45*random.nextDouble(),twist=0.3*(random.nextDouble()-0.5),shear=random.nextDouble();
		return (u,v) -> new double[] {bend*Math.sin(2*Math.PI*u)+width*v*Math.cos(dip)+twist*u*v,
				length*u+shear*v,5+width*v*Math.sin(dip)+0.25*v*v+0.1*u*v};
	}

	static Map<String,EvenlyGriddedSurface> examples() {
		Map<String,EvenlyGriddedSurface> examples=new LinkedHashMap<>();
		examples.put("twisted",twisted());
		examples.put("uneven_traces",unevenTraces());
		examples.put("narrow_necks",narrowNecks());
		examples.put("thin_tongue",thinTongue());
		examples.put("near_horizontal",planar(9.7,6.3,0.01,5));
		examples.put("near_vertical",planar(9.7,6.3,89.99,5));
		return examples;
	}
}
