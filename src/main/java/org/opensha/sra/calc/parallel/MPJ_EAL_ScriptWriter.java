package org.opensha.sra.calc.parallel;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeDependentEpistemicList;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.AS_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;

public class MPJ_EAL_ScriptWriter {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		ERF erf = new MeanUCERF2();
//		UCERF2_TimeDependentEpistemicList erf = new UCERF2_TimeDependentEpistemicList();
		int listIndex = 0;
		
//		int startYear = 2012;
		String backSeis = UCERF2.BACK_SEIS_ONLY;
		ScalarIMR imr = new CB_2008_AttenRel(null);
//		ScalarIMR imr = new BA_2008_AttenRel(null);
//		ScalarIMR imr = new AS_2008_AttenRel(null);
//		ScalarIMR imr = new CY_2008_AttenRel(null);
		
		int mins = 500;
		int nodes = 10;
		String queue = "nbns";
		
		File portfolioFile = new File("/home/scec-02/kmilner/tree_trimming/Porter-28-Mar-2012-CEA-proxy-pof.txt");
		File vulnFile = new File("/home/scec-02/kmilner/tree_trimming/2011_11_07_VUL06.txt");
//		String vulnFileName = ""
		
//		erf.getTimeSpan().setStartTime(startYear);
		erf.setParameter(UCERF2.BACK_SEIS_NAME, backSeis);
		
		String jobName = imr.getShortName();
		jobName = new SimpleDateFormat("yyyy_MM_dd").format(new Date())+"-"+jobName;
		jobName += "-only-back";
//		jobName += "-epi-excl-back";
		
		File localDir = new File("/tmp", jobName);
		File remoteDir = new File("/auto/scec-02/kmilner/tree_trimming", jobName);
		
		localDir.mkdir();
		
		imr.setParamDefaults();
		
		ArrayList<File> classpath = new ArrayList<File>();
		classpath.add(new File(remoteDir.getParentFile(), "OpenSHA_complete.jar"));
		classpath.add(new File(remoteDir.getParentFile(), "commons-cli-1.2.jar"));
		
		MPJExpressShellScriptWriter mpjWrite = new MPJExpressShellScriptWriter(USC_HPCC_ScriptWriter.JAVA_BIN, 2048,
				classpath, USC_HPCC_ScriptWriter.MPJ_HOME);
		
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		if (erf instanceof AbstractEpistemicListERF)
			((AbstractEpistemicListERF)erf).toXMLMetadata(root, listIndex);
		else if (erf instanceof AbstractERF)
			((AbstractERF)erf).toXMLMetadata(root);
		else
			throw new RuntimeException("uh oh.");
		imr.toXMLMetadata(root);
		
		String xmlName = jobName+".xml";
		File localXML = new File(localDir, xmlName);
		File remoteXML = new File(remoteDir, xmlName);
		
		XMLUtils.writeDocumentToFile(localXML, doc);
		
		File outputFile = new File(remoteDir, jobName+".txt");
		
		String argz = "--vuln-file "+vulnFile.getAbsolutePath()+" "+portfolioFile.getAbsolutePath()+" "
					+remoteXML.getAbsolutePath()+" "+outputFile.getAbsolutePath();
		
		List<String> script = mpjWrite.buildScript(MPJ_EAL_Calc.class.getName(), argz);
		
		File pbsFile = new File(localDir, jobName+".pbs");
		USC_HPCC_ScriptWriter usc = new USC_HPCC_ScriptWriter();
		script = usc.buildScript(script, mins, nodes, 8, queue);
		usc.writeScript(pbsFile, script);
	}

}
