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

public class FastMPJShellScriptWriter extends JavaShellScriptWriter {
	
	public static final String XML_METADATA_NAME = "MPJShellScriptWriter";
	
	private static Device DEVICE_DEFAULT = Device.NIODEV;
	public static enum Device {
		NIODEV("niodev"),
		MXDEV("mxdev");
		
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
	private boolean useLaunchWrapper = false;
	
	private FastMPJShellScriptWriter(JavaShellScriptWriter javaWriter,
			File mpjHome) {
		this(javaWriter, mpjHome, DEVICE_DEFAULT);
	}
	
	private FastMPJShellScriptWriter(JavaShellScriptWriter javaWriter,
			File mpjHome, Device device) {
		this(javaWriter.getJavaBin(), javaWriter.getMaxHeapSizeMB(), javaWriter.getClasspath(),
				mpjHome, device);
	}
	
	public FastMPJShellScriptWriter(File javaBin, int heapSizeMB, Collection<File> classpath,
			File mpjHome) {
		this(javaBin, heapSizeMB, classpath, mpjHome, DEVICE_DEFAULT);
	}
	
	public FastMPJShellScriptWriter(File javaBin, int heapSizeMB, Collection<File> classpath,
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
	
	public boolean isUseLaunchWrapper() {
		return useLaunchWrapper;
	}

	public void setUseLaunchWrapper(boolean useLaunchWrapper) {
		this.useLaunchWrapper = useLaunchWrapper;
	}

	@Override
	public void setAutoMemDetect(boolean autoMemDetect) {
		Preconditions.checkState(!autoMemDetect, "Not supported for FastMPJ as will only affect node 0");
		super.setAutoMemDetect(autoMemDetect);
	}

	@Override
	public List<String> buildScript(List<String> classNames, List<String> argss) {
		ArrayList<String> script = new ArrayList<String>();
		
		script.add("#!/bin/bash");
		script.add("");
		
		// new lines added to remove host that job starts on from list of
		// supplied nodes
		script.add("NEW_NODEFILE=\"/tmp/${USER}-hostfile-fmpj-${PBS_JOBID}\"");
		script.add("echo \"creating PBS_NODEFILE: $NEW_NODEFILE\"");
		script.add("hname=$(hostname)");
		script.add("if [ \"$hname\" == \"\" ]");
		script.add("then");
		script.add("  echo \"Error getting hostname. Exiting\"");
		script.add("  exit 1");
		script.add("else");
		script.add("  cat $PBS_NODEFILE | sort | uniq | fgrep -v $hname > $NEW_NODEFILE");
		script.add("fi");
		script.add("");
		script.add("export PBS_NODEFILE=$NEW_NODEFILE");

		script.add("export FMPJ_HOME="+mpjHome.getAbsolutePath());
		script.add("export PATH=$PATH:$FMPJ_HOME/bin");
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
		script.addAll(getJVMSetupLines());
		
		String launchCommand;
		if (isUseLaunchWrapper())
			launchCommand = "fmpjrun_errdetect_wrapper.sh";
		else
			launchCommand = "fmpjrun";
 
		String dev = getDevice().getDeviceName();
		for (int i=0; i<classNames.size(); i++) {
			script.add("");
			script.add("date");
			script.add("echo \"RUNNING FMPJ\"");
			String command = launchCommand+" -machinefile $PBS_NODEFILE -np $NP -dev "+dev+" -Djava.library.path=$FMPJ_HOME/lib";
			command += getJVMArgs(" ");
			if (!command.endsWith(" "))
				command += " ";
			command += "-class "+classNames.get(i);
			command += getFormattedArgs(argss.get(i));
			script.add(command);
			
			script.add("ret=$?");
			script.add("");
			script.add("date");
			script.add("echo \"DONE with process "+i+". EXIT CODE: $ret\"");
		}
		
		
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
	
	public static FastMPJShellScriptWriter fromXMLMetadata(Element mpjEl) {
		File mpjHome = new File(mpjEl.attributeValue("mpjHome"));
		Attribute deviceAtt = mpjEl.attribute("device");
		Device device = DEVICE_DEFAULT;
		if (deviceAtt != null)
			device = Device.valueOf(deviceAtt.getValue());
		
		JavaShellScriptWriter javaWriter = JavaShellScriptWriter.fromXMLMetadata(
				mpjEl.element(JavaShellScriptWriter.XML_METADATA_NAME));
		
		return new FastMPJShellScriptWriter(javaWriter, mpjHome, device);
	}

}
