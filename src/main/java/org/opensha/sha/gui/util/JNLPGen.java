package org.opensha.sha.gui.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.imageio.ImageIO;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.opensha.commons.data.siteData.gui.SiteDataCombinedApp;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGeneratorApplet;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.DevStatus;
import org.opensha.commons.util.IconGen;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.gcim.ui.GCIM_HazardCurveApp;
import org.opensha.sha.gui.HazardCurveApplication;
import org.opensha.sha.gui.HazardSpectrumApplication;
import org.opensha.sha.gui.ScenarioShakeMapApp;
import org.opensha.sha.imr.attenRelImpl.gui.AttenuationRelationshipApplet;
import org.opensha.sha.magdist.gui.MagFreqDistApp;

public class JNLPGen {
	
	public static final String jnlpDir = "ant" + File.separator + "jnlp";
	protected static final String webRoot = "http://opensha.usc.edu/apps/opensha";
	
	protected static final int[] icon_sizes = { 128, 64, 48, 32, 16 };
	
	private static final String vendor = "OpenSHA";
	private static final String homepage = "http://www.opensha.org";
	
	private static final String iconsDirName = "icons";
	
	private Class<?> theClass;
	private String shortName;
	private String title;
	private String iconText;
	private int xmxMegs = 1024;
	private ServerPrefs prefs = ServerPrefUtils.SERVER_PREFS;
	private boolean startMenu = true;
	private boolean desktop = true;
	private boolean allowOffline = true;
	
	private ArrayList<IconEntry> icons;
	
	public JNLPGen(Class<?> theClass, String shortName, String title, String iconText, boolean allowOffline) {
		System.out.println("Creating JNLP for: " + theClass.getName());
		this.theClass = theClass;
		this.shortName = shortName;
		this.title = title;
		this.iconText = iconText;
		this.allowOffline = allowOffline;
	}
	
	public void generateAppIcons(String baseDir, HashMap<int[], BufferedImage> logoIcon) throws IOException {
		String iconDir = baseDir + File.separator + iconsDirName;
		File dirFile = new File(iconDir);
		if (!dirFile.exists())
			dirFile.mkdirs();
		
		IconGen gen = new IconGen(logoIcon, iconText, Font.SANS_SERIF, Color.WHITE, Color.BLACK);
		if (allowOffline)
			gen.setUpperRightImage(IconGen.loadLocalIcon());
		else
			gen.setUpperRightImage(IconGen.loadServerIcon());
		icons = new ArrayList<IconEntry>();
		for (int size : icon_sizes) {
			BufferedImage icon = gen.getIcon(size, size);
			String fileName = getIconName(shortName, size);
			ImageIO.write(icon, "png", new File(iconDir + File.separator + fileName));
			icons.add(new IconEntry(iconsDirName + "/" + fileName, size, size));
		}
	}
	
	private void generateAppIcons(String baseDir) throws IOException {
		generateAppIcons(baseDir, IconGen.loadLogoIcon());
	}
	
	protected static String getIconName(String shortName, int size) {
		return shortName + "_" + size + "x" + size + ".png";
	}
	
	public void setServerPrefs(ServerPrefs prefs) {
		this.prefs = prefs;
	}
	
	private DevStatus getDistType() {
		return prefs.getBuildType();
	}
	
	public void setAllowOffline(boolean allowOffline) {
		this.allowOffline = allowOffline;
	}
	
	public void writeJNLPFile() throws IOException {
		writeJNLPFile(jnlpDir);
	}
	
	public void writeJNLPFile(String dir) throws IOException {
		Document doc = createDocument();
		
		File dirFile = new File(dir);
		if (!dirFile.exists())
			dirFile.mkdirs();
		
		String fileName = dir + File.separator + shortName + ".jnlp";
		System.out.println("Writing JNLP to: " + fileName);
		
		XMLUtils.writeDocumentToFile(new File(fileName), doc);
	}
	
	public Document createDocument() {
		Document doc = DocumentHelper.createDocument();
		
		doc.addElement("jnlp");
		Element root = doc.getRootElement();
		
		// root attributes
		root.addAttribute("spec", "6.0+");
		String codeBaseURL = webRoot + "/" + shortName + "/" + getDistType().getBuildDirName();
		root.addAttribute("codebase", codeBaseURL);
		root.addAttribute("href", shortName + ".jnlp");
		
		// information
		Element infoEl = root.addElement("information");
		Element titleEl = infoEl.addElement("title");
		titleEl.addText(title);
		Element vendorEl = infoEl.addElement("vendor");
		vendorEl.addText(vendor);
		// shortcuts
		if (startMenu || desktop) {
			Element shortcutEl = infoEl.addElement("shortcut");
			// online should always be true here because if false, it will never check
			// for updates when launched by a shortcut. it will still run fine if
			// offline if offline-allowed is set.
			shortcutEl.addAttribute("online", "true");
			if (desktop)
				shortcutEl.addElement("desktop");
			if (startMenu) {
				Element menuEl = shortcutEl.addElement("menu");
				menuEl.addAttribute("submenu", vendor);
			}
		}
		infoEl.addElement("homepage").addAttribute("href", homepage);
		if (allowOffline) {
			// offline-allowed
			infoEl.addElement("offline-allowed");
		}
		// icons
		if (icons != null) {
			for (IconEntry icon : icons) {
				icon.toXMLMetadata(infoEl);
			}
		}
		
		// resources
		Element resourcesEl = root.addElement("resources");
		Element javaEl = resourcesEl.addElement("java");
		javaEl.addAttribute("version", "1.6+");
		javaEl.addAttribute("java-vm-args", "-Xmx"+xmxMegs+"M");
		javaEl.addAttribute("href", "http://java.sun.com/products/autodl/j2se");
		Element jarEl = resourcesEl.addElement("jar");
		String jarName = shortName + ".jar";
		jarEl.addAttribute("href", jarName);
		jarEl.addAttribute("main", "true");
		
		// application-desc
		Element appDestEl = root.addElement("application-desc");
		appDestEl.addAttribute("name", title);
		appDestEl.addAttribute("main-class", theClass.getName());
		
		// update
		Element updateEl = root.addElement("update");
		updateEl.addAttribute("check", "timeout");
		
		// security
		Element securityEl = root.addElement("security");
		securityEl.addElement("all-permissions");
		
		return doc;
	}
	
	private class IconEntry implements XMLSaveable {
		
		String url;
		String kind;
		int width;
		int height;
		
		public IconEntry(String url, int width, int height) {
			this(url, width, height, null);
		}
		
		public IconEntry(String url, int width, int height, String kind) {
			this.url = url;
			this.width = width;
			this.height = height;
			this.kind = kind;
		}

		@Override
		public Element toXMLMetadata(Element root) {
			Element iconEl = root.addElement("icon");
			iconEl.addAttribute("href", url);
			iconEl.addAttribute("width", width+"");
			iconEl.addAttribute("height", height+"");
			if (kind != null && kind.length() > 0)
				iconEl.addAttribute("kind", kind);
			return root;
		}
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String outputDir = null;
		ServerPrefs[] prefsToBuild = ServerPrefs.values();
		if (args.length == 0) {
			outputDir = JNLPGen.jnlpDir;
		} else if (args.length == 1 || args.length == 2) {
			outputDir = args[0];
			if (args.length == 2) {
				String buildType = args[1];
				prefsToBuild = new ServerPrefs[1];
				prefsToBuild[0] = ServerPrefs.fromBuildType(buildType);
			}
		} else {
			System.err.println("USAGE: JNLPGen [outputDir [build_type]]");
			System.exit(2);
		}
		ArrayList<JNLPGen> appsToBuild = new ArrayList<JNLPGen>();
		/*		Hazard Curve				*/
		appsToBuild.add(new JNLPGen(GCIM_HazardCurveApp.class,
				GCIM_HazardCurveApp.APP_SHORT_NAME, 
				GCIM_HazardCurveApp.APP_NAME, "GC", true));
		appsToBuild.add(new JNLPGen(HazardCurveApplication.class,
				HazardCurveApplication.APP_SHORT_NAME, 
				HazardCurveApplication.APP_NAME, "HC", true));
		/*		Hazard Spectrum				*/
		appsToBuild.add(new JNLPGen(HazardSpectrumApplication.class,
				HazardSpectrumApplication.APP_SHORT_NAME, 
				HazardSpectrumApplication.APP_NAME, "HS", true));
		/*		Scenario ShakeMap			*/
		appsToBuild.add(new JNLPGen(ScenarioShakeMapApp.class,
				ScenarioShakeMapApp.APP_SHORT_NAME, 
				ScenarioShakeMapApp.APP_NAME, "SM", false));
		/*		Attenuation Relationship	*/
		appsToBuild.add(new JNLPGen(AttenuationRelationshipApplet.class,
				AttenuationRelationshipApplet.APP_SHORT_NAME, 
				AttenuationRelationshipApplet.APP_NAME, "AR", true));
		/*		Magnitude Frequency Dist	*/
		appsToBuild.add(new JNLPGen(MagFreqDistApp.class,
				MagFreqDistApp.APP_SHORT_NAME, 
				MagFreqDistApp.APP_NAME, "MFD", true));
		/*		GMT Map Generator			*/
		appsToBuild.add(new JNLPGen(GMT_MapGeneratorApplet.class,
				GMT_MapGeneratorApplet.APP_SHORT_NAME, 
				GMT_MapGeneratorApplet.APP_NAME, "GMT", false));
		/*		Site Data App				*/
		appsToBuild.add(new JNLPGen(SiteDataCombinedApp.class,
				SiteDataCombinedApp.APP_SHORT_NAME, 
				SiteDataCombinedApp.APP_NAME, "SD", false));
		
		for (ServerPrefs myPrefs : prefsToBuild) {
			String distOutDir = outputDir + File.separator + myPrefs.getBuildType().getBuildDirName();
			for (JNLPGen app : appsToBuild) {
				app.setServerPrefs(myPrefs);
				app.generateAppIcons(distOutDir);
				app.writeJNLPFile(distOutDir);
			}
		}
	}

}
