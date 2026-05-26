package gov.usgs.earthquake.nshmp.erf.seismicity;

import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

public class CrustalGridSourceBuilder {

	/**
	 * Interface for nucleation probabilities at each location in a {@link CubedGriddedRegion}.
	 */
	public interface NucleationPDF_3D {
		
		/**
		 * This returns the nucleation probability for a given cube.
		 * 
		 * @param cubeIndex cube index in the  {@link CubedGriddedRegion}
		 * @param gridCellIndex 2D grid index in the {@link CubedGriddedRegion#getGriddedRegion()}, which is likely coarser
		 * than the 2D grid spacing
		 * @param depthIndex depth index in the {@link CubedGriddedRegion}
		 * @return nucleation probability at the given grid and depth indexex
		 */
		public double getCubeNucleationProb(int cubeIndex, int gridCellIndex, int depthIndex);
		
		/**
		 * This returns the aggregate nucleation prob for a given 2D grid cell, summed across all cubes for that cell
		 * @param gridCellIndex
		 * @return
		 */
		public double getGridCellNucleationProb(int gridCellIndex);
		
		public default double getGridCellNormalizedCubeNucleationProb(int cubeIndex, int gridCellIndex, int depthIndex) {
			double cellNuclProb = getGridCellNucleationProb(gridCellIndex);
			if (cellNuclProb == 0d)
				return 0d;
			return getCubeNucleationProb(cubeIndex, gridCellIndex, depthIndex) / cellNuclProb;
		}
	}
	
	/**
	 * Depth-dependent nucleation probability distribution where the depth-distribution is constant for all grid
	 * locations, and a 2D PDF supplied
	 */
	public static class DepthDistAnd2D_PDF_NucleationPDF implements NucleationPDF_3D {

		private final EvenlyDiscretizedFunc depthNuclProbHist;
		private final double[] spatialPDF;
		private final double cubesPerCellScalar2D;

		public DepthDistAnd2D_PDF_NucleationPDF(CubedGriddedRegion cgr, EvenlyDiscretizedFunc depthNuclProbHist,
				double[] spatialPDF) {
			// each cell is first divided into a smaller gridding, this will be used to scale PDF values to that smaller
			// scale (before applying the depth scaling)
			cubesPerCellScalar2D = 1d/(cgr.getNumCubesPerGridEdge()*cgr.getNumCubesPerGridEdge());
			double testSum=0;
			for(double val:spatialPDF) testSum += val;
			Preconditions.checkState(testSum < 1.001 && testSum > 0.999, "spatialPDF values must sum to 1.0; sum=%s", testSum);
			this.spatialPDF = spatialPDF;
			
			this.depthNuclProbHist = validateOrUpdateDepthDistr(depthNuclProbHist, cgr);
		}
		
		private EvenlyDiscretizedFunc validateOrUpdateDepthDistr(EvenlyDiscretizedFunc depthNuclProbHist, CubedGriddedRegion cgr) {
			double testSum = depthNuclProbHist.calcSumOfY_Vals();
			Preconditions.checkState(testSum < 1.001 && testSum > 0.999, "depthNuclProbHist y-axis values must sum to 1.0; sum=%s", testSum);
			// now make sure it matches depths used in CubedGriddedRegion
			double cubeDiscr = cgr.getCubeDepthDiscr();
			int numCubes = cgr.getNumCubeDepths();
			double firstDepth = cgr.getCubeDepth(0);
			if ((float)depthNuclProbHist.getDelta() == (float)cubeDiscr && (float)firstDepth == (float)depthNuclProbHist.getMinX()) {
				// same gridding
				if (numCubes == depthNuclProbHist.size())
					// identical
					return depthNuclProbHist;
				// not identical, resize to the same number of bins, padding with zeros if needed
				EvenlyDiscretizedFunc ret = new EvenlyDiscretizedFunc(firstDepth, numCubes, cubeDiscr);
				for (int i=0; i<ret.size(); i++) {
					if (i >= depthNuclProbHist.size())
						System.err.println("WARNING: depth-nucleation probability histogram doesn't have a value at "
								+(float)ret.getX(i)+", setting to 0");
					else
						ret.set(i, depthNuclProbHist.getY(i));
				}
				double weightBelow = 0d;
				for (int i=ret.size(); i<depthNuclProbHist.size(); i++)
					weightBelow += depthNuclProbHist.getY(i);
				if (weightBelow > 0d) {
					System.err.println("WARNING: depth-nucleation probability histogram has values below CubedGriddedRegion "
							+ "with total weight="+(float)weightBelow+", ignoring those values and re-normalizing.");
					ret.scale(1d/ret.calcSumOfY_Vals());
				} else {
					testSum = ret.calcSumOfY_Vals();
					Preconditions.checkState(testSum < 1.001 && testSum > 0.999,
							"re-gridded depth-nucleation hist doesn't sum to 1? %s", testSum);
				}
				return ret;
			}
			// TODO: could add interpolation support
			throw new IllegalStateException("Supplied depth-nucleation probability histogram does not match "
					+ "CubedGriddedRegion gridding.");
		}

		@Override
		public double getCubeNucleationProb(int cubeIndex, int gridCellIndex, int depthIndex) {
			return cubesPerCellScalar2D * spatialPDF[gridCellIndex] * depthNuclProbHist.getY(depthIndex);
		}

		@Override
		public double getGridCellNucleationProb(int gridCellIndex) {
			return spatialPDF[gridCellIndex];
		}
		
	}
	
	/**
	 * This interface defines regional-scale rate balancing, wherein the rates of on-fault supra-seismogenic
	 * ruptures can be removed from the total regional MFD, leaving the remainder for the gridded seismicity model
	 * (and thus preserving the total regional MFD without any double-counting).
	 * <p>
	 * Although this interface supports both uniform and rupture-specific spatial kernels, it is not to be confused
	 * with a {@link NearFaultCarveOutModel}. The near fault carveout handles local reductions in the immediate vicinity
	 * of faults, not regional rate balancing.
	 * <p>
	 * If both a {@link RuptureRateBalancingModel} and {@link NearFaultCarveOutModel} are supplied, then any carved-out
	 * rate near faults will later be redistributed to all off-fault grid cells.
	 */
	public interface RuptureRateBalancingModel {

		/**
		 * This returns the rate balancing weights for the given rupture at each 2D grid cell in the supplied
		 * {@link GriddedRegion}. The returned array must be of length {@link GriddedRegion#getNodeCount()}.
		 * @param fss
		 * @param rupIndex
		 * @param gridReg
		 * @return weights array of length {@link GriddedRegion#getNodeCount()}
		 */
		public double[] getRateBalancingGridCellWeights(FaultSystemSolution fss, int rupIndex, GriddedRegion gridReg);
	}
	
	public static final class UniformRateBalancingModel implements RuptureRateBalancingModel {
		
		private volatile double[] prevArray;
		
		@Override
		public double[] getRateBalancingGridCellWeights(FaultSystemSolution fss, int rupIndex, GriddedRegion gridReg) {
			double[] ret = prevArray;
			if (ret == null || ret.length != gridReg.getNodeCount()) {
				ret = new double[gridReg.getNodeCount()];
				for (int i=0; i<ret.length; i++)
					ret[i] = 1d;
				prevArray = ret;
			}
			return ret;
		}
		
	}
	
	public interface NearFaultCarveOutModel {
		
		public IncrementalMagFreqDist getAssociatedCubeMFD(IncrementalMagFreqDist cellGridMFD, double assocCubeWt,
				double sectMinMag, double sectMaxMag, IncrementalMagFreqDist sectMappedToCubeSupraMFD);
	}
	
	/**
	 * Carve out gridded seismicity at all associated grid nodes at and above the associated on-fault supra-seis
	 * minimum magnitude (including any above the supra-seis Mmax).
	 * 
	 * This is what was done in UCERF3, NSHM23-WUS, and NSHM25-PRVI.
	 */
	public static class NearFaultCarveOutAboveSupraMmin implements NearFaultCarveOutModel {

		@Override
		public IncrementalMagFreqDist getAssociatedCubeMFD(IncrementalMagFreqDist cellGridMFD, double assocCubeWt,
				double sectMinMag, double sectMaxMag, IncrementalMagFreqDist sectMappedToCubeSupraMFD) {
			IncrementalMagFreqDist ret = cellGridMFD.deepClone();
			ret.scale(assocCubeWt);
			double mfdBinMax = cellGridMFD.getMaxX() + 0.5*cellGridMFD.getDelta();
			if (sectMinMag <= mfdBinMax) {
				int minMagIndex = ret.getClosestXIndex(sectMinMag);
				for (int i=minMagIndex; i<ret.size(); i++)
					ret.set(i, 0d);
			}
			return ret;
		}
		
	}
	
	/**
	 * Carve out gridded seismicity at all associated grid nodes at and above the associated on-fault supra-seis
	 * minimum magnitude and below the supra-seis maximum magnitude, but preserve any the gridded seismicity rates
	 * above Mmax if off-fault Mmax is above supra-seis Mmax.
	 */
	public static class NearFaultCarveOutInSupraRange implements NearFaultCarveOutModel {

		@Override
		public IncrementalMagFreqDist getAssociatedCubeMFD(IncrementalMagFreqDist cellGridMFD, double assocCubeWt,
				double sectMinMag, double sectMaxMag, IncrementalMagFreqDist sectMappedToCubeSupraMFD) {
			IncrementalMagFreqDist ret = cellGridMFD.deepClone();
			ret.scale(assocCubeWt);
			double mfdBinMax = cellGridMFD.getMaxX() + 0.5*cellGridMFD.getDelta();
			if (sectMinMag <= mfdBinMax) {
				int minMagIndex = ret.getClosestXIndex(sectMinMag);
				int maxMagIndex = sectMaxMag <= mfdBinMax ? ret.getClosestXIndex(sectMaxMag) : Integer.MAX_VALUE;
				for (int i=minMagIndex; i<ret.size() && i<=maxMagIndex; i++)
					ret.set(i, 0d);
			}
			return ret;
		}
		
	}
	
	/**
	 * Use the gridded seismicity model as a floor, such that any associated on-fault rates can only increase the
	 * local rates; if the on-fault rate is below the off-fault rate, then a partial off-fault rate is retained to
	 * preserve the original rate.
	 */
	public static class NearFaultCarveGriddedRateFloor implements NearFaultCarveOutModel {

		@Override
		public IncrementalMagFreqDist getAssociatedCubeMFD(IncrementalMagFreqDist cellGridMFD, double assocCubeWt,
				double sectMinMag, double sectMaxMag, IncrementalMagFreqDist sectMappedToCubeSupraMFD) {
			Preconditions.checkState(sectMappedToCubeSupraMFD.size() == cellGridMFD.size());
			Preconditions.checkState((float)sectMappedToCubeSupraMFD.getMinX() == (float)cellGridMFD.getMinX());
			Preconditions.checkState((float)sectMappedToCubeSupraMFD.getDelta() == (float)cellGridMFD.getDelta());
			IncrementalMagFreqDist ret = cellGridMFD.deepClone();
			ret.scale(assocCubeWt);
			for (int i=0; i<cellGridMFD.size(); i++) {
				double origVal = ret.getY(i);
				double assocVal = sectMappedToCubeSupraMFD.getY(i);
				ret.set(i, Math.max(0d, origVal-assocVal));
			}
			return ret;
		}
		
	}
	
	/**
	 * Interface for converting magnitudes/rates at individual 3D (or summed to 2D) locations into finite ruptures.
	 * 
	 * This will apply any faulting style (or full focal mechanism) distributions, as well as any split between
	 * active crustal and stable continental ruptures.
	 */
	public interface RuptureBuilder {
		
		public List<GriddedRupture> buildFiniteRuptures(int gridCellIndex, IncrementalMagFreqDist cellTotalMFD,
				FaultCubeAssociations cubeAssoc, int[] cubeIndexes, IncrementalMagFreqDist[] cubeMFDs);
	}
	
	public static GridSourceList build(FaultSystemSolution fss, IncrementalMagFreqDist totalMFD,
			FaultCubeAssociations faultCubeAssociations, NucleationPDF_3D nucleationPDF,
			RuptureRateBalancingModel rateBalancingModel, NearFaultCarveOutModel nearFaultCarveout,
			RuptureBuilder ruptureBuilder) {
		GriddedRegion gridReg = faultCubeAssociations.getRegion();
		CubedGriddedRegion cgr = faultCubeAssociations.getCubedGriddedRegion();
		
		FaultSystemRupSet rupSet = fss.getRupSet();
		double minGridMag = totalMFD.getMinX() - 0.5*totalMFD.getDelta();
		double maxGridMag = totalMFD.getMaxX() + 0.5*totalMFD.getDelta();
		
		// determine the grid cell MFDs and total gridded MFDs, including any rate balancing, but not yet including
		// any near-fault carveout
		IncrementalMagFreqDist[] gridCellMFDs = new IncrementalMagFreqDist[gridReg.getNodeCount()];
		if (rateBalancingModel == null || rateBalancingModel instanceof UniformRateBalancingModel) {
			IncrementalMagFreqDist totalGriddedMFD;
			if (rateBalancingModel != null) {
				// uniform regional rate balancing
				IncrementalMagFreqDist solNuclMFD = fss.calcNucleationMFD_forRegion(
						gridReg, totalMFD.getMinX(), totalMFD.getMaxX(), totalMFD.size(), false);
				totalGriddedMFD = new IncrementalMagFreqDist(totalMFD.getMinX(), totalMFD.getMaxX(), totalMFD.size());
				for (int i=0; i<totalMFD.size(); i++) {
					double totalRate = totalMFD.getY(i);
					if (totalRate > 0) {
						double solRate = solNuclMFD.getY(i);
						if (solRate > totalRate) {
							System.err.println("WARNING: MFD bulge at M="+(float)totalMFD.getX(i)
								+"\tGR="+(float)totalRate+"\tsol="+(float)solRate);
						} else {
							totalGriddedMFD.set(i, totalRate - solRate);
						}
					}
				}
			} else {
				totalGriddedMFD = totalMFD.deepClone();
			}
			// each grid cell nucleation MFD is the total gridded MFD scaled by the grid cell prob
			for (int l=0; l<gridReg.getNodeCount(); l++) {
				gridCellMFDs[l] = totalGriddedMFD.deepClone();
				gridCellMFDs[l].scale(nucleationPDF.getGridCellNucleationProb(l));
			}
		} else {
			// spatially-variable rate balancing model
			
			// start with the total MFD scaled by each grid cell prob
			for (int l=0; l<gridReg.getNodeCount(); l++) {
				gridCellMFDs[l] = totalMFD.deepClone();
				gridCellMFDs[l].scale(nucleationPDF.getGridCellNucleationProb(l));
			}
			
			// now subtract ruptures
			double[] rupFracts = rupSet.getFractRupsInsideRegion(gridReg, false);
			RupMFDsModule rupMFDs = fss.getModule(RupMFDsModule.class);
			for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
				double nuclRate = fss.getRateForRup(rupIndex) * rupFracts[rupIndex];
				if (nuclRate == 0d)
					continue;
				DiscretizedFunc rupMFD = rupMFDs == null ? null : rupMFDs.getRuptureMFD(rupIndex);
				double[] mags, rates;
				if (rupMFD == null) {
					mags = new double[] {rupSet.getMagForRup(rupIndex)};
					rates = new double[] {nuclRate};
				} else {
					mags = new double[rupMFD.size()];
					rates = new double[rupMFD.size()];
					for (int m=0; m<mags.length; m++) {
						mags[m] = rupMFD.getX(m);
						rates[m] = rupMFD.getY(m)*rupFracts[rupIndex];
					}
				}
				
				double[] weights = rateBalancingModel.getRateBalancingGridCellWeights(fss, rupIndex, gridReg);
				Preconditions.checkState(weights.length == gridReg.getNodeCount());
				double sumWeight = StatUtils.sum(weights);
				for (int m=0; sumWeight>0 && m<mags.length; m++) {
					if (mags[m] < minGridMag || mags[m] > maxGridMag)
						continue;
					int magIndex = totalMFD.getClosestXIndex(mags[m]);
					
					for (int l=0; l<gridCellMFDs.length; l++) {
						if (weights[l] > 0d) {
							double subtractRate = rates[m] * weights[l] / sumWeight;
							gridCellMFDs[l].set(magIndex, Math.max(0d, gridCellMFDs[l].getY(magIndex) - subtractRate));
						}
					}
				}
			}
		}
		
		// MFDs for cubes when carve-outs apply
		// unassociated MFDs for each cube (if null, full cube weight is used to scale cell MFD)
		IncrementalMagFreqDist[] cubeUnassocMFDs = null;
		// assocated MFDs for each cube; if non-null, associated MFDs will be in order of faultCubeAssociations.getSectsAtCube(c)
		IncrementalMagFreqDist[][] cubeAssocMFDs = null;
		if (nearFaultCarveout != null) {
			// now carve out rates in the immediate vicinity of faults, according to the fault cube associations
			
			IncrementalMagFreqDist carvedOutSum = null;
			// keep track of what we carved out
			if (rateBalancingModel != null)
				carvedOutSum = new IncrementalMagFreqDist(totalMFD.getMinX(), totalMFD.getMaxX(), totalMFD.size());

			// these are branch-averaged mags, even if rupMFDs are attached to the rupSet
			double[] minMagForSect = new double[rupSet.getNumSections()];
			double[] maxMagForSect = new double[rupSet.getNumSections()];
			IncrementalMagFreqDist[] sectSupraMFDs = new IncrementalMagFreqDist[rupSet.getNumSections()];
			ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
			for (int s=0; s<minMagForSect.length; s++) {
				if (modMinMags == null)
					minMagForSect[s] = rupSet.getMinMagForSection(s);
				else
					minMagForSect[s] = modMinMags.getMinMagForSection(s);
				maxMagForSect[s] = rupSet.getMaxMagForSection(s);
				sectSupraMFDs[s] = fss.calcNucleationMFD_forSect(s, totalMFD.getMinX(), totalMFD.getMaxX(), totalMFD.size());
			}
			
			double[] cellAssocFracts = new double[gridReg.getNodeCount()];
			int numCubes = cgr.getNumCubes();
			double[] cubeAssocFracts = new double[numCubes];
			cubeUnassocMFDs = new IncrementalMagFreqDist[numCubes];
			cubeAssocMFDs = new IncrementalMagFreqDist[numCubes][];
			for(int c=0; c<numCubes; c++) {
				double[] sectDistWeights = faultCubeAssociations.getScaledSectDistWeightsAtCube(c);
				if (sectDistWeights == null)
					// no sections nucleate here
					continue;
				int gridIndex = cgr.getRegionIndexForCubeIndex(c);
				Preconditions.checkState(gridIndex < gridReg.getNodeCount());
				int depIndex = cgr.getDepthIndexForCubeIndex(c);
				int[] sects = faultCubeAssociations.getSectsAtCube(c);
				int cellIndex = cgr.getRegionIndexForCubeIndex(c);
				IncrementalMagFreqDist cellMFD = gridCellMFDs[cellIndex];
				double cubeNormWt = nucleationPDF.getGridCellNormalizedCubeNucleationProb(c, gridIndex, depIndex);
				cubeAssocMFDs[c] = new IncrementalMagFreqDist[sects.length];
				for(int s=0; s<sects.length; s++) {
					Preconditions.checkState(s < sectDistWeights.length);
					cubeAssocFracts[c] += sectDistWeights[s];
					double assocCubeWt = cubeNormWt * sectDistWeights[s];
					IncrementalMagFreqDist sectMappedToCubeSupraMFD = sectSupraMFDs[sects[s]].deepClone();
					sectMappedToCubeSupraMFD.scale(sectDistWeights[s] / faultCubeAssociations.getTotalScaledDistWtAtCubesForSect(sects[s]));
					// calculate the associated MFD for this sections
					cubeAssocMFDs[c][s] = nearFaultCarveout.getAssociatedCubeMFD(cellMFD, assocCubeWt,
							minMagForSect[sects[s]], maxMagForSect[sects[s]], sectMappedToCubeSupraMFD);
				}
				cellAssocFracts[cellIndex] += cubeAssocFracts[c]*cubeNormWt;
				if (cubeAssocFracts[c] < 1d) {
					// not all was associated, keep the unassocaited fraction
					cubeUnassocMFDs[c] = cellMFD.deepClone();
					cubeUnassocMFDs[c].scale(cubeNormWt * (1d-cubeAssocFracts[c]));
				}
				
				if (carvedOutSum != null) {
					for (int m=0; m<totalMFD.size(); m++) {
						double origRate = cellMFD.getY(m)*cubeNormWt;
						double myRate = 0d;
						for (int s=0; s<sects.length; s++)
							myRate += cubeAssocMFDs[c][s].getY(m);
						if (cubeUnassocMFDs[c] != null)
							myRate += cubeUnassocMFDs[c].getY(m);
						if (myRate < origRate)
							carvedOutSum.add(m, origRate - myRate);
					}
				}
			}
			
			double totalUnassocMass = 0d;
			for (int l=0; l<gridCellMFDs.length; l++)
				totalUnassocMass += nucleationPDF.getGridCellNucleationProb(l) * (1d-cellAssocFracts[l]);
			
			// recalculate their MFDs as needed (if associated, or if redistributing)
			for (int l=0; l<gridCellMFDs.length; l++) {
				if (cellAssocFracts[l] > 0) {
					// need to recalculate
					IncrementalMagFreqDist origCellMFD = gridCellMFDs[l];
					SummedMagFreqDist newCellMFD = new SummedMagFreqDist(origCellMFD.getMinX(), origCellMFD.getMaxX(), origCellMFD.size());
					for (int c : cgr.getCubeIndicesForGridCell(l)) {
						if (cubeAssocMFDs[c] == null) {
							// cube is fully unassocaited
							int gridIndex = cgr.getRegionIndexForCubeIndex(c);
							int depIndex = cgr.getDepthIndexForCubeIndex(c);
							double cubeNormWt = nucleationPDF.getGridCellNormalizedCubeNucleationProb(c, gridIndex, depIndex);
							newCellMFD.addIncrementalMagFreqDist(origCellMFD, cubeNormWt);
							if (carvedOutSum != null)
								newCellMFD.addIncrementalMagFreqDist(carvedOutSum, nucleationPDF.getCubeNucleationProb(c, gridIndex, depIndex)/totalUnassocMass);
						} else {
							for (IncrementalMagFreqDist mfd : cubeAssocMFDs[c])
								newCellMFD.addIncrementalMagFreqDist(mfd);
							if (cubeUnassocMFDs[c] != null) {
								newCellMFD.addIncrementalMagFreqDist(cubeUnassocMFDs[c]);
								if (carvedOutSum != null) {
									int gridIndex = cgr.getRegionIndexForCubeIndex(c);
									int depIndex = cgr.getDepthIndexForCubeIndex(c);
									newCellMFD.addIncrementalMagFreqDist(carvedOutSum,
											(1d-cubeAssocFracts[c])*nucleationPDF.getCubeNucleationProb(c, gridIndex, depIndex)/totalUnassocMass);
								}
							}
						}
					}
					gridCellMFDs[l]  = newCellMFD;
				} else if (carvedOutSum != null) {
					// redistribute carved out portion
					double cellWtScalar = nucleationPDF.getGridCellNucleationProb(l)/totalUnassocMass;
					for (int m=0; m<gridCellMFDs[l].size(); m++)
						gridCellMFDs[l].add(m, carvedOutSum.getY(m)*cellWtScalar);
				}
			}
		}
		
		// TODO build the ruptures
		return null;
	}
}
