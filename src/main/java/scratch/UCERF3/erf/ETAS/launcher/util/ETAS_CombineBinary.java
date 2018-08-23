package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;

import org.opensha.commons.util.ClassUtils;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;

public class ETAS_CombineBinary {

	public static void main(String[] args) throws ZipException, IOException {
		if (args.length < 3) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_CombineBinary.class)
						+" <input1> <input2> [...<inputN>] <output>");
			System.exit(2);
		}
		
		File outputFile = new File(args[args.length-1]);
		File[] inputFiles = new File[args.length-1];
		
		for (int i=0; i<args.length-1; i++) {
			inputFiles[i] = new File(args[i]);
			Preconditions.checkState(inputFiles[i].exists() && inputFiles[i].getName().endsWith(".bin"));
		}
		
		ETAS_CatalogIO.mergeBinary(outputFile, -1, inputFiles);
	}

}
