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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.util.ExceptionUtils;

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
		this(file, null);
	}
	
	/**
	 * Load modules from an existing archive. Modules themselves will be lazily loaded on demand, unless they
	 * are assignable from the given preload class
	 * 
	 * @param file
	 * @param preloadClass class to preload
	 * @throws IOException
	 */
	public ModuleArchive(File file, Class<? extends E> preloadClass) throws IOException {
		this(new ZipFile(file), preloadClass);
	}
	
	/**
	 * Load modules from an existing archive. Modules themselves will be lazily loaded on demand
	 * 
	 * @param zip
	 * @throws IOException
	 */
	public ModuleArchive(ZipFile zip) throws IOException {
		this(zip, null);
	}
	
	/**
	 * Load modules from an existing archive. Modules themselves will be lazily loaded on demand, unless they
	 * are assignable from the given preload class
	 * 
	 * @param zip
	 * @param preloadClass class to preload
	 * @throws IOException
	 */
	public ModuleArchive(ZipFile zip, Class<? extends E> preloadClass) throws IOException {
		super();
		this.zip = zip;
		System.out.println("------------ LOADING ARCHIVE ------------");
		System.out.println("Archive: "+zip.getName());
		loadModules(this, zip, getPrefix(null, getNestingPrefix()), preloadClass, new HashSet<>());
		List<E> modules = getModules();
		if (!modules.isEmpty())
			System.out.println("Loaded "+modules.size()+" top-level modules");
		List<Callable<E>> availableModules = getAvailableModules();
		if (!availableModules.isEmpty())
			System.out.println("Loaded "+availableModules.size()+" available top-level modules");
		System.out.println("---------- END LOADING ARCHIVE ----------");
	}
	
	public static final String MODULE_FILE_NAME = "modules.json";
	
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
	public static <E extends OpenSHA_Module> void loadModules(ModuleContainer<E> container, ZipFile zip, String prefix,
			Class<? extends E> preloadClass, HashSet<String> prevPrefixes) throws IOException {
//		System.out.println("Loading modules for "+container.getClass().getName()+" with prefix="+prefix);
		if (prefix ==null)
			prefix = "";
		else
			prefix = prefix.trim();
		
		Preconditions.checkState(!prevPrefixes.contains(prefix),
				"Found container with duplicate path, we've already loaded a container with path: '%s'", prefix);
		prevPrefixes.add(prefix);
		
		String entryName = prefix+MODULE_FILE_NAME;
		ZipEntry modulesEntry = zip.getEntry(entryName);
		if (modulesEntry == null) {
			System.out.println("Modules index not found in zip file, skipping loading sub-modules: "+entryName);
			return;
		}
		
		Gson gson = new GsonBuilder().create();
		
		InputStream zin = zip.getInputStream(modulesEntry);
		BufferedReader reader = new BufferedReader(new InputStreamReader(zin));
		List<ModuleRecord> records = gson.fromJson(reader,
				TypeToken.getParameterized(List.class, ModuleRecord.class).getType());
		for (ModuleRecord record : records) {
			System.out.println("\tFound available module '"+record.name+"' with path='"+record.path+"'");
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
				record.path = record.path.trim();
				Preconditions.checkState(!record.path.isBlank(),
						"Module '%s' is also a container but does not supply a path.Container modules must always "
						+ "supply a custom path, otherwise we would keep loading the same top-level modules.json "
						+ "file in an infinite loop.", record.name);
				Preconditions.checkState(prefix.isBlank() || record.path.startsWith(prefix),
						"Module '%s' is a container but it's supplied path ('%s') is not nested within the parent "
						+ "module's path ('%s')", record.name, record.path, prefix);
				Preconditions.checkState(!prevPrefixes.contains(record.path),
						"Module '%s' is a container with a duplicate path, we've already loaded a container with path: '%s'",
						record.name, record.path);
			}
			
			ZipLoadCallable<E> call = new ZipLoadCallable<E>(record, moduleClass, zip, container, prevPrefixes);
			if (preloadClass != null && preloadClass.isAssignableFrom(moduleClass)) {
				// load it now
				E module = null;
				try {
					module = call.call();
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				if (module == null) {
					// error loading preload module, throw that exception
					throw new IllegalStateException("Failed to load module that matches pre-load class", call.t);
				}
				container.addModule(module);
			} else {
				container.addAvailableModule(call, moduleClass);
			}
		}
		zin.close();
	}
	
	/**
	 * Attempts to load a module from the current zip archive that was not listed in any modules.json files
	 * 
	 * @param <M>
	 * @param loadingClass loading class for the given module
	 * @param entryPrefix prefix for the given module inside this archive.
	 * @return
	 */
	public <M extends E> M loadUnlistedModule(Class<? extends M> loadingClass, String entryPrefix) {
		return loadUnlistedModule(loadingClass, entryPrefix, this);
	}
	
	/**
	 * Attempts to load a module from the current zip archive that was not listed in any modules.json files
	 * 
	 * @param <M>
	 * @param loadingClass loading class for the given module
	 * @param entryPrefix prefix for the given module inside this archive.
	 * @container container container to add the module to
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <M extends E> M loadUnlistedModule(Class<? extends M> loadingClass, String entryPrefix,
			ModuleContainer<E> container) {
		ModuleRecord record = new ModuleRecord("Unlisted Module", loadingClass.getName(), entryPrefix, null);
		Preconditions.checkNotNull(zip, "Can only unlisted modules for an archives created from a zip file");
		ZipLoadCallable<E> call = new ZipLoadCallable<>(record, (Class<E>)loadingClass, zip, container, new HashSet<>());
		try {
			M module = (M)call.call();
			container.addModule(module);
			return module;
		} catch (ClassCastException e) {
			throw e;
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	private static class ZipLoadCallable<E extends OpenSHA_Module> implements Callable<E> {
		
		private ModuleRecord record;
		private Class<E> clazz;
		private ZipFile zip;
		private ModuleContainer<E> container;
		private HashSet<String> prevPrefixes;
		
		private Throwable t;

		public ZipLoadCallable(ModuleRecord record, Class<E> clazz, ZipFile zip, ModuleContainer<E> container,
				HashSet<String> prevPrefixes) {
			this.record = record;
			this.clazz = clazz;
			this.zip = zip;
			this.container = container;
			this.prevPrefixes = prevPrefixes;
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
				t = e;
				return null;
			}
			
			try {
				constructor.setAccessible(true);
			} catch (Exception e) {
				System.err.println("WANRING: couldn't make constructor accessible, loading will likely fail: "+e.getMessage());
				t = e;
			}
			
			try {
				System.out.println("Building instance: "+clazz.getName());
				E module = constructor.newInstance();
				if (module instanceof SubModule<?>) {
					SubModule<ModuleContainer<E>> subModule;
					try {
						subModule = container.getAsSubModule(module);
					} catch (Exception e) {
						System.err.println("WARNING: cannot load module '"+record.name+"' of type '"+clazz.getName()
								+"' as the it is a sub-module that is not applicable to the container of type '"
								+container.getClass().getName()+"'");
						t = e;
						return null;
					}
					subModule.setParent(container);
				}
				((ArchivableModule)module).initFromArchive(zip, record.path);
				if (module instanceof ModuleContainer<?>) {
					ModuleContainer<?> moduleContainer = (ModuleContainer<?>)module;
					loadModules(moduleContainer, zip, record.path, null, prevPrefixes);
					int availableModules = moduleContainer.getAvailableModules().size();
					if (availableModules > 0)
						System.out.println("Loaded "+availableModules+" available sub-modules");
				}
				return (E)module;
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error loading module '"+record.name+"', skipping.");
				t = e;
			}
			return null;
		}
		
	}

	/**
	 * Writes this archive to the given file. It will first be written to /path/to/output_file.zip.tmp, and then
	 * copied to /path/to/output_file.zip after a successful write.
	 * <p>
	 * If this archive was initialized with an existing archive, then any unused files from the original archive will
	 * also be copied over (call {@link ModuleArchive#write(File,boolean)} instead to control this behavior).
	 * @param outputFile
	 * @throws IOException
	 */
	public void write(File outputFile) throws IOException {
		write(outputFile, zip != null);
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
	public void write(File outputFile, boolean copySourceFiles) throws IOException {
		System.out.println("------------ WRITING ARCHIVE ------------");
		File tmpOutput = new File(outputFile.getAbsolutePath()+".tmp");
		System.out.println("Temporary archive: "+tmpOutput.getAbsolutePath());
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(tmpOutput));
		
		copySourceFiles = copySourceFiles && zip != null;
		EntryTrackingZOUT zout = new EntryTrackingZOUT(bout);
		
		// no prefix=null for top level container
		writeModules(this, zout, null, new HashSet<>());
		
		if (copySourceFiles) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				
				if (!zout.entries.containsKey(entry.getName())) {
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
	
	/**
	 * Writes modules to the given zip output stream
	 * 
	 * @param <E>
	 * @param container
	 * @param zout
	 * @param prefix
	 * @param prevPrefixes
	 * @return
	 * @throws IOException
	 */
	public static <E extends OpenSHA_Module> boolean writeModules(ModuleContainer<E> container, ZipOutputStream zout,
			String prefix, HashSet<String> prevPrefixes) throws IOException {
		EntryTrackingZOUT ezout;
		if (zout instanceof EntryTrackingZOUT)
			ezout = (EntryTrackingZOUT)zout;
		else
			ezout = new EntryTrackingZOUT(zout);
		List<ModuleRecord> records = new ArrayList<>();
		
		if (prefix == null)
			prefix = "";
		else
			prefix = prefix.trim();
		
		Preconditions.checkState(!prevPrefixes.contains(prefix), "Duplicate prefix detected in archive: %s", prefix);
		prevPrefixes.add(prefix);
		
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
				
				// check for no-arg constructor
				try {
					Preconditions.checkNotNull(archivable.getLoadingClass().getDeclaredConstructor());
				} catch (Throwable t) {
					System.err.println("WARNING: Loading class for module doesn't contain a no-arg constructor, "
							+ "loading from a zip file will fail: "+archivable.getLoadingClass().getName());
				}
				
				List<String> moduleAssets = new ArrayList<>();
				String modulePrefix;
				if (module instanceof ModuleContainer && module != container) {
					ModuleContainer<?> archive = (ModuleContainer<?>)module;
					String nestingPrefix = archive.getNestingPrefix();
//					System.out.println("\tWriting nested module container '"+module.getName()
//						+"' with nesting prefix: '"+nestingPrefix+"'");
					Preconditions.checkState(nestingPrefix != null && !nestingPrefix.isEmpty(),
							"Module '%s' is a nested archive but does not override getNestingPrefix() "
							+ "to provide a non-empty nesting prefix (supplied: '%s')",
							module.getName(), nestingPrefix);
					
					String downstreamPrefix = prefix+nestingPrefix;
//					System.out.println("ds pre: "+downstreamPrefix);
//					if (downstreamPrefix.length() > 20)
//						throw new IllegalStateException("here I be");
					if (writeModules(archive, ezout, downstreamPrefix, prevPrefixes))
						moduleAssets.add(MODULE_FILE_NAME);
					
					modulePrefix = downstreamPrefix;
				} else {
					modulePrefix = prefix;
				}
				
				ezout.initNewModule(modulePrefix);
				archivable.writeToArchive(ezout, modulePrefix);
				moduleAssets.addAll(ezout.endCurrentModuleEntries());
				Collections.sort(moduleAssets);
				records.add(new ModuleRecord(archivable.getName(), archivable.getLoadingClass().getName(),
						modulePrefix, moduleAssets));
			} else {
				System.out.println("\tSkipping transient module: "+module.getName());
			}
		}
		
		if (!records.isEmpty()) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			
			String entryName = prefix+MODULE_FILE_NAME;
			System.out.println("Wrote "+records.size()+" modules, writing index to "+entryName);
			ezout.putNextEntry(new ZipEntry(entryName));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ezout));
//			System.out.println("------ MODULES JSON ------");
//			System.out.println(gson.toJson(records));
//			System.out.println("--------------------------");
			gson.toJson(records, writer);
			writer.write("\n");
			writer.flush();
			ezout.closeEntry();
			return true;
		}
		return false;
	}
	
	private static class ModuleRecord {
		public String name;
		public String className;
		
		public String path;
		private List<String> assets;
		
		@SuppressWarnings("unused") // used by Gson
		public ModuleRecord() {
			// for serialization
		}

		public ModuleRecord(String name, String className, String path, List<String> assets) {
			super();
			this.name = name;
			this.className = className;
			if (path != null && path.isBlank())
				// so that it doesn't show up in JSON when empty
				path = null;
			this.path = path;
			this.assets = assets;
		}
	}
	
	private static String getPrefix(String upstreamPrefix, String nestingPrefix) {
		String ret = upstreamPrefix == null ? "" : upstreamPrefix;
		return nestingPrefix == null ? ret : ret+nestingPrefix;
	}
	
	private static class EntryTrackingZOUT extends ZipOutputStream {

		private final HashMap<String, ZipEntry> entries;
		
		private String modulePath;
		private List<String> moduleEntries;

		public EntryTrackingZOUT(OutputStream out) {
			super(out);
			this.entries = new HashMap<>();
		}
		
		public void initNewModule(String modulePath) {
			this.modulePath = modulePath;
			moduleEntries = new ArrayList<>();
		}
		
		public List<String> endCurrentModuleEntries() {
			List<String> ret = moduleEntries;
			moduleEntries = null;
			return ret;
		}

		@Override
		public void putNextEntry(ZipEntry e) throws IOException {
			String name = e.getName();
			this.entries.put(name, e);
			if (moduleEntries != null && name.startsWith(modulePath)) {
				String subPath = name.substring(modulePath.length());
				moduleEntries.add(subPath);
			}
			super.putNextEntry(e);
		}
		
	}

}
