package org.opensha.sha.simulators.writers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.opensha.sha.simulators.RectangularElement;
import org.opensha.sha.simulators.SimulatorElement;

import com.google.common.base.Preconditions;

public class WardFileWriter {
	
	public static void writeToWardFile(File outputFile, List<SimulatorElement> elems) throws IOException {
		FileWriter efw = new FileWriter(outputFile);
		for (SimulatorElement rectElem : elems) {
			Preconditions.checkState(rectElem instanceof RectangularElement, "Only rectangular supported here");
			efw.write(((RectangularElement)rectElem).toWardFormatLine() + "\n");
		}
		efw.close();
	}

}
