package scratch.kevin.ucerf3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.FileNameComparator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;

public class TestPDFCombine {
	
	public static void combine(List<File> inputFiles, File outputFile) throws IOException, DocumentException {
		int num = inputFiles.size();
		Preconditions.checkState(num>=2);
		int cols;
		if (num == 2)
			cols = 2;
		else if (num >= 7)
			cols = 2;
		else if (num >= 5)
			cols = 3;
		else
			cols = 2;
		int numAccountedFor = 0;
		int rows = 1;
		int numOnCurRow = 0;
		while (numAccountedFor < num) {
			if (numOnCurRow == cols) {
				numOnCurRow = 0;
				rows++;
			}
			numOnCurRow++;
			numAccountedFor++;
		}
		System.out.println(num+": "+rows+"x"+cols);
		
		double scale = 1d/(double)rows;
		scale *= 0.95;
		
		System.out.println("Scale: "+scale);
		
		boolean rotate = false;
		if (cols > 2) {
			rotate = true;
			scale *= 0.8;
		}
		if (num == 2) {
			rotate = true;
			scale *= 0.65;
		}
		
		combine(inputFiles, outputFile, cols, scale, rotate, 0d, 0d);
	}
	
	public static void combine(List<File> inputFiles, File outputFile, int cols, double scale, boolean rotate,
			double xMarginBuffFract, double yMarginBuffFract)
					throws IOException, DocumentException {
		combine(inputFiles, outputFile, cols, scale, rotate, xMarginBuffFract, yMarginBuffFract, 1.05, 0.95);
	}
	
	public static void combine(List<File> inputFiles, File outputFile, int cols, double scale, boolean rotate,
			double xMarginBuffFract, double yMarginBuffFract, double xPosMult, double yPosMult)
					throws IOException, DocumentException {
		int num = inputFiles.size();
		Preconditions.checkState(num>=2);
		
		int numAccountedFor = 0;
		int rows = 1;
		int numOnCurRow = 0;
		while (numAccountedFor < num) {
			if (numOnCurRow == cols) {
				numOnCurRow = 0;
				rows++;
			}
			numOnCurRow++;
			numAccountedFor++;
		}
		
		Rectangle size = PageSize.LETTER;
		if (rotate)
			size = size.rotate();
//		List<PDPage> pages = Lists.newArrayList();
//		for (File file : inputFiles) {
//			PDDocument part = PDDocument.load(file);
//			pages.add((PDPage) part.getDocumentCatalog().getAllPages().get(0));
//		}
//		
//		int row = 0;
//		int col = 0;
//		
//		PDDocument document = new PDDocument();
//		PDPage combPage = new PDPage();
//		PDPageContentStream contentStream = new PDPageContentStream(document, combPage);
//		contentStream.
		
		Document doc = new Document(size);
		PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(outputFile));
		doc.open();
		PdfContentByte cb = writer.getDirectContent();
		int row = 0;
		int col = 0;
		doc.newPage();
		double maxX = size.getWidth();
		double maxY = size.getHeight();
		for (File file : inputFiles) {
			if (col == cols) {
				col = 0;
				row++;
			}
			System.out.println("Reading "+file.getName());
			PdfReader reader = new PdfReader(new FileInputStream(file));
			PdfImportedPage page = writer.getImportedPage(reader, 1);
//			cb.addTemplate(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
			
			// now flip it so that we're reading from top left
			int myRow = rows-row-1;
			
			double x = maxX * (double)col / (double)cols;
			x *= xPosMult;
			if (cols == 1)
				x = maxX*0.25;
			x += xMarginBuffFract*maxX;
			double y = maxY * (double)myRow / (double)rows;
			y *= yPosMult;
			y += yMarginBuffFract*maxY;
			
			System.out.println("doc at x="+x+", y="+y);
			
			cb.addTemplate(page, (float)scale, 0f, 0f, (float)scale, (float)x, (float)y);
			col++;
		}
		doc.close();
		writer.close();
	}
	
	public static void combine(File pdfDir, File outputDir) throws IOException, DocumentException {
		if (!outputDir.exists())
			outputDir.mkdir();
		HashSet<String> prefixes = new HashSet<String>();
		for (File file : pdfDir.listFiles()) {
			String name = file.getName();
			if (!name.endsWith(".pdf") || !name.contains("_"))
				continue;
			if (name.contains("hist") || name.contains("combined"))
				continue;
			String prefix = name.substring(0, name.indexOf("_"));
			prefixes.add(prefix);
		}
		for (String prefix : prefixes) {
			List<File> files = Lists.newArrayList();
			for (File file : pdfDir.listFiles()) {
				String name = file.getName();
				if (!name.endsWith(".pdf") || !name.startsWith(prefix))
					continue;
				if (name.contains("_hist") || name.contains("diff") || name.contains("combined"))
					continue;
				files.add(file);
			}
			Collections.sort(files, new FileNameComparator());
			System.out.println("Doing "+prefix);
			combine(files, new File(outputDir, prefix+"_combined.pdf"));
		}
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		File rootDir = new File("/home/kevin/OpenSHA/UCERF3/TimeDependent_AVE_ALL/m6.7_30yr/BranchSensitivityMaps");
		File outputDir = new File("/tmp/pdf_combine");
		combine(rootDir, outputDir);
	}

}
