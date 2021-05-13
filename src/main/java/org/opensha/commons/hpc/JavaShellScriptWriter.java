package org.opensha.commons.hpc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class JavaShellScriptWriter implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "JavaShellScriptWriter";
	
	private File javaBin;
	private int maxHeapSizeMB;
	private int initialHeapSizeMB = -1;;
	private Collection<File> classpath;
	private boolean headless;
	private Map<String, String> properties;
	
	private int autoMemBufferMB = 5000;
	private boolean autoMemDetect = false;
	
	public JavaShellScriptWriter(File javaBin, int maxHeapSizeMB, Collection<File> classpath) {
		setJavaBin(javaBin);
		this.maxHeapSizeMB = maxHeapSizeMB;
		this.classpath = classpath;
	}
	
	public File getJavaBin() {
		return javaBin;
	}

	public void setJavaBin(File javaBin) {
		Preconditions.checkNotNull(javaBin, "java binary path cannot be null");
		this.javaBin = javaBin;
	}

	public int getMaxHeapSizeMB() {
		return maxHeapSizeMB;
	}

	public void setMaxHeapSizeMB(int maxHeapSizeMB) {
		this.maxHeapSizeMB = maxHeapSizeMB;
	}
	
	public int getInitialHeapSizeMB() {
		return initialHeapSizeMB;
	}
	
	public void setInitialHeapSizeMB(int initialHeapSizeMB) {
		this.initialHeapSizeMB = initialHeapSizeMB;
	}
	
	public boolean isHeadless() {
		return headless;
	}
	
	public void setHeadless(boolean headless) {
		this.headless = headless;
	}

	public Collection<File> getClasspath() {
		return classpath;
	}

	public void setClasspath(Collection<File> classpath) {
		this.classpath = classpath;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperty(String key, String value) {
		if (properties == null)
			properties = Maps.newHashMap();
		properties.put(key, value);
	}
	
	public void clearProperty(String key) {
		properties.remove(key);
	}
	
	public void clearProperties() {
		if (properties != null)
			properties.clear();
	}
	
	public void setAutoMemDetect(boolean autoMemDetect) {
		this.autoMemDetect = autoMemDetect;
	}
	
	public List<String> getJVMSetupLines() {
		List<String> lines = Lists.newArrayList();
		
		if (maxHeapSizeMB > 0 || autoMemDetect)
			lines.add("JVM_MEM_MB="+maxHeapSizeMB);
		if (initialHeapSizeMB > 0)
			lines.add("JVM_INITIAL_MEM_MB="+initialHeapSizeMB);
		if (autoMemDetect) {
			lines.add("AUTO_MEM_BUFFER_MB="+autoMemBufferMB);
			lines.add("echo \"Minimum JVM memory: $JVM_MEM_MB\"");
			lines.add("# detect system memory");
			lines.add("let MEM=`awk '/MemTotal/{print $2}' /proc/meminfo`");
			lines.add("# convert to MB");
			lines.add("let MEM=$MEM/1024");
			lines.add("echo \"System memory: $MEM MB\"");
			lines.add("let MAXMEM=$MEM-$AUTO_MEM_BUFFER_MB");
			lines.add("echo \"Max auto system memory: $MAXMEM MB\"");
			lines.add("if [[ $MAXMEM -gt $JVM_MEM_MB ]];then");
			lines.add("\tJVM_MEM_MB=$MAXMEM");
			lines.add("\techo \"Increased JVM memory to $JVM_MEM_MB MB\"");
			lines.add("else");
			lines.add("\techo \"Keeping minimum JVM MEM of $JVM_MEM_MB MB\"");
			lines.add("fi");
		}
		
		return lines;
	}

	protected String getJVMArgs(String className) {
		Preconditions.checkNotNull(className, "class name cannot be null or empty");
		Preconditions.checkArgument(!className.isEmpty(), "class name cannot be null or empty");
		String cp = "";
		if (classpath != null && !classpath.isEmpty()) {
			cp = " -cp ";
			boolean first = true;
			for (File el : classpath) {
				if (first)
					first = false;
				else
					cp += ":";
				cp += el.getAbsolutePath();
			}
		}
		
		String jvmArgs = "";
		if (headless)
			jvmArgs = " -Djava.awt.headless=true";
		if (properties != null) {
			for (String key : properties.keySet()) {
				String value = properties.get(key);
				Preconditions.checkState(!key.contains(" ") && !value.contains(" "), "no spaces allowed in properties!");
				jvmArgs += " -D"+key+"="+value;
			}
		}
		if (maxHeapSizeMB > 0)
			jvmArgs += " -Xmx${JVM_MEM_MB}M";
		if (initialHeapSizeMB > 0)
			jvmArgs += " -Xms${JVM_INITIAL_MEM_MB}M";
		
		String args = getFormattedArgs(jvmArgs+cp);
		
		return args+" "+className;
	}
	
	public String buildCommand(String className, String args) {
		
		String command = javaBin.getAbsolutePath()+getJVMArgs(className);
		
		command += getFormattedArgs(args);
		
		return command;
	}
	
	/**
	 * @param args
	 * @return arguments with a space in front, or empty string if null/empty
	 */
	protected static String getFormattedArgs(String args) {
		if (args != null && !args.isEmpty()) {
			if (!args.startsWith(" "))
				args = " "+args;
			return args;
		}
		return "";
	}
	
	public List<String> buildScript(String className, String args) {
		return buildScript(Lists.newArrayList(className), Lists.newArrayList(args));
	}
	
	public List<String> buildScript(List<String> classNames, List<String> argss) {
		ArrayList<String> script = new ArrayList<String>();
		
		Preconditions.checkArgument(classNames.size() == argss.size());
		
		script.add("#!/bin/bash");
		script.add("");
		script.addAll(getJVMSetupLines());
		for (int i=0; i<classNames.size(); i++) {
			script.add("");
			script.add(buildCommand(classNames.get(i), argss.get(i)));
			if (i < classNames.size()-1) {
				// not last
				script.add("if [[ $? -ne 0 ]];then");
				script.add("\texit $?");
				script.add("fi");
			}
		}
		script.add("exit $?");
		
		return script;
	}
	
	public static void writeScript(File file, List<String> script) throws IOException {
		FileWriter fw = new FileWriter(file);
		
		for (String line: script)
			fw.write(line + "\n");
		
		fw.close();
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		el.addAttribute("javaBin", javaBin.getAbsolutePath());
		el.addAttribute("heapSizeMB", maxHeapSizeMB+"");
		
		if (classpath != null && !classpath.isEmpty()) {
			Element cpEl = el.addElement("Classpath");
			for (File cp : classpath) {
				Element cpElEl = cpEl.addElement("element");
				cpElEl.addAttribute("path", cp.getAbsolutePath());
			}
		}
		
		return root;
	}
	
	public static JavaShellScriptWriter fromXMLMetadata(Element javaEl) {
		File javaBin = new File(javaEl.attributeValue("javaBin"));
		int heapSizeMB = Integer.parseInt(javaEl.attributeValue("heapSizeMB"));
		
		Element cpEl = javaEl.element("Classpath");
		ArrayList<File> classpath = null;
		if (cpEl != null) {
			Iterator<Element> it = cpEl.elementIterator("element");
			classpath = new ArrayList<File>();
			while (it.hasNext()) {
				Element cpElEl = it.next();
				File cp = new File(cpElEl.attributeValue("path"));
				classpath.add(cp);
			}
		}
		
		return new JavaShellScriptWriter(javaBin, heapSizeMB, classpath);
	}

}
