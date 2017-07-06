package org.opensha.commons.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

public class IconGen {
	
	private static final String resourcesDir = "/resources/images";
	private static final String shareURL = "http://opensha.usc.edu/shared";
	
	private Map<int[], ? extends Image> backgrounds;
	private String text;
	private String fontName;
	private Color outlineColor;
	private Color fillColor;
	private Image upperRight = null;
	
	private static HashMap<int[], BufferedImage> logoBackground = null;
	
	private static HashMap<int[], ? extends Image> wrapInMap(Image image) {
		HashMap<int[], Image> map = new HashMap<int[], Image>();
		
		int[] size = { image.getWidth(null), image.getHeight(null) };
		map.put(size, image);
		
		return map;
	}
	
	public IconGen(Image background, String text, String fontName, Color fillColor, Color outlineColor) {
		this(wrapInMap(background), text, fontName, fillColor, outlineColor);
	}
	
	public IconGen(Map<int[], ? extends Image> backgrounds, String text, String fontName, Color fillColor, Color outlineColor) {
		this.backgrounds = backgrounds;
		if (text.length() > 3)
			System.err.println("WARNING: Text might render off image!");
		this.text = text;
		this.fontName = fontName;
		this.outlineColor = outlineColor;
		this.fillColor = fillColor;
	}
	
	public static BufferedImage loadLocalIcon() throws IOException {
		return ImageIO.read(IconGen.class.getResourceAsStream(resourcesDir+"/icons/laptop.png"));
	}
	
	public static BufferedImage loadServerIcon() throws IOException {
		return ImageIO.read(IconGen.class.getResourceAsStream(resourcesDir+"/icons/server.png"));
	}
	
	public static HashMap<int[], BufferedImage> loadLogoIcon() throws IOException {
		if (logoBackground == null) {
			logoBackground = new HashMap<int[], BufferedImage>();
			int[] sizes = { 16, 32, 64, 128 };
			for (int width : sizes) {
				int[] size = { width, width };
				String url = shareURL+"/opensha_"+width+".png";
				logoBackground.put(size, ImageIO.read(new URL(url)));
			}
		}
		return logoBackground;
	}
	
	public void setUpperRightImage(Image upperRight) {
		this.upperRight = upperRight;
	}
	
	private Image getBackground(int width, int height) {
		Image biggest = null;
		int[] biggestSize = null;
		for (int[] size : backgrounds.keySet()) {
			if (size[0] == width && size[1] == height)
				return backgrounds.get(size);
			if (biggestSize == null || size[0] > biggestSize[0]) {
				biggestSize = size;
				biggest = backgrounds.get(size);
			}
		}
		return biggest;
	}
	
	public BufferedImage getIcon(int width, int height) {
		System.out.println("Creating icon with dimensions " + width + "x" + height);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		
		// background
		g.drawImage(getBackground(width, height), 0, 0, width, height, null);
		
		// upper right image
		if (upperRight != null) {
			int urWidth = (int)(width*0.5 + 0.5);
			int urX = (int)(width*0.5 + 0.5);
			int urY = (int)(height*0.5 - urWidth + 0.5);
			System.out.println("placing image at " + urX + "," + urY + ", width=" + urWidth);
			g.drawImage(upperRight, urX, urY, urWidth, urWidth, null);
		}
		
		if (text != null && text.trim().length() > 0) {
			text = text.trim();
			int baseSize; // for width 64
			if (text.length() > 2)
				baseSize = 20;
			else
				baseSize = 28;
			// text stuff
			int fontSize = (int)(baseSize * (double)height / 64d + 0.5);
			
			Font font = new Font(fontName, Font.BOLD, fontSize);
			
			boolean drawOutline = width >= 32;
			int disp = 1 * width / 16;
			if (disp < 1)
				disp = 1;
			
			Color textColor;
			if (drawOutline)
				textColor = fillColor;
			else
				textColor = outlineColor;
			
			// draw the main text
			g.setFont(font);
			g.setColor(textColor);
			g.drawString(text, disp, height - disp);
		    
		    // text outline
		    if (drawOutline) {
		    	FontRenderContext frc = g.getFontRenderContext();
			    TextLayout textTl = new TextLayout(text, font, frc);
			    Shape outline = textTl.getOutline(null);
			    g.translate(disp, height - disp);
			    g.setColor(outlineColor);
			    g.draw(outline);
		    }
		}
		
		return image;
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String text = "GMT";
		IconGen gen = new IconGen(loadLogoIcon(), text, Font.SANS_SERIF, Color.WHITE, Color.BLACK);
		gen.setUpperRightImage(loadLocalIcon());
//		gen.setUpperRightImage(loadServerIcon());
		int[] sizes = {16, 32, 64, 128};
		for (int size : sizes) {
			BufferedImage icon = gen.getIcon(size, size);
			ImageIO.write(icon, "png", new File("/tmp/icon_"+size+".png"));
		}
	}

}
