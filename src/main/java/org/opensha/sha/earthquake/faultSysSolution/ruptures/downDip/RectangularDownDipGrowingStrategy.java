package org.opensha.sha.earthquake.faultSysSolution.ruptures.downDip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils;
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
	
	private boolean debug = false;
	
	private float neighborThreshold;
	private boolean requireFullWidthAfterJumps;

	public RectangularDownDipGrowingStrategy() {
		this(0.5f, false);
	}

	public RectangularDownDipGrowingStrategy(float neighborThreshold, boolean requireFullWidthAfterJumps) {
		this.neighborThreshold = neighborThreshold;
		this.requireFullWidthAfterJumps = requireFullWidthAfterJumps;
	}

	@Override
	public String getName() {
		return "Rectangular Down-Dip";
	}
	
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		List<List<FaultSection>> rowColOrganized = getRowColOrganized(fullCluster.subSects);
		int numRows = rowColOrganized.size();
//		NeighborOverlaps neighbors = new NeighborOverlaps(fullCluster, rowColOrganized, false);
		NeighborOverlaps neighborsDD = new NeighborOverlaps(fullCluster, rowColOrganized, true);
		
		// grow out in rough squares until we hit the foll thickness, then grow out in columns
		List<FaultSubsectionCluster> variations = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		
		// start with single-section rupture
		FaultSubsectionCluster current = buildCluster(fullCluster, List.of(firstSection), firstSection);
		variations.add(current);
		uniques.add(current.unique);
		
		if (debug) System.out.println("Building variations from "+firstSection+" on cluster "+fullCluster);
		if (debug) System.out.println("\tBuilding sub-seismogenic first");
		
		// build out sub-seismogenic
		while (true) {
			// first grow it in each direction
			IntSummaryStatistics curRowStats = current.subSects.stream().mapToInt(s->s.getSubSectionIndexDownDip()).summaryStatistics();
			if (curRowStats.getMin() == 0 && curRowStats.getMax() == numRows-1) {
				if (debug) System.out.println("\tReached full seismogenic width");
				// reached the full seismogenic thickness
				break;
			}
			
			if (debug)
				System.out.println("\tExpanding rupture: "+current);
			
			FaultSubsectionCluster unilateral = null;
			for (GrowthDirection direction : GrowthDirection.values()) {
				FaultSubsectionCluster expanded = expandRupture(fullCluster, current, rowColOrganized, neighborsDD, direction);
				if (expanded != null) {
					if (debug) System.out.println("\t\tEpanded "+direction+" to: "+expanded);
					if (!uniques.contains(expanded.unique)) {
						variations.add(expanded);
						uniques.add(expanded.unique);
					}
					if (direction == GrowthDirection.UNILATERAL)
						unilateral = expanded;
				} else if (debug) {
					System.out.println("\t\tCouldn't exapand "+direction);
				}
			}
			Preconditions.checkNotNull(unilateral,
					"Couldn't expand rupture in any direction; current=%s, rowStats=%s", current, curRowStats);
			current = unilateral;
			if (current.subSects.size() == fullCluster.subSects.size()) {
				// reached the full fault rupture, stop
				if (debug) System.out.println("\tReached full fault, stopping with "+variations.size()+" variations");
				return variations;
			}
		}
		
		if (debug) System.out.println("\tExpanding full-width ruptures unilaterally");
		FaultSubsectionCluster firstFullRupture = current;
		// now grow out full-width in each direction
		for (GrowthDirection direction : horizontal) {
			current = firstFullRupture;
			while (true) {
				current = expandRupture(fullCluster, current, rowColOrganized, neighborsDD, direction);
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
		
		if (debug) System.out.println("\tDone; total variation: "+variations.size());
		
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
			
			for (GrowthDirection direction : horizontal) {
				FaultSubsectionCluster current = firstVariation;
				while (true) {
					current = expandRupture(fullCluster, current, rowColOrganized, neighborsDD, direction);
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
	
	enum GrowthDirection {
		FORWARD,
		BACKWARD,
		UP,
		DOWN,
		UNILATERAL
	}
	
	private static final GrowthDirection[] horizontal = {GrowthDirection.FORWARD, GrowthDirection.BACKWARD};
	private static final GrowthDirection[] vertical = {GrowthDirection.UP, GrowthDirection.DOWN};
	
	private FaultSubsectionCluster expandRupture(FaultSubsectionCluster fullCluster,
			FaultSubsectionCluster current, List<List<FaultSection>> rowColOrganized,
			NeighborOverlaps neighborsDD, GrowthDirection direction) {
		int numRows = rowColOrganized.size();
		int minRow = Integer.MAX_VALUE;
		int maxRow = 0;
		for (FaultSection sect : current.subSects) {
			int row = sect.getSubSectionIndexDownDip();
			minRow = Integer.min(minRow, row);
			maxRow = Integer.max(maxRow, row);
		}
		
		int midRowIndex = (int)Math.round((double)minRow + (maxRow - minRow)/2d);
		
		Set<FaultSection> nextVariationSects = new HashSet<>(current.subSects);
		
		// do up/down first to establish new row bounds for the unilateral case
		if (direction == GrowthDirection.UP || direction == GrowthDirection.UNILATERAL) {
			if (minRow > 0)
				nextVariationSects.addAll(growVertically(
						rowColOrganized, neighborsDD, nextVariationSects, midRowIndex, minRow-1));
		}
		
		if (direction == GrowthDirection.DOWN || direction == GrowthDirection.UNILATERAL) {
			if (minRow < numRows-1)
				nextVariationSects.addAll(growVertically(
						rowColOrganized, neighborsDD, nextVariationSects, midRowIndex, maxRow+1));
		}
		
		// find the the furthest section on that middle row and grow from there
		FaultSection[] forwardSectsPerRow = new FaultSection[numRows];
		FaultSection[] backwardSectsPerRow = new FaultSection[numRows];
		for (FaultSection sect : nextVariationSects) {
			int row = sect.getSubSectionIndexDownDip();
			minRow = Integer.min(minRow, row);
			maxRow = Integer.max(maxRow, row);
			int col = sect.getSubSectionIndexAlong();
			if (forwardSectsPerRow[row] == null || col > forwardSectsPerRow[row].getSubSectionIndexAlong())
				forwardSectsPerRow[row] = sect;
			if (backwardSectsPerRow[row] == null || col < backwardSectsPerRow[row].getSubSectionIndexAlong())
				backwardSectsPerRow[row] = sect;
		}
		midRowIndex = (int)Math.round((double)minRow + (maxRow - minRow)/2d);
		Preconditions.checkState(forwardSectsPerRow[midRowIndex] != null && backwardSectsPerRow[midRowIndex] != null,
				"No sections in most central row (%s) for rupture %s with row span=[%s, %s]", midRowIndex, current, minRow, maxRow);
		
		if (direction == GrowthDirection.FORWARD || direction == GrowthDirection.UNILATERAL) {
			// will handle end of row gracefully and return empty set
			nextVariationSects.addAll(growHorizontally(
					rowColOrganized, neighborsDD, nextVariationSects, forwardSectsPerRow, true, midRowIndex));
		}
		
		if (direction == GrowthDirection.BACKWARD || direction == GrowthDirection.UNILATERAL) {
			// will handle end of row gracefully and return empty set
			nextVariationSects.addAll(growHorizontally(
					rowColOrganized, neighborsDD, nextVariationSects, backwardSectsPerRow, false, midRowIndex));
		}
		
		if (nextVariationSects.size() == current.subSects.size())
			// we didn't add anything
			return null;
		
		return buildCluster(fullCluster, nextVariationSects, current.startSect);
	}
	
	private Set<FaultSection> growHorizontally(List<List<FaultSection>> rowColOrganized,
			NeighborOverlaps neighborsDD, Set<FaultSection> currentSects, FaultSection[] currentEdge,
			boolean forward, int midRowIndex) {
		Preconditions.checkState(currentEdge[midRowIndex] != null);
		
		int maxCurrentRow = 0;
		int minCurrentRow = Integer.MAX_VALUE;
		for (int row=0; row<currentEdge.length; row++) {
			if (currentEdge[row] != null) {
				maxCurrentRow = Integer.max(maxCurrentRow, row);
				minCurrentRow = Integer.min(minCurrentRow, row);
			}
		}
		
//		if (debug) System.out.println("\t\t\tGrowing horizontally (forward="+forward+") from "+currentSects.size()
//				+" sects spanning rows ["+minCurrentRow+", "+maxCurrentRow+"]");
		
		List<FaultSection> growToSects;
		if (forward && currentEdge[midRowIndex].getSubSectionIndexAlong() < rowColOrganized.get(midRowIndex).size()-1) {
			growToSects = List.of(rowColOrganized.get(midRowIndex).get(currentEdge[midRowIndex].getSubSectionIndexAlong()+1));
		} else if (!forward && currentEdge[midRowIndex].getSubSectionIndexAlong() > 0) {
			growToSects = List.of(rowColOrganized.get(midRowIndex).get(currentEdge[midRowIndex].getSubSectionIndexAlong()-1));
		} else {
			// we've reached the end, but there could still be a corner in other rows
			// just grow all rows with room left, each one column
			growToSects = new ArrayList<>();
			for (FaultSection furthest : currentEdge) {
				if (furthest != null) {
					List<FaultSection> furthestRow = rowColOrganized.get(furthest.getSubSectionIndexDownDip());
					int col = furthest.getSubSectionIndexAlong();
					if (forward && col < furthestRow.size()-1)
						growToSects.add(furthestRow.get(col+1));
					else if (!forward && col > 0)
						growToSects.add(furthestRow.get(col-1));
				}
			}
		}
		
		if (growToSects.isEmpty())
			// fully grown out
			return Set.of();

		int[] newMaxCols = new int[currentEdge.length];
		int[] newMinCols = new int[currentEdge.length];
		for (int i=0; i<currentEdge.length; i++) {
			if (currentEdge[i] == null) {
				newMaxCols[i] = -1;
				newMinCols[i] = -1;
			} else {
				newMaxCols[i] = currentEdge[i].getSubSectionIndexAlong();
				newMinCols[i] = newMaxCols[i];
			}
		}
		for (FaultSection growToSect : growToSects) {
//			if (debug) System.out.println("\t\t\tGrowing to "+growToSect);
			// add that section
			Preconditions.checkState(!currentSects.contains(growToSect));
			newMinCols[growToSect.getSubSectionIndexDownDip()] = Integer.min(
					newMinCols[growToSect.getSubSectionIndexDownDip()], growToSect.getSubSectionIndexAlong());
			newMaxCols[growToSect.getSubSectionIndexDownDip()] = Integer.max(
					newMaxCols[growToSect.getSubSectionIndexDownDip()], growToSect.getSubSectionIndexAlong());
			// make sure the growTo's down-dip neighbors are included
			for (FaultSection neighbor : neighborsDD.getNeighbors(growToSect, neighborThreshold)) {
				int row = neighbor.getSubSectionIndexDownDip();
				if (row >= minCurrentRow && row <= maxCurrentRow && !currentSects.contains(neighbor)) {
					int col = neighbor.getSubSectionIndexAlong();
					newMinCols[row] = Integer.min(newMinCols[row], col);
					newMaxCols[row] = Integer.max(newMaxCols[row], col);
				}
			}
		}
		// now fill in this way to ensure no gaps
		HashSet<FaultSection> addition = new HashSet<>();
		for (int row=0; row<currentEdge.length; row++) {
			if (newMinCols[row] >= 0) {
				for (int col=newMinCols[row]; col<=newMaxCols[row]; col++) {
					FaultSection neighbor = rowColOrganized.get(row).get(col);
					if (!currentSects.contains(neighbor))
						addition.add(neighbor);
				}
			}
		}
		
		return addition;
	}
	
	private Set<FaultSection> growVertically(List<List<FaultSection>> rowColOrganized,
			NeighborOverlaps neighborsDD, Set<FaultSection> currentSects, int midRowIndex, int toRowIndex) {
		Preconditions.checkState(toRowIndex != midRowIndex);
		
		List<FaultSection> middleRowSects = new ArrayList<>();
		if (currentSects.size() > rowColOrganized.get(midRowIndex).size()*2) {
			// search the row and find the ones int he rupture
			for (FaultSection sect : rowColOrganized.get(midRowIndex))
				if (currentSects.contains(sect))
					middleRowSects.add(sect);
		} else {
			// search the rupture and find the ones in the row
			for (FaultSection sect : currentSects)
				if (sect.getSubSectionIndexDownDip() == midRowIndex)
					middleRowSects.add(sect);
		}
		Preconditions.checkState(!middleRowSects.isEmpty());
		
		int minAddedCol = Integer.MAX_VALUE;
		int maxAddedCol = 0;
		for (FaultSection sect : middleRowSects) {
			// add all neighbors in the destination row
			for (FaultSection neighbor : neighborsDD.getNeighbors(sect, neighborThreshold)) {
				if (neighbor.getSubSectionIndexDownDip() == toRowIndex) {
					int col = neighbor.getSubSectionIndexAlong();
					minAddedCol = Integer.min(minAddedCol, col);
					maxAddedCol = Integer.max(maxAddedCol, col);
				}
			}
		}
		if (minAddedCol > maxAddedCol)
			// none
			return Set.of();
		
		// do it by min/max valid index to make sure we donn't leave any holes
		HashSet<FaultSection> additions = new HashSet<>();
		List<FaultSection> toRow = rowColOrganized.get(toRowIndex);
		for (int i=minAddedCol; i<=maxAddedCol; i++)
			additions.add(toRow.get(i));
		
		return additions;
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
			LocationList surf1Lower = surf1.getEvenlyDiscritizedLowerEdge();
			LocationList surf1Upper = surf1.getEvenlyDiscritizedUpperEdge();
			double strike1 = FaultUtils.getAngleAverage(List.of(
					LocationUtils.azimuth(surf1Upper.first(), surf1Upper.last()),
					LocationUtils.azimuth(surf1Lower.first(), surf1Lower.last())));
			RuptureSurface surf2 = toSect.getFaultSurface(discr);
			LocationList surf2Lower = surf2.getEvenlyDiscritizedLowerEdge();
			LocationList surf2Upper = surf2.getEvenlyDiscritizedUpperEdge();
			double strike2 = FaultUtils.getAngleAverage(List.of(
					LocationUtils.azimuth(surf2Upper.first(), surf2Upper.last()),
					LocationUtils.azimuth(surf2Lower.first(), surf2Lower.last())));
			// calculate both ways while we have the surfaces/edges handy
			for (boolean reverse : falseTrue) {
				LocationList fromEdge;
				LocationList toEdge;
				double fromStrike;
				if (reverse) {
					// from 2 to 1
					if (row2 > row1) {
						// 2 is below (greater row) 1
						fromEdge = surf2Upper;
						toEdge = surf1Lower;
					} else if (row2 < row1) {
						// 1 is below 2
						fromEdge = surf2Lower;
						toEdge = surf1Upper;
					} else {
						throw new IllegalStateException("Sections must be in different rows: "+row1+", "+row2);
					}
					fromStrike = strike2;
				} else {
					// from 1 to 2
					if (row2 > row1) {
						// 2 is below (greater row) 1
						fromEdge = surf1Lower;
						toEdge = surf2Upper;
					} else if (row2 < row1) {
						// 1 is below 2
						fromEdge = surf1Upper;
						toEdge = surf2Lower;
					} else {
						throw new IllegalStateException("Sections must be in different rows: "+row1+", "+row2);
					}
					fromStrike = strike1;
				}
				
				// line coordinates perpendicular to the fault such that a location overlaps if it is right of the line
				// from s1->s2 and left of the line from e1->e2
				Location s1, s2;
				Location e1, e2;
				
				// use purpendicular to average strike
				Location start = fromEdge.first();
				Location end = fromEdge.last();
				// azimuth perpendicular and to the left of the strike
				double leftAz = Math.toRadians(fromStrike) - HALF_PI;

				s1 = start;
				s2 = LocationUtils.location(start, leftAz, 10d);

				e1 = end;
				e2 = LocationUtils.location(end, leftAz, 10d);
				
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
