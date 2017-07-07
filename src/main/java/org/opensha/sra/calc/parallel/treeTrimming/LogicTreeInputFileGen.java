package org.opensha.sra.calc.parallel.treeTrimming;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeDependentEpistemicList;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sra.calc.parallel.MPJ_EAL_Calc;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class LogicTreeInputFileGen {
	
	public static void writeJob(MPJExpressShellScriptWriter writer, File portfolio, File vulnFile,
			File localDir, File remoteDir, String jobName, File outputFile, int mins, int nodes,
			String queue, boolean multiERF)
					throws IOException {
		File inputsFile = new File(remoteDir, jobName+".xml");
		String args = "--vuln-file "+vulnFile.getAbsolutePath();
		if (multiERF)
			args += " --mult-erfs";
		args += " "+portfolio.getAbsolutePath()
				+" "+inputsFile.getAbsolutePath();
		if (outputFile != null)
			args += " "+outputFile.getAbsolutePath();
		
		File jobFile = new File(localDir, jobName+".pbs");
		
		List<String> script = writer.buildScript(MPJ_EAL_Calc.class.getName(), args);
		
		USC_HPCC_ScriptWriter usc = new USC_HPCC_ScriptWriter();
		script = usc.buildScript(script, mins, nodes, 8, queue);
		usc.writeScript(jobFile, script);
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InvocationTargetException 
	 */
	public static void main(String[] args) throws IOException, InvocationTargetException {
		int batchSize = 20;
		int normalJobMins = 600;
		int backgroundJobMins = 1500;
		
//		File localDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/tree_trimming");
//		File remoteDir = new File("/auto/scec-02/kmilner/tree_trimming");
//		
//		File portfolio = new File(remoteDir, "portfolio.csv");
//		File vulnFile = new File(remoteDir, "2011_11_07_VUL06.txt");
		
//		File localDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run2");
//		File remoteDir = new File("/auto/scec-02/kmilner/tree_trimming/run2");
//		
//		File portfolio = new File(remoteDir, "Porter-01-Jan-2012-CEA-Pof2.txt");
//		File vulnFile = new File(remoteDir, "2012_01_01_VUL06.txt");
		
//		File localDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run3");
//		File remoteDir = new File("/auto/scec-02/kmilner/tree_trimming/run3");
//		
//		File portfolio = new File(remoteDir, "Porter-03-Jan-2012-CEA-Pof3.txt");
//		File vulnFile = new File(remoteDir, "2012_01_02_VUL06.txt");
		
//		File localDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run4");
//		File remoteDir = new File("/auto/scec-02/kmilner/tree_trimming/run4");
//		
//		File portfolio = new File(remoteDir, "portfolio_toposlope.txt");
//		File vulnFile = new File(remoteDir, "2012_01_02_VUL06.txt");
		
		File localDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run5_CA99ptc");
		File remoteDir = new File("/auto/scec-02/kmilner/tree_trimming/run5_CA99ptc");
		
		File portfolio = new File(remoteDir, "Porter-23-Jan-2012-CA99ptcPof.txt");
		File vulnFile = new File(remoteDir, "2012_01_02_VUL06.txt");
		
		ScalarIMR[] imrs = { AttenRelRef.CB_2008.instance(null), AttenRelRef.BA_2008.instance(null),
				AttenRelRef.CY_2008.instance(null), AttenRelRef.AS_2008.instance(null)};
		
		ArrayList<File> classpath = new ArrayList<File>();
		classpath.add(new File(remoteDir, "OpenSHA_complete.jar"));
		classpath.add(new File(remoteDir, "commons-cli-1.2.jar"));
		
		MPJExpressShellScriptWriter mpjWrite = new MPJExpressShellScriptWriter(USC_HPCC_ScriptWriter.JAVA_BIN, 2048,
				classpath, USC_HPCC_ScriptWriter.MPJ_HOME);
		
		AbstractEpistemicListERF erfList = new UCERF2_TimeDependentEpistemicList();
		
		for (ScalarIMR imr : imrs) {
			
			File imrLocalDir = new File(localDir, imr.getShortName());
			imrLocalDir.mkdir();
			File imrRemoteDir = new File(remoteDir, imr.getShortName());
			imrRemoteDir.mkdir();
			
			imr.setParamDefaults();
			if (imr.getSiteParams().containsParameter(DepthTo1pt0kmPerSecParam.NAME))
				imr.getSiteParams().setValue(DepthTo1pt0kmPerSecParam.NAME, null);
			if (imr.getSiteParams().containsParameter(DepthTo2pt5kmPerSecParam.NAME))
				imr.getSiteParams().setValue(DepthTo2pt5kmPerSecParam.NAME, null);
			
			erfList.getAdjustableParameterList().getParameter(String.class,
					UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_ONLY);
			
			Document doc = XMLUtils.createDocumentWithRoot();
			Element root = doc.getRootElement();
			
			erfList.toXMLMetadata(root, 0);
			imr.toXMLMetadata(root);
			
			String name = imr.getShortName()+"_backseis";
			
			XMLUtils.writeDocumentToFile(new File(imrLocalDir, name+".xml"), doc);
			File outputFile = new File(imrRemoteDir, name+".txt");
			writeJob(mpjWrite, portfolio, vulnFile, imrLocalDir, imrRemoteDir,
					name, outputFile, backgroundJobMins, 10, "nbns", true);
			
			erfList.getAdjustableParameterList().getParameter(String.class,
					UCERF2.BACK_SEIS_NAME).setValue(UCERF2.BACK_SEIS_EXCLUDE);
			
			ArrayList<Integer> erfIDs = new ArrayList<Integer>();
			for (int i=0; i<erfList.getNumERFs(); i++)
				erfIDs.add(i);
			
			while (!erfIDs.isEmpty()) {
				doc = XMLUtils.createDocumentWithRoot();
				root = doc.getRootElement();
				
				String numStr = erfIDs.get(0)+"";
				while (numStr.length() < (erfList.getNumERFs()+"").length())
					numStr = "0"+numStr;
				
				name = imr.getShortName()+"_"+numStr;
				
				for (int i=0; i<batchSize && !erfIDs.isEmpty(); i++) {
					Element el = root.addElement(MPJ_EAL_Calc.BATCH_ELEMENT_NAME);
					int myID = erfIDs.remove(0);
					erfList.toXMLMetadata(el, myID);
					imr.toXMLMetadata(el);
					
					String myNumStr = myID+"";
					while (myNumStr.length() < (erfList.getNumERFs()+"").length())
						myNumStr = "0"+myNumStr;
					String myName = imr.getShortName()+"_"+myNumStr;
					
					el.addAttribute("outputFile", new File(imrRemoteDir, myName+".txt").getAbsolutePath());
				}
				
				XMLUtils.writeDocumentToFile(new File(imrLocalDir, name+".xml"), doc);
				writeJob(mpjWrite, portfolio, vulnFile, imrLocalDir, imrRemoteDir,
						name, null, normalJobMins, 5, "nbns", true);
			}
		}
	}

}
