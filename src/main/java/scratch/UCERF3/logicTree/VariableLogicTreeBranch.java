package scratch.UCERF3.logicTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dom4j.Element;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;


public class VariableLogicTreeBranch extends LogicTreeBranch {
	
	private List<String> variations;

	public VariableLogicTreeBranch(LogicTreeBranch branch, List<String> variations) {
		super(branch);
		this.variations = variations;
	}
	
	public List<String> getVariations() {
		return variations;
	}
	
	public boolean matchesVariation(VariableLogicTreeBranch branch) {
		if (variations != null) {
			List<String> o = branch.getVariations();
			if (o == null)
				return variations.isEmpty();
			if (variations.size()> o.size())
				return false;
			for (int i=0; i<variations.size(); i++) {
				String myVar = variations.get(i);
				if (myVar != null && !variations.get(i).equals(o.get(i)))
					return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((variations == null) ? 0 : variations.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof LogicTreeBranch))
			return false;
		LogicTreeBranch other = (LogicTreeBranch) obj;
		List<String> oVariations;
		if (other instanceof VariableLogicTreeBranch)
			oVariations = ((VariableLogicTreeBranch)other).variations;
		else
			oVariations = null;
		if (variations == null || variations.isEmpty()) {
			if (oVariations != null && !oVariations.isEmpty())
				return false;
		} else if (!variations.equals(oVariations))
			return false;
		return true;
	}
	
	@Override
	public int compareTo(LogicTreeBranch o) {
		int lBranchComp = super.compareTo(o);
		if (lBranchComp != 0)
			return lBranchComp;
		if (!(o instanceof VariableLogicTreeBranch))
			return 1;
		VariableLogicTreeBranch other = (VariableLogicTreeBranch)o;
		if (variations == null) {
			Preconditions.checkState(other.variations == null);
			return 0;
		}
		Preconditions.checkState(other.variations.size() == variations.size(), "Num variations inconsistent!");
		for (int i=0; i<variations.size(); i++) {
			String val = variations.get(i);
			String oval = other.variations.get(i);
			int cmp = val.compareTo(oval);
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}
	
	private static List<String> parseVariations(String name) {
		ArrayList<String> vars = null;
		while (name.contains("_Var")) {
			if (vars == null)
				vars = new ArrayList<String>();
			name = name.substring(name.indexOf("_Var")+4);
			String sub = name;
			if (sub.endsWith(".csv"))
				sub = sub.substring(0, name.indexOf(".csv"));
			if (sub.endsWith("_sol.zip"))
				sub = sub.substring(0, name.indexOf("_sol.zip"));
			if (sub.endsWith("_rates.bin"))
				sub = sub.substring(0, name.indexOf("_rates.bin"));
			if (sub.contains("_Run"))
				sub = sub.substring(0, sub.indexOf("_Run"));
			if (sub.contains("_Var"))
				sub = sub.substring(0, sub.indexOf("_Var"));
			vars.add(sub);
//			System.out.println("VARIATION: "+sub);
		}
		return vars;
	}
	
	public static LogicTreeBranch fromStringValues(List<String> strings) {
		return fromFileName(Joiner.on("_").join(strings));
	}
	
	public static VariableLogicTreeBranch fromFileName(String name) {
		List<String> variations = parseVariations(name);
		LogicTreeBranch branch = LogicTreeBranch.fromFileName(name);
		return new VariableLogicTreeBranch(branch, variations);
	}
	
	@Override
	public String buildFileName() {
		String name = super.buildFileName();
		if (variations != null)
			for (String variation : variations)
				name += "_Var"+variation;
		return name;
	}

	public static void main(String[] args) {
		String name = "FM3_1_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPaleo10_VarSectNuclMFDWt0.01";
//		for (String var : parseVariations(name))
//			System.out.println(var);
		VariableLogicTreeBranch branch = VariableLogicTreeBranch.fromFileName(name);
		for (String var : branch.getVariations())
			System.out.println(var);
		
		// test sorting
		List<LogicTreeBranch> branches = Lists.newArrayList();
		
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_1_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPOISSON_VarABCD"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_1_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarMID_VarABCD"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_1_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarHIGH_VarCB2008"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_1_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPOISSON_Var2190"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_2_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPOISSON_VarABCD"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_2_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarMID_VarABCD"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_2_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarHIGH_VarCB2008"));
		branches.add(VariableLogicTreeBranch.fromFileName("FM3_2_ZENG_HB08_DsrUni_CharConst_M5Rate8.7_MMaxOff7.6_NoFix_SpatSeisU3_VarPOISSON_Var2190"));
		
		Collections.shuffle(branches);
		Collections.sort(branches);
		for (LogicTreeBranch b : branches)
			System.out.println(b.buildFileName());
	}
	
	public static VariableLogicTreeBranch fromXMLMetadata(Element branchEl) {
		LogicTreeBranch branch = LogicTreeBranch.fromXMLMetadata(branchEl);
		Preconditions.checkState(branch instanceof VariableLogicTreeBranch, "Has no variations!");
		return (VariableLogicTreeBranch)branch;
	}
	
}