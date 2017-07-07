package org.opensha.sra.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.opensha.commons.util.IconGen;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.sha.gui.util.JNLPGen;
import org.opensha.sra.gui.portfolioeal.PortfolioEALCalculatorController;

public class SRA_JNLPGen {

	public static void main(String[] args) throws MalformedURLException, IOException {
		ArrayList<JNLPGen> appsToBuild = new ArrayList<JNLPGen>();
		/*		Hazard Curve				*/
		appsToBuild.add(new JNLPGen(BCR_Application.class,
				BCR_Application.APP_SHORT_NAME, 
				BCR_Application.APP_NAME, "BCR", true));
		appsToBuild.add(new JNLPGen(LossEstimationApplication.class,
				LossEstimationApplication.APP_SHORT_NAME, 
				LossEstimationApplication.APP_NAME, "LEC", true));
		appsToBuild.add(new JNLPGen(PortfolioEALCalculatorController.class,
				PortfolioEALCalculatorController.APP_SHORT_NAME, 
				PortfolioEALCalculatorController.APP_NAME, "PEAL", true));
		
		ServerPrefs prefs = ServerPrefUtils.SERVER_PREFS;
		String outputDir = JNLPGen.jnlpDir + File.separator + prefs.getBuildType().getBuildDirName();
		
		HashMap<int[], BufferedImage> icons = IconGen.loadLogoIcon();
		
		for (JNLPGen app : appsToBuild) {
			app.setServerPrefs(prefs);
			app.generateAppIcons(outputDir, icons);
			app.writeJNLPFile(outputDir);
		}
	}
}
