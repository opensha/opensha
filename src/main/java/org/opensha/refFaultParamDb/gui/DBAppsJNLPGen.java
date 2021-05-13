package org.opensha.refFaultParamDb.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import javax.imageio.ImageIO;

import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.sha.gui.util.JNLPGen;

public class DBAppsJNLPGen {
	
	private static HashMap<int[], BufferedImage> loadLogoIcons() throws MalformedURLException, IOException {
		HashMap<int[], BufferedImage> icons = new HashMap<int[], BufferedImage>();
		
		BufferedImage icon = ImageIO.read(new URL("http://wgcep.org/sites/all/themes/wgcep/favicon.png"));
		
		int[] size = { icon.getWidth(), icon.getHeight() };
		icons.put(size, icon);
		
		return icons;
	}
	
	public static void main(String[] args) throws MalformedURLException, IOException {
		ArrayList<JNLPGen> appsToBuild = new ArrayList<JNLPGen>();
		/*		Hazard Curve				*/
		appsToBuild.add(new JNLPGen(FaultSectionsAndModelsApp.class,
				FaultSectionsAndModelsApp.APP_SHORT_NAME, 
				FaultSectionsAndModelsApp.APP_NAME, "FS", false));
		appsToBuild.add(new JNLPGen(PaleoSiteApp2.class,
				PaleoSiteApp2.APP_SHORT_NAME, 
				PaleoSiteApp2.APP_NAME, "PS", false));
		
		ServerPrefs prefs = ServerPrefUtils.SERVER_PREFS;
		String outputDir = JNLPGen.jnlpDir + File.separator + prefs.getBuildType().getBuildDirName();
		
		HashMap<int[], BufferedImage> icons = loadLogoIcons();
		
		for (JNLPGen app : appsToBuild) {
			app.setServerPrefs(prefs);
			app.generateAppIcons(outputDir, icons);
			app.writeJNLPFile(outputDir);
		}
	}

}
