package org.opensha.sha.gui.util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServerPrefs;

import com.google.common.base.Stopwatch;

public class IconFetcher {
	
	private static final boolean D = false;
	
	public static ArrayList<BufferedImage> fetchIcons(String appShortName) {
		return fetchIcons(appShortName, ServerPrefUtils.SERVER_PREFS);
	}
	
	public static ArrayList<BufferedImage> fetchIcons(String appShortName, ServerPrefs prefs) {
		ArrayList<BufferedImage> images = new ArrayList<BufferedImage>();
		String localBase = "/resources/images/icons/";
		for (int size : JNLPGen.icon_sizes) {
			URL url = null;
			String fileName = JNLPGen.getIconName(appShortName, size);
			try {
				// first try local images
				url = IconFetcher.class.getResource(localBase+fileName);
			} catch (Throwable t) {}
			if (url == null) {
				// then try internet
				try {
					String addy = JNLPGen.webRoot+"/"+appShortName+"/"+prefs.getBuildType().getBuildDirName()
									+"/icons/"+fileName;
					url = new URL(addy);
				} catch (Throwable t) {}
			}
			
			if (url == null)
				// couldn't load from local and url construction failed
				continue;
			
			if (D) System.out.println("loading icon from: " + url);
			BufferedImage img = loadWithTimeout(url, 2000);
			if (img == null)
				break;
			images.add(img);
		}
		if (images.size() == 0) {
			try {
				BufferedImage fallback = ImageIO.read(IconFetcher.class.getResourceAsStream(
						"/resources/images/logos/opensha_64.png"));
				images.add(fallback);
			} catch (IOException e) {
				// print and return null;
				e.printStackTrace();
				return null;
			}
		}
		return images;
	}
	
	private static class ImageReadThread extends Thread {
		BufferedImage img;
		URL url;
		
		public ImageReadThread(URL url) {
			this.url = url;
		}
		
		@Override
		public void run() {
			try {
				img = ImageIO.read(url);
			} catch (IOException e) {}
		}
	}
	
	private static BufferedImage loadWithTimeout(URL url, long timeout) {
		ImageReadThread t = new ImageReadThread(url);
		
		Stopwatch watch = Stopwatch.createStarted();
		t.start();
		
		while (t.isAlive() && watch.elapsed(TimeUnit.MILLISECONDS) < timeout) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// shouldn't happen, but in case just print it and continue waiting
				e.printStackTrace();
			}
		}
		
		if (t.isAlive()) {
			// exceeded timeout
			try {
				t.interrupt();
			} catch (Exception e) {
				// just print it and continue with failure
				e.printStackTrace();
			}
			return null;
		}
		
		return t.img;
	}
	
	public static void main(String[] args) throws IOException {
		ArrayList<BufferedImage> icons = new ArrayList<BufferedImage>();
		icons.add(ImageIO.read(new URL("http://opensha.usc.edu/apps/opensha/HazardCurveLocal/nightly/icons/HazardCurveLocal_16x16.png")));
		icons.add(ImageIO.read(new URL("http://opensha.usc.edu/apps/opensha/HazardCurveLocal/nightly/icons/HazardCurveLocal_32x32.png")));
		icons.add(ImageIO.read(new URL("http://opensha.usc.edu/apps/opensha/HazardCurveLocal/nightly/icons/HazardCurveLocal_48x48.png")));
		icons.add(ImageIO.read(new URL("http://opensha.usc.edu/apps/opensha/HazardCurveLocal/nightly/icons/HazardCurveLocal_128x128.png")));
		JFrame frame = new JFrame();
		frame.setSize(400, 400);
		frame.setIconImages(icons);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

}
