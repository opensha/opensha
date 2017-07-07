package org.opensha.ui.util;

import java.awt.Color;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

/**
 * Preferences. Used to store shared and application specific preferences.
 *
 * @author Peter Powers
 * @version $Id: Prefs.java 94 2010-06-29 22:51:30Z pmpowers $
 */
class Prefs {

	private static Preferences prefs;
	private static Map<Class<?>, Preferences> appPrefs;

	private static final String EXPORT_PATH_KEY = "EXPORT_PATH";
	private static final String EXPORT_PATH_DEFAULT = SystemUtils.USER_HOME;
	
	private static final String LEGAL_NOTICE_KEY = "LEGAL_NOTICE";
	private static final String LEGAL_NOTICE_DEFAULT = "0.0.0";

	static {
		prefs = Preferences.userRoot();
		appPrefs = new HashMap<Class<?>, Preferences>();
	}
	
	private static Preferences getAppPrefs(Class<?> c) {
		if (!appPrefs.containsKey(c)) {
			appPrefs.put(c, Preferences.userNodeForPackage(c));
		}
		return appPrefs.get(c);
	}
		
	static void close() {
		try {
			prefs.flush();
		} catch (BackingStoreException bse) {
			// TODO log error
			bse.printStackTrace();
		}
	}

	static String getExportDir() {
		String path = prefs.get(EXPORT_PATH_KEY, EXPORT_PATH_DEFAULT);
		return !(new File(path)).exists() ? EXPORT_PATH_DEFAULT : path;
	}

	static void setExportDir(String path) {
		if (StringUtils.isBlank(path)) return;
		prefs.put(EXPORT_PATH_KEY, path);
	}

//	public static boolean getShowLegal(Class<?> clazz) {
//		if (clazz == null) return LEGAL_NOTICE_DEFAULT;
//		return getAppPrefs(clazz).getBoolean(
//			LEGAL_NOTICE_KEY, LEGAL_NOTICE_DEFAULT);
//	}
//	
	public static void setShowLegal(Class<?> clazz, boolean show) {
		if (clazz == null) return;
		getAppPrefs(clazz).putBoolean(LEGAL_NOTICE_KEY, show);
	}

    public void setColor(String key, Color c) {
    	if (StringUtils.isBlank(key)) return;
    	if (c == null) return;
    	prefs.putInt(key, c.getRGB());
    }
    
	public static Color getColor(String key) {
		if (StringUtils.isBlank(key)) return Color.RED;
		return new Color(prefs.getInt(key,Color.RED.getRGB()));
	}
	
    public void setColor(Class<?> clazz, String key, Color c) {
    	if (clazz == null) return;
    	if (StringUtils.isBlank(key)) return;
    	if (c == null) return;
    	getAppPrefs(clazz).putInt(key, c.getRGB());
    }
    
	public static Color getColor(Class<?> clazz, String key) {
    	if (clazz == null) return Color.RED;
		if (StringUtils.isBlank(key)) return Color.RED;
		return new Color(prefs.getInt(key,Color.RED.getRGB()));
	}

	
}
