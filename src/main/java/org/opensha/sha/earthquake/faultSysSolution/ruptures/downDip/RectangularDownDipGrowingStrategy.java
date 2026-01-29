package org.opensha.sha.earthquake.faultSysSolution.ruptures.downDip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class RectangularDownDipGrowingStrategy implements RuptureGrowingStrategy {
	
	private float neighborThreshold;
	private boolean requireFullWidthAfterJumps;

	public RectangularDownDipGrowingStrategy(float neighborThreshold, boolean requireFullWidthAfterJumps) {
		this.neighborThreshold = neighborThreshold;
		this.requireFullWidthAfterJumps = requireFullWidthAfterJumps;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		List<List<FaultSection>> rowColOrganized = getRowColOrganized(fullCluster.subSects);
		int numRows = rowColOrganized.size();
		NeighborOverlaps neighbors = new NeighborOverlaps(fullCluster, rowColOrganized, false);
		NeighborOverlaps neighborsDD = new NeighborOverlaps(fullCluster, rowColOrganized, true);
		
		// grow out in rough squares until we hit the foll thickness, then grow out in columns
		List<FaultSubsectionCluster> variations = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		
		// start with single-section rupture
		FaultSubsectionCluster current = buildCluster(fullCluster, List.of(firstSection), firstSection);
		variations.add(current);
		uniques.add(current.unique);
		
		// used when finding serious overlap
		float majorityThreshold = Float.max(0.5f, neighborThreshold);
		
		// build out sub-seismogenic
		while (true) {
			if (current.subSects.size() == 1) {
				// special case for the first one: include mini rects with 4 subsections and start in a corner
				List<FaultSection> firstNeighbors = neighbors.getNeighbors(firstSection, neighborThreshold);
				List<FaultSection> neighborsAbove = new ArrayList<>();
				List<FaultSection> neighborsBelow = new ArrayList<>();
				FaultSection sectBefore = null;
				FaultSection sectAfter = null;
				
				int firstRow = firstSection.getSubSectionIndexDownDip();
				int firstCol = firstSection.getSubSectionIndexAlong();
				for (FaultSection neighbor : firstNeighbors) {
					int row = neighbor.getSubSectionIndexDownDip();
					int col = neighbor.getSubSectionIndexAlong();
					if (row == firstRow) {
						if (col == firstCol+1)
							sectAfter = neighbor;
						else if (col == firstCol-1)
							sectBefore = neighbor;
						else
							throw new IllegalStateException("Unexpected neighbor col from "+firstSection +" to "+neighbor);
					} else {
						if (row == firstRow+1)
							neighborsBelow.add(neighbor);
						else if (row == firstRow-1)
							neighborsAbove.add(neighbor);
						else
							throw new IllegalStateException("Unexpected neighbor row from "+firstSection +" to "+neighbor);
					}
				}
				Collections.sort(neighborsAbove, forwardColumnComparator);
				Collections.sort(neighborsBelow, forwardColumnComparator);
				
				for (boolean after : falseTrue) {
					FaultSection rowNeighbor = after ? sectAfter : sectBefore;
					if (rowNeighbor == null)
						continue;
					for (boolean above : falseTrue) {
						List<FaultSection> adjacentRow = above ? neighborsAbove : neighborsBelow;
						if (adjacentRow.isEmpty())
							continue;
						List<FaultSection> rupSects = new ArrayList<>(4);
						rupSects.add(firstSection);
						rupSects.add(rowNeighbor);
						if (adjacentRow.size() == 1) {
							// simple case, add either side
							FaultSection adjacent = adjacentRow.get(0);
							int row = adjacent.getSubSectionIndexDownDip();
							int col = adjacent.getSubSectionIndexAlong();
							if (after && col < rowColOrganized.get(row).size()-1)
								rupSects.add(rowColOrganized.get(row).get(col+1));
						} else {
							// also common, more complicated
							// keep any that overlap >50% with the center section
							for (FaultSection neighbor : adjacentRow)
								if (neighbors.getOverlap(firstSection, neighbor) >= majorityThreshold)
									rupSects.add(neighbor);
							
							// also add any that overlap >50% with the row neighbor
							int row = adjacentRow.get(0).getSubSectionIndexDownDip();
							for (FaultSection neighbor : neighbors.getNeighbors(rowNeighbor, majorityThreshold))
								if (neighbor.getSubSectionIndexDownDip() == row)
									rupSects.add(neighbor);
						}
						FaultSubsectionCluster variation = buildCluster(fullCluster, rupSects, firstSection);
						if (!uniques.contains(variation.unique)) {
							variations.add(variation);
							uniques.add(variation.unique);
						}
					}
				}
			}
			
			HashSet<FaultSection> nextVariationSects = new HashSet<>(current.subSects);
			for (FaultSection growFrom : current.endSects)
				// add direct neighbors; for perfectly oriented faults, this will create + shaped ruptures
				nextVariationSects.addAll(neighbors.getNeighbors(growFrom, neighborThreshold));
			
			// we don't what + shaped ruptures, we want squares
			// also add up/down-dip neighbors of the interior sections
			List<List<FaultSection>> provisionalOrganized = getTrimmedRowColOrganized(nextVariationSects);
			List<List<FaultSection>> interiors;
			if (provisionalOrganized.size() == 1) {
				interiors = provisionalOrganized;
			} else if (provisionalOrganized.size() == 2) {
				List<FaultSection> row0 = provisionalOrganized.get(0);
				List<FaultSection> row1 = provisionalOrganized.get(1);
				if (row0.contains(firstSection)) {
					interiors = provisionalOrganized.subList(0, 1);
				} else if (row1.contains(firstSection)) {
					interiors = provisionalOrganized.subList(1, 2);
				} else {
					throw new IllegalStateException("First section not contained?");
				}
			} else {
				interiors = provisionalOrganized.subList(1, provisionalOrganized.size()-1);
			}
			int topRowID = provisionalOrganized.get(0).get(0).getSubSectionIndexDownDip();
			int bottomRowID = provisionalOrganized.get(provisionalOrganized.size()-1).get(0).getSubSectionIndexDownDip();
			for (List<FaultSection> interior : interiors) {
				for (FaultSection sect : interior) {
					// neighborsDD: all down (or up) dip neighbors in any row
					for (FaultSection neighbor : neighborsDD.getNeighbors(sect, majorityThreshold))
						if (neighbor.getSubSectionIndexDownDip() >= topRowID && neighbor.getSubSectionIndexDownDip() <= bottomRowID)
							nextVariationSects.add(neighbor);
				}
			}
//			List<FaultSection> bottomInteriorRow, topInteriorRow;
//			if (provisionalOrganized.size() == 1) {
//				topInteriorRow = provisionalOrganized.get(0);
//				bottomInteriorRow = topInteriorRow;
//			} else if (provisionalOrganized.size() == 2) {
//				List<FaultSection> row0 = provisionalOrganized.get(0);
//				List<FaultSection> row1 = provisionalOrganized.get(1);
//				if (row0.contains(firstSection)) {
//					topInteriorRow = null;
//					bottomInteriorRow = row0;
//				} else if (row1.contains(firstSection)) {
//					topInteriorRow = row1;
//					bottomInteriorRow = null;
//				} else {
//					throw new IllegalStateException("First section not contained?");
//				}
//			} else if (provisionalOrganized.size() == 3) {
//				topInteriorRow = provisionalOrganized.get(1);
//				bottomInteriorRow = topInteriorRow;
//			} else {
//				topInteriorRow = provisionalOrganized.get(1);
//				bottomInteriorRow = provisionalOrganized.get(provisionalOrganized.size()-2);
//			}
//			if (topInteriorRow != null) {
//				for (FaultSection sect : topInteriorRow) {
//					int row = sect.getSubSectionIndexDownDip();
//					if (row == 0)
//						// can't go any higher
//						break;
//					for (FaultSection neighbor : neighbors.getNeighbors(sect, majorityThreshold))
//						if (neighbor.getSubSectionIndexDownDip() == row-1)
//							nextVariationSects.add(neighbor);
//				}
//			}
//			if (bottomInteriorRow != null) {
//				for (FaultSection sect : bottomInteriorRow) {
//					int row = sect.getSubSectionIndexDownDip();
//					if (row == numRows-1)
//						// can't go any deeper
//						break;
//					for (FaultSection neighbor : neighbors.getNeighbors(sect, majorityThreshold))
//						if (neighbor.getSubSectionIndexDownDip() == row+1)
//							nextVariationSects.add(neighbor);
//				}
//			}
			
			FaultSubsectionCluster variation = buildCluster(fullCluster, nextVariationSects, firstSection);
			if (!uniques.contains(variation.unique)) {
				variations.add(variation);
				uniques.add(variation.unique);
			}
			
			current = variation;
			if (current.subSects.size() == fullCluster.subSects.size())
				// reached the full fault rupture, stop
				return variations;
			// see if we're full-width
			IntSummaryStatistics curRowStats = variation.subSects.stream().mapToInt(s->s.getSubSectionIndexDownDip()).summaryStatistics();
			if (curRowStats.getMin() == 0 && curRowStats.getMax() == numRows-1)
				// reached the full seismigenic thickness
				break;
		}
		// now grow out full-width in each direction
		FaultSubsectionCluster firstFullRupture = current;
		for (boolean forward : new boolean[] {true,false}) {
			current = firstFullRupture;
			while (true) {
				current = expandFullWidth(fullCluster, current, rowColOrganized, neighborsDD, forward);
				if (current != null) {
					if (!uniques.contains(current.unique)) {
						variations.add(current);
						uniques.add(current.unique);
					}
				} else {
					break;
				}
			}
		}
		
		return variations;
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		Preconditions.checkNotNull(currentRupture);
		
		if (requireFullWidthAfterJumps) {
			List<List<FaultSection>> rowColOrganized = getRowColOrganized(fullCluster.subSects);
			NeighborOverlaps neighborsDD = new NeighborOverlaps(fullCluster, rowColOrganized, true);
			
			List<FaultSection> firstSects = new ArrayList<>(rowColOrganized.size());
			firstSects.add(firstSection);
			for (FaultSection neighbor : neighborsDD.getNeighbors(firstSection, neighborThreshold))
				firstSects.add(neighbor);
			
			
			List<FaultSubsectionCluster> variations = new ArrayList<>();
			HashSet<UniqueRupture> uniques = new HashSet<>();
			FaultSubsectionCluster firstVariation = buildCluster(fullCluster, firstSects, firstSection);
			variations.add(firstVariation);
			uniques.add(firstVariation.unique);
			
			for (boolean forward : new boolean[] {true,false}) {
				FaultSubsectionCluster current = firstVariation;
				while (true) {
					current = expandFullWidth(fullCluster, current, rowColOrganized, neighborsDD, forward);
					if (current != null) {
						if (!uniques.contains(current.unique)) {
							variations.add(current);
							uniques.add(current.unique);
						}
					} else {
						break;
					}
				}
			}
			
			return variations;
		} else {
			// grow out like any other, no special jump treatment
			return getVariations(fullCluster, firstSection);
		}
	}
	
	private FaultSubsectionCluster expandFullWidth(FaultSubsectionCluster fullCluster,
			FaultSubsectionCluster current, List<List<FaultSection>> rowColOrganized,
			NeighborOverlaps neighborsDD, boolean forward) {
		int numRows = rowColOrganized.size();
		IntSummaryStatistics curRowStats = current.subSects.stream().mapToInt(s->s.getSubSectionIndexDownDip()).summaryStatistics();
		
		double rowIndexMidpoint = (double)curRowStats.getMin() + (curRowStats.getMax() - curRowStats.getMin())/2d;
		// find the the furthest section on that middle row and grow from there
		FaultSection[] furthestSectsPerRow = new FaultSection[numRows];
		for (FaultSection sect : current.subSects) {
			int row = sect.getSubSectionIndexDownDip();
			int col = sect.getSubSectionIndexAlong();
			if (furthestSectsPerRow[row] == null || (forward && col > furthestSectsPerRow[row].getSubSectionIndexAlong())
					|| (!forward && col < furthestSectsPerRow[row].getSubSectionIndexAlong()))
				furthestSectsPerRow[row] = sect;
		}
		// choose the most central section on the end and grow from there
		double minMidpointRowDist = Integer.MAX_VALUE;
		FaultSection growFromSect = null;
		for (FaultSection sect : furthestSectsPerRow) {
			if (sect != null) {
				double dist = Math.abs(sect.getSubSectionIndexDownDip() - rowIndexMidpoint);
				if (dist < minMidpointRowDist) {
					minMidpointRowDist = dist;
					growFromSect = sect;
				}
			}
		}
		Preconditions.checkNotNull(growFromSect, "furthestSectsPerRow is all null?");
		HashSet<FaultSection> nextVariationSects = new HashSet<>(current.subSects);
		
		// make sure the growFrom's down-dip neighbors are included
		for (FaultSection neighbor : neighborsDD.getNeighbors(growFromSect, neighborThreshold))
			nextVariationSects.add(neighbor);
		
		// now grow it out one
		List<FaultSection> growFromRow = rowColOrganized.get(growFromSect.getSubSectionIndexDownDip());
		int growFromCol = growFromSect.getSubSectionIndexAlong();
		List<FaultSection> growToSects;
		if (forward) {
			if (growFromCol < growFromRow.size()-1) {
				growToSects = List.of(growFromRow.get(growFromCol+1));
			} else {
				// we've reached the end, but there could still be a corner in other rows
				// just grow all rows with room left one
				growToSects = new ArrayList<>();
				for (FaultSection furthest : furthestSectsPerRow) {
					if (furthest != null) {
						List<FaultSection> furthestRow = rowColOrganized.get(furthest.getSubSectionIndexDownDip());
						int col = furthest.getSubSectionIndexAlong();
						if (col < furthestRow.size()-1)
							growToSects.add(furthestRow.get(col+1));
					}
				}
			}
		} else {
			if (growFromCol > 0) {
				growToSects = List.of(growFromRow.get(growFromCol-1));
			} else {
				// we've reached the end, but there could still be a corner in other rows
				// just grow all rows with room left one
				growToSects = new ArrayList<>();
				for (FaultSection furthest : furthestSectsPerRow) {
					if (furthest != null) {
						List<FaultSection> furthestRow = rowColOrganized.get(furthest.getSubSectionIndexDownDip());
						int col = furthest.getSubSectionIndexAlong();
						if (col > 0)
							growToSects.add(furthestRow.get(col-1));
					}
				}
			}
		}
		
		if (growToSects.isEmpty())
			// fully grown out
			return null;
		
		for (FaultSection growToSect : growToSects) {
			// add that section
			nextVariationSects.add(growToSect);
			// make sure the growTo's down-dip neighbors are included
			for (FaultSection neighbor : neighborsDD.getNeighbors(growToSect, neighborThreshold))
				nextVariationSects.add(neighbor);
		}
		
		return buildCluster(fullCluster, nextVariationSects, current.startSect);
	}
	
	private static FaultSubsectionCluster buildCluster(FaultSubsectionCluster fullCluster,
			Collection<FaultSection> subSects, FaultSection startSect) {
		Preconditions.checkState(!subSects.isEmpty(), "No subsections?");
		// this is trimmed to non-null/empty
		List<List<FaultSection>> organized = getTrimmedRowColOrganized(subSects);
		
		// top to bottom for now
		List<FaultSection> orderedSects = new ArrayList<>(subSects.size());
		List<FaultSection> endSects = new ArrayList<>();
		
		for (int i=0; i<organized.size(); i++) {
			List<FaultSection> rowSects = organized.get(i);
			for (int j=0; j<rowSects.size(); j++) {
				FaultSection sect = rowSects.get(j);
				orderedSects.add(sect);
				boolean edge = i == 0 || i == organized.size()-1 || j == 0 || j == rowSects.size()-1;
				if (edge && (subSects.size() == 1 || sect != startSect))
					endSects.add(sect);
			}
		}
		Preconditions.checkState(!endSects.isEmpty(), "No end sections?");
		
		FaultSubsectionCluster variation = new FaultSubsectionCluster(orderedSects, startSect, endSects);
		
		for (FaultSection sect : orderedSects)
			for (Jump jump : fullCluster.getConnections(sect))
				variation.addConnection(new Jump(sect, variation,
						jump.toSection, jump.toCluster, jump.distance));
		
		return variation;
	}
	
	public static List<List<FaultSection>> getRowColOrganized(Collection<? extends FaultSection> subSects) {
		List<List<FaultSection>> rowColOrganized = new ArrayList<>();
		for (FaultSection sect : subSects) {
			int row = sect.getSubSectionIndexDownDip();
			int col = sect.getSubSectionIndexAlong();
			Preconditions.checkState(row >= 0, "Section %s has bad index down-dip: %s", sect, row);
			Preconditions.checkState(col >= 0, "Section %s has bad index down-dip: %s", sect, col);
			while (rowColOrganized.size() <= row)
				rowColOrganized.add(new ArrayList<>());
			
			List<FaultSection> rowSects = rowColOrganized.get(row);
			while (rowSects.size() <= col)
				rowSects.add(null);
			Preconditions.checkState(rowSects.get(col) == null, "Duplicate section at (%s, %s)", row, col);
			rowSects.set(col, sect);
		}
		return rowColOrganized;
	}
	
	public static List<List<FaultSection>> getTrimmedRowColOrganized(Collection<? extends FaultSection> subSects) {
		List<List<FaultSection>> rowColOrganized = getRowColOrganized(subSects);
		trimToNonNull(rowColOrganized);
		return rowColOrganized;
	}
	
	private static void trimToNonNull(List<List<FaultSection>> rowColOrganized) {
		for (int row=rowColOrganized.size(); --row>=0;) {
			List<FaultSection> rowSects = rowColOrganized.get(row);
			if (rowSects.isEmpty()) {
				rowColOrganized.remove(row);
			} else {
				for (int col=rowSects.size(); --col>=0;)
					if (rowSects.get(col) == null)
						rowSects.remove(col);
			}
		}
		Preconditions.checkState(!rowColOrganized.isEmpty());
	}
	
	private static boolean adjacent(int index1, int index2) {
		return index1 == index2+1 || index1 == index2-1;
	}
	
	private static Comparator<FaultSection> forwardColumnComparator = (S1, S2) ->
			{return Integer.compare(S1.getSubSectionIndexAlong(), S2.getSubSectionIndexAlong());};
	private static Comparator<FaultSection> backwardColumnComparator = (S1, S2) ->
			{return -Integer.compare(S1.getSubSectionIndexAlong(), S2.getSubSectionIndexAlong());};
	private static final boolean[] falseTrue = {false,true};
	
	public static class NeighborOverlaps {
		
		private final List<List<FaultSection>> sectNeighbors;
		private double[][] overlaps;
		private FaultSubsectionCluster cluster;
		private final boolean downDipOnly;
		
		public NeighborOverlaps(FaultSubsectionCluster cluster, boolean downDipOnly) {
			this(cluster, getRowColOrganized(cluster.subSects), downDipOnly);
		}
		
		public NeighborOverlaps(FaultSubsectionCluster cluster, List<List<FaultSection>> rowColOrganized, boolean downDipOnly) {
			this.cluster = cluster;
			this.downDipOnly = downDipOnly;
			ImmutableList<FaultSection> subSects = cluster.subSects;

			sectNeighbors = new ArrayList<>(subSects.size());
			overlaps = new double[subSects.size()][subSects.size()];
			// initialize to NaN
			for (int i=0; i<overlaps.length; i++)
				for (int j=0; j<overlaps.length; j++)
					overlaps[i][j] = Double.NaN;
			
			int rows = rowColOrganized.size();
			
			for (FaultSection sect : subSects) {
				int index = sect.getSubSectionIndex();
				
				while (sectNeighbors.size() <= index)
					sectNeighbors.add(null);
				
				List<FaultSection> neighbors = new ArrayList<>();
				
				Preconditions.checkState(sectNeighbors.get(index) == null);
				sectNeighbors.set(index, neighbors);
				
				int row = sect.getSubSectionIndexDownDip();
				int col = sect.getSubSectionIndexAlong();
				
				List<FaultSection> rowSects = rowColOrganized.get(row);
				
				// add them in a clockwise direction starting with the immediate neighbor before on the same row
				
				// to the right
				if (!downDipOnly && col < rowSects.size()-1) {
					FaultSection neighbor = rowSects.get(col+1);
					neighbors.add(neighbor);
					// fully overlapping by definition
					overlaps[index][neighbor.getSubSectionIndex()] = 1d;
					overlaps[neighbor.getSubSectionIndex()][index] = 1d;
				}
				
				// now go below
				if (row < rows-1) {
					List<Integer> testRows;
					if (downDipOnly) {
						// search all rows below
						testRows = new ArrayList<>();
						for (int testRow=row+1; row<rows; row++)
							testRows.add(testRow);
					} else {
						// only immediate neighbors
						testRows = List.of(row+1);
					}
					for (int testRow : testRows) {
						List<FaultSection> below = rowColOrganized.get(testRow);
						// false means in reverse, so to the left to stay clockwise
						neighbors.addAll(getNeighborsFrom(sect, below, false));
					}
				}
				
				// now to the left
				if (!downDipOnly && col > 0) {
					FaultSection neighbor = rowSects.get(col-1);
					neighbors.add(neighbor);
					// fully overlapping by definition
					overlaps[index][neighbor.getSubSectionIndex()] = 1d;
					overlaps[neighbor.getSubSectionIndex()][index] = 1d;
				}
				
				// now go above
				if (row > 0) {
					List<Integer> testRows;
					if (downDipOnly) {
						// search all rows below
						testRows = new ArrayList<>();
						for (int testRow=row; --testRow>=0;)
							testRows.add(testRow);
					} else {
						// only immediate neighbors
						testRows = List.of(row-1);
					}
					for (int testRow : testRows) {
						List<FaultSection> above = rowColOrganized.get(testRow);
						// true means in reverse, so to the right to stay clockwise
						neighbors.addAll(getNeighborsFrom(sect, above, true));
					}
				}
			}
		}
		
		public List<FaultSection> getNeighbors(FaultSection sect, float threshold) {
			Preconditions.checkState(threshold >= 0 && threshold <= 1, "Bad threshold=%s", threshold);
			Preconditions.checkState(cluster.contains(sect));
			List<FaultSection> neighbors = sectNeighbors.get(sect.getSubSectionIndex());
			List<FaultSection> ret = new ArrayList<>(neighbors.size());
			for (FaultSection neighbor : neighbors)
				if ((float)getOverlap(sect, neighbor) >= threshold)
					ret.add(neighbor);
			return ret;
		}
		
		private List<FaultSection> getNeighborsFrom(FaultSection sect, List<FaultSection> targetRow, boolean forward) {
			// start by guessing that the column indexes line up
			int colIndex = sect.getSubSectionIndexAlong();
			
			List<FaultSection> neighbors = new ArrayList<>();
			
			boolean continueForward = true;
			boolean continueBackward = true;
			for (int testNumAway=0; testNumAway<targetRow.size() && (continueForward || continueBackward); testNumAway++) {
				int[] testCols;
				if (testNumAway == 0) {
					testCols = new int[] {colIndex};
				} else if (continueBackward && continueForward) {
					testCols = new int[] {colIndex+testNumAway, colIndex-testNumAway};
				} else if (continueBackward) {
					testCols = new int[] {colIndex-testNumAway};
				} else {
					testCols = new int[] {colIndex+testNumAway};
				}
				
				for (int testCol : testCols) {
					if (testCol < 0) {
						continueBackward = false;
						continue;
					}
					if (testCol >= targetRow.size()) {
						continueForward = false;
						continue;
					}
					FaultSection candidate = targetRow.get(testCol);
					double overlap = getOverlap(sect, candidate);
					if (overlap > 0) {
						neighbors.add(candidate);
					} else if (!neighbors.isEmpty()) {
						// this one doesn't overlap and we have already found at least one that does (either in this
						// direction, or the other). stop searching in this direction
						if (testCol < colIndex)
							continueBackward = false;
						else if (testCol > colIndex)
							continueForward = false;
					}
				}
			}
			
			if (forward)
				neighbors.sort(forwardColumnComparator);
			else
				neighbors.sort(backwardColumnComparator);
			return neighbors;
		}
				
		private static final double HALF_PI = 0.5*Math.PI;
		
		public double getOverlap(FaultSection fromSect, FaultSection toSect) {
			Preconditions.checkState(cluster.contains(fromSect));
			Preconditions.checkState(cluster.contains(toSect));
			int index1 = fromSect.getSubSectionIndex();
			int index2 = toSect.getSubSectionIndex();
			if (Double.isFinite(overlaps[index1][index2])) {
				return overlaps[index1][index2];
			}
			
			int row1 = fromSect.getSubSectionIndexDownDip();
			int row2 = toSect.getSubSectionIndexDownDip();
			if (row1 == row2) {
				if (downDipOnly) {
					overlaps[index1][index2] = 0d;
					overlaps[index2][index1] = 0d;
				} else {
					// same row; full overlap if neighbors, no overlap otherwise
					int col1 = fromSect.getSubSectionIndexAlong();
					int col2 = toSect.getSubSectionIndexAlong();
					if (adjacent(col1, col2)) {
						overlaps[index1][index2] = 1d;
						overlaps[index2][index1] = 1d;
					} else {
						overlaps[index1][index2] = 0d;
						overlaps[index2][index1] = 0d;
					}
				}
				return overlaps[index1][index2];
			} else if (!downDipOnly && !adjacent(row1, row2)) {
				// not in adjacent rows, no overlap
				overlaps[index1][index2] = 0d;
				overlaps[index2][index1] = 0d;
				return overlaps[index1][index2];
			}
			
			double discr = Math.min(1d, Math.min(fromSect.getTraceLength(), toSect.getTraceLength())/20d);
			RuptureSurface surf1 = fromSect.getFaultSurface(discr);
			RuptureSurface surf2 = toSect.getFaultSurface(discr);
			LocationList edge1, edge2;
			if (row2 > row1) {
				// sect2 is below (greater row) sect1
				edge1 = surf1.getEvenlyDiscritizedLowerEdge();
				edge2 = surf2.getEvenlyDiscritizedUpperEdge();
			} else if (row2 < row1) {
				// sect2 is above (smaller row) sect1
				edge1 = surf1.getEvenlyDiscritizedUpperEdge();
				edge2 = surf2.getEvenlyDiscritizedLowerEdge();
			} else {
				throw new IllegalStateException("Sections must be in different rows: "+row1+", "+row2);
			}
			// calculate both ways while we have the surfaces/edges handy
			for (boolean reverse : falseTrue) {
				RuptureSurface fromSurf;
				LocationList toEdge;
				if (reverse) {
					fromSurf = surf2;
					toEdge = edge1;
				} else {
					fromSurf = surf1;
					toEdge = edge2;
				}
				
				// line coordinates perpendicular to the fault such that a location overlaps if it is right of the line
				// from s1->s2 and left of the line from e1->e2
				Location s1, s2;
				Location e1, e2;
				
				if (fromSurf.getAveDip() < 80) {
					// use the edges
					LocationList lower = fromSurf.getEvenlyDiscritizedLowerEdge();
					
					s1 = lower.first();
					s2 = fromSurf.getFirstLocOnUpperEdge();
					
					e1 = lower.last();
					e2 = fromSurf.getLastLocOnUpperEdge();
				} else {
					// use purpendicular to average strike
					Location start = fromSurf.getFirstLocOnUpperEdge();
					Location end = fromSurf.getLastLocOnUpperEdge();
					// azimuth perpendicular and to the left of the strike
					double leftAz = LocationUtils.azimuthRad(start, end) - HALF_PI;
					
					s1 = start;
					s2 = LocationUtils.location(start, leftAz, 10d);
					
					e1 = start;
					e2 = LocationUtils.location(end, leftAz, 10d);
				}
				
				int numInside = 0;
				for (Location loc : toEdge)
					// if right of line s1->s2 and left of line e1->e2
					if (LocationUtils.distanceToLineFast(s1, s2, loc) >= 0d
							&& LocationUtils.distanceToLineFast(e1, e2, loc) <= 0d)
						numInside++;
				
				double overlap = (double)numInside/(double)toEdge.size();
				if (reverse)
					overlaps[index2][index1] = overlap;
				else
					overlaps[index1][index2] = overlap;
			}
			
			
			return overlaps[index1][index2];
		}
	}

}
