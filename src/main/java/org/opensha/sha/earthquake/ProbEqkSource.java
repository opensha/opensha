package org.opensha.sha.earthquake;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.util.TectonicRegionType;
/**
 * <p>Title: ProbEqkSource</p>
 * <p>Description: Class for Probabilistic earthquake source.
 * Note that the tectonicRegionType must be one of the options given by the TYPE_* options in the class
 * org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam, and the default here is the
 * TYPE_ACTIVE_SHALLOW option in that class.  Subclasses must override this in the constructor,
 *  or users can change the value using the setTectonicRegion() method here.</p>
 *
 * @author Ned Field, Nitin Gupta, Vipin Gupta
 * @date Aug 27, 2002
 * @version 1.0
 */

public abstract class ProbEqkSource implements EqkSource, Named, Iterable<ProbEqkRupture> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Name of this class
	 */
	protected String name = new String("ProbEqkSource");

	// This represents the tectonic region type for this source (as well as the default)
	private TectonicRegionType tectonicRegionType = TectonicRegionType.ACTIVE_SHALLOW;

	//index of the source as defined by the Earthquake Rupture Forecast
	private int sourceIndex;


	/**
	 * This boolean tells whether the source is Poissonian, which will influence the
	 * calculation sequence in the HazardCurveCalculator.  Note that the default value
	 * is true, so non-Poissonian sources will need to overide this value.
	 */
	protected boolean isPoissonian = true;

	/**
	 * string to save the information about this source
	 */
	private String info;


	/**
	 * This method tells whether the source is Poissonian, which will influence the
	 * calculation sequence in the HazardCurveCalculator
	 */
	public boolean isSourcePoissonian() {
		return isPoissonian;
	}

	/**
	 * Get the iterator over all ruptures
	 * This function returns the iterator for the rupturelist after calling the method getRuptureList()
	 * @return the iterator object for the RuptureList
	 */
	public Iterator<ProbEqkRupture> getRupturesIterator() {
		return new Iterator<ProbEqkRupture>() {
			
			private int index = 0;

			@Override
			public boolean hasNext() {
				return index < getNumRuptures();
			}

			@Override
			public ProbEqkRupture next() {
				return getRupture(index++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("Not supported by this iterator");
			}
		};
	}


	/**
	 * Checks if the source is Poission.
	 * @return boolean
	 */
	public boolean isPoissonianSource(){
		return isPoissonian;
	}

	/**
	 * This computes some measure of the minimum distance between the source and
	 * the site passed in.  This is useful for ignoring sources that are at great
	 * distanced from a site of interest.  Actual implementation depend on subclass.
	 * @param site
	 * @return minimum distance
	 */
	public abstract double getMinDistance(Site site);

	/**
	 * Get the number of ruptures for this source
	 *
	 * @return returns an integer value specifying the number of ruptures for this source
	 */
	public abstract int getNumRuptures() ;

	/**
	 * Get the ith rupture for this source
	 * This is a handle(or reference) to existing class variable. If this function
	 *  is called again, then output from previous function call will not remain valid
	 *  because of passing by reference
	 * It is a secret, fast but dangerous method
	 *
	 * @param i  ith rupture
	 */
	public abstract ProbEqkRupture getRupture(int nRupture);


	/**
	 * this function can be used if a clone is wanted instead of handle to class variable
	 * Subsequent calls to this function will not affect the result got previously.
	 * This is in contrast with the getRupture(int i) function
	 *
	 * @param nRupture
	 * @return the clone of the probEqkRupture
	 */
	public ProbEqkRupture getRuptureClone(int nRupture){
		ProbEqkRupture eqkRupture =getRupture(nRupture);
		ProbEqkRupture eqkRuptureClone= (ProbEqkRupture)eqkRupture.clone();
		return eqkRuptureClone;
	}

	/**
	 * Returns the ArrayList consisting of all ruptures for this source
	 * all the objects are cloned. so this vector can be saved by the user
	 *
	 * @return ArrayList consisting of the rupture clones
	 */
	public List<ProbEqkRupture> getRuptureList() {
		ArrayList<ProbEqkRupture> v= new ArrayList<ProbEqkRupture>();
		for(int i=0; i<getNumRuptures();i++)
			v.add(getRuptureClone(i));
		return v;
	}

	/**
	 * get the name of this class
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the info for this Prob Eqk source
	 * @param infoString : Info
	 * @return
	 */
	public void setInfo(String infoString) {
		this.info = new String(infoString);
	}

	/**
	 * Get the info for this source
	 *
	 * @return
	 */
	public String getInfo() {
		return info;
	}


	/**
	 * Returns the Source Metadata.
	 * Source Metadata provides info. about the following :
	 * <ul>
	 * <li>source index - As defined in ERF
	 * <li>Num of Ruptures  in the given source
	 * <li>Is source poisson - true or false
	 * <li>Total Prob. of the source
	 * <li>Name of the source
	 * </ul>
	 * All this source info is represented as String in one line with each element
	 * seperated by a tab ("\t").
	 * @return String
	 */
	public String getSourceMetadata() {
		//source Metadata
		String sourceMetadata;
		sourceMetadata = sourceIndex+"\t";
		sourceMetadata += getNumRuptures()+"\t";
		sourceMetadata += isPoissonian+"\t";
		sourceMetadata += (float)computeTotalProb()+"\t";
		sourceMetadata += "\""+getName()+"\"";
		return sourceMetadata;
	}
	
	
	/**
	 * This computes the equivalent mean annual rate for this source
	 * (a sum of the rates of all the ruptures)
	 * @return
	 */
	public double computeTotalEquivMeanAnnualRate(double duration) {
		double rate = 0;
		for(ProbEqkRupture rup: this)
			rate += rup.getMeanAnnualRate(duration);
		return rate;
	}



	/**
	 * This computes the total probability for this source
	 * (a sum of the probabilities of all the ruptures)
	 * @return
	 */
	public double computeTotalProb() {
		return computeTotalProbAbove(-10.0);
	}


	/**
	 * This computes the total probability of all rutures great than or equal to the
	 * given mangitude
	 * @return
	 */
	public double computeTotalProbAbove(double mag) {
		return computeTotalProbAbove( mag, null);
	}

	/**
	 * This computes the Approx total probability of all ruptures great than or equal to the
	 * given mangitude.
	 * It checks the 2 end points of the rupture to see whether the rupture lies within region
	 * If both points are within region, rupture is assumed to be in region
	 * 
	 * @return
	 */
	public double computeApproxTotalProbAbove(double mag,Region region) {
		double totProb=0;
		ProbEqkRupture tempRup;
		for(int i=0; i<getNumRuptures(); i++) {
			tempRup = getRupture(i);
			if(tempRup.getMag() < mag) continue;
			totProb+=getApproxRupProbWithinRegion(tempRup, region);
		}
		if(isPoissonian)
			return 1 - Math.exp(totProb);
		else
			return totProb;
	}

	/**
	 * This computes the total probability of all rutures great than or equal to the
	 * given mangitude
	 * @return
	 */
	public double computeTotalProbAbove(double mag,Region region) {
		double totProb=0;
		ProbEqkRupture tempRup;
		for(int i=0; i<getNumRuptures(); i++) {
			tempRup = getRupture(i);
			if(tempRup.getMag() < mag) continue;
			totProb+=getRupProbWithinRegion(tempRup, region);
		}
		if(isPoissonian)
			return 1 - Math.exp(totProb);
		else
			return totProb;
	}

	/**
	 * Get rupture probability within a region. It finds the fraction of rupture surface points 
	 * within the region and then adjusts the probability accordingly.
	 * 
	 * @param tempRup
	 * @param region
	 * @return
	 */
	private double getRupProbWithinRegion(ProbEqkRupture tempRup, Region region) {
		int numLocsInside = 0;
		int totPoints = 0;
		if(region!=null) {
			// get num surface points inside region
			Iterator<Location> locIt = tempRup.getRuptureSurface().getLocationsIterator();
			while(locIt.hasNext()) {
				if(region.contains(locIt.next()))
					++numLocsInside;
				++totPoints;
			}
		} else {
			numLocsInside=1;
			totPoints = numLocsInside;
		}
		if(isPoissonian)
			return Math.log(1-tempRup.getProbability()*numLocsInside/(double)totPoints);
		else
			return tempRup.getProbability()*numLocsInside/(double)totPoints;
	}

	/**
	 * Get rupture probability within a region. 
	 * It first checks whether the end points are within region. If yes,
	 * then rupture is considered within the region
	 * Else It finds the fraction of rupture FAULT TRACE (not the surface) points 
	 * within the region and then adjusts the probability accordingly.
	 * 
	 * 
	 * @param tempRup
	 * @param region
	 * @return
	 */
	private double getApproxRupProbWithinRegion(ProbEqkRupture tempRup, Region region) {
		int numLocsInside = 0;
		int totPoints = 0;
		if(region!=null) {
			// get num surface points inside region
			RuptureSurface rupSurface = tempRup.getRuptureSurface();
			Location loc1 = rupSurface.getFirstLocOnUpperEdge();
			Location loc2 = rupSurface.getLastLocOnUpperEdge();
			// if both surface points are within region, rupture is considered within region
			if(region.contains(loc1) && region.contains(loc2)) {
				numLocsInside=1;
				totPoints = numLocsInside;
			} else { // if both points are not within region, calculate rupProb
				Iterator<Location> locIt = rupSurface.getEvenlyDiscritizedUpperEdge().iterator();
				while(locIt.hasNext()) {
					if(region.contains(locIt.next()))
						++numLocsInside;
					++totPoints;
				}
			}
		} else {
			numLocsInside=1;
			totPoints = numLocsInside;
		}
		if(isPoissonian)
			return Math.log(1-tempRup.getProbability()*numLocsInside/(double)totPoints);
		else
			return tempRup.getProbability()*numLocsInside/(double)totPoints;
	}

	/**
	 * This draws a random list of ruptures.  Non-poisson sources are not yet implemented
	 * @return
	 */
	public ArrayList<ProbEqkRupture> drawRandomEqkRuptures() {
		ArrayList<ProbEqkRupture> rupList = new ArrayList<ProbEqkRupture>();
		//	  System.out.println("New Rupture")
		if(isPoissonian) {
			for(int r=0; r<getNumRuptures();r++) {
				ProbEqkRupture rup = getRupture(r);
				//			  if(rup.getProbability() > 0.99) System.out.println("Problem!");
				double expected = -Math.log(1-rup.getProbability());
				//			  double rand = 0.99;
				double rand = Math.random();
				double sum =0;
				double factoral = 1;
				int maxNum = (int) Math.round(10*expected)+2;
				int num;
				for(num=0; num <maxNum; num++) {
					if(num != 0) factoral *= num;
					double prob = Math.pow(expected, num)*Math.exp(-expected)/factoral;
					sum += prob;
					if(rand <= sum) break;
				}
				for(int i=0;i<num;i++) rupList.add((ProbEqkRupture)rup.clone());
				/*			  if(num >0)
				  System.out.println("expected="+expected+"\t"+
					  "rand="+rand+"\t"+
					  "num="+num+"\t"+
					  "mag="+rup.getMag());
				 */			  
			}
		}
		else
			throw new RuntimeException("drawRandomEqkRuptures(): Non poissonsources are not yet supported");
		return rupList;
	}
	
	
	
	/**
	 * This draws a random list of rupture indices.  Non-poisson sources are not yet implemented
	 * @return
	 */
	public ArrayList<Integer> drawRandomEqkRuptureIndices() {
		ArrayList<Integer> rupIndexList = new ArrayList<Integer>();
		//	  System.out.println("New Rupture")
		if(isPoissonian) {
			for(int r=0; r<getNumRuptures();r++) {
				ProbEqkRupture rup = getRupture(r);
				//			  if(rup.getProbability() > 0.99) System.out.println("Problem!");
				double expected = -Math.log(1-rup.getProbability());
				//			  double rand = 0.99;
				double rand = Math.random();
				double sum =0;
				double factoral = 1;
				int maxNum = (int) Math.round(10*expected)+2;
				int num;
				for(num=0; num <maxNum; num++) {
					if(num != 0) factoral *= num;
					double prob = Math.pow(expected, num)*Math.exp(-expected)/factoral;
					sum += prob;
					if(rand <= sum) break;
				}
				for(int i=0;i<num;i++) rupIndexList.add(r);
				/*			  if(num >0)
				  System.out.println("expected="+expected+"\t"+
					  "rand="+rand+"\t"+
					  "num="+num+"\t"+
					  "mag="+rup.getMag());
				 */			  
			}
		}
		else
			throw new RuntimeException("drawRandomEqkRuptures(): Non poissonsources are not yet supported");
		return rupIndexList;
	}
	
	/**
	 * This draws a single rupture index based on the relative probabilities.
	 * @return
	 */
	public int drawSingleRandomEqkRuptureIndex() {
		int numRup = getNumRuptures();
		IntegerPDF_FunctionSampler rupSampler = new IntegerPDF_FunctionSampler(numRup);
		for (int r=0; r< this.getNumRuptures(); r++)
			rupSampler.add((double)r, getRupture(r).getProbability());
		return rupSampler.getRandomInt();
	}

	/**
	 * This draws a single rupture index based on the relative probabilities, 
	 * where the random number is supplied for reproducibility.
	 * 
	 * @param randDouble - a random value between 0 (inclusive) and 1 (exclusive)
	 * @return
	 */
	public int drawSingleRandomEqkRuptureIndex(double randDouble) {
		int numRup = getNumRuptures();
		IntegerPDF_FunctionSampler rupSampler = new IntegerPDF_FunctionSampler(numRup);
		for (int r=0; r< this.getNumRuptures(); r++)
			rupSampler.add((double)r, getRupture(r).getProbability());
		return rupSampler.getRandomInt(randDouble);
	}


	/**
	 * This draws a single rupture index based on relative rupture rates rather than
	 * relative probabilities (e.g., to honor the Gutenberg-Richter distribution
	 * if the probability approaches one or greater at lowest magnitudes).  The actual
	 * duration does not matter here, so a value of 1.0 is applied.
	 * The random number is supplied for reproducibility.
	 * 
	 * @param randDouble - a random value between 0 (inclusive) and 1 (exclusive)
	 * @return
	 */
	public int drawSingleRandomEqkRuptureIndexFromRelativeRates(double randDouble) {
		int numRup = getNumRuptures();
		IntegerPDF_FunctionSampler rupSampler = new IntegerPDF_FunctionSampler(numRup);
		for (int r=0; r< this.getNumRuptures(); r++) {
			double rate = -Math.log(1.0 - getRupture(r).getProbability());
			if(Double.isInfinite(rate))
				throw new RuntimeException("Infinite rate error");
			rupSampler.add((double)r, rate);
		}
		return rupSampler.getRandomInt(randDouble);
	}


	/**
	 * This gets the TectonicRegionType for this source
	 */
	public TectonicRegionType getTectonicRegionType() {
		return tectonicRegionType;
	}


	/**
	 * This allows one to change the default tectonic-region type.  The value must be one of those
	 * defined by the TYPE_* fields of the class org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam.
	 * @param tectonicRegionType
	 */
	public void setTectonicRegionType(TectonicRegionType tectonicRegionType) {
		if(tectonicRegionType == null)
			throw new RuntimeException("tectonicRegionType cannot be set as null");
		this.tectonicRegionType=tectonicRegionType;
	}

	@Override
	public Iterator<ProbEqkRupture> iterator() {
		return getRupturesIterator();
	}
	
	
	/**
	 * this computes the Poisson equivalent total moment rate of the source 
	 * @param duration
	 * @return moRate in Newton-meters/year
	 */
	public double computeEquivTotalMomentRate(double duration) {
		double moRate=0;
		for(ProbEqkRupture rup : this) {
			moRate += MagUtils.magToMoment(rup.getMag())*rup.getMeanAnnualRate(duration);
		}
		return moRate;
	}


	/*
  public IncrementalMagFreqDist computeMagProbDist() {

    ArbDiscrEmpiricalDistFunc distFunc = new ArbDiscrEmpiricalDistFunc();
    ArbitrarilyDiscretizedFunc tempFunc = new ArbitrarilyDiscretizedFunc();
    IncrementalMagFreqDist magFreqDist = null;

    ProbEqkRupture qkRup;
    for(int i=0; i<getNumRuptures(); i++) {
      qkRup = getRupture(i);
      distFunc.set(qkRup.getMag(),qkRup.getProbability());
    }
    // duplicate the distFunce
    for(int i = 0; i < distFunc.getNum(); i++) tempFunc.set(distFunc.get(i));

    // now get the cum dist
    for(int i=tempFunc.getNum()-2; i >=0; i--)
      tempFunc.set(tempFunc.getX(i),tempFunc.getY(i)+tempFunc.getY(i+1));

    // now make the evenly discretized

for(int i = 0; i < distFunc.getNum(); i++)
      System.out.println((float)distFunc.getX(i)+"  "+(float)tempFunc.getX(i)+"  "+(float)distFunc.getY(i)+"  "+(float)tempFunc.getY(i));

    return magFreqDist;
  }
	 */
}
