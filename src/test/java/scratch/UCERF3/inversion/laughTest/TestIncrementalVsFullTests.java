package scratch.UCERF3.inversion.laughTest;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.util.ClassUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.SectionCluster;
import scratch.UCERF3.inversion.SectionClusterList;
import scratch.UCERF3.inversion.SectionCluster.FailureHandler;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;

public class TestIncrementalVsFullTests implements FailureHandler {
	
	private LogicTreeBranch branch;
	private LaughTestFilter laughTest;
	private DeformationModelFetcher fetcher;
	private long failCheckCount = 0;

	@Before
	public void setUp() throws Exception {
		branch = LogicTreeBranch.fromValues(FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		
		FaultModels faultModel = branch.getValue(FaultModels.class);
		DeformationModels deformationModel = branch.getValue(DeformationModels.class);
		
		fetcher = new DeformationModelFetcher(faultModel, deformationModel,
				UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE);
		
		laughTest = LaughTestFilter.getDefault();
	}

	@Test
	public void test() {
		SectionClusterList clusters = new SectionClusterList(fetcher, laughTest);
		
		// this will test that each rejected rupture should indeed be rejected
		for (SectionCluster cluster : clusters) {
			cluster.setFailureHandler(this);
		}
		
		InversionFaultSystemRupSet rupSet = new InversionFaultSystemRupSet(branch,
				clusters, fetcher.getSubSectionList());
		System.out.println("New rup set has "+rupSet.getNumRuptures()+" ruptures.");
		System.out.println("Verified "+failCheckCount+" failures");
		
		// now test that every included rupture should indeed be included
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			List<? extends FaultSection> rupture = rupSet.getFaultSectionDataForRupture(r);
			for (AbstractLaughTest test : laughTest.getLaughTests())
				assertTrue("Rupture included but fails "+ClassUtils.getClassNameWithoutPackage(test.getClass()),
						test.doesRupturePass(rupture));
		}
		
		System.out.println("Verified "+rupSet.getNumRuptures()+" passes");
	}



	@Override
	public void ruptureFailed(List<FaultSection> rupture,
			boolean continuable) {
		// make sure that is really does fail
		boolean pass = true;
		boolean cont = true;
		for (AbstractLaughTest test : laughTest.getLaughTests()) {
			if (!test.doesRupturePass(rupture)) {
				pass = false;
				if (!test.isContinueOnFaulure()) {
					cont = false;
					break;
				}
			}
		}
		
		failCheckCount++;
		
		assertFalse("Rupture was rejected but should not have been!", pass);
		assertEquals("Inconsistencies with continuable", cont, continuable);
	}

}
