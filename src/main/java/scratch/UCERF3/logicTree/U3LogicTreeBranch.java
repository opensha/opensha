package scratch.UCERF3.logicTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.ClassUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;

/**
 * Stores LogicTreeBranch choices. Each node is an enum which implements the LogicTreeBranchNode interface.
 * 
 * @author kevin
 *
 */
public class U3LogicTreeBranch extends LogicTreeBranch<LogicTreeBranchNode<?>>
implements XMLSaveable {
//public class LogicTreeBranch implements Iterable<LogicTreeBranchNode<? extends Enum<?>>>,
//	Cloneable, Serializable, Comparable<LogicTreeBranch>, XMLSaveable {
	
	public static final String XML_METADATA_NAME = "LogicTreeBranch";
	
	/**
	 * This is the default UCERF3 reference branch
	 */
	public static final U3LogicTreeBranch DEFAULT = fromValues(FaultModels.FM3_1, DeformationModels.ZENGBB,
			ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_7p9,
			MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
	
	/**
	 * This is the default UCERF2 reference branch
	 */
	public static final U3LogicTreeBranch UCERF2 = fromValues(FaultModels.FM2_1, DeformationModels.UCERF2_ALL,
			ScalingRelationships.AVE_UCERF2, SlipAlongRuptureModels.UNIFORM, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_6p5,
			MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);
	// this one is when we are just using UCERF2 DM
//	public static final LogicTreeBranch UCERF2 = fromValues(FaultModels.FM2_1, DeformationModels.UCERF2_ALL,
//			ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_8p7,
//			MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF3);
	
	/**
	 * This is the Mean UCERF3 reference branch
	 */
	public static U3LogicTreeBranch getMEAN_UCERF3(FaultModels fm) {
		return getMEAN_UCERF3(fm, DeformationModels.MEAN_UCERF3);
	}
	
	public static U3LogicTreeBranch getMEAN_UCERF3(FaultModels fm, DeformationModels dm) {
		return fromValues(fm, dm, ScalingRelationships.MEAN_UCERF3,
				SlipAlongRuptureModels.MEAN_UCERF3, InversionModels.CHAR_CONSTRAINED, TotalMag5Rate.RATE_6p5,
				MaxMagOffFault.MAG_7p6, MomentRateFixes.NONE, SpatialSeisPDF.UCERF2);
	}
	
	private static List<Class<? extends LogicTreeBranchNode<?>>> logicTreeClasses;
	private static List<LogicTreeLevel<? extends LogicTreeBranchNode<?>>> levels;
	
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
		
	public static synchronized List<LogicTreeLevel<? extends LogicTreeBranchNode<?>>> getLogicTreeLevels() {
		if (levels == null) {
			levels = new ArrayList<>();
			for (Class<? extends LogicTreeBranchNode<?>> clazz : getLogicTreeNodeClasses()) {
				LogicTreeBranchNode<?> value0 = clazz.getEnumConstants()[0];
				LogicTreeLevel<LogicTreeBranchNode<?>> level = LogicTreeLevel.forEnumUnchecked(
						value0, value0.getBranchLevelName(), value0.getShortBranchLevelName());
				levels.add(level);
			}
		}
		
		return levels;
	}
	
	private static Table<Class<? extends LogicTreeBranchNode<?>>, InversionModels, Double> classWeightTotals;
	
	protected U3LogicTreeBranch(U3LogicTreeBranch branch) {
		super(branch);
	}
	
	protected U3LogicTreeBranch(List<LogicTreeBranchNode<?>> branch) {
		super(getLogicTreeLevels(), branch);
	}
	
	protected U3LogicTreeBranch() {
		super();
	}
	
	public int getNumAwayFrom(U3LogicTreeBranch branch) {
		return super.getNumAwayFrom(branch);
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
	 * Builds a file name using the encodeChoiceString method on each branch value, separated by undercores.
	 * Can be parsed with fromFileName(String).
	 * @return
	 */
	public String buildFileName() {
		String str = null;
		for (int i=0; i<size(); i++) {
			LogicTreeBranchNode<?> value = getValue(i);
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
	public static U3LogicTreeBranch fromValues(List<LogicTreeBranchNode<?>> vals) {
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
	public static U3LogicTreeBranch fromValues(LogicTreeBranchNode<?>... vals) {
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
	public static U3LogicTreeBranch fromValues(boolean setNullToDefault, LogicTreeBranchNode<?>... vals) {
		List<Class<? extends LogicTreeBranchNode<?>>> classes = getLogicTreeNodeClasses();
		
		// initialize branch with null
		List<LogicTreeBranchNode<?>> values = Lists.newArrayList();
		for (int i=0; i<classes.size(); i++)
			values.add(null);
		
		// now add each value
		for (LogicTreeBranchNode<?> val : vals) {
			if (val == null)
				continue;
			
			// find the class
			@SuppressWarnings("rawtypes")
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
			values.set(ind, val);
		}
		
		U3LogicTreeBranch branch = new U3LogicTreeBranch(values);
		
		if (setNullToDefault) {
			// little fault model hack, since fault model can be dependent on deformation model if DM is specified
			if (branch.getValue(FaultModels.class) == null && branch.getValue(DeformationModels.class) != null) {
				int fmIndex = getLogicTreeNodeClasses().indexOf(FaultModels.class);
				DeformationModels dm = branch.getValue(DeformationModels.class);
				FaultModels defaultFM = DEFAULT.getValue(FaultModels.class);
				if (dm.getApplicableFaultModels().contains(defaultFM))
					branch.setValue(fmIndex, defaultFM);
				else
					branch.setValue(fmIndex, dm.getApplicableFaultModels().get(0));
			}
			for (int i=0; i<classes.size(); i++) {
				if (branch.getValue(i) == null)
					branch.setValue(i, DEFAULT.getValue(i));
			}
		}
		
		return branch;
	}
	
	/**
	 * Parses a list of strings to build a LogicTreeBranch. Strings should match the result
	 * encodeStringValue() for each node. Any missing or unmatched values are left as null.
	 * @param strings
	 * @return
	 */
	public static U3LogicTreeBranch fromStringValues(List<String> strings) {
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
	public static U3LogicTreeBranch fromFileName(String fileName) {
		List<Class<? extends LogicTreeBranchNode<?>>> classes = getLogicTreeNodeClasses();
		List<LogicTreeBranchNode<?>> branch = Lists.newArrayList();
		
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
		return new U3LogicTreeBranch(branch);
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
	@SuppressWarnings("unchecked")
	public static <E extends Enum<E>> E parseValue(Class<? extends LogicTreeBranchNode<E>> clazz, String str) {
		LogicTreeBranchNode<E>[] options = clazz.getEnumConstants();
		for (LogicTreeBranchNode<E> option : options)
			if (doesStringContainOption(option, str))
				return (E)option;
		return null;
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
		for (LogicTreeBranchNode<?> val : this) {
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
		U3LogicTreeBranch br = fromFileName(str);
		
		FaultModels fm = parseValue(FaultModels.class, str);
		System.out.println("FM? "+fm);
		
//		for (Class<? extends LogicTreeBranchNode<?>> clazz : getLogicTreeNodeClasses())
//			System.out.println(clazz+"\t?\t"+br.getValue(clazz));
		
		System.out.println("Num away? "+br.getNumAwayFrom(br));
		
		U3LogicTreeBranch br2 = fromFileName(str);
		System.out.println(br2);
		System.out.println("Num away? "+br.getNumAwayFrom(br2));
		br2.setValue(FaultModels.FM3_2);
		System.out.println(br2);
		System.out.println("Num away? "+br.getNumAwayFrom(br2));
	}

	@Override
	public Object clone() {
		List<LogicTreeBranchNode<?>> newBranches = Lists.newArrayList();
		
		for (int i=0; i<size(); i++)
			newBranches.add(getValue(i));
		
		return new U3LogicTreeBranch(newBranches);
	}
	
	public U3LogicTreeBranch copy() {
		return new U3LogicTreeBranch(this);
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
		for (LogicTreeBranchNode<?> node : this)
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
		@SuppressWarnings("rawtypes")
		Class<? extends LogicTreeBranchNode> clazz = getEnumEnclosingClass(node.getClass());
		return node.getRelativeWeight(im) / getClassWeightTotal(clazz, im);
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
	
	public static U3LogicTreeBranch fromXMLMetadata(Element branchEl) {
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
		List<LogicTreeBranchNode<?>> branchList = Lists.newArrayList();
		
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
		
		U3LogicTreeBranch branch = new U3LogicTreeBranch(branchList);
		
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
