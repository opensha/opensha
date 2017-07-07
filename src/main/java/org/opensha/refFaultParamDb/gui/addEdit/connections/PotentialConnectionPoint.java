package org.opensha.refFaultParamDb.gui.addEdit.connections;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionData;
import org.opensha.sha.faultSurface.FaultTrace;

public class PotentialConnectionPoint implements Comparable<PotentialConnectionPoint> {
		private FaultSectionData fs1;
		private FaultSectionData fs2;
		
		private double[][] distances = new double[2][2];
		
		public PotentialConnectionPoint(FaultSectionData fs1, FaultSectionData fs2) {
			this.fs1 = fs1;
			this.fs2 = fs2;
		}
		
		private PotentialConnectionPoint(FaultSectionData fs1, FaultSectionData fs2, double[][] distances) {
			this.fs1 = fs1;
			this.fs2 = fs2;
			this.distances = distances;
		}
		
		public void compute() {
			FaultTrace trace1 = fs1.getFaultTrace();
			Location first1 = trace1.get(0);
			Location last1 = trace1.get(trace1.size()-1);
			
			FaultTrace trace2 = fs2.getFaultTrace();
			Location first2 = trace2.get(0);
			Location last2 = trace2.get(trace2.size()-1);
			
			distances[0][0] = LocationUtils.horzDistanceFast(first1, first2);
			distances[1][0] = LocationUtils.horzDistanceFast(last1, first2);
			distances[0][1] = LocationUtils.horzDistanceFast(first1, last2);
			distances[1][1] = LocationUtils.horzDistanceFast(last1, last2);
		}
		
		public double getMinDist() {
			double min = Double.MAX_VALUE;
			for (double[] dist1 : distances) {
				for (double dist : dist1) {
					if (dist < min)
						min = dist;
				}
			}
			return min;
		}
		
		public double getDistance(int i, int j) {
			return distances[i][j];
		}
		
		public PotentialConnectionPoint getReversal() {
			double[][] rev = new double[2][2];
			
			rev[0][0] = distances[0][0];
			rev[0][1] = distances[1][0];
			rev[1][0] = distances[0][1];
			rev[1][1] = distances[1][1];
			
			return new PotentialConnectionPoint(fs2, fs1, rev);
		}
		
		public FaultSectionData getFSD1() {
			return fs1;
		}
		
		public FaultSectionData getFSD2() {
			return fs2;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fs1 == null) ? 0 : fs1.hashCode());
			result = prime * result + ((fs2 == null) ? 0 : fs2.hashCode());
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
			PotentialConnectionPoint other = (PotentialConnectionPoint) obj;
			if (fs1 == null) {
				if (other.fs1 != null)
					return false;
			} else if (!fs1.equals(other.fs1))
				return false;
			if (fs2 == null) {
				if (other.fs2 != null)
					return false;
			} else if (!fs2.equals(other.fs2))
				return false;
			return true;
		}

		@Override
		public int compareTo(PotentialConnectionPoint o) {
			double myMin = getMinDist();
			double otherMin = o.getMinDist();
			return Double.compare(myMin, otherMin);
		}
	}