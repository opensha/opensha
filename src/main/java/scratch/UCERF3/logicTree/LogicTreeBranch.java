package scratch.UCERF3.logicTree;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.ClassUtils;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/**
 * Stores LogicTreeBranch choices. Each node is an enum which implements the LogicTreeBranchNode interface.
 * 
 * @author kevin
 *
 */
public class LogicTreeBranch implements Iterable<LogicTreeBranchNode<? extends Enum<?>>>,
	Cloneable, Serializable, Comparable<LogicTreeBranch>, XMLSaveable {
	
	public static final String XML_METADATA_NAME = "LogicTreeBranch";
	
	/**
	 * This is the default UCERF3 reference branch
	 */
	public static final LogicTreeBranch DEFAULT = fromValues(FaultModels.FM3_1, DeformationModels.ZENGBB,
			ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_7p9,
			MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
	
	/**
	 * This is the default UCERF2 reference branch
	 */
	public static final LogicTreeBranch UCERF2 = fromValues(FaultModels.FM2_1, DeformationModels.UCERF2_ALL,
			ScalingRelationships.AVE_UCERF2, SlipAlongRuptureModels.UNIFORM, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_6p5,
			MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);
	// this one is when we are just using UCERF2 DM
//	public static final LogicTreeBranch UCERF2 = fromValues(FaultModels.FM2_1, DeformationModels.UCERF2_ALL,
//			ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//			MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
	
	/**
	 * This is the Mean UCERF3 reference branch
	 */
	public static LogicTreeBranch getMEAN_UCERF3(FaultModels fm) {
		return getMEAN_UCERF3(fm, DeformationModels.MEAN_UCERF3);
	}
	
	public static LogicTreeBranch getMEAN_UCERF3(FaultModels fm, DeformationModels dm) {
		return fromValues(fm, dm, ScalingRelationships.MEAN_UCERF3,
				SlipAlongRuptureModels.MEAN_UCERF3, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_6p5,
				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);
	}
	
	private static List<Class<? extends LogicTreeBranchNode<?>>> logicTreeClasses;
	
	/**
	 * List of Logic Tree node classes
	 * @return
	 */
	public static synchronized List<Class<? extends LogicTreeBranchNode<?>>> getLogicTreeNodeClasses() {
		if (logicTreeClasses == null) {
			logicTreeClasses = Lists.newArrayList();
			
			logicTreeClasses.add(FaultModels.class);
			logicTreeClasses.add(DeformationModels.class);
			logicTreeClasses.add(ScalingRelationships.class);
			logicTreeClasses.add(SlipAlongRuptureModels.class);
			logicTreeClasses.add(InversionModels.class);
			logicTreeClasses.add(TotalMag5Rate.class);
			logicTreeClasses.add(MaxMagOffFault.class);
			logicTreeClasses.add(MomentRateFixes.class);
			logicTreeClasses.add(SpatialSeisPDF.class);
			
			logicTreeClasses = Collections.unmodifiableList(logicTreeClasses);
		}
		
		return logicTreeClasses;
	}
	
	private static Table<Class<? extends LogicTreeBranchNode<?>>, InversionModels, Double> classWeightTotals;
	
	private List<LogicTreeBranchNode<? extends Enum<?>>> branch;
	
	protected LogicTreeBranch(LogicTreeBranch branch) {
		this(branch.branch);
	}
	
	protected LogicTreeBranch(List<LogicTreeBranchNode<? extends Enum<?>>> branch) {
		this.branch = branch;
	}
	
	@SuppressWarnings("unchecked")
//	public <E extends Enum<?>> E getValue(Class<? extends LogicTreeBranchNode<?>> clazz) {
	public <E extends Enum<E>> E getValue(Class<? extends LogicTreeBranchNode<E>> clazz) {
		return getValue(clazz, branch);
	}
	
	/**
	 * 
	 * @param clazz
	 * @return true of the value for the given class is not null
	 */
	public boolean hasNonNullValue(Class<? extends LogicTreeBranchNode<?>> clazz) {
		return getValueUnchecked(clazz) != null;
	}
	
	/**
	 * Get the value for the given class. This unchecked version can be useful to get around
	 * Java generics issues (use getValue(...) if possible)
	 * @param clazz
	 * @return
	 */
	public LogicTreeBranchNode<?> getValueUnchecked(Class<? extends LogicTreeBranchNode<?>> clazz) {
		return getValueUnchecked(clazz, branch);
	}
	
	private static <E extends Enum<E>> E getValue(Class<? extends LogicTreeBranchNode<E>> clazz,
		List<LogicTreeBranchNode<? extends Enum<?>>> branch) {
	clazz = getEnumEnclosingClass(clazz);
	for (LogicTreeBranchNode<?> node : branch) {
		if (node != null && getEnumEnclosingClass(node.getClass()).equals(clazz)) {
			return (E)node;
		}
	}
	return null;
}
		
	private static LogicTreeBranchNode<?> getValueUnchecked(Class<? extends LogicTreeBranchNode<?>> clazz,
			List<LogicTreeBranchNode<? extends Enum<?>>> branch) {
		clazz = getEnumEnclosingClass(clazz);
		for (LogicTreeBranchNode<?> node : branch) {
			if (node != null && getEnumEnclosingClass(node.getClass()).equals(clazz)) {
				return node;
			}
		}
		return null;
	}
	
	/**
	 * 
	 * @return number of logic tree branch nodes (including nulls)
	 */
	public int size() {
		return branch.size();
	}
	
	/**
	 * 
	 * @param index
	 * @return Logic tree branch node value at the given index
	 */
	public LogicTreeBranchNode<?> getValue(int index) {
		return branch.get(index);
	}
	
	/**
	 * Enums with choices that implement an abstract method create subclasses for each
	 * choice. This ensures that the clazz you are using is an enum parent class and not
	 * a choice's subclass.
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <E extends LogicTreeBranchNode<?>> Class<E> getEnumEnclosingClass(Class<E> clazz) {
		if (!clazz.isEnum())
			clazz = (Class<E>) clazz.getEnclosingClass();
		return clazz;
	}
	
	/**
	 * Sets the value for the given class to null.
	 * @param clazz
	 */
	public void clearValue(Class<? extends LogicTreeBranchNode<?>> clazz) {
		clazz = getEnumEnclosingClass(clazz);
		branch.set(getLogicTreeNodeClasses().indexOf(clazz), null);
	}
	
	/**
	 * Sets the value at the given index to null.
	 * @param index
	 */
	public void clearValue(int index) {
		branch.set(index, null);
	}
	
	/**
	 * Sets the given value in the branch. Cannot be null (use clearValue(clazz)).
	 * @param value
	 */
	public void setValue(LogicTreeBranchNode<?> value) {
		Class<? extends LogicTreeBranchNode> clazz = getEnumEnclosingClass(value.getClass());
		
//		System.out.println("Clazz? "+clazz);
		
		List<Class<? extends LogicTreeBranchNode<?>>> branchClasses = getLogicTreeNodeClasses();
		Preconditions.checkState(branch.size() == branchClasses.size());
		for (int i=0; i<branchClasses.size(); i++) {
			Class<? extends LogicTreeBranchNode<?>> nodeClazz = branchClasses.get(i);
//			System.out.println("testing: "+nodeClazz);
			if (nodeClazz.equals(clazz)) {
				branch.set(i, value);
				return;
			}
		}
		throw new IllegalArgumentException("Class '"+clazz+"' not part of logic tree node classes");
	}
	
	/**
	 * 
	 * @return true if all branch values are non-null
	 */
	public boolean isFullySpecified() {
		for (LogicTreeBranchNode<?> val : branch)
			if (val == null)
				return false;
		return true;
	}
	/**
	 * @param branch
	 * @return the number of logic tree branches that are non null in this branch and differ from the given
	 * branch.
	 */
	public int getNumAwayFrom(LogicTreeBranch o) {
		Preconditions.checkArgument(branch.size() == o.branch.size(), "branch sizes inconsistant!");
		int away = 0;
		
		for (int i=0; i<branch.size(); i++) {
			Object mine = branch.get(i);
			Object theirs = o.branch.get(i);
			
			if (mine != null && !mine.equals(theirs))
				away++;
		}
		
		return away;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((branch == null) ? 0 : branch.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof LogicTreeBranch))
			return false;
		LogicTreeBranch other = (LogicTreeBranch) obj;
		if (branch == null) {
			if (other.branch != null)
				return false;
		} else if (!branch.equals(other.branch))
			return false;
		return true;
	}

	/**
	 * 
	 * @param branch
	 * @return true if every non null value of this branch matches the given branch
	 */
	public boolean matchesNonNulls(LogicTreeBranch branch) {
		return getNumAwayFrom(branch) == 0;
	}
	
	/**
	 * Builds a file name using the encodeChoiceString method on each branch value, separated by undercores.
	 * Can be parsed with fromFileName(String).
	 * @return
	 */
	public String buildFileName() {
		String str = null;
		for (int i=0; i<branch.size(); i++) {
			LogicTreeBranchNode<?> value = branch.get(i);
			if (value == null)
				throw new IllegalStateException("Must be fully specified to build file name! (missing="
					+ClassUtils.getClassNameWithoutPackage(getLogicTreeNodeClasses().get(i))+")");
			if (str == null)
				str = "";
			else
				str += "_";
			str += value.encodeChoiceString();
		}
		return str;
	}
	
	/**
	 * Creates a LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static LogicTreeBranch fromValues(List<LogicTreeBranchNode<?>> vals) {
		LogicTreeBranchNode<?>[] valsArray = new LogicTreeBranchNode[vals.size()];
		
		for (int i=0; i<vals.size(); i++)
			valsArray[i] = vals.get(i);
		
		return fromValues(valsArray);
	}
	
	/**
	 * Creates a LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static LogicTreeBranch fromValues(LogicTreeBranchNode<?>... vals) {
		return fromValues(true, vals);
	}
	
	/**
	 * Creates a LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from LogicTreeBranch.DEFAULT) if setNullToDefault
	 * is true.
	 * 
	 * @param setNullToDefault if true, null or missing values will be set to their default value
	 * @param vals
	 * @return
	 */
	public static LogicTreeBranch fromValues(boolean setNullToDefault, LogicTreeBranchNode<?>... vals) {
		List<Class<? extends LogicTreeBranchNode<?>>> classes = getLogicTreeNodeClasses();
		
		// initialize branch with null
		List<LogicTreeBranchNode<? extends Enum<?>>> branch = Lists.newArrayList();
		for (int i=0; i<classes.size(); i++)
			branch.add(null);
		
		// now add each value
		for (LogicTreeBranchNode<?> val : vals) {
			if (val == null)
				continue;
			
			// find the class
			Class<? extends LogicTreeBranchNode> valClass = getEnumEnclosingClass(val.getClass());
			int ind = -1;
			for (int i=0; i<classes.size(); i++) {
				Class<? extends LogicTreeBranchNode<?>> clazz = classes.get(i);
				if (clazz.equals(valClass)) {
					ind = i;
					break;
				}
			}
			Preconditions.checkArgument(ind >= 0, "Value of class '"+valClass+"' not a valid logic tree branch class");
			branch.set(ind, val);
		}
		
		if (setNullToDefault) {
			// little fault model hack, since fault model can be dependent on deformation model if DM is specified
			if (getValue(FaultModels.class, branch) == null && getValue(DeformationModels.class, branch) != null) {
				int fmIndex = getLogicTreeNodeClasses().indexOf(FaultModels.class);
				DeformationModels dm = getValue(DeformationModels.class, branch);
				FaultModels defaultFM = DEFAULT.getValue(FaultModels.class);
				if (dm.getApplicableFaultModels().contains(defaultFM))
					branch.set(fmIndex, defaultFM);
				else
					branch.set(fmIndex, dm.getApplicableFaultModels().get(0));
			}
			for (int i=0; i<classes.size(); i++) {
				if (branch.get(i) == null)
					branch.set(i, DEFAULT.branch.get(i));
			}
		}
		
		return new LogicTreeBranch(branch);
	}
	
	/**
	 * Parses a list of strings to build a LogicTreeBranch. Strings should match the result
	 * encodeStringValue() for each node. Any missing or unmatched values are left as null.
	 * @param strings
	 * @return
	 */
	public static LogicTreeBranch fromStringValues(List<String> strings) {
		return fromFileName(Joiner.on("_").join(strings));
	}
	
	/**
	 * Parses a file name string to build a LogicTreeBranch. Format should match the result
	 * encodeStringValue() for each node, separated by underscores. Any missing or unmatched
	 * values are left as null.
	 * 
	 * @param fileName
	 * @return
	 */
	public static LogicTreeBranch fromFileName(String fileName) {
		List<Class<? extends LogicTreeBranchNode<?>>> classes = getLogicTreeNodeClasses();
		List<LogicTreeBranchNode<? extends Enum<?>>> branch = Lists.newArrayList();
		
		for (Class<? extends LogicTreeBranchNode<?>> clazz : classes) {
//			LogicTreeBranchNode<?> value = parseValue(clazz, fileName);
			LogicTreeBranchNode<?> value = null;
			LogicTreeBranchNode<?>[] options = clazz.getEnumConstants();
			for (LogicTreeBranchNode<?> option : options) {
				if (doesStringContainOption(option, fileName)) {
					value = option;
					break;
				}
			}
			branch.add(value);
		}
		return new LogicTreeBranch(branch);
	}
	
	private static boolean doesStringContainOption(LogicTreeBranchNode<?> option, String str) {
		String encoded = option.encodeChoiceString();
		if (str.startsWith(encoded+"_") || str.contains("_"+encoded+"_")
				|| str.contains("_"+encoded+".") || str.endsWith("_"+encoded))
			return true;
		return false;
	}
	
	/**
	 * Parse the string value for the given class.
	 * 
	 * @param clazz
	 * @param str
	 * @return
	 */
	public static <E extends Enum<E>> E parseValue(Class<? extends LogicTreeBranchNode<E>> clazz, String str) {
		LogicTreeBranchNode<E>[] options = clazz.getEnumConstants();
		for (LogicTreeBranchNode<E> option : options)
			if (doesStringContainOption(option, str))
				return (E)option;
		return null;
	}
	
	@Override
	public String toString() {
		String str = null;
		for (LogicTreeBranchNode<?> val : branch) {
			if (str == null)
				str = ClassUtils.getClassNameWithoutPackage(getClass())+"[";
			else
				str += ", ";
//			str += ClassUtils.getClassNameWithoutPackage(getEnumEnclosingClass(val.getClass()))+"="+val.getShortName();
			if (val == null)
				str += "(null)";
			else
				str += val.encodeChoiceString();
		}
		return str+"]";
	}
	
	/**
	 * Used for Pre Inversion Analysis
	 * @return
	 */
	public String getTabSepValStringHeader() {
		return "FltMod\tDefMod\tScRel\tSlipAlongMod\tInvModels\tM5Rate\tMmaxOff\tMoRateFix\tSpatSeisPDF";
	}
	
	/**
	 * Used for Pre Inversion Analysis
	 * @return
	 */
	public String getTabSepValString() {
		String str = "";
		boolean first = true;
		for (LogicTreeBranchNode<?> val : branch) {
			if (!first)
				str += "\t";
			else
				first = false;
			if (val == null)
				str += "(null)";
			else
				str += val.getShortName();
		}
		return str;
	}
	
	
	public static void main(String[] args) {
//		String str = "FM3_1_GLpABM_MaEllB_DsrTap_DrEllB_Char";
		String str = DEFAULT.buildFileName();
		System.out.println("PARSING: "+str);
		LogicTreeBranch br = fromFileName(str);
		
		FaultModels fm = parseValue(FaultModels.class, str);
		System.out.println("FM? "+fm);
		
//		for (Class<? extends LogicTreeBranchNode<?>> clazz : getLogicTreeNodeClasses())
//			System.out.println(clazz+"\t?\t"+br.getValue(clazz));
		
		System.out.println("Num away? "+br.getNumAwayFrom(br));
		
		LogicTreeBranch br2 = fromFileName(str);
		System.out.println(br2);
		System.out.println("Num away? "+br.getNumAwayFrom(br2));
		br2.setValue(FaultModels.FM3_2);
		System.out.println(br2);
		System.out.println("Num away? "+br.getNumAwayFrom(br2));
	}

	@Override
	public Iterator<LogicTreeBranchNode<? extends Enum<?>>> iterator() {
		return branch.iterator();
	}

	@Override
	public Object clone() {
		List<LogicTreeBranchNode<? extends Enum<?>>> newBranches = Lists.newArrayList();
		
		for (int i=0; i<size(); i++)
			newBranches.add(branch.get(i));
		
		return new LogicTreeBranch(newBranches);
	}
	
	/**
	 * This returns the normalized branch weight using a priori weights specified in the logic tree branch
	 * node enums.
	 * 
	 * @return
	 */
	public double getAprioriBranchWt() {
		double wt = 1;
		InversionModels im = getValue(InversionModels.class);
		for (LogicTreeBranchNode<?> node : branch)
			wt *= getNormalizedWt(node, im);
		return wt;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static synchronized double getClassWeightTotal(Class<? extends LogicTreeBranchNode> clazz, InversionModels im) {
		if (classWeightTotals == null)
			classWeightTotals = HashBasedTable.create();
		
		Double tot = classWeightTotals.get(clazz, im);
		
		if (tot == null) {
			tot = 0d;
			for (LogicTreeBranchNode<?> val : clazz.getEnumConstants())
				tot += val.getRelativeWeight(im);
			classWeightTotals.put((Class<? extends LogicTreeBranchNode<?>>) clazz, im, tot);
		}
		
		return tot;
	}
	
	/**
	 * @param node
	 * @param im
	 * @return normalized weight for the given node
	 */
	public static double getNormalizedWt(
			LogicTreeBranchNode<? extends Enum<?>> node, InversionModels im) {
		if (node == null)
			return 0d;
		Class<? extends LogicTreeBranchNode> clazz = getEnumEnclosingClass(node.getClass());
		return node.getRelativeWeight(im) / getClassWeightTotal(clazz, im);
	}

	@Override
	public int compareTo(LogicTreeBranch o) {
		for (int i=0; i<getLogicTreeNodeClasses().size(); i++) {
			LogicTreeBranchNode<?> val = getValue(i);
			LogicTreeBranchNode<?> oval = o.getValue(i);
			int cmp = val.getShortName().compareTo(oval.getShortName());
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		Element nodesEl = el.addElement("Nodes");
		
		for (Class<? extends LogicTreeBranchNode<?>> clazz : getLogicTreeNodeClasses()) {
			LogicTreeBranchNode<?> val = getValueUnchecked(clazz);
			if (val == null)
				continue;
			
			String className = ClassUtils.getClassNameWithoutPackage(clazz);
			
			Element nodeEl = nodesEl.addElement("Node");
			
			nodeEl.addAttribute("className", className);
			nodeEl.addAttribute("enumName", val.name());
			nodeEl.addAttribute("longName", val.getName());
			nodeEl.addAttribute("shortName", val.getShortName());
		}
		
		if (this instanceof VariableLogicTreeBranch) {
			VariableLogicTreeBranch variable = (VariableLogicTreeBranch)this;
			
			Element varsEl = el.addElement("Variations");
			
			for (int i=0; i<variable.getVariations().size(); i++) {
				String variation = variable.getVariations().get(i);
				
				Element varEl = varsEl.addElement("Variation");
				
				varEl.addAttribute("index", i+"");
				varEl.addAttribute("variation", variation);
			}
		}
		
		return root;
	}
	
	public static LogicTreeBranch fromXMLMetadata(Element branchEl) {
		Element nodesEl = branchEl.element("Nodes");
		
		// first load in names
		Map<String, String> nodeValMap = Maps.newHashMap();
		Map<String, String> nodeShortNameMap = Maps.newHashMap();
		
		Iterator<Element> nodesIt = nodesEl.elementIterator();
		while (nodesIt.hasNext()) {
			Element nodeEl = nodesIt.next();
			String className = nodeEl.attributeValue("className");
			String enumName = nodeEl.attributeValue("enumName");
			String shortName = nodeEl.attributeValue("shortName");
			
			nodeValMap.put(className, enumName);
			nodeShortNameMap.put(className, shortName);
		}
		
		// now populate branch
		List<Class<? extends LogicTreeBranchNode<?>>> classes = getLogicTreeNodeClasses();
		List<LogicTreeBranchNode<? extends Enum<?>>> branchList = Lists.newArrayList();
		
		for (Class<? extends LogicTreeBranchNode<?>> clazz : classes) {
			String className = ClassUtils.getClassNameWithoutPackage(clazz);
			
			String enumName = nodeValMap.get(className);
			String shortName = nodeShortNameMap.get(className);
			
			if (enumName == null) {
				System.err.println("Warning: Couldn't load "+className+" from logic tree branch XML, no value set");
				branchList.add(null);
				continue;
			}
			
			LogicTreeBranchNode<?> value = null;
			LogicTreeBranchNode<?>[] options = clazz.getEnumConstants();
			for (LogicTreeBranchNode<?> option : options) {
				if (option.name().equals(enumName) || option.getShortName().equals(shortName)) {
					value = option;
					break;
				}
			}
			if (value == null) {
				System.err.println("Warning: Couldn't load "+className+" from logic tree branch XML, " +
						"enum value unknown/changed: "+enumName+" ("+shortName+")");
			}
			branchList.add(value);
		}
		
		LogicTreeBranch branch = new LogicTreeBranch(branchList);
		
		// now look for Variations
		Element varsEl = branchEl.element("Variations");
		if (varsEl != null) {
			Iterator<Element> varsIt = varsEl.elementIterator();
			Map<Integer, String> varIndexMap = Maps.newHashMap();
			
			while (varsIt.hasNext()) {
				Element varEl = varsIt.next();
				Integer index = Integer.parseInt(varEl.attributeValue("index"));
				String variation = varEl.attributeValue("variation");
				varIndexMap.put(index, variation);
			}
			
			if (!varIndexMap.isEmpty()) {
				List<String> variations = Lists.newArrayList();
				for (int i=0; i<varIndexMap.size(); i++) {
					String variation = varIndexMap.get(i);
					Preconditions.checkNotNull(variation, "No variation for index: "+i);
					variations.add(variation);
				}
				branch = new VariableLogicTreeBranch(branch, variations);
			}
		}
		
		return branch;
	}

}
