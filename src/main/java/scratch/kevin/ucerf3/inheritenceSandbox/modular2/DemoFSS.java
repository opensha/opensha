package scratch.kevin.ucerf3.inheritenceSandbox.modular2;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.CSV_BackedModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;

import com.google.common.base.Preconditions;

import scratch.kevin.ucerf3.inheritenceSandbox.modular2.DemoFSRS.DemoInfoModule;

public class DemoFSS extends ModuleContainer<OpenSHA_Module> implements ArchivableModule {

	private ModuleArchive<OpenSHA_Module> archive;
	
	private DemoFSRS rupSet;
	
	private DemoFSS() {
		
	}
	
	private DemoFSS(DemoFSRS rupSet, double[] rates) {
		this();
		this.rupSet = rupSet;
	}
	
	private void setArchive(ModuleArchive<OpenSHA_Module> archive) {
		this.archive = archive;
		if (!archive.hasModule(DemoFSRS.class))
			archive.addModule(this);
	}
	
	public static DemoFSS load(File file) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(file);
		
		DemoFSRS rupSet = archive.getModule(DemoFSRS.class);
		Preconditions.checkState(rupSet != null);
		DemoFSS sol = archive.getModule(DemoFSS.class);
		Preconditions.checkState(sol != null);
		sol.setArchive(archive);
		sol.rupSet = rupSet;
		
		return sol;
	}
	
	public void writeArchive(File file) throws IOException {
		boolean existing = archive != null;
		if (archive == null)
			archive = new ModuleArchive<>();
		if (!archive.hasModule(DemoFSS.class)) {
			archive.addModule(this);
			archive.addModule(rupSet);
		}
		archive.write(file, existing);
	}

	@Override
	public String getName() {
		return "Solution";
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		// TODO load core data here
		CSVFile<String> ratesCSV = new CSVFile<>(false);
		ratesCSV.addLine("Rupture Index", "Rate");
		ratesCSV.addLine("0", "0.001");
		CSV_BackedModule.writeToArchive(ratesCSV, zout, entryPrefix, "rates.csv");
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		// TODO write core data here
		CSV_BackedModule.loadFromArchive(zip, entryPrefix, "rates.csv");
	}

	@Override
	protected String getNestingPrefix() {
		return "solution/";
	}

	public static void main(String[] args) throws IOException {
		DemoFSRS fsrs = new DemoFSRS();
		fsrs.addModule(new DemoInfoModule("This is my fake rupture info\nnew line here\n"));
		DemoFSS fss = new DemoFSS(fsrs, null);
		fss.addModule(new DemoInfoModule("This is my fake solution info\nnew line here\n"));
		
		File archive = new File("/tmp/new_module_test_sol.zip");
		fss.writeArchive(archive);
		
		DemoFSS fss2 = load(archive);
//		System.out.println(fss2.getModule(DemoInfoModule.class).getText());
		
		fss2.writeArchive(new File("/tmp/new_module_test_sol_2.zip"));
	}

}
