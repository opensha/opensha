package org.opensha.commons.util;

import java.awt.Desktop;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.google.common.base.Throwables;

import edu.stanford.ejalbert.BrowserLauncher;

/**
 * This util class
 * 
 * @author Kevin
 *
 */
public class BrowserUtils {
	
	public static void launch(String url) {
		try {
			launch(new URL(url));
		} catch (MalformedURLException e) {
			Throwables.propagate(e);
		}
	}
	
	public static void launch(URL url) {
		try {
			launch(url.toURI());
		} catch (URISyntaxException e) {
			Throwables.propagate(e);
		}
	}
	
	public static void launch(URI uri) {
		try {
			Desktop.getDesktop().browse(uri);
		} catch (Throwable t) {
			// use BrowserLauncher2 as a fallback
			try {
				new BrowserLauncher().openURLinBrowser(uri.toString());
			} catch (Throwable t2) {
				t2.printStackTrace();
			}
		}
	}

}
