package org.opensha.commons.util.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * {@link ModuleContainer} that can be written to a and loaded from a zip archive. A module index will be written
 * to 'modules.json' in the archive, and upon loading, all modules will be lazily initialized.
 * 
 * @author kevin
 *
 * @param <E>
 */
public class ModuleArchive<E extends OpenSHA_Module> extends ModuleContainer<E> {
	
	private ZipFile zip;
	
	/**
	 * Create a new module container that can be written to an archive
	 */
	public ModuleArchive() {
		
	}
	
	/**
	 * Load modules from an existing archive. Modules themselves will be lazily loaded on demand
	 * 
	 * @param file
	 * @throws IOException
	 */
	public ModuleArchive(File file) throws IOException {
		this(new ZipFile(file));
	}
	
	/**
	 * Load modules from an existing archive. Modules themselves will be lazily loaded on demand
	 * 
	 * @param zip
	 * @throws IOException
	 */
	public ModuleArchive(ZipFile zip) throws IOException {
		super();
		this.zip = zip;
		System.out.println("------------ LOADING ARCHIVE ------------");
		System.out.println("Archive: "+zip.getName());
		loadModules(this, zip, getPrefix(null, getNestingPrefix()));
		System.out.println("Loaded "+getAvailableModules().size()+" available top-level modules");
		System.out.println("---------- END LOADING ARCHIVE ----------");
	}
	
	private static final String MODULE_FILE_NAME = "modules.json";
	
	/**
	 * Loads available modules from the given zip file into the given container. It will first read the modules JSON
	 * index from the zip file (from {@code prefix == null ? "modules.json" : prefix+"modules.json"}), and then register
	 * each available module. The zip file should not be closed, or lazily loaded modules will fail.
	 * 
	 * @param <E>
	 * @param container container to load available modules into
	 * @param zip zip file containing available modules, should not be closed without first calling 
	 * {@link ModuleContainer#loadAllAvailableModules()}
	 * @param prefix prefix applied when writing modules for this container to the zip file, or null if top level archive
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	private static <E extends OpenSHA_Module> void loadModules(ModuleContainer<E> container, ZipFile zip, String prefix)
			throws IOException {
//		System.out.println("Loading modules for "+container.getClass().getName()+" with prefix="+prefix);
		if (prefix ==null)
			prefix = "";
		String entryName = prefix+MODULE_FILE_NAME;
		ZipEntry modulesEntry = zip.getEntry(entryName);
		Preconditions.checkNotNull(modulesEntry, "Modules file not found in zip file: %s", entryName);
		
		Gson gson = new GsonBuilder().create();
		
		InputStream zin = zip.getInputStream(modulesEntry);
		BufferedReader reader = new BufferedReader(new InputStreamReader(zin));
		List<ModuleRecord> records = gson.fromJson(reader,
				TypeToken.getParameterized(List.class, ModuleRecord.class).getType());
		for (ModuleRecord record : records) {
			Class<?> clazz;
			try {
				clazz = Class.forName(record.className);
			} catch(Exception e) {
				System.err.println("WARNING: Skipping module '"+record.name
						+"', couldn't locate class: "+record.className);
				continue;
			}
			
			
			Class<E> moduleClass;
			try {
				moduleClass = (Class<E>)clazz;
			} catch (Exception e) {
				System.err.println("WARNING: cannot load module '"+record.name
						+"' as the loading class isn't of the specified type: "+e.getMessage());
				continue;
			}
			
			if (ModuleContainer.class.isAssignableFrom(moduleClass)) {
				// make sure that this record has a path, otherwise we could get stuck in an infinite loading loop
				Preconditions.checkState(!record.path.isBlank(),
						"Module '%s' is also a container but does not supply a path.Container modules must always "
						+ "supply a custom path, otherwise we would keep loading the same top-level modules.json "
						+ "file in an infinite loop.", record.name);
				Preconditions.checkState(prefix.isBlank() || record.path.startsWith(prefix),
						"Module '%s' is a container but it's supplied path ('%s') is not nested within the parent "
						+ "module's path ('%s')", record.name, record.path, prefix);
			}
			
			container.addAvailableModule(new ZipLoadCallable<E>(record, moduleClass, zip), moduleClass);
		}
		zin.close();
	}
	
	private static class ZipLoadCallable<E extends OpenSHA_Module> implements Callable<E> {
		
		private ModuleRecord record;
		private Class<E> clazz;
		private ZipFile zip;

		public ZipLoadCallable(ModuleRecord record, Class<E> clazz, ZipFile zip) {
			this.record = record;
			this.clazz = clazz;
			this.zip = zip;
		}

		@Override
		public E call() throws Exception {
			if (!ArchivableModule.class.isAssignableFrom(clazz)) {
				System.err.println("WARNING: Module class is not an ArchivableModule, skipping: "+record.className);
				return null;
			}
			
			Constructor<E> constructor;
			try {
				constructor = clazz.getDeclaredConstructor();
			} catch (Exception e) {
				System.err.println("WARNING: cannot load module '"+record.name
						+"' as the loading class doesn't have a no-arg constructor: "+clazz.getName()+"");
				return null;
			}
			
			try {
				constructor.setAccessible(true);
			} catch (Exception e) {
				System.err.println("WANRING: couldn't make constructor accessible, loading will likely fail: "+e.getMessage());
			}
			
			try {
				E module = constructor.newInstance();
				((ArchivableModule)module).initFromArchive(zip, record.path);
				if (module instanceof ModuleContainer<?>) {
					loadModules((ModuleContainer<?>)module, zip, record.path);
				}
				return (E)module;
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error loading module '"+record.name+"', skipping.");
			}
			return null;
		}
		
	}

	/**
	 * Writes this archive to the given file. It will first be written to /path/to/output_file.zip.tmp, and then
	 * copied to /path/to/output_file.zip after a successful write.
	 * <p>
	 * If this archive was initialized with an existing archive, then any unused files from the original archive will
	 * also be copied over (call {@link ModuleArchive#writeArchive(File,boolean)} instead to control this behavior).
	 * @param outputFile
	 * @throws IOException
	 */
	public void writeArchive(File outputFile) throws IOException {
		writeArchive(outputFile, zip != null);
	}

	/**
	 * Writes this archive to the given file. It will first be written to /path/to/output_file.zip.tmp, and then
	 * copied to /path/to/output_file.zip after a successful write.
	 * <p>
	 * If this archive was initialized with an existing archive and copySourceFiles is true, then any unused files from
	 * the original archive will also be copied over.
	 * 
	 * @param outputFile
	 * @param copySourceFiles
	 * @throws IOException
	 */
	public void writeArchive(File outputFile, boolean copySourceFiles) throws IOException {
		System.out.println("------------ WRITING ARCHIVE ------------");
		File tmpOutput = new File(outputFile.getAbsolutePath()+".tmp");
		System.out.println("Temporary archive: "+tmpOutput.getAbsolutePath());
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(tmpOutput));
		
		copySourceFiles = copySourceFiles && zip != null;
		ZipOutputStream zout;
		if (copySourceFiles)
			// need to track written entries for copying
			zout = new EntryTrackingZOUT(bout);
		else
			// not copying any over, so no entry tracking needed
			zout = new ZipOutputStream(bout);
		
		// no prefix=null for top level container
		writeModules(this, zout, null);
		
		if (copySourceFiles) {
			EntryTrackingZOUT trackZOUT = (EntryTrackingZOUT)zout;
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				
				if (!trackZOUT.entries.containsKey(entry.getName())) {
					// need to copy this over
					System.out.println("Copying over unknown file from previous archive: "+entry.getName());
					zout.putNextEntry(new ZipEntry(entry.getName()));
					
					BufferedInputStream bin = new BufferedInputStream(zip.getInputStream(entry));
					bin.transferTo(zout);
					zout.flush();
					
					zout.closeEntry();
				}
			}
		}
		
		zout.close();
		
		System.out.println("Moving to "+outputFile.getAbsolutePath());
		Files.move(tmpOutput, outputFile);
		System.out.println("---------- END WRITING ARCHIVE ----------");
	}
	
	private static <E extends OpenSHA_Module> void writeModules(ModuleContainer<E> container, ZipOutputStream zout,
			String prefix) throws IOException {
		List<ModuleRecord> records = new ArrayList<>();
		
		if (prefix == null)
			prefix = "";
		
		String moduleStr;
		if (container.getClass().equals(ModuleArchive.class))
			moduleStr = "modules";
		else if (container instanceof OpenSHA_Module)
			moduleStr = "nested modules from '"+((OpenSHA_Module)container).getName()+"'";
		else
			moduleStr = "nested modules from '"+container.getClass().getName()+"'";
		System.out.println("Writing "+container.getModules().size()+" "
			+moduleStr+(prefix.isBlank() ? "" : " with prefix='"+prefix+"'"));
		
		for (OpenSHA_Module module : container.getModules(true)) {
			if (module instanceof ArchivableModule) {
				ArchivableModule archivable = (ArchivableModule)module;
				System.out.println("\tWriting module: "+module.getName());
				
				if (module instanceof ModuleContainer && module != container) {
					ModuleContainer<?> archive = (ModuleContainer<?>)module;
					String nestingPrefix = archive.getNestingPrefix();
//					System.out.println("\tWriting nested module container '"+module.getName()
//						+"' with nesting prefix: '"+nestingPrefix+"'");
					Preconditions.checkState(nestingPrefix != null && !nestingPrefix.isEmpty(),
							"Module '%s' is a nested archive but does not override getNestingPrefix()",
							module.getName());
					
					String downstreamPrefix = prefix+nestingPrefix;
//					System.out.println("ds pre: "+downstreamPrefix);
//					if (downstreamPrefix.length() > 20)
//						throw new IllegalStateException("here I be");
					writeModules(archive, zout, downstreamPrefix);
					
					archivable.writeToArchive(zout, downstreamPrefix);
					
					records.add(new ModuleRecord(archivable.getName(), archivable.getLoadingClass().getName(), downstreamPrefix));
				} else {
					archivable.writeToArchive(zout, prefix);
					
					records.add(new ModuleRecord(archivable.getName(), archivable.getLoadingClass().getName(), prefix));
				}
			}
		}
		
		if (!records.isEmpty()) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			String entryName = prefix+MODULE_FILE_NAME;
			System.out.println("Wrote "+records.size()+" modules, writing index to "+entryName);
			zout.putNextEntry(new ZipEntry(entryName));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
//			System.out.println("------ MODULES JSON ------");
//			System.out.println(gson.toJson(records));
//			System.out.println("--------------------------");
			gson.toJson(records, writer);
			writer.write("\n");
			writer.flush();
			zout.closeEntry();
		}
	}
	
	private static class ModuleRecord {
		public String name;
		public String className;
		
		public String path;
		
		@SuppressWarnings("unused") // used by Gson
		public ModuleRecord() {
			// for serialization
		}

		public ModuleRecord(String name, String className, String path) {
			super();
			this.name = name;
			this.className = className;
			if (path != null && path.isBlank())
				// so that it doesn't show up in JSON when empty
				path = null;
			this.path = path;
		}
	}
	
	private static String getPrefix(String upstreamPrefix, String nestingPrefix) {
		String ret = upstreamPrefix == null ? "" : upstreamPrefix;
		return nestingPrefix == null ? ret : ret+nestingPrefix;
	}
	
	private static class EntryTrackingZOUT extends ZipOutputStream {
		
		private final HashMap<String, ZipEntry> entries;

		public EntryTrackingZOUT(OutputStream out) {
			super(out);
			this.entries = new HashMap<>();
		}

		@Override
		public void putNextEntry(ZipEntry e) throws IOException {
			this.entries.put(e.getName(), e);
			super.putNextEntry(e);
		}
		
	}

}
