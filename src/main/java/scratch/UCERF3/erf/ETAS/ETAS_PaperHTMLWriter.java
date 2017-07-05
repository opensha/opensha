package scratch.UCERF3.erf.ETAS;

import java.io.File;
import java.io.IOException;

import org.dom4j.DocumentException;
import org.opensha.commons.util.ClassUtils;

public class ETAS_PaperHTMLWriter {

	public static void main(String[] args) throws IOException, DocumentException {
		if (args.length != 3) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_PaperHTMLWriter.class)
				+" <scen-file> <input-dir> <output-dir>");
			System.exit(2);
		}
		
		ETAS_MultiSimAnalysisTools.writePaperHTML(new File(args[0]), new File(args[1]), new File(args[2]));
	}

}
