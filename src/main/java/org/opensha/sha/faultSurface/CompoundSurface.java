package org.opensha.sha.faultSurface;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationUtils.LocationAverager;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet.IntListWrapper;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet.ShortListWrapper;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;

/**
 * Updated compound surface implementation with support for multiple subsections down-dip and decreased memory footprint.
 * <p>
 * The {@link Simple} implementation reproduces the original implementation exactly if the surface list isn't passed in, but
 * can have different/better ordering if a surface list is supplied. Distance metrics are identical.
 * <p>
 * The {@link DownDip} implementation requires a surface list with populated indexes along strike and down dip, as well
 * as parent section indexes. It only uses the uppermost row (within any parent) for DistanceX calculations.
 */
public abstract class CompoundSurface implements CacheEnabledSurface {
	
	/*
	 * These are populated by the top level init
	 */
	
	/**
	 * number of surfaces
	 */
	protected final int numSurfaces;
	
	/**
	 * Original list in order passed in; not final because of {@link #getMoved(LocationVector)}
	 */
	protected List<? extends RuptureSurface> surfaces;
	/**
	 * Areas of each surface (in original order)
	 */
	protected final double[] surfaceAreas;
	/**
	 * Total area
	 */
	protected final double totArea;
	
	/*
	 * These are lazy-init
	 */
	private double length = Double.NaN;
	private double width = Double.NaN;
	private double horzWidth = Double.NaN;
	private double topDepth = Double.NaN;
	private double bottomDepth = Double.NaN;
	
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	public static CompoundSurface get(List<? extends RuptureSurface> surfaces) {
		return new Simple(surfaces);
	}
	
	public static CompoundSurface get(List<? extends RuptureSurface> surfaces, List<? extends FaultSection> sections) {
		if (sections == null)
			return new Simple(surfaces);
		boolean anyDD = sections.stream().anyMatch(S->S.getSubSectionIndexDownDip()>0);
		if (anyDD)
			return new DownDip(surfaces, sections);
		return new Simple(surfaces);
	}

	protected CompoundSurface(List<? extends RuptureSurface> surfaces) {
		Preconditions.checkNotNull(surfaces, "Surfaces list is null");
		this.numSurfaces = surfaces.size();
		Preconditions.checkArgument(numSurfaces > 1, "Must supply at least 2 surfaces (have %s)", numSurfaces);
		this.surfaces = Collections.unmodifiableList(surfaces);
		surfaceAreas = new double[surfaces.size()];
		double totArea = 0d;
		for (int s=0; s<surfaceAreas.length; s++) {
			surfaceAreas[s] = surfaces.get(s).getArea();
			totArea += surfaceAreas[s];
		}
		this.totArea = totArea;
	}
	
	private CompoundSurface(List<? extends RuptureSurface> surfaces, double[] surfaceAreas, double totArea) {
		this.surfaces = surfaces;
		this.numSurfaces = surfaces.size();
		this.surfaceAreas = surfaceAreas;
		this.totArea = totArea;
	}
	
	/**
	 * Simple {@link CompoundSurface} implementation for ruptures without any sections down-dip. An optional
	 * {@link FaultSection} list can be supplied to help with grouping and ordering. If that surface list is omitted,
	 * the original CompoundSurface ordering implementation is retained.
	 */
	public static class Simple extends CompoundSurface {
		
		private final double avgDip;
		private final BitSet reversed;
		private final boolean wholeRupReversed;
		private final ContiguousIntList indexes;
		
		public Simple(List<? extends RuptureSurface> surfaces) {
			this(surfaces, null);
		}
		
		public Simple(List<? extends RuptureSurface> surfaces, List<? extends FaultSection> sects) {
			super(surfaces);

			// keep track of those we will need to reverse; don't do so just yet because we might have to flip the whole
			// surface at the end
			BitSet reversed = null;
			
			if (sects != null && !sects.isEmpty()) {
				Preconditions.checkState(sects.size() == surfaces.size(),
						"Have %s sects and %s surfaces", sects.size(), surfaces.size());
				// use surface indexing

				List<List<Integer>> groups = new ArrayList<>();
				List<Integer> curBundle = null;
				int prevParent = -1;
				int prevAlong = -1;
				Boolean curForward = null;
				for (int s=0; s<sects.size(); s++) {
					FaultSection sect = sects.get(s);
					int parent = sect.getParentSectionId();
					int along = sect.getSubSectionIndexAlong();
					if (parent < 0 || along < 0) {
						// no parent or along strike information
						groups = null;
						break;
					}
					if (prevParent == parent) {
						if (along == prevAlong+1) {
							if (curForward == null) {
								curForward = true;
							} else if (!curForward) {
								// not monotonic 
								groups = null;
								break;
							}
						} else if (along == prevAlong-1) {
							if (curForward == null) {
								curForward = false;
							} else if (curForward) {
								// not monotonic 
								groups = null;
								break;
							}
						} else {
							// not contiguous
							groups = null;
							break;
						}
					} else {
						curBundle = new ArrayList<>();
						groups.add(curBundle);
						curForward = null;
					}
					curBundle.add(s);
					prevParent = parent;
					prevAlong = along;
				}
				
				if (groups != null) {
					// we have valid section data to use
					
					// figure out order within groups
					Boolean[] groupReversals = new Boolean[groups.size()];
					for (int g=0; g<groups.size(); g++) {
						List<Integer> group = groups.get(g);
						if (group.size() > 1) {
							FaultSection sect1 = sects.get(group.get(0));
							FaultSection sect2 = sects.get(group.get(group.size()-1));
							groupReversals[g] = sect2.getSubSectionIndexAlong() < sect1.getSubSectionIndexAlong();
						} else {
							// we only have a single section per row, need to determine direction
							RuptureSurface surface = surfaces.get(group.get(0));
							if (g > 0) {
								// compare to previous group
								List<Integer> priorGroup = groups.get(g-1);
								if (groupReversals[g-1] != null) {
									RuptureSurface priorFirst = surfaces.get(priorGroup.get(0));
									RuptureSurface priorLast = surfaces.get(priorGroup.get(priorGroup.size()-1));
									Location priorStart, priorEnd;
									if (groupReversals[g-1]) {
										priorStart = priorFirst.getLastLocOnUpperEdge();
										priorEnd = priorLast.getFirstLocOnUpperEdge();
									} else {
										priorStart = priorFirst.getFirstLocOnUpperEdge();
										priorEnd = priorLast.getLastLocOnUpperEdge();
									}
									double priorStrike = LocationUtils.azimuth(priorStart, priorEnd);
									double strike = LocationUtils.azimuth(surface.getFirstLocOnUpperEdge(), surface.getLastLocOnUpperEdge());
									// this returns differences in the range [0, 180]
									double diff = FaultUtils.getAbsAngleDiff(priorStrike, strike);
									// if we're more than 90 degrees off, we're facing the opposite direction
									groupReversals[g] = diff > 90d;
								} else {
									// we don't know the direction of either
									Preconditions.checkState(priorGroup.size() == 1);
									RuptureSurface priorSurface = surfaces.get(priorGroup.get(0));
									double[] dist = new double[4];
									dist[0] = distForReversalCheck(priorSurface.getFirstLocOnUpperEdge(), surface.getFirstLocOnUpperEdge());
									dist[1] = distForReversalCheck(priorSurface.getFirstLocOnUpperEdge(), surface.getLastLocOnUpperEdge());
									dist[2] = distForReversalCheck(priorSurface.getLastLocOnUpperEdge(), surface.getFirstLocOnUpperEdge());
									dist[3] = distForReversalCheck(priorSurface.getLastLocOnUpperEdge(), surface.getLastLocOnUpperEdge());
									
									double min = dist[0];
									int minIndex = 0;
									for(int i=1; i<4;i++) {
										if(dist[i]<min) {
											minIndex = i;
											min = dist[i];
										}
									}
									
									if (minIndex==0) { // first_first
										groupReversals[g-1] = true;
										groupReversals[g] = false;
									} else if (minIndex==1) { // first_last
										groupReversals[g-1] = true;
										groupReversals[g] = true;
									} else if (minIndex==2) { // last_first
										groupReversals[g-1] = false;
										groupReversals[g] = false;
									} else { // minIndex==3 // last_last
										groupReversals[g-1] = false;
										groupReversals[g] = true;
									}
								}
							}
						}
						if (g > 0 && groupReversals[g-1] == null) {
							RuptureSurface priorSurface = surfaces.get(groups.get(g-1).get(0));
							double priorStrike = LocationUtils.azimuth(priorSurface.getFirstLocOnUpperEdge(), priorSurface.getLastLocOnUpperEdge());
							RuptureSurface myFirst = surfaces.get(group.get(0));
							RuptureSurface myLast = surfaces.get(group.get(group.size()-1));
							Location start, end;
							if (groupReversals[g]) {
								start = myFirst.getLastLocOnUpperEdge();
								end = myLast.getFirstLocOnUpperEdge();
							} else {
								start = myFirst.getFirstLocOnUpperEdge();
								end = myLast.getLastLocOnUpperEdge();
							}
							double strike = LocationUtils.azimuth(start, end);
							// this returns differences in the range [0, 180]
							double diff = FaultUtils.getAbsAngleDiff(priorStrike, strike);
							// if we're more than 90 degrees off, we're facing the opposite direction
							groupReversals[g-1] = diff > 90d;
						}
					}
					if (groups.size() == 1 && groupReversals[0] == null)
						// only one group, and only 1 column in that group
						groupReversals[0] = false;
					
					reversed = new BitSet(numSurfaces);
					for (int g=0; g<groups.size(); g++) {
						if (groupReversals[g]) {
							for (int s : groups.get(g))
								reversed.set(s);
						}
					}
				}
			}
			
			if (reversed == null) {
				// either no section data, or that section data was unusable
				reversed = new BitSet(numSurfaces);
				
				// naive approach, similar to original implementation but with bugfixes
				RuptureSurface surf1 = surfaces.get(0);
				RuptureSurface surf2 = surfaces.get(1);
				double[] dist = new double[4];
				dist[0] = distForReversalCheck(surf1.getFirstLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
				dist[1] = distForReversalCheck(surf1.getFirstLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
				dist[2] = distForReversalCheck(surf1.getLastLocOnUpperEdge(), surf2.getFirstLocOnUpperEdge());
				dist[3] = distForReversalCheck(surf1.getLastLocOnUpperEdge(), surf2.getLastLocOnUpperEdge());
				
				double min = dist[0];
				int minIndex = 0;
				for(int i=1; i<4;i++) {
					if(dist[i]<min) {
						minIndex = i;
						min = dist[i];
					}
				}
				
				if (minIndex==0) { // first_first
					reversed.set(0);
				} else if (minIndex==1) { // first_last
					reversed.set(0);
					reversed.set(1);
				} else if (minIndex==2) { // last_first
				} else { // minIndex==3 // last_last
					reversed.set(1);
				}
				
				// there was a bug in the prior implementation: it always compared against the original orientation of the prior
				// surface, not the potentially-reversed version; that's wrong, but may have never caused issues in practice.
				Location prevLast = reversed.get(1) ? surf2.getFirstLocOnUpperEdge() : surf2.getLastLocOnUpperEdge();
				double d1, d2;
				Location first, last;
				RuptureSurface surf;
				for (int s=2; s<numSurfaces; s++) {
					// uncomment this if you want to reproduce the old buggy behavior
//					prevLast = surfaces.get(s-1).getLastLocOnUpperEdge();
					surf = surfaces.get(s);
					first = surf.getFirstLocOnUpperEdge();
					last = surf.getLastLocOnUpperEdge();
					d1 = distForReversalCheck(prevLast, first);
					d2 = distForReversalCheck(prevLast, last);
					if (d2 < d1) {
						reversed.set(s);
						prevLast = first;
					} else {
						prevLast = last;
					}
				}
			}
			this.reversed = reversed;
			
			double avgDip = 0d;
			RuptureSurface surf;
			for (int s=0; s<numSurfaces; s++) {
				surf = surfaces.get(s);
				double dip = surf.getAveDip();
				if (reversed.get(s))
					avgDip += (180d-dip)*surfaceAreas[s];
				else
					avgDip += dip*surfaceAreas[s];
			}
			avgDip /= totArea;
			Preconditions.checkState(avgDip > 0 && avgDip < 180d, "Bad avgDip=%s", avgDip);
			wholeRupReversed = avgDip > 90d;
			
			if (wholeRupReversed) {
				// whole surface is reversed according to aki & richards, need to flip it
				avgDip = 180d-avgDip;
				// flip the direction of each surface
				reversed.flip(0, numSurfaces);
			}
			this.avgDip = avgDip;
			this.indexes = new ContiguousIntList();
		}

		private Simple(List<? extends RuptureSurface> surfaces, double[] surfaceAreas, double totArea,
				double avgDip, BitSet reversed,
				boolean wholeRupReversed, ContiguousIntList indexes) {
			super(surfaces, surfaceAreas, totArea);
			this.avgDip = avgDip;
			this.reversed = reversed;
			this.wholeRupReversed = wholeRupReversed;
			this.indexes = indexes;
		}

		@Override
		public double getAveDip() {
			return avgDip;
		}

		@Override
		public boolean hasSurfacesDownDip() {
			return false;
		}

		@Override
		public List<Integer> getUpperEdgeSurfaceIndexes() {
			return indexes;
		}

		@Override
		public List<Integer> getLowerEdgeSurfaceIndexes() {
			return indexes;
		}

		@Override
		public List<Integer> getRightEdgeSurfaceIndexes() {
			return List.of(wholeRupReversed ? 0 : surfaces.size()-1);
		}

		@Override
		public List<Integer> getLeftEdgeSurfaceIndexes() {
			return List.of(wholeRupReversed ? surfaces.size()-1 : 0);
		}

		@Override
		public boolean isSurfaceReversed(int index) {
			return reversed.get(index);
		}

		@Override
		public boolean isSurfaceOnUpperEdge(int index) {
			return true;
		}

		@Override
		public Simple copyShallow() {
			return new Simple(surfaces, surfaceAreas, totArea, avgDip, reversed, wholeRupReversed, indexes);
		}
		
		private class ContiguousIntList extends AbstractList<Integer> {

			@Override
			public int size() {
				return numSurfaces;
			}

			@Override
			public Integer get(int index) {
				return wholeRupReversed ? (numSurfaces-1) - index : index;
			}
			
		}
		
	}
	
	/**
	 * Implementation of {@link CompoundSurface} that supports multiple sections down-dip. For this implementation,
	 * the subsection list is required and all sections must have {@link FaultSection#getSubSectionIndexDownDip()},
	 * {@link FaultSection#getSubSectionIndexAlong()}, and {@link FaultSection#getParentSectionId()} populated.
	 */
	public static class DownDip extends CompoundSurface {

		private final BitSet reversed;
		private final BitSet tops;
		private final List<Integer> topIndexes;
		private final List<Integer> bottomIndexes;
		private final List<Integer> leftIndexes;
		private final List<Integer> rightIndexes;
		private final double avgDip;
		
		public DownDip(List<? extends RuptureSurface> surfaces, List<? extends FaultSection> sections) {
			super(surfaces);
			Preconditions.checkArgument(sections.size() == numSurfaces,
					"Passed in section list must be null or of equal size as the surfaces list: %s != %s",
					sections.size(), numSurfaces);
			
			List<Integer> topIndexes = new ArrayList<>();
			List<Integer> bottomIndexes = new ArrayList<>();
			List<Integer> leftIndexes = new ArrayList<>(1);
			List<Integer> rightIndexes = new ArrayList<>(1);
			
			// bundle by parent section ID
			List<List<FaultSection>> groups = new ArrayList<>();
			List<FaultSection> current = null;
			int prevParent = -1;
			for (FaultSection sect : sections) {
				int parent = sect.getParentSectionId();
				if (current == null || parent != prevParent) {
					current = new ArrayList<>();
					groups.add(current);
					prevParent = parent;
				}
				current.add(sect);
			}
			
			List<List<List<Integer>>> groupedRowColOrganized = new ArrayList<>(groups.size()); 
			Boolean[] groupReversals = new Boolean[groups.size()];
			int globalIndex = 0;
			int[] numPopulatedRows = new int[groups.size()];
			for (int g=0; g<groups.size(); g++) {
				List<FaultSection> group = groups.get(g);
				List<List<Integer>> rowColOrganized = new ArrayList<>(1);
				for (FaultSection sect : group) {
					int indexDD = sect.getSubSectionIndexDownDip();
					Preconditions.checkState(indexDD >= 0, "Bad indexDD=%s for section %s", indexDD, sect);
					while (indexDD >= rowColOrganized.size())
						rowColOrganized.add(new ArrayList<>(group.size()));
					rowColOrganized.get(indexDD).add(globalIndex++);
				}
				Boolean forward = null;
				for (List<Integer> row : rowColOrganized) {
					if (!row.isEmpty()) {
						numPopulatedRows[g]++;
						if (row.size() > 1) {
							// direction checks
							if (forward == null) {
								// first one
								forward = sections.get(row.get(1)).getSubSectionIndexAlong() > sections.get(row.get(0)).getSubSectionIndexAlong();
							}
							int prevIndexAlong = -1;
							for (int i=0; i<row.size(); i++) {
								FaultSection sect = sections.get(row.get(i));
								int indexAlong = sect.getSubSectionIndexAlong();
								Preconditions.checkState(indexAlong >= 0,
										"Bad indexAlong=%s for sect %s", indexAlong, sect);
								if (i > 0) {
									if (forward)
										Preconditions.checkState(indexAlong > prevIndexAlong, "Column ordering inconsistent");
									else
										Preconditions.checkState(indexAlong < prevIndexAlong, "Column ordering inconsistent");
								}
								prevIndexAlong = indexAlong;
							}
						}
					}
				}
				if (forward == null) {
					// we only have a single section per row, need to determine direction
					if (g > 0) {
						// compare to previous section
						List<List<Integer>> priorGroup = groupedRowColOrganized.get(g-1);
						if (groupReversals[g-1] != null) {
							AngleAverager priorStrikeAvg = new AngleAverager();
							for (List<Integer> row : priorGroup)
								for (Integer index : row)
									priorStrikeAvg.add(sections.get(index).getFaultTrace().getStrikeDirection(), surfaceAreas[index]);
							double priorStrike = priorStrikeAvg.getAverage();
							AngleAverager strikeAvg = new AngleAverager();
							for (List<Integer> row : rowColOrganized)
								for (Integer index : row)
									strikeAvg.add(sections.get(index).getFaultTrace().getStrikeDirection(), surfaceAreas[index]);
							double strike = strikeAvg.getAverage();
							// we already know the direction of the prior group
							if (groupReversals[g-1])
								priorStrike += 180;
							// this returns differences in the range [0, 180]
							double diff = FaultUtils.getAbsAngleDiff(priorStrike, strike);
							// if we're more than 90 degrees off, we're facing the opposite direction
							groupReversals[g] = diff > 90d;
						} else {
							// we don't know the direction of either
							double[] dist = new double[4];
							
							// find average start and end locations for each
							Location start1 = null;
							Location end1 = null;
							Location start2 = null;
							Location end2 = null;
							for (boolean prior : new boolean[] {true,false}) {
								List<List<Integer>> rowCol = prior ? priorGroup : rowColOrganized;
								LocationUtils.LocationAverager startAvg = new LocationUtils.LocationAverager();
								LocationUtils.LocationAverager endAvg = new LocationUtils.LocationAverager();
								for (List<Integer> row : rowCol) {
									for (int index : row) {
										FaultSection sect = sections.get(index);
										startAvg.add(sect.getFaultTrace().first(), 1d);
										endAvg.add(sect.getFaultTrace().last(), 1d);
									}
								}
								if (prior) {
									start1 = startAvg.getAverage();
									end1 = endAvg.getAverage();
								} else {
									start2 = startAvg.getAverage();
									end2 = endAvg.getAverage();
								}
							}
							
							dist[0] = distForReversalCheck(start1, start2);
							dist[1] = distForReversalCheck(start1, end2);
							dist[2] = distForReversalCheck(end1, start2);
							dist[3] = distForReversalCheck(end1, end2);
							
							double min = dist[0];
							int minIndex = 0;
							for(int i=1; i<4;i++) {
								if(dist[i]<min) {
									minIndex = i;
									min = dist[i];
								}
							}
							
							if (minIndex==0) { // first_first
								groupReversals[g-1] = true;
								groupReversals[g] = false;
							} else if (minIndex==1) { // first_last
								groupReversals[g-1] = true;
								groupReversals[g] = true;
							} else if (minIndex==2) { // last_first
								groupReversals[g-1] = false;
								groupReversals[g] = false;
							} else { // minIndex==3 // last_last
								groupReversals[g-1] = false;
								groupReversals[g] = true;
							}
						}
					}
				} else {
					groupReversals[g] = !forward;
					if (g > 0 && groupReversals[g-1] == null) {
						// need to back-propagate and fill in the direction of that one
						List<List<Integer>> priorGroup = groupedRowColOrganized.get(g-1);
						AngleAverager priorStrikeAvg = new AngleAverager();
						for (List<Integer> row : priorGroup)
							for (Integer index : row)
								priorStrikeAvg.add(sections.get(index).getFaultTrace().getStrikeDirection(), surfaceAreas[index]);
						double priorStrike = priorStrikeAvg.getAverage();
						AngleAverager strikeAvg = new AngleAverager();
						for (List<Integer> row : rowColOrganized)
							for (Integer index : row)
								strikeAvg.add(sections.get(index).getFaultTrace().getStrikeDirection(), surfaceAreas[index]);
						double strike = strikeAvg.getAverage();
						// we already know the direction of this group
						if (groupReversals[g])
							strike += 180;
						// this returns differences in the range [0, 180]
						double diff = FaultUtils.getAbsAngleDiff(priorStrike, strike);
						// if we're more than 90 degrees off, we're facing the opposite direction
						groupReversals[g-1] = diff > 90d;
					}
				}
				
				groupedRowColOrganized.add(rowColOrganized);
			}
			if (groups.size() == 1 && groupReversals[0] == null)
				// only one group, and only 1 column in that group
				groupReversals[0] = false;
			
			// now build the edges and set reversal flags
			reversed = new BitSet(numSurfaces);
			double avgDip = 0d;
			for (int g=0; g<groups.size(); g++) {
				// just assume that the top row is the extent of the top and the bottom row is the extent of the bottom
				// we could try to define the top edge more carefully of a curved surface, but it's not really important
				// and would add computational cost for no real benefit
				
				boolean first = true;
				
				List<List<Integer>> rowColOrganized = groupedRowColOrganized.get(g);
				for (int r=0; r<rowColOrganized.size(); r++) {
					List<Integer> row = rowColOrganized.get(r);
					if (row.isEmpty())
						continue;
					if (first) {
						// we're on top
						topIndexes.addAll(row);
						first = false;
					}
					if (r == rowColOrganized.size()-1)
						// wer're on bottom
						bottomIndexes.addAll(row);
					if (g == 0)
						// first group
						leftIndexes.add(row.get(0));
					if (g == groups.size()-1)
						rightIndexes.add(row.get(row.size()-1));
					for (int index : row) {
						double dip = sections.get(index).getAveDip();
						if (groupReversals[g]) {
							reversed.set(index);
							avgDip += (180d-dip)*surfaceAreas[index];
						} else {
							avgDip += dip*surfaceAreas[index];
						}
					}
				}
			}
			
			avgDip /= totArea;
			Preconditions.checkState(avgDip > 0 && avgDip < 180d, "Bad avgDip=%s", avgDip);
			if (avgDip > 90d) {
				// the whole thing is reversed
				Collections.reverse(topIndexes);
				Collections.reverse(bottomIndexes);
				List<Integer> tmp = leftIndexes;
				leftIndexes = rightIndexes;
				rightIndexes = tmp;
				reversed.flip(0, numSurfaces);
			}
			
			tops = new BitSet(numSurfaces);
			for (int index : topIndexes)
				tops.set(index);
			
			// wrap lists in more memory efficient versions
			if (numSurfaces < Short.MAX_VALUE) {
				this.topIndexes = new ShortListWrapper(topIndexes);
				this.bottomIndexes = new ShortListWrapper(bottomIndexes);
				this.leftIndexes = new ShortListWrapper(leftIndexes);
				this.rightIndexes = new ShortListWrapper(rightIndexes);
			} else {
				// still more memory efficient (int array rather than boxed)
				this.topIndexes = new IntListWrapper(topIndexes);
				this.bottomIndexes = new IntListWrapper(bottomIndexes);
				this.leftIndexes = new IntListWrapper(leftIndexes);
				this.rightIndexes = new IntListWrapper(rightIndexes);
			}
			this.avgDip = avgDip;
		}

		private DownDip(List<? extends RuptureSurface> surfaces, double[] surfaceAreas, double totArea,
				double avgDip, BitSet reversed, BitSet tops, List<Integer> topIndexes, List<Integer> bottomIndexes,
				List<Integer> leftIndexes, List<Integer> rightIndexes) {
			super(surfaces, surfaceAreas, totArea);
			this.avgDip = avgDip;
			this.reversed = reversed;
			this.tops = tops;
			this.topIndexes = topIndexes;
			this.bottomIndexes = bottomIndexes;
			this.leftIndexes = leftIndexes;
			this.rightIndexes = rightIndexes;
		}

		@Override
		public double getAveDip() {
			return avgDip;
		}

		@Override
		public boolean hasSurfacesDownDip() {
			return true;
		}

		@Override
		public List<Integer> getUpperEdgeSurfaceIndexes() {
			return topIndexes;
		}

		@Override
		public List<Integer> getLowerEdgeSurfaceIndexes() {
			return bottomIndexes;
		}

		@Override
		public List<Integer> getRightEdgeSurfaceIndexes() {
			return rightIndexes;
		}

		@Override
		public List<Integer> getLeftEdgeSurfaceIndexes() {
			return leftIndexes;
		}

		@Override
		public boolean isSurfaceReversed(int index) {
			return reversed.get(index);
		}

		@Override
		public boolean isSurfaceOnUpperEdge(int index) {
			return tops.get(index);
		}

		@Override
		public DownDip copyShallow() {
			// TODO Auto-generated method stub
			return new DownDip(surfaces, surfaceAreas, totArea, avgDip, reversed, tops,
					topIndexes, bottomIndexes, leftIndexes, rightIndexes);
		}
		
	}
	
	private static double distForReversalCheck(Location loc1, Location loc2) {
		// this would probably work just fine (we only care about relative distances)
//		return LocationUtils.cartesianDistanceSq(loc1, loc2);
		// but this is what the original compound surface used
		return LocationUtils.horzDistanceFast(loc1, loc2);
	}
	
	public abstract boolean hasSurfacesDownDip();
	
	/**
	 * @return List indexes in the surfaces list corresponding to the upper edge and ordered in the along-strike
	 * direction
	 */
	public abstract List<Integer> getUpperEdgeSurfaceIndexes();
	
	/**
	 * @return array of indexes in the surfaces list corresponding to the upper edge and ordered in the along-strike
	 * direction
	 */
	public abstract List<Integer> getLowerEdgeSurfaceIndexes();
	
	/**
	 * @return array of indexes in the surfaces list corresponding to the right (last along-strike) edge and ordered
	 * from top to bottom
	 */
	public abstract List<Integer> getRightEdgeSurfaceIndexes();
	
	/**
	 * @return array of indexes in the surfaces list corresponding to the left (first along-strike) edge and ordered
	 * from top to bottom
	 */
	public abstract List<Integer> getLeftEdgeSurfaceIndexes();
	
	/**
	 * @param index surface index
	 * @return true if the surface must be flipped to connect to its neighbors and conform with the Aki & Richards
	 * convention
	 */
	public abstract boolean isSurfaceReversed(int index);
	
	/**
	 * @param index surface index
	 * @return true if the surface is on the upper edge and should thus be included in distance X calculations
	 * convention
	 */
	public abstract boolean isSurfaceOnUpperEdge(int index);
	
	public List<? extends RuptureSurface> getSurfaceList() {
		return surfaces;
	}

	@Override
	public abstract CompoundSurface copyShallow();

	@Override
	public double getAveStrike() {
		return getUpperEdge().getStrikeDirection();
	}

	@Override
	public double getAveLength() {
		if (Double.isNaN(length)) {
			double length = 0d;
			for (int index : getUpperEdgeSurfaceIndexes())
				length += surfaces.get(index).getAveLength();
			this.length = length;
		}
		return length;
	}

	@Override
	public double getAveWidth() {
		if (Double.isNaN(width)) {
			if (hasSurfacesDownDip()) {
				// we have subsections down-dip, approximate it
				double upper = getAveRupTopDepth();
				double lower = getAveRupBottomDepth();
				width = (lower - upper)/Math.sin(Math.toRadians(getAveDip()));
			} else {
				// simple
				double width = 0d;
				for (int index : getUpperEdgeSurfaceIndexes())
					width += surfaces.get(index).getAveWidth()*surfaceAreas[index];
				this.width = width/totArea;
			}
		}
		return width;
	}

	@Override
	public double getAveHorizontalWidth() {
		if (Double.isNaN(horzWidth)) {
			if (hasSurfacesDownDip()) {
				// we have subsections down-dip, approximate it
				double width = getAveWidth();
				horzWidth = width * Math.cos(Math.toRadians(getAveDip()));
			} else {
				double horzWidth = 0;
				for (int index : getUpperEdgeSurfaceIndexes())
					horzWidth += surfaces.get(index).getAveHorizontalWidth()*surfaceAreas[index];
				this.horzWidth = horzWidth/totArea;
			}
		}
		return horzWidth;
	}

	@Override
	public double getArea() {
		return totArea;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		double area = 0d;
		for (RuptureSurface surf : surfaces)
			area += surf.getAreaInsideRegion(region);
		return area;
	}

	@Override
	public double getAveGridSpacing() {
		double avgSpacing = 0d;
		for (int s=0; s<surfaceAreas.length; s++)
			avgSpacing += surfaces.get(s).getAveGridSpacing()*surfaceAreas[s];
		avgSpacing /= totArea;
		return avgSpacing;
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceJB();
	}

	@Override
	public double getDistanceRup(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceX();
	}
	
	@Override
	public double getQuickDistance(Location siteLoc) {
		return cache.getQuickDistance(siteLoc);
	}
	
	@Override
	public SurfaceDistances getDistances(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc);
	}

	@Override
	public double getAveRupTopDepth() {
		if (Double.isNaN(topDepth)) {
			double topDepth = 0d;
			double sumArea = 0d;
			for (int s : getUpperEdgeSurfaceIndexes()) {
				topDepth += surfaces.get(s).getAveRupTopDepth()*surfaceAreas[s];
				sumArea += surfaceAreas[s];
			}
			this.topDepth = topDepth/sumArea;
		}
		return topDepth;
	}

	@Override
	public double getAveRupBottomDepth() {
		if (Double.isNaN(bottomDepth)) {
			double bottomDepth = 0d;
			double sumArea = 0d;
			for (int s : getLowerEdgeSurfaceIndexes()) {
				bottomDepth += surfaces.get(s).getAveRupBottomDepth()*surfaceAreas[s];
				sumArea += surfaceAreas[s];
			}
			this.bottomDepth = bottomDepth/sumArea;
		}
		return bottomDepth;
	}

	@Override
	public double getAveDipDirection() {
		double dipDir = getAveStrike() + 90;
		while (dipDir > 360d)
			dipDir -= 360d;
		return dipDir;
	}

	@Override
	public FaultTrace getUpperEdge() {
		FaultTrace upperEdge = new FaultTrace(null);
		for (int index : getUpperEdgeSurfaceIndexes()) {
			FaultTrace trace;
			try {
				// some surfaces don't support getUpperEdge in some circumstances,
				// so revert to evenly discretized upper if needed
				trace = surfaces.get(index).getUpperEdge();
			} catch (RuntimeException e) {
				trace = surfaces.get(index).getEvenlyDiscritizedUpperEdge();
			}
			if (isSurfaceReversed(index)) {
				for (int i=trace.size(); --i>=0;)
					upperEdge.add(trace.get(i));
			} else {
				upperEdge.addAll(trace);
			}
		}
		return upperEdge;
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		int index = getUpperEdgeSurfaceIndexes().get(0);
		return isSurfaceReversed(index) ? surfaces.get(index).getLastLocOnUpperEdge() : surfaces.get(index).getFirstLocOnUpperEdge();
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		List<Integer> upperIndexes = getUpperEdgeSurfaceIndexes();
		int index = upperIndexes.get(upperIndexes.size()-1);
		return isSurfaceReversed(index) ? surfaces.get(index).getFirstLocOnUpperEdge() : surfaces.get(index).getLastLocOnUpperEdge();
	}

	@Override
	public Location getFirstLocOnLowerEdge() {
		int index = getLowerEdgeSurfaceIndexes().get(0);
		return isSurfaceReversed(index) ? surfaces.get(index).getLastLocOnLowerEdge() : surfaces.get(index).getFirstLocOnLowerEdge();
	}

	@Override
	public Location getLastLocOnLowerEdge() {
		List<Integer> upperIndexes = getLowerEdgeSurfaceIndexes();
		int index = upperIndexes.get(upperIndexes.size()-1);
		return isSurfaceReversed(index) ? surfaces.get(index).getFirstLocOnLowerEdge() : surfaces.get(index).getLastLocOnLowerEdge();
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		LocationList locList = getEvenlyDiscritizedListOfLocsOnSurface();
		double numInside = 0;
		for(Location loc: locList)
			if(region.contains(loc))
				numInside += 1;
		return numInside/(double)locList.size();
	}

	@Override
	public String getInfo() {
		// TODO could populate with top/bottom and reversed info if ever useful
		return "";
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {
		double minDist = Double.POSITIVE_INFINITY;
		for (RuptureSurface mySurf : surfaces)
			minDist = Math.min(minDist, mySurf.getMinDistance(surface));
		return minDist;
	}

	@Override
	public CompoundSurface getMoved(LocationVector v) {
		List<RuptureSurface> movedSurfaces = new ArrayList<>(surfaces.size());
		for (RuptureSurface surf : surfaces) {
			RuptureSurface moved = surf.getMoved(v);
			movedSurfaces.add(moved);
		}
		
		CompoundSurface copy = copyShallow();
		copy.surfaces = movedSurfaces;
		
		return copy;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		return getEvenlyDiscritizedListOfLocsOnSurface().listIterator();
	}
	


	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		// modified to use an expected size
		// not caching in order to avoid memory bloat, but individual surfaces should already be cached, making this quick'
		int count = 0;
		List<LocationList> surfLists = new ArrayList<>(surfaces.size());
		for(RuptureSurface surf:surfaces) {
			LocationList surfList = surf.getEvenlyDiscritizedListOfLocsOnSurface();
			count += surfList.size();
			surfLists.add(surfList);
		}
		LocationList locs = new LocationList(count);
		for (LocationList surfList : surfLists)
			locs.addAll(surfList);
		return locs;
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		return getEvenlyDiscretizedEdge(true);
	}

	@Override
	public FaultTrace getEvenlyDiscritizedLowerEdge() {
		return getEvenlyDiscretizedEdge(false);
	}
	
	private FaultTrace getEvenlyDiscretizedEdge(boolean upper) {
		List<Integer> indexes = upper ? getUpperEdgeSurfaceIndexes() : getLowerEdgeSurfaceIndexes();
		
		final double identicalSpacingThreshold = 0.1*getAveGridSpacing();
		
		FaultTrace evenEdge = new FaultTrace(null);
		for (int i=0; i<indexes.size(); i++) {
			int index = indexes.get(i);
			boolean reversed = isSurfaceReversed(index);
			LocationList edge = upper ? surfaces.get(index).getEvenlyDiscritizedUpperEdge()
					: surfaces.get(index).getEvenlyDiscritizedLowerEdge();
			if (reversed) {
				if (i == 0 || LocationUtils.linearDistanceFast(evenEdge.last(), edge.last()) > identicalSpacingThreshold) {
					// include the whole thing
					for (int j=edge.size(); --j>=0;)
						evenEdge.add(edge.get(j));
				} else {
					// skip the first one
					for (int j=edge.size()-1; --j>=0;)
						evenEdge.add(edge.get(j));
				}
			} else {
				if (i == 0 || LocationUtils.linearDistanceFast(evenEdge.last(), edge.first()) > identicalSpacingThreshold) {
					// include the whole thing
					for (int j=0; j<edge.size(); j++)
						evenEdge.add(edge.get(j));
				} else {
					// skip the first one
					for (int j=1; j<edge.size(); j++)
						evenEdge.add(edge.get(j));
				}
			}
		}
		
		return evenEdge;
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		LocationList perim = new LocationList();
		final double avgSpacing = getAveGridSpacing();
		final double identicalSpacingThreshold = 0.1*avgSpacing;
		
		perim.addAll(getEvenlyDiscritizedUpperEdge());
		
		// right edge
		List<Integer> rightIndexes = getRightEdgeSurfaceIndexes();
		for (int i=0; i<rightIndexes.size(); i++) {
			int index = rightIndexes.get(i);
			RuptureSurface surf = surfaces.get(index);
			Location top, bottom;
			if (isSurfaceReversed(index)) {
				top = surf.getFirstLocOnUpperEdge();
				bottom = surf.getFirstLocOnLowerEdge();
			} else {
				top = surf.getLastLocOnUpperEdge();
				bottom = surf.getLastLocOnLowerEdge();
			}
			if (i > 0 && LocationUtils.linearDistanceFast(perim.last(), top) > identicalSpacingThreshold)
				// top of the first will always be a duplicate, skip it; only include if not the first on the edge and
				// not equal to the prior bottom
				perim.add(top);
			addDiscretizedLineBetween(perim, top, bottom, avgSpacing);
			if (i < rightIndexes.size()-1)
				// only add the bottom if we're not last, otherwise it will be a duplicate with the lower edge
				perim.add(bottom);
		}
		
		LocationList lower = getEvenlyDiscritizedLowerEdge();
		// add lower in reverse order
		for (int i=lower.size(); --i>=0;)
			perim.add(lower.get(i));
		
		// left edge
		List<Integer> leftIndexes = getLeftEdgeSurfaceIndexes();
		for (int i=leftIndexes.size(); --i>=0;) {
			int index = leftIndexes.get(i);
			RuptureSurface surf = surfaces.get(index);
			Location top, bottom;
			if (isSurfaceReversed(index)) {
				top = surf.getLastLocOnUpperEdge();
				bottom = surf.getLastLocOnLowerEdge();
			} else {
				top = surf.getFirstLocOnUpperEdge();
				bottom = surf.getFirstLocOnLowerEdge();
			}
			if (i < leftIndexes.size()-1 && LocationUtils.linearDistanceFast(perim.last(), top) > identicalSpacingThreshold)
				// bottom of the first will always be a duplicate, skip it; only include if not the first on the edge and
				// not equal to the prior top
				perim.add(bottom);
			addDiscretizedLineBetween(perim, bottom, top, avgSpacing);
			if (i > 0)
				// only add the top if we're not last, otherwise it will be a duplicate with the upper edge
				perim.add(top);
		}
		return perim;
	}
	
	/**
	 * Adds an evenly discretized line to the given list spanning from -> to, but with from and to omittied (only
	 * interior locations retained).
	 * @param perim
	 * @param from
	 * @param to
	 * @param avgSpacing
	 */
	private static void addDiscretizedLineBetween(LocationList perim, Location from, Location to, double avgSpacing) {
		LocationList line = GriddedSurfaceUtils.getEvenlyDiscretizedLine(from, to, avgSpacing);
		// skip first and last as they'll be duplicates
		for (int i=1; i<line.size()-1; i++)
			perim.add(line.get(i));
	}

	@Override
	public LocationList getPerimeter() {
		LocationList perim = new LocationList();
		// top
		for (int index : getUpperEdgeSurfaceIndexes()) {
			RuptureSurface surf = surfaces.get(index);
			if (isSurfaceReversed(index)) {
				perim.add(surf.getLastLocOnUpperEdge());
				perim.add(surf.getFirstLocOnUpperEdge());
			} else {
				perim.add(surf.getFirstLocOnUpperEdge());
				perim.add(surf.getLastLocOnUpperEdge());
			}
		}

		// right
		for (int index : getRightEdgeSurfaceIndexes()) {
			RuptureSurface surf = surfaces.get(index);
			if (isSurfaceReversed(index)) {
				perim.add(surf.getFirstLocOnUpperEdge());
				perim.add(surf.getFirstLocOnLowerEdge());
			} else {
				perim.add(surf.getLastLocOnUpperEdge());
				perim.add(surf.getLastLocOnLowerEdge());
			}
		}
		
		// bottom
		List<Integer> bottomIndexes = getLowerEdgeSurfaceIndexes();
		for (int i=bottomIndexes.size(); --i>=0;) {
			int index = bottomIndexes.get(i);
			RuptureSurface surf = surfaces.get(index);
			if (isSurfaceReversed(index)) {
				perim.add(surf.getFirstLocOnLowerEdge());
				perim.add(surf.getLastLocOnLowerEdge());
			} else {
				perim.add(surf.getLastLocOnLowerEdge());
				perim.add(surf.getFirstLocOnLowerEdge());
			}
		}
		
		// left
		List<Integer> LeftIndexes = getLeftEdgeSurfaceIndexes();
		for (int i=LeftIndexes.size(); --i>=0;) {
			int index = LeftIndexes.get(i);
			RuptureSurface surf = surfaces.get(index);
			if (isSurfaceReversed(index)) {
				perim.add(surf.getLastLocOnLowerEdge());
				perim.add(surf.getLastLocOnUpperEdge());
			} else {
				perim.add(surf.getFirstLocOnLowerEdge());
				perim.add(surf.getFirstLocOnUpperEdge());
			}
		}
		
		return perim;
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public SurfaceDistances calcDistances(Location loc) {
		double distanceJB = Double.MAX_VALUE;
		double distanceRup = Double.MAX_VALUE;
		double distanceRup_topRow = Double.MAX_VALUE;
		double dist;
		int surfIndexForX = -1;
		for (int i=0; i<surfaces.size(); i++) {
			RuptureSurface surf = surfaces.get(i);
			dist = surf.getDistanceJB(loc);
			if (dist<distanceJB) distanceJB=dist;
			dist = surf.getDistanceRup(loc);
			if (dist < distanceRup)
				distanceRup = dist;
			if (dist < distanceRup_topRow && isSurfaceOnUpperEdge(i)) {
				// keep track of closest surface that is on the upper edge for distance x calculation
				distanceRup_topRow = dist;
				surfIndexForX = i;
			}
		}
		// use the closest sub-surface (determined via rRup) for distanceX
		// TODO: implement GC2 which gets rid of a lot of nastiness
		final RuptureSurface theSurfForX = surfaces.get(surfIndexForX);
		final boolean surfForXReversed = isSurfaceReversed(surfIndexForX);
		return new SurfaceDistances.PrecomputedLazyX(loc, distanceRup, distanceJB, new Function<Location, Double>() {
			
			@Override
			public Double apply(Location t) {
				double rX = theSurfForX.getDistanceX(loc);
				if (surfForXReversed) {
					// The closest section, which we use to determine Rx, is reversed. That means that its Rx sign
					// (footwall vs hanging wall) is opposite that of the rupture in general; we can either return its
					// sign, e.g., saying that you are on the footwall of the closest section even if you are on the
					// hanging wall side of the rupture more broadly, or flip it to match the full rupture.
					
					// If we return the sign as is, we're saying that what matters is if you're on the HW vs FW of the
					// local/nearest surface, even if you are on the opposite side of the rupture more broadly. So if a
					// fault briefly dips the opposite way in a complex rupture, this would return the HW/FW
					// classification of that deviation (if it's closest). That might make sense if the local section
					// is actually dipping because the closest section should control the ground motion and it makes
					// sense that the HW term, if enabled, should be based on that closest section.
					// 
					// Looking at actual rupture examples in our models, they can get really squirrelly such that
					// the rupture strike direction doesn't carry a lot of meaning; for now, we'll stick to using the
					// closest section's Rx sign, and note that we should move to GC2 distance-weighting in the future
					return rX;
					
					// If we instead return the flipped sign, we're saying that what matters is if you're on the HW vs
					// FW more generally of the full rupture, even though the local version might be a deviation. The
					// argument I can see here is that the closest section might be SS and its HW/FW classification
					// might be arbitrary, but it's part of a largely dipping rupture, and we would then want to honor
					// the HW/FW flag of the main rupture. 
//					return -rX;
				}
				return rX;
			}
		});
	}

	@Override
	public double calcQuickDistance(Location siteLoc) {
		double minDist = Double.POSITIVE_INFINITY;
		for (RuptureSurface surf : surfaces)
			minDist = Math.min(minDist, surf.getQuickDistance(siteLoc));
		return minDist;
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}

}
