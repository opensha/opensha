package org.opensha.sra.calc.parallel.treeTrimming;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.NSHMP_2008_CA;
import org.opensha.sra.calc.parallel.MPJ_EAL_Calc;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

public class UCERF3_EAL_ScriptWriter {

	public static void main(String[] args) throws IOException {
		ERF erf = new FaultSystemSolutionERF();
//		UCERF2_TimeDependentEpistemicList erf = new UCERF2_TimeDependentEpistemicList();
		int listIndex = 0;
		
//		int startYear = 2012;
		IncludeBackgroundOption backSeisInclude = IncludeBackgroundOption.ONLY;
		BackgroundRupType backSeisType = BackgroundRupType.POINT;
//		ScalarIMR imr = new CB_2008_AttenRel(null);
//		ScalarIMR imr = new BA_2008_AttenRel(null);
//		ScalarIMR imr = new AS_2008_AttenRel(null);
//		ScalarIMR imr = new CY_2008_AttenRel(null);
		ScalarIMR imr = new NSHMP_2008_CA(null);
		
		int mins = 800;
		int nodes = 20;
		String queue = "nbns";
		
		File portfolioFile = new File("/home/scec-02/kmilner/tree_trimming/Porter-28-Mar-2012-CEA-proxy-pof.txt");
		File vulnFile = new File("/home/scec-02/kmilner/tree_trimming/2011_11_07_VUL06.txt");
		File solFile = new File("/home/scec-02/kmilner/ucerf3/inversion_compound_plots/"
				+ "2013_05_10-ucerf3p3-production-10runs/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
		String jobName = "ucerf3_single_branch_only_point";
//		String vulnFileName = ""
		
//		erf.getTimeSpan().setStartTime(startYear);
		erf.setParameter(IncludeBackgroundParam.NAME, backSeisInclude);
		erf.setParameter(BackgroundRupParam.NAME, backSeisType);
		erf.setParameter(FaultSystemSolutionERF.FILE_PARAM_NAME, solFile);
		
//		String jobName = imr.getShortName();
		jobName = new SimpleDateFormat("yyyy_MM_dd").format(new Date())+"-"+jobName;
//		jobName += "-only-back";
//		jobName += "-epi-excl-back";
		
		File localDir = new File("/tmp", jobName);
		File remoteDir = new File("/home/scec-02/kmilner/tree_trimming", jobName);
		
		localDir.mkdir();
		
		imr.setParamDefaults();
		
		ArrayList<File> classpath = new ArrayList<File>();
		classpath.add(new File(remoteDir.getParentFile(), "OpenSHA_complete.jar"));
		classpath.add(new File(remoteDir.getParentFile(), "commons-cli-1.2.jar"));
		
		FastMPJShellScriptWriter mpjWrite = new FastMPJShellScriptWriter(USC_HPCC_ScriptWriter.JAVA_BIN, 8000,
				classpath, USC_HPCC_ScriptWriter.FMPJ_HOME);
		
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
