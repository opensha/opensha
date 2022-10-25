package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public class ModuleCopier {

	public static void main(String[] args) throws ZipException, IOException {
		Preconditions.checkArgument(args.length == 2 || args.length == 3, "USAGE: <add-to-file> <add-from-file> [<output-file>]");
		
		File addToFile = new File(args[0]);
		ZipFile addToZip = new ZipFile(addToFile);
		FaultSystemRupSet addToRupSet;
		FaultSystemSolution addToSol;
		if (FaultSystemSolution.isSolution(addToZip)) {
			addToSol = FaultSystemSolution.load(addToZip);
			addToRupSet = addToSol.getRupSet();
		} else {
			addToRupSet = FaultSystemRupSet.load(addToFile);
			addToSol = null;
		}
		
		File addFromFile = new File(args[1]);
		ZipFile addFromZip = new ZipFile(addFromFile);
		FaultSystemRupSet addFromRupSet;
		FaultSystemSolution addFromSol;
		if (FaultSystemSolution.isSolution(addFromZip)) {
			Preconditions.checkNotNull(addToSol);
			addFromSol = FaultSystemSolution.load(addFromZip);
			addFromRupSet = addFromSol.getRupSet();
		} else {
			addFromRupSet = FaultSystemRupSet.load(addFromFile);
			addFromSol = null;
		}
		
		List<OpenSHA_Module> allAdded = new ArrayList<>();
		
		for (OpenSHA_Module module : addFromRupSet.getModules(true)) {
			if (!addToRupSet.hasModuleSuperclass(module.getClass())) {
				// completely new module
				System.out.println("Copying module '"+module.getName()+"' of type "+module.getClass().getName());
				allAdded.add(module);
				addToRupSet.addModule(module);
			}
		}
		
		if (addFromSol != null) {
			for (OpenSHA_Module module : addFromSol.getModules(true)) {
				if (!addToSol.hasModuleSuperclass(module.getClass())) {
					// completely new module
					System.out.println("Copying module '"+module.getName()+"' of type "+module.getClass().getName());
					allAdded.add(module);
					addToSol.addModule(module);
				}
			}
		}
		
		File outputFile = args.length == 3 ? new File(args[2]) : addToFile;
		
		if (addToSol == null)
			addToRupSet.write(outputFile);
		else
			addToSol.write(outputFile);
		
		System.out.println("Summary of added modules:");
		for (OpenSHA_Module module : allAdded)
			System.out.println("\t"+module.getName()+": "+module.getClass().getName());
	}

}
