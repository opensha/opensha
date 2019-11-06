package org.opensha.commons.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.opensha.commons.data.CSVFile;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

/**
 * Utility class for programmatically generating Markdown pages, e.g. for github READMEs and wikis
 * @author kevin
 *
 */
public class MarkdownUtils {
	
	/**
	 * Builder model for genearting Markdown tables
	 * @author kevin
	 *
	 */
	public static class TableBuilder {
		
		private List<String[]> lines;
		
		private List<String> curLine;
		
		private TableBuilder() {
			lines = new LinkedList<>();
		}
		
		public TableBuilder addLine(List<String> vals) {
			return addLine(vals.toArray(new String[vals.size()]));
		}
		
		public TableBuilder addLine(Object... vals) {
			String[] strs = new String[vals.length];
			for (int i=0; i<vals.length;  i++)
				strs[i] = vals[i].toString();
			return addLine(strs);
		}
		
		public TableBuilder addLine(String... vals) {
			lines.add(vals);
			
			return this;
		}
		
		public TableBuilder initNewLine() {
			if (curLine != null && !curLine.isEmpty())
				finalizeLine();
			curLine = new ArrayList<>();
			return this;
		}
		
		public TableBuilder addColumn(Object val) {
			return addColumn(val.toString());
		}
		
		public TableBuilder addColumn(String val) {
			if (curLine == null)
				initNewLine();
			curLine.add(val);
			return this;
		}
		
		public TableBuilder finalizeLine() {
			Preconditions.checkState(curLine != null && !curLine.isEmpty());
			addLine(curLine);
			curLine = null;
			return this;
		}
		
		public TableBuilder wrap(int maxDataCols, int headerCols) {
			if (curLine != null)
				finalizeLine();
			int curDataCols = lines.get(0).length - headerCols;
			if (curDataCols <= maxDataCols)
				return this;
			int numWraps = (int)Math.ceil((double)curDataCols / (double)maxDataCols);
			int newDataCols = (int)Math.ceil((double)curDataCols/(double)numWraps);
			
			System.out.println("Wrapping data from "+lines.size()+"x"+curDataCols+" to "+(lines.size()*numWraps)+"x"+newDataCols);
			
			List<String[]> newLines = new ArrayList<>(lines.size()*numWraps);
			
			// init new lines with headers if necessary
			for (int i=0; i<lines.size()*numWraps; i++) {
				String[] newLine = new String[headerCols+newDataCols];
				for (int h=0; h<headerCols; h++)
					newLine[h] = lines.get(i % numWraps)[h];
				newLines.add(newLine);
			}
			
			// fill in data
			for (int i=0; i<lines.size(); i++) {
				for (int c=0; c<curDataCols; c++) {
					int row = i + (c/newDataCols)*lines.size();
					int col = headerCols + c%newDataCols;
					newLines.get(row)[col] = lines.get(i)[c];
				}
			}
			
			this.lines = newLines;
			
			return this;
		}
		
		public List<String> build() {
			Preconditions.checkState(lines.size() >= 1);
			
			List<String> strings = new ArrayList<>(lines.size()+1);
			
			for (int i=0; i<lines.size(); i++) {
				strings.add(tableLine(lines.get(i)));
				if (i == 0)
					strings.add(generateTableDashLine(lines.get(i).length));
			}
			
			return strings;
		}
	}
	
	/**
	 * Creates a new TableBuilder instance for generating Markdown tables
	 * @return
	 */
	public static TableBuilder tableBuilder() {
		return new TableBuilder();
	}
	
	public static TableBuilder tableFromCSV(CSVFile<String> csv, boolean boldFirstColumn) {
		TableBuilder table = tableBuilder();
		for (int row=0; row<csv.getNumRows(); row++) {
			List<String> line = new ArrayList<>(csv.getLine(row));
			if (line.isEmpty())
				continue;
			if (boldFirstColumn)
				line.set(0, "**"+line.get(0)+"**");
			table.addLine(line);
		}
		return table;
	}
	
	private static String generateTableDashLine(int numVals) {
		Preconditions.checkState(numVals >= 1);
		String[] vals = new String[numVals];
		for (int i=0; i<vals.length; i++)
			vals[i] = "-----";
		return tableLine(vals).replaceAll(" ", "");
	}
	
	private static String tableLine(String[] vals) {
		Preconditions.checkState(vals.length >= 1);
		StringBuilder line = new StringBuilder().append("| ");
		for (int i=0; i<vals.length; i++) {
			if (i > 0)
				line.append(" | ");
			if (vals[i] != null)
				line.append(vals[i]);
		}
		line.append(" |");
		return line.toString();
	}
	
	/**
	 * Writes the given markdown lines to README.md as well as an accompanying index.html rendering
	 * of that page
	 * @param lines
	 * @param outputDir
	 * @throws IOException
	 */
	public static void writeReadmeAndHTML(List<String> lines, File outputDir) throws IOException {
		File mdFile = new File(outputDir, "README.md");
		// write markdown
		FileWriter fw = new FileWriter(mdFile);
		for (String line : lines)
			fw.write(line+"\n");
		fw.close();

		// write html
		File htmlFile = new File(outputDir, "index.html");
		writeHTML(lines, htmlFile);
	}
	
	/**
	 * Returns the name of the first header (no matter what header level)
	 * @param markdownPage
	 * @return
	 * @throws IOException
	 */
	public static String getTitle(File markdownPage) throws IOException {
		for (String line : Files.readLines(markdownPage, Charset.defaultCharset())) {
			if (line.startsWith("#")) {
				return line.replaceAll("#", "").trim();
			}
		}
		return null;
	}
	
	private static final CharMatcher ALNUM = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
			  .or(CharMatcher.inRange('0', '9')).or(CharMatcher.is('_')).or(CharMatcher.is('-'));
	
	/**
	 * Builds a table of contents with links to all headers. Each header should be unique
	 * @param lines
	 * @param minLevel Minimum header level (number of #'s) for inclusion in TOC
	 * @return
	 */
	public static List<String> buildTOC(List<String> lines, int minLevel) {
		return buildTOC(lines, minLevel, 0);
	}
	
	/**
	 * Builds a table of contents with links to all headers at or above the given minimum header
	 * level (number of #'s) and at or below the given maximum header level. Each header should be unique
	 * @param lines
	 * @param minLevel Minimum header level (number of #'s) for inclusion in TOC
	 * @param maxLevel Maximum header level (number of #'s), or zero for no maximum, for inclusion in TOC
	 * @return
	 */
	public static List<String> buildTOC(List<String> lines, int minLevel, int maxLevel) {
		LinkedList<String> toc = new LinkedList<>();
		
		for (int i=0; i<lines.size(); i++) {
			String line = lines.get(i);
			if (line.startsWith("#")) {
				String headerPart = line.substring(0, line.lastIndexOf('#')+1);
				int level = headerPart.length();
				if (level >= minLevel && (maxLevel <=0 || level <= maxLevel)) {
					String tocLine = "";
					while ((level > minLevel)) {
						tocLine += "  ";
						level--;
					}
					String title = line.substring(headerPart.length()).trim();
					
					String link = null;
					// see if it's just a link, if so use that link rather than a link to the link
//					System.out.println("Looking for links on "+title);
					for (int j=i+1; j<lines.size(); j++) {
						String l2 = lines.get(j).trim();
//						System.out.println(l2);
						if (l2.isEmpty())
							continue;
						if (l2.contains("[(top)]"))
							continue;
						if (l2.startsWith("#"))
							break;
						if (l2.startsWith("[") && l2.contains("](") && l2.endsWith(")")) {
//							System.out.println("Has a link!");
							// it's a link
							if (link == null) {
								link = l2.substring(l2.lastIndexOf("(")+1);
								link = link.substring(0, link.length()-1);
							} else {
								// not the first link
//								System.out.println("not the first link");
								link = null;
								break;
							}
						} else {
							// not a link
//							System.out.println("Has non link!");
							link = null;
							break;
						}
					}
					if (link == null)
						link = "#"+getAnchorName(title);
					tocLine += "* ["+title+"]("+link+")";
					toc.add(tocLine);
				}
			}
		}
		return toc;
	}
	
	/**
	 * 
	 * @param heading
	 * @return the name of the anchor link for a given heading
	 */
	public static String getAnchorName(String heading) {
		while (heading.startsWith("#"))
			heading = heading.substring(1);
		while (heading.contains("&") && heading.contains(";")) {
			int indexAnd = heading.indexOf("&");
			int indexSemi = heading.indexOf(";");
			if (indexSemi > indexAnd) {
				// remove special symbol
				String symbol = heading.substring(indexAnd, indexSemi+1);
				heading = heading.replace(symbol, "");
			}
		}
		heading = heading.trim();
		return ALNUM.retainFrom(heading.toLowerCase().replaceAll(" ", "-")).toLowerCase();
	}
	
	/**
	 * Converts the given Markdown lines to HTML and writes to a file
	 * @param lines
	 * @param outputFile
	 * @throws IOException
	 */
	public static void writeHTML(List<String> lines, File outputFile) throws IOException {
		StringBuilder str = new StringBuilder();
		for (String line : lines) {
			if (line.contains("[") && line.contains("](") &&
					(line.contains("README.md)") || line.contains("README.md#")))
				// replace links to README.md with index.html
				line = line.replace("README.md", "index.html");
			str.append(line).append("\n");
		}
		writeHTML(str.toString(), outputFile);
	}
	
	public static int MAX_WIDTH = 1000;
	
	private static final Pattern htmlEntityPattern = Pattern.compile(
			"&(#[xX][a-fA-F0-9]{1,8}|#[0-9]{1,8}|[a-zA-Z][a-zA-Z0-9]{1,31});");

	
	/**
	 * Converts the given Markdown to HTML and writes to a file
	 * @param markdown
	 * @param outputFile
	 * @throws IOException
	 */
	public static void writeHTML(String markdown, File outputFile) throws IOException {
		List<Extension> extensions = Arrays.asList(TablesExtension.create(), HeadingAnchorExtension.create());
		Parser parser = Parser.builder().extensions(extensions).build();
		Map<String, String> htmlSymbols = new HashMap<>();
		// track any html entities that were explicitly included, so that we can re-escape them after commonmark un-escapes them
		Matcher symbolMatcher = htmlEntityPattern.matcher(markdown);
		while (symbolMatcher.find()) {
			String symbol = symbolMatcher.group();
			if (!htmlSymbols.containsKey(symbol)) {
				String concrete = StringEscapeUtils.unescapeHtml4(symbol);
//				System.out.println(symbol+" => "+concrete);
				htmlSymbols.put(symbol, concrete);
			}
		}
		Node document = parser.parse(markdown);
		HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();
		
		
		FileWriter fw = new FileWriter(outputFile);
		fw.write("<!DOCTYPE html>\n");
		fw.write("<html>\n");
		fw.write("<head>\n");
		fw.write("<style>\n");
		fw.write("table {\n");
		fw.write("\tborder-collapse: collapse\n");
		fw.write("}\n");
		fw.write("table, th, td {\n");
		fw.write("\tborder: 1px solid black\n");
		fw.write("}\n");
		if (MAX_WIDTH > 0) {
			fw.write("body {\n");
			fw.write("\tmax-width:"+MAX_WIDTH+"px;\n");
			fw.write("\tmargin: auto;\n");
			fw.write("}\n");
			fw.write("img {\n");
			fw.write("\tmax-width: 100%;\n");
			fw.write("\tmax-height: 100%;\n");
			fw.write("}\n");
		}
		fw.write("</style>\n");
		fw.write("</head>\n");
		fw.write("<body>\n");
		String html = renderer.render(document);
		for (String symbol : htmlSymbols.keySet())
			html = html.replaceAll(htmlSymbols.get(symbol), symbol);
//		renderer.render(document, fw);
		fw.write(html);
		fw.write("</body>\n");
		fw.write("</html>\n");
		fw.close();
	}
	
	public static void main(String[] args) throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add("");
		lines.add("# Hello!");
		lines.add("");
		lines.add("# Heading 1");
		lines.add("");
		for (int i=0; i<50; i++)
			lines.add("astdstdsggdsagsd\n");
		lines.add("# Heading 2");
		lines.add("");
		lines.add("# Heading 3 test &ge;4");
		lines.add("");
		for (int i=0; i<50; i++)
			lines.add("astdstdsggdsagsd\n");
		lines.add("## Heading 3.1 &phi;&ge;&tau;");
		lines.add("");
		
		lines.addAll(0, buildTOC(lines, 0));
		File outputDir = new File("/tmp/html_test");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		writeReadmeAndHTML(lines, outputDir);
	}

}
