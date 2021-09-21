package org.opensha.commons.util.modules;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.helpers.TextBackedModule;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

public class ModuleArchiveTest {
	
	private static File parentDir;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		parentDir = Files.createTempDir();
		System.out.println("Creating temp dir: "+parentDir.getAbsolutePath());
	}
	
	@AfterClass
	public static void tearDownAfterClass() {
		System.out.println("Deleting temp dir: "+parentDir.getAbsolutePath());
		FileUtils.deleteRecursive(parentDir);
	}
	
	@Test
	public void testSaveLoadText() throws IOException {
		System.out.println("*** testSaveLoadText() ***");
		System.out.println("Testing text saving and loading");
		
		boolean[] endNewlines = { false, true };
		
		for (boolean endNewline : endNewlines) {
			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
			String text;
			File outputFile;
			if (endNewline) {
				text = "This is my multi line text 1\nanother line that ends with a new line\n";
				outputFile = new File(parentDir, "save_load_text_end_newline.zip");
			} else {
				text = "This is my multi line text 1\nanother line that ends with a character";
				outputFile = new File(parentDir, "save_load_text_end_char.zip");
			}
			// Text that doesn't end in a new line
			System.out.println("Writing text:\n"+text);
			archive.addModule(new TextModule(text));
			
			archive.write(outputFile);
			
			ZipFile zip = new ZipFile(outputFile);
			printEntries(zip);
			assertNotNull("zip doesn't contain expected entry: "+text_file_name, zip.getEntry(text_file_name));
			archive = new ModuleArchive<>(outputFile);
			
			TextModule loaded = archive.getModule(TextModule.class);
			System.out.println("Loaded text:\n"+loaded.getText());
			assertTrue("Loaded text isn't expected when trimmed", text.trim().equals(loaded.getText().trim()));
			assertTrue("Loaded text isn't expected when not trimmed?", text.equals(loaded.getText()));
		}
	
		System.out.println("*** END testSaveLoadText() ***");
	}
	
	@Test
	public void testAltLoadingClass() throws IOException {
		System.out.println("*** testAltLoadingClass() ***");
		System.out.println("Testing that loading uses getLoadingClass");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		String text = "this is my text";
		archive.addModule(new AltLoadTextModule(text, TextModule.class));
		
		File outputFile = new File(parentDir, "alt_load_test.zip");
		archive.write(outputFile);
		
		archive = new ModuleArchive<>(outputFile);
		assertFalse("Should have been loaded back in as a regular TextModule",
				archive.hasModule(AltLoadTextModule.class));
		TextModule loaded = archive.getModule(TextModule.class);
		assertNotNull("Should have loaded in as a TextModule", loaded);
		assertEquals("Text mangled in load", text, loaded.getText());

		System.out.println("*** END testAltLoadingClass() ***");
	}
	
	@Test
	public void testSingleNested() throws IOException {
		System.out.println("*** testSingleNested() ***");
		System.out.println("Testing storing and loading a single nested module");
		
		String nestedText = "Nested text here";
		String topText = "Top level text here";
		
		NestedModule nested = new NestedModule("nested/");
		nested.addModule(new TextModule(nestedText));
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		archive.addModule(nested);
		archive.addModule(new TextModule(topText));
		
		File outputFile = new File(parentDir, "single_nested.zip");
		archive.write(outputFile);
		
		ZipFile zip = new ZipFile(outputFile);
		printEntries(zip);
		archive = new ModuleArchive<>(zip);
		nested = archive.getModule(NestedModule.class);
		assertNotNull("Nested module didn't load", nested);
		TextModule nestedTextModule = nested.getModule(TextModule.class);
		assertNotNull("Nested sub-module didn't load", nestedTextModule);
		assertEquals("Nested sub-module text isn't right", nestedText, nestedTextModule.getText());
		TextModule topTextModule = archive.getModule(TextModule.class);
		assertNotNull("Top-level sub-module didn't load", topTextModule);
		assertEquals("Top-level sub-module text isn't right", topText, topTextModule.getText());

		System.out.println("*** END testSingleNested() ***");
	}
	
	@Test
	public void testMultipleNested() throws IOException {
		System.out.println("*** testMultipleNested() ***");
		System.out.println("Testing storing and loading a multiple layers of nested modules");
		
		String nestedText1 = "Nested 1 text here";
		String nestedText2 = "Nested 2 text here";
		String topText = "Top level text here";
		
		NestedModule nested1 = new NestedModule("nested_1/");
		nested1.addModule(new TextModule(nestedText1));
		
		NestedModule nested2 = new NestedModule("nested_2/");
		nested2.addModule(new TextModule(nestedText2));
		nested1.addModule(nested2);
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		archive.addModule(nested1);
		archive.addModule(new TextModule(topText));
		
		File outputFile = new File(parentDir, "multiple_nested.zip");
		archive.write(outputFile);
		
		ZipFile zip = new ZipFile(outputFile);
		printEntries(zip);
		archive = new ModuleArchive<>(zip);
		
		nested1 = archive.getModule(NestedModule.class);
		assertNotNull("Nested 1 module didn't load", nested1);
		TextModule nestedTextModule1 = nested1.getModule(TextModule.class);
		assertNotNull("Nested 1 sub-module didn't load", nestedTextModule1);
		assertEquals("Nested 1 sub-module text isn't right", nestedText1, nestedTextModule1.getText());
		
		nested2 = nested1.getModule(NestedModule.class);
		assertNotNull("Nested 2 module didn't load", nested2);
		TextModule nestedTextModule2 = nested2.getModule(TextModule.class);
		assertNotNull("Nested 2 sub-module didn't load", nestedTextModule2);
		assertEquals("Nested 2 sub-module text isn't right", nestedText2, nestedTextModule2.getText());
		
		TextModule topTextModule = archive.getModule(TextModule.class);
		assertNotNull("Top-level sub-module didn't load", topTextModule);
		assertEquals("Top-level sub-module text isn't right", topText, topTextModule.getText());

		System.out.println("*** END testMultipleNested() ***");
	}
	
	@Test
	public void testNestingPrefixAssertion() throws IOException {
		System.out.println("*** testNestingPrefixAssertion() ***");
		System.out.println("Testing that non-empty nesting prefixes are enforced when nesting a module");

		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		archive.addModule(new NestedModule(null));
		
		File outputFile = new File(parentDir, "nested_null_prefix.zip");
		try {
			archive.write(outputFile);
			fail("Should have thrown an IllegalStateException");
		} catch (IllegalStateException e) {
			System.out.println("Caught expected IllegalStateException: "+e.getMessage());
		}
		
		archive.clearModules();
		archive.addModule(new NestedModule(""));
		
		outputFile = new File(parentDir, "nested_empty_prefix.zip");
		try {
			archive.write(outputFile);
			fail("Should have thrown an IllegalStateException");
		} catch (IllegalStateException e) {
			System.out.println("Caught expected IllegalStateException: "+e.getMessage());
		}
		
		System.out.println("*** END testNestingPrefixAssertion() ***");
	}
	
	@Test
	public void testMultipleNestedSamePrefix() throws IOException {
		System.out.println("*** testMultipleNestedSamePrefix() ***");
		System.out.println("Testing that an exception is thrown if we attempt to nest multiple modules with the same prefix");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		archive.addModule(new NestedModule("prefix/"));
		archive.addModule(new OtherNestedModule("prefix/"));
		
		File outputFile = new File(parentDir, "nested_duplicate_prefix.zip");
		try {
			archive.write(outputFile);
			printEntries(new ZipFile(outputFile));
			fail("Should have thrown an IllegalStateException");
		} catch (IllegalStateException e) {
			System.out.println("Caught expected IllegalStateException: "+e.getMessage());
		}
		
		System.out.println("*** END testMultipleNestedSamePrefix() ***");
	}
	
	@Test
	public void testLoadUniquePrefix() throws IOException {
		System.out.println("*** testLoadUniquePrefix() ***");
		System.out.println("Testing that loading code enforces that all prefixes are unique");
		
		// need to manipulate the written archive, as tests above will ensure that a malformed archive with duplicate
		// paths are never written with our code
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		String prefix = "prefix/";
		String fakePrefix = "fake_prefix";
		archive.addModule(new NestedModule(prefix));
		archive.addModule(new OtherNestedModule(fakePrefix));
		
		File outputFile = new File(parentDir, "nested_before_duplicate_prefix.zip");
		archive.write(outputFile);
		File modOutputFile = new File(parentDir, "nested_with_duplicate_prefix.zip");
		findReplaceInZipFile(outputFile, modOutputFile, "modules.json", fakePrefix, prefix);
		try {
			new ModuleArchive<>(modOutputFile, NestedModule.class);
			printEntries(new ZipFile(modOutputFile));
			fail("Should have thrown an IllegalStateException");
		} catch (IllegalStateException e) {
//			e.printStackTrace();
			System.out.println("Caught expected IllegalStateException: "+e.getMessage());
		}
		archive = new ModuleArchive<>(modOutputFile); // should be able to initialize it just fine
		assertNotNull("this one should work", archive.getModule(NestedModule.class));
		assertNull("loading should have failed", archive.getModule(OtherNestedModule.class));

		System.out.println("*** END testLoadUniquePrefix() ***");
	}
	
	@Test
	public void testLoadNestedPathsValid() throws IOException {
		System.out.println("*** testLoadNestedPathsValid() ***");
		System.out.println("Testing that loading code enforces that all paths are valid, and that sub-paths start "
				+ "with the parent path (to avoid infinite loops)");
		
		// need to manipulate the written archive, as tests above will ensure that a malformed archive with duplicate
		// paths are never written with our code
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		String prefix = "prefix/";
		String fakePrefix = "bad_prefix";
		NestedModule nested = new NestedModule(prefix);
		archive.addModule(nested);
		nested.addModule(new OtherNestedModule(fakePrefix));
		
		File outputFile = new File(parentDir, "nested_before_duplicate_prefix.zip");
		archive.write(outputFile);
		File modOutputFile = new File(parentDir, "nested_with_duplicate_prefix.zip");
		findReplaceInZipFile(outputFile, modOutputFile, "prefix/modules.json", prefix+fakePrefix, fakePrefix);
		try {
			new ModuleArchive<>(modOutputFile, NestedModule.class);
//			new ModuleArchive<>(modOutputFile, NestedModule.class).getModule(NestedModule.class).loadAllAvailableModules();
			printEntries(new ZipFile(modOutputFile));
			fail("Should have thrown an IllegalStateException");
		} catch (IllegalStateException e) {
			System.out.println("Caught expected IllegalStateException: "+e.getMessage());
		}
		archive = new ModuleArchive<>(modOutputFile); // should be able to initialize it just fine
		assertNull("loading should have failed", archive.getModule(NestedModule.class));

		System.out.println("*** END testLoadNestedPathsValid() ***");
	}
	
	@Test
	public void testAvailableWritten() throws IOException {
		System.out.println("*** testAvailableWritten() ***");
		System.out.println("Testing that available modules added to containers are then written to archives, "
				+ "even if not previously loaded");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		String text = "This is my text";
		Callable<TextModule> textCall = new Callable<>() {

			@Override
			public TextModule call() throws Exception {
				return new TextModule(text);
			}
		};
		archive.addAvailableModule(textCall, TextModule.class);
		assertEquals("should start with 1 available", 1, archive.getAvailableModules().size());
		assertEquals("should start with 0 loaded", 0, archive.getModules(false).size());
		
		File outputFile = new File(parentDir, "available_test.zip");
		archive.write(outputFile);
		assertEquals("should now have 0 available", 0, archive.getAvailableModules().size());
		assertEquals("should now have 1 loaded", 1, archive.getModules(false).size());
		
		archive = new ModuleArchive<>(outputFile);
		TextModule module = archive.getModule(TextModule.class);
		assertNotNull("Available module was not loaded?", module);
		assertEquals("Avaiable loading garbled", text, module.getText());
		
		// now do it with a nested module
		archive = new ModuleArchive<>();
		Callable<NestedModule> nestingCall = new Callable<>() {

			@Override
			public NestedModule call() throws Exception {
				NestedModule module = new NestedModule("prefix/");
				module.addAvailableModule(textCall, TextModule.class);
				return module;
			}
		};
		archive.addAvailableModule(nestingCall, NestedModule.class);
		assertEquals("should start with 1 available", 1, archive.getAvailableModules().size());
		assertEquals("should start with 0 loaded", 0, archive.getModules(false).size());
		
		outputFile = new File(parentDir, "available_nested_test.zip");
		archive.write(outputFile);
		assertEquals("should now have 0 available", 0, archive.getAvailableModules().size());
		assertEquals("should now have 1 loaded", 1, archive.getModules(false).size());
		
		archive = new ModuleArchive<>(outputFile);
		NestedModule nested = archive.getModule(NestedModule.class);
		assertNotNull("Available nested module was not loaded?", nested);
		module = nested.getModule(TextModule.class);
		assertNotNull("Available module was not loaded?", module);
		assertEquals("Avaiable loading garbled", text, module.getText());

		System.out.println("*** END testAvailableWritten() ***");
	}
	
	@Test
	public void testExtraCopyOver() throws IOException {
		System.out.println("*** testExtraCopyOver() ***");
		System.out.println("Testing that extra files are copied over (when requested)");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		String text = "This is my multi line text 1\nanother line that ends with a new line\n";
		File outputFile = new File(parentDir, "pre_extra_files.zip");
		// Text that doesn't end in a new line
		System.out.println("Writing text:\n"+text);
		archive.addModule(new TextModule(text));
		
		archive.write(outputFile);
		
		// now add some extra files
		Map<String, String> extraFiles = new HashMap<>();
		extraFiles.put("extra_file_1.txt", "File contents 1");
		extraFiles.put("extra_file_2.txt", "File contents 2");
		extraFiles.put("extra_dir_1/", null);
		extraFiles.put("extra_dir_2/", null);
		extraFiles.put("extra_dir_2/file.txt", "Sub-file 2 contents");
		extraFiles.put("extra_dir_3/file.txt", "Sub-file 3 contents");
		
		ZipFile zip = new ZipFile(outputFile);
		File extraOutputFile = new File(parentDir, "orig_with_extra_files.zip");
		ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(extraOutputFile)));
		
		printEntries(zip);
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			// copy it over
			zout.putNextEntry(new ZipEntry(entry.getName()));
			
			BufferedInputStream bin = new BufferedInputStream(zip.getInputStream(entry));
			bin.transferTo(zout);
			zout.flush();
			
			zout.closeEntry();
		}
		for (String extraName : extraFiles.keySet()) {
			String contents = extraFiles.get(extraName);
			ZipEntry entry = new ZipEntry(extraName);
			zout.putNextEntry(entry);
			
			if (contents != null)
				TextBackedModule.writeToStream(zout, contents);
			
			zout.flush();
			zout.closeEntry();
		}
		zip.close();
		zout.close();

		printEntries(new ZipFile(extraOutputFile));
		
		archive = new ModuleArchive<>(extraOutputFile);
		TextModule module = archive.getModule(TextModule.class);
		assertNotNull("Module failed from zip with extras", module);
		System.out.println("Loaded text: "+module.getText());
		assertEquals("Module load garbled from zip with extras", text, module.getText());
		
		// now copy it over
		File writtenWithExtraOutputFile = new File(parentDir, "written_with_extra_files.zip");
		archive.write(writtenWithExtraOutputFile, true);
		ZipFile writtenWithExtra = new ZipFile(writtenWithExtraOutputFile);
		printEntries(writtenWithExtra);
		for (String extraName : extraFiles.keySet()) {
			ZipEntry entry = writtenWithExtra.getEntry(extraName);
			assertNotNull("Extra file was omitted: "+extraName, entry);
			String contents = extraFiles.get(extraName);
			if (contents != null) {
				// make sure it was correctly copied over
				String readContents = TextBackedModule.readFromStream(writtenWithExtra.getInputStream(entry));
				assertEquals("copy-over contents garbled", contents, readContents);
			}
		}
		writtenWithExtra.close();
		
		// now write without copying
		File writtenWithoutExtraOutputFile = new File(parentDir, "written_without_extra_files.zip");
		archive.write(writtenWithoutExtraOutputFile, false);
		ZipFile writtenWithoutExtra = new ZipFile(writtenWithoutExtraOutputFile);
		printEntries(writtenWithoutExtra);
		for (String extraName : extraFiles.keySet()) {
			ZipEntry entry = writtenWithoutExtra.getEntry(extraName);
			assertNull("Extra file was included but copyOver was false: "+extraName, entry);
		}
		writtenWithoutExtra.close();

		System.out.println("*** END testExtraCopyOver() ***");
	}
	
	/**
	 * Replaces a string in a zip entry, used to muck with already written archives to test input validation on load
	 */
	private static void findReplaceInZipFile(File inZipFile, File outZipFile, String entryName, String find,
			String replace) throws IOException {
		ZipFile zip = new ZipFile(inZipFile);
		boolean found = false;
		
		ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outZipFile)));
		
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			
			if (entry.getName().equals(entryName)) {
				// it's  a match, modify
				found = true;
				String origText = TextBackedModule.readFromStream(zip.getInputStream(entry));
				String modText = origText.replace(find, replace);
				Preconditions.checkState(!origText.equals(modText), "String not found in text: %s", find);
				System.out.println("Replaced '"+find+"' with '"+replace+"' in '"+entryName+"'");
				
				zout.putNextEntry(new ZipEntry(entry.getName()));
				TextBackedModule.writeToStream(zout, modText);
				zout.flush();
				
				zout.closeEntry();
			} else {
				// copy it over
				zout.putNextEntry(new ZipEntry(entry.getName()));
				
				BufferedInputStream bin = new BufferedInputStream(zip.getInputStream(entry));
				bin.transferTo(zout);
				zout.flush();
				
				zout.closeEntry();
			}
		}
		Preconditions.checkState(found);
		zout.close();
		zip.close();
	}
	
	private static void printEntries(ZipFile zip) {
		System.out.println(zip.getName());
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements())
			System.out.println("\t- "+entries.nextElement().getName());
	}
	
	private static final String text_file_name = "text_module.txt";
	
	private static class TextModule implements TextBackedModule {
		
		private String text;
		
		private TextModule() {
			
		}

		public TextModule(String text) {
			this.text = text;
		}

		@Override
		public String getFileName() {
			return text_file_name;
		}

		@Override
		public String getName() {
			return "Text Module";
		}

		@Override
		public String getText() {
			return text;
		}

		@Override
		public void setText(String text) {
			this.text = text;
		}
		
	}
	
	private static class AltLoadTextModule implements TextBackedModule {
		
		private String text;
		private Class<? extends TextBackedModule> loadingClass;

		public AltLoadTextModule(String text, Class<? extends TextBackedModule> loadingClass) {
			this.text = text;
			this.loadingClass = loadingClass;
		}

		@Override
		public String getFileName() {
			return text_file_name;
		}

		@Override
		public String getName() {
			return "Text Module";
		}

		@Override
		public String getText() {
			return text;
		}

		@Override
		public void setText(String text) {
			this.text = text;
		}

		@Override
		public Class<? extends ArchivableModule> getLoadingClass() {
			return loadingClass;
		}

		@Override
		public void initFromStream(BufferedInputStream in) throws IOException {
			throw new IllegalStateException("This should never be called");
		}
		
	}
	
	private static class NestedModule extends ModuleContainer<OpenSHA_Module> implements ArchivableModule {
		
		private String nestingPrefix;
		
		private NestedModule() {
			
		}

		public NestedModule(String nestingPrefix) {
			this.nestingPrefix = nestingPrefix;
		}

		@Override
		public String getName() {
			return "Simple Nested Module";
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		protected String getNestingPrefix() {
			return nestingPrefix;
		}
		
	}
	
	private static class OtherNestedModule extends ModuleContainer<OpenSHA_Module> implements ArchivableModule {
		
		private String nestingPrefix;
		
		private OtherNestedModule() {
			
		}

		public OtherNestedModule(String nestingPrefix) {
			this.nestingPrefix = nestingPrefix;
		}

		@Override
		public String getName() {
			return "Other Nested Module";
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			// do nothing
		}

		@Override
		protected String getNestingPrefix() {
			return nestingPrefix;
		}
		
	}

}
