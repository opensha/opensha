/**
 * 
 */
package scratch.UCERF3.utils.FindEquivUCERF2_Ruptures;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt1;
import scratch.UCERF3.utils.ModUCERF2.ModMeanUCERF2_FM2pt2;

public abstract class FindEquivUCERF2_Ruptures {
	
	protected final static boolean D = false;  // for debugging
	
	protected ERF modifiedUCERF2;	// note that this is a special version (see notes above)
	
	public enum UCERF2_FaultModel {
		FM2_1(11706,	// num ruptures for FM 2.1, found after running this once
				394,	// num sources for FM 2.1
				274),	// num sources for FM 2.1, this is to exclude non-CA B faults
		FM2_2(11886,	// num ruptures for FM 2.2, found after running this once
				397,	// num sources for FM 2.2
				277);	// num sources for FM 2.2, this is to exclude non-CA B faults

		protected int numRuptures, numSources, sourcesToUse;

		private UCERF2_FaultModel(int numRuptures, int numSources, int sourcesToUse) {
			this.numRuptures = numRuptures;
			this.numSources = numSources;
			this.sourcesToUse = sourcesToUse;
		}
	}
	
	protected int NUM_SECTIONS;		// including the SAF Creeping Section
	protected int NUM_INVERSION_RUPTURES;
	
	protected UCERF2_FaultModel ucerf2_fm;
	
	protected List<? extends FaultSection> faultSectionData;
	
	protected FaultSystemRupSet faultSysRupSet;
	
	Hashtable<String,ArrayList<String>> subsectsForSect;
	
	protected final static String SUB_DIR_NAME = "FindEquivUCERF2_Ruptures";
	protected File scratchDir;
	
	public FindEquivUCERF2_Ruptures(FaultSystemRupSet faultSysRupSet, File scratchDir, UCERF2_FaultModel ucerf2_fm) {
		scratchDir = new File(scratchDir, SUB_DIR_NAME);
		this.scratchDir = scratchDir;
		if (!scratchDir.exists())
			scratchDir.mkdir();
		
		this.faultSysRupSet = faultSysRupSet;
		
		this.ucerf2_fm = ucerf2_fm;
		
		faultSectionData = faultSysRupSet.getFaultSectionDataList();
				
		NUM_SECTIONS = faultSectionData.size();
		NUM_INVERSION_RUPTURES = faultSysRupSet.getNumRuptures();
		
		modifiedUCERF2 = buildERF(ucerf2_fm);
	}
	
	protected static UCERF2_FaultModel getUCERF2_FM(FaultModels fm) {
		if (fm == null || fm == FaultModels.FM2_1)
			// UCERF2 fault model
			// only 2.1 used
			return UCERF2_FaultModel.FM2_1;
		else
			if (fm == FaultModels.FM3_1)
				return UCERF2_FaultModel.FM2_1;
			else
				return UCERF2_FaultModel.FM2_2;
	}
	
	
	/**
	 * This tells whether a give section is the first or last in a list of subsections from the parent section
	 * @param sectIndex
	 * @return
	 */
	protected boolean isFirstOrLastSubsectInSect(int sectIndex) {

		if(subsectsForSect == null) {
			subsectsForSect = new Hashtable<String,ArrayList<String>>();
			for(FaultSection data : faultSectionData) {
				if(subsectsForSect.containsKey(data.getParentSectionName())) {
					subsectsForSect.get(data.getParentSectionName()).add(data.getSectionName());
				}
				else {
					ArrayList<String> list = new ArrayList<String>();
					list.add(data.getSectionName());
					subsectsForSect.put(data.getParentSectionName(), list);
				}
			}			
		}
		
		FaultSection sectData = faultSectionData.get(sectIndex);
		ArrayList<String> subSectList = subsectsForSect.get(sectData.getParentSectionName());
		String firstName = subSectList.get(0);
		String lastName = subSectList.get(subSectList.size()-1);
		boolean result = false;
		if(firstName.equals(sectData.getSectionName()))
			result=true;
		else if(lastName.equals(sectData.getSectionName()))
			result=true;
		
		return result;

	}

	
	/**
	 * This generates the UCERF2 instance used here (for a specific set of adjustable params).
	 * @return
	 */
	public static ERF buildERF(FaultModels fm) {
		return buildERF(getUCERF2_FM(fm));
	}
	
	/**
	 * This generates the UCERF2 instance used here (for a specific set of adjustable params).
	 * @return
	 */
	public static ERF buildERF(UCERF2_FaultModel fm) {
		ERF modifiedUCERF2;
		if(D) System.out.println("Instantiating UCERF2 "+fm);
		if(fm == UCERF2_FaultModel.FM2_2)
			modifiedUCERF2 = new ModMeanUCERF2_FM2pt2();
		else
			modifiedUCERF2 = new ModMeanUCERF2_FM2pt1();

		modifiedUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_EXCLUDE);
		modifiedUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		modifiedUCERF2.getTimeSpan().setDuration(30.0);
		modifiedUCERF2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.FULL_DDW_FLOATER);
		modifiedUCERF2.updateForecast();
		if(D) System.out.println("Done Instantiating UCERF2 "+fm);

		//			int index=0;
		//			for(ProbEqkSource src: modifiedUCERF2) {
		//				System.out.println(index+"\t"+src.getName());
		//				index +=1;
		//			}

		// Do some tests
		// the following is a weak test to make sure nothering in UCERF2 has changed
		if(modifiedUCERF2.getNumSources() != fm.numSources)
			throw new RuntimeException("Error - wrong number of sources (should be "+fm.numSources+
					" rather than "+modifiedUCERF2.getNumSources()+"); some UCERF2 adj params not set correctly?");
			// another weak test to make sure nothing has changed
		int numUCERF2_Ruptures = 0;
		for(int s=0; s<fm.sourcesToUse; s++){
			//					System.out.println(s+"\t"+modMeanUCERF2_FM2pt1.getSource(s).getName());
			numUCERF2_Ruptures += modifiedUCERF2.getSource(s).getNumRuptures();
		}
		if(numUCERF2_Ruptures != fm.numRuptures)
			throw new RuntimeException("problem with NUM_RUPTURES; something changed?  old="+fm.numRuptures+
					"\tnew="+numUCERF2_Ruptures);
		
		return modifiedUCERF2;
	}
	
	
	/**
	 * This returns the UCERF2 mag and rate for the specified inversion rupture
	 * (or null if there was no association, which will usually be the case).
	 * 
	 * @param invRupIndex - the index of the inversion rupture
	 * @return - double[2] with mag in index 0 and rate in index 1.
	 */
	public abstract ArrayList<double[]> getMagsAndRatesForRuptures();


	/**
	 * This returns the mag and rate for the specified inversion rupture 
	 * (or null if there is no corresponding UCERF2 rupture, which will usually be the case).
	 * The mag and rate are in the double[] object at index 0 and 1, respectively.
	 * 
	 * @return
	 */
	public abstract double[] getMagsAndRatesForRupture(int invRupIndex);
	
	/**
	 * This returns the total number of UCERF2 ruptures 
	 * (adding up the number of ruptures for every source)
	 * @return
	 */
	public final int getNumUCERF2_Ruptures() {
		return ucerf2_fm.numRuptures;
	}
	
	/**
	 * This gives the ProbEqkRupture for the rth UCERF2 rupture (where the index is
	 * relative to the total number given by getNumUCERF2_Ruptures())
	 * @param r
	 * @return
	 */
	public abstract ProbEqkRupture getRthUCERF2_Rupture(int r);
	
	
	/**
	 * This gives the faultSystemRupSet index for the rupture that is equivalent to the 
	 * specified UCERF2 rupture.  The returned value is -1 if there is no equivalent.
	 * @param r
	 * @return
	 */
	public abstract int getEquivFaultSystemRupIndexForUCERF2_Rupture(int r);

	
	/**
	 *  this returns the passed in faultSysRupSet.
	 * @return
	 */
	public final FaultSystemRupSet getFaultSysRupSet() {
		return faultSysRupSet;
	}
	
	public ERF getUCERF2_ERF() {
		return modifiedUCERF2;
	}

}
