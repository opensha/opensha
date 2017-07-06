package org.opensha.commons.hpc.mpj;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;

public class MPJExpressShellScriptWriter extends JavaShellScriptWriter {
	
	public static final String XML_METADATA_NAME = "MPJShellScriptWriter";
	
	private static Device DEVICE_DEFAULT = Device.HYBDEV;
	public static enum Device {
		NIODEV("niodev"),
		HYBDEV("hybdev");
		
		private String name;
		private Device(String name) {
			this.name = name;
		}
		
		public String getDeviceName() {
			return name;
		}
	}
	
	private File mpjHome;
	private Device device;
	
	private MPJExpressShellScriptWriter(JavaShellScriptWriter javaWriter,
			File mpjHome, Device device) {
		this(javaWriter.getJavaBin(), javaWriter.getMaxHeapSizeMB(), javaWriter.getClasspath(),
				mpjHome, device);
	}
	
	public MPJExpressShellScriptWriter(File javaBin, int heapSizeMB, Collection<File> classpath,
			File mpjHome) {
		this(javaBin, heapSizeMB, classpath, mpjHome, DEVICE_DEFAULT);
	}
	
	public MPJExpressShellScriptWriter(File javaBin, int heapSizeMB, Collection<File> classpath,
			File mpjHome, Device device) {
		super(javaBin, heapSizeMB, classpath);
		setMpjHome(mpjHome);
		this.device = device;
	}
	
	public void setMpjHome(File mpjHome) {
		Preconditions.checkNotNull(mpjHome, "MPJ_HOME cannot be null!");
		this.mpjHome = mpjHome;
	}

	public File getMpjHome() {
		return mpjHome;
	}

	public void setDevice(Device device) {
		this.device = device;
	}

	public Device getDevice() {
		return device;
	}
	
	@Override
	public List<String> buildScript(List<String> classNames, List<String> argss) {
		ArrayList<String> script = new ArrayList<String>();
		
		script.add("#!/bin/bash");
		script.add("");
		script.add("export MPJ_HOME="+mpjHome.getAbsolutePath());
		script.add("export PATH=$PATH:$MPJ_HOME/bin");
		script.add("");
		script.add("if [[ -e $PBS_NODEFILE ]]; then");
		script.add("  #count the number of processors assigned by PBS");
		script.add("  NP=`wc -l < $PBS_NODEFILE`");
		script.add("  echo \"Running on $NP processors: \"`cat $PBS_NODEFILE`");
		script.add("else");
		script.add("  echo \"This script must be submitted to PBS with 'qsub -l nodes=X'\"");
		script.add("  exit 1");
		script.add("fi");
		script.add("");
		script.add("if [[ $NP -le 0 ]]; then");
		script.add("  echo \"invalid NP: $NP\"");
		script.add("  exit 1");
		script.add("fi");
		script.add("");
		script.add("date");
		script.add("echo \"STARTING MPJ\"");
		script.add("mpjboot $PBS_NODEFILE");
		script.add("");
		script.addAll(getJVMSetupLines());
		
		String dev = getDevice().getDeviceName();
		for (int i=0; i<classNames.size(); i++) {
			script.add("");
			script.add("date");
			script.add("echo \"RUNNING MPJ\"");
			String command = "mpjrun.sh -machinesfile $PBS_NODEFILE -np $NP -dev "+dev+" -Djava.library.path=$MPJ_HOME/lib";
			command += getJVMArgs(classNames.get(i));
			String myArgs = argss.get(i);
			Preconditions.checkState(!myArgs.contains("\n"), "MPJExpress commands don't support newlines (sadly)");
			command += getFormattedArgs(myArgs);
			script.add(command);
		}
		
		script.add("ret=$?");
		script.add("");
		script.add("date");
		script.add("echo \"HALTING MPJ\"");
		script.add("mpjhalt $PBS_NODEFILE");
		script.add("");
		script.add("exit $ret");
		
		return script;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element mpjEl = root.addElement(XML_METADATA_NAME);
		
		mpjEl.addElement("mpjHome", mpjHome.getAbsolutePath());
		mpjEl.addElement("device", device.name());
		
		// add the java args
		super.toXMLMetadata(mpjEl);
		
		return root;
	}
	
	public static MPJExpressShellScriptWriter fromXMLMetadata(Element mpjEl) {
		File mpjHome = new File(mpjEl.attributeValue("mpjHome"));
		Attribute deviceAtt = mpjEl.attribute("device");
		Device device = DEVICE_DEFAULT;
		if (deviceAtt != null)
			device = Device.valueOf(deviceAtt.getValue());
		
		JavaShellScriptWriter javaWriter = JavaShellScriptWriter.fromXMLMetadata(
				mpjEl.element(JavaShellScriptWriter.XML_METADATA_NAME));
		
		return new MPJExpressShellScriptWriter(javaWriter, mpjHome, device);
	}

}
