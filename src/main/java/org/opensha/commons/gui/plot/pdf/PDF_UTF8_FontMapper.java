package org.opensha.commons.gui.plot.pdf;

import java.awt.Font;
import java.util.HashMap;

import com.itextpdf.awt.FontMapper;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.pdf.BaseFont;
/** Default class to map awt fonts to BaseFont.
 * @author Paulo Soares
 */

public class PDF_UTF8_FontMapper implements FontMapper {

	/** A representation of BaseFont parameters.
	 */
	public static class BaseFontParameters {
		/** The font name.
		 */
		public String fontName;
		/** The encoding for that font.
		 */
		public String encoding;
		/** The embedding for that font.
		 */
		public boolean embedded;
		/** Whether the font is cached of not.
		 */
		public boolean cached;
		/** The font bytes for ttf and afm.
		 */
		public byte ttfAfm[];
		/** The font bytes for pfb.
		 */
		public byte pfb[];

		/** Constructs default BaseFont parameters.
		 * @param fontName the font name or location
		 */
		public BaseFontParameters(String fontName) {
			this.fontName = fontName;
			//            encoding = BaseFont.CP1252;
			encoding = BaseFont.IDENTITY_H;
			embedded = BaseFont.EMBEDDED;
			cached = BaseFont.CACHED;
		}
	}

	/** Maps aliases to names.
	 */
	private HashMap<String, String> aliases = new HashMap<String, String>();
	/** Maps names to BaseFont parameters.
	 */
	private HashMap<String, BaseFontParameters> mapper = new HashMap<String, BaseFontParameters>();

	public static final String LIBERATION_SANS = "LiberationSans";
	public static final String LIBERATION_SANS_BOLD = "LiberationSans-Bold";
	public static final String LIBERATION_SANS_BOLD_ITALIC = "LiberationSans-BoldItalic";
	public static final String LIBERATION_SANS_ITALIC = "LiberationSans-Italic";
	public static final String LIBERATION_MONO = "LiberationMono";
	public static final String LIBERATION_MONO_BOLD = "LiberationMono-Bold";
	public static final String LIBERATION_MONO_BOLD_ITALIC = "LiberationMono-BoldItalic";
	public static final String LIBERATION_MONO_ITALIC = "LiberationMono-Italic";
	
	static {
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-Regular.ttf").toString(), LIBERATION_SANS);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-Bold.ttf").toString(), LIBERATION_SANS_BOLD);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-BoldItalic.ttf").toString(), LIBERATION_SANS_BOLD_ITALIC);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-Italic.ttf").toString(), LIBERATION_SANS_ITALIC);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-Regular.ttf").toString(), LIBERATION_MONO);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-Bold.ttf").toString(), LIBERATION_MONO_BOLD);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-BoldItalic.ttf").toString(), LIBERATION_MONO_BOLD_ITALIC);
		FontFactory.register(PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-Italic.ttf").toString(), LIBERATION_MONO_ITALIC);
	}
	
	/**
	 * Returns a BaseFont which can be used to represent the given AWT Font
	 *
	 * @param	font		the font to be converted
	 * @return	a BaseFont which has similar properties to the provided Font
	 */

	public BaseFont awtToPdf(Font font) {
		try {
			BaseFontParameters p = getBaseFontParameters(font.getFontName());
			if (p != null)
				return BaseFont.createFont(p.fontName, p.encoding, p.embedded, p.cached, p.ttfAfm, p.pfb);
			String fontKey = null;
			String logicalName = font.getName();

			if (logicalName.equalsIgnoreCase("DialogInput") || logicalName.equalsIgnoreCase("Monospaced") || logicalName.equalsIgnoreCase("Courier")) {

				if (font.isItalic()) {
					if (font.isBold()) {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-BoldItalic.ttf").toString();
					} else {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-Italic.ttf").toString();
					}

				} else {
					if (font.isBold()) {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-Bold.ttf").toString();
					} else {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationMono-Regular.ttf").toString();
					}
				}

			} else if (logicalName.equalsIgnoreCase("Serif") || logicalName.equalsIgnoreCase("TimesRoman")) {

				if (font.isItalic()) {
					if (font.isBold()) {
						fontKey = BaseFont.TIMES_BOLDITALIC;

					} else {
						fontKey = BaseFont.TIMES_ITALIC;
					}

				} else {
					if (font.isBold()) {
						fontKey = BaseFont.TIMES_BOLD;

					} else {
						fontKey = BaseFont.TIMES_ROMAN;
					}
				}

			} else {  // default, this catches Dialog and SansSerif

				if (font.isItalic()) {
					if (font.isBold()) {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-BoldItalic.ttf").toString();
					} else {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-Italic.ttf").toString();
					}

				} else {
					if (font.isBold()) {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-Bold.ttf").toString();
					} else {
						fontKey = PDF_UTF8_FontMapper.class.getResource("fonts/LiberationSans-Regular.ttf").toString();
					}
				}
			}
//			return BaseFont.createFont(fontKey, BaseFont.CP1252, false);
//			System.out.println("FontKey: "+fontKey);
			
			BaseFont ret;
			try {
				ret = BaseFont.createFont(fontKey, BaseFont.IDENTITY_H, true);
			} catch(Exception e) {
				System.err.println("WARNING: failed to create unicode compatible font with key="+fontKey);
				ret = BaseFont.createFont(fontKey, BaseFont.CP1252, false);
			}
			return ret;
		}
		catch (Exception e) {
			throw new ExceptionConverter(e);
		}
	}

	/**
	 * Returns an AWT Font which can be used to represent the given BaseFont
	 *
	 * @param	font		the font to be converted
	 * @param	size		the desired point size of the resulting font
	 * @return	a Font which has similar properties to the provided BaseFont
	 */

	public Font pdfToAwt(BaseFont font, int size) {
		String names[][] = font.getFullFontName();
		if (names.length == 1)
			return new Font(names[0][3], 0, size);
		String name10 = null;
		String name3x = null;
		for (int k = 0; k < names.length; ++k) {
			String name[] = names[k];
			if (name[0].equals("1") && name[1].equals("0"))
				name10 = name[3];
			else if (name[2].equals("1033")) {
				name3x = name[3];
				break;
			}
		}
		String finalName = name3x;
		if (finalName == null)
			finalName = name10;
		if (finalName == null)
			finalName = names[0][3];
		return new Font(finalName, 0, size);
	}

	/** Looks for a BaseFont parameter associated with a name.
	 * @param name the name
	 * @return the BaseFont parameter or <CODE>null</CODE> if not found.
	 */
	public BaseFontParameters getBaseFontParameters(String name) {
		String alias = aliases.get(name);
		if (alias == null)
			return mapper.get(name);
		BaseFontParameters p = mapper.get(alias);
		if (p == null)
			return mapper.get(name);
		else
			return p;
	}
}
