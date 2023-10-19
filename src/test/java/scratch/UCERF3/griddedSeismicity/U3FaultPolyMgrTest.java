package scratch.UCERF3.griddedSeismicity;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractFaultGridAssociationsTest;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import scratch.UCERF3.inversion.U3InversionConfigFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

public class U3FaultPolyMgrTest extends AbstractFaultGridAssociationsTest {
	
	private static FaultSystemRupSet rupSet;
	private static FaultGridAssociations assoc;
	
	@BeforeClass
	public static void beforeClass() throws IOException {
		U3InversionConfigFactory factory = new U3InversionConfigFactory();
		rupSet = factory.buildRuptureSet(U3LogicTreeBranch.DEFAULT, FaultSysTools.defaultNumThreads());
		assoc = rupSet.requireModule(FaultGridAssociations.class);
	}

	public U3FaultPolyMgrTest() {
		super(rupSet, assoc);
	}

}
