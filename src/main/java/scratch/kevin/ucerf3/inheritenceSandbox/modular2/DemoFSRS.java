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
import org.opensha.commons.util.modules.TextBackedModule;

import com.google.common.base.Preconditions;

public class DemoFSRS extends ModuleContainer<OpenSHA_Module> implements ArchivableModule {

	private ModuleArchive<OpenSHA_Module> archive;

	public DemoFSRS() {
		
	}
	
	private void setArchive(ModuleArchive<OpenSHA_Module> archive) {
		this.archive = archive;
		if (!archive.hasModule(DemoFSRS.class))
			archive.addModule(this);
	}
	
	public static DemoFSRS load(File file) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(file);
		
		DemoFSRS rupSet = archive.getModule(DemoFSRS.class);
		Preconditions.checkState(rupSet != null);
		rupSet.setArchive(archive);
		
		return rupSet;
	}
	
	public void writeArchive(File file) throws IOException {
		if (archive == null)
			archive = new ModuleArchive<>();
		if (!archive.hasModule(DemoFSRS.class))
			archive.addModule(this);
		archive.writeArchive(file);
	}

	@Override
	public String getName() {
		return "Rupture Set";
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		// TODO load core data here
		CSVFile<String> rupsCSV = new CSVFile<>(false);
		rupsCSV.addLine("Rupture Index", "Magnitude");
		rupsCSV.addLine("0", "7.0");
		CSV_BackedModule.writeToArchive(rupsCSV, zout, entryPrefix, "ruptures.csv");
		
		CSVFile<String> sectsCSV = new CSVFile<>(false);
		sectsCSV.addLine("Section Index", "Section Name");
		sectsCSV.addLine("0", "Not Your Fault");
		CSV_BackedModule.writeToArchive(sectsCSV, zout, entryPrefix, "sections.csv");
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		// TODO write core data here
		CSV_BackedModule.loadFromArchive(zip, entryPrefix, "ruptures.csv");
		CSV_BackedModule.loadFromArchive(zip, entryPrefix, "sections.csv");
	}

	@Override
	protected String getNestingPrefix() {
		return "ruptures/";
	}
	
	public static void main(String[] args) throws IOException {
		DemoFSRS fsrs = new DemoFSRS();
		fsrs.addModule(new DemoInfoModule("This is my fake info\nnew line here\n"));
		DemoModuleContainer container = new DemoModuleContainer();
		container.addModule(new DemoInfoModule("Further nested info"));
		fsrs.addModule(container);
		File archive = new File("/tmp/new_module_test.zip");
		fsrs.writeArchive(archive);
		
		DemoFSRS frss2 = load(archive);
		DemoInfoModule info = frss2.getModule(DemoInfoModule.class);
		Preconditions.checkNotNull(info);
//		System.out.println(info.getText());
	}
	
	static class DemoInfoModule implements TextBackedModule {
		
		private String info;
		
		@SuppressWarnings("unused") // used by Gson
		private DemoInfoModule() {
			
		}

		public DemoInfoModule(String info) {
			this.info = info;
		}

		@Override
		public String getName() {
			return "Info";
		}

		@Override
		public String getFileName() {
			return "info.txt";
		}

		@Override
		public String getText() {
			return info;
		}

		@Override
		public void setText(String text) {
			this.info = text;
		}
		
	}
	
	static class DemoModuleContainer extends ModuleContainer<OpenSHA_Module> implements ArchivableModule {

		@Override
		public String getName() {
			return "Demo embedded module container";
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			
		}

		@Override
		protected String getNestingPrefix() {
			return "demo_container/";
		}
		
	}

}
