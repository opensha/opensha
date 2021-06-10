package org.opensha.commons.util.modules.helpers;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipFile;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class FileBackedHelperTests {

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
	public void testLoadSavePlainText() throws IOException {
		System.out.println("*** testSaveLoadText() ***");
		System.out.println("Testing text saving and loading");
		
		List<String> texts = new ArrayList<>();
		List<String> fileNames = new ArrayList<>();
		
		texts.add("Single line text");
		fileNames.add("text_single_line.zip");
		
		texts.add("Multi line text\nLine two");
		fileNames.add("text_multi_line.zip");
		
		texts.add("Multi line text\nLine two\n");
		fileNames.add("text_multi_line_end_newline.zip");
		
		texts.add("\nMulti line text\nLine two");
		fileNames.add("text_multi_line_start_newline.zip");
		
		texts.add("\n");
		fileNames.add("text_newline_only.zip");
		
		texts.add("");
		fileNames.add("text_empty.zip");
		
		for (int i=0; i<texts.size(); i++) {
			String text = texts.get(i);
			
			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
			archive.addModule(new TextModule(text));
			assertFalse("Helper class should not have been mapped", archive.hasModule(TextBackedModule.class));
			
			System.out.println("--- Testing with text ---");
			System.out.println(text);
			System.out.println("-------------------------");
			File file = new File(parentDir, fileNames.get(i));
			archive.write(file);
			
			archive = new ModuleArchive<>(file);
			TextModule module = archive.getModule(TextModule.class);
			assertNotNull("Failed to load text module", module);
			String loaded = module.getText();
			System.out.println("------ Loaded text ------");
			System.out.println(loaded);
			System.out.println("-------------------------");
			assertEquals("Trimmed loaded text doesn't match exptected", text.trim(), loaded.trim());
			assertEquals("Loaded ext doesn't match exptected", text, loaded);
		}

		System.out.println("*** END testSaveLoadText() ***");
	}
	
	private static class TextModule implements TextBackedModule {
		
		private String text;
		
		private TextModule() {
			
		}

		public TextModule(String text) {
			this.text = text;
		}

		@Override
		public String getFileName() {
			return "file.txt";
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

	@Test
	public void testLoadSaveCSV() throws IOException {
		System.out.println("*** testLoadSaveCSV() ***");
		System.out.println("Testing CSV");
		
		CSVFile<String> csv = new CSVFile<>(false);
		Random r = new Random();
		int numVals = 0;
		for (int row=0; row<10+r.nextInt(10); row++) {
			List<String> line = new ArrayList<>();
			for (int col=0; col<1+r.nextInt(10); col++) {
				line.add(r.nextDouble()+"");
			}
			csv.addLine(line);
			numVals += line.size();
		}
		
		System.out.println("Built CSV with "+csv.getNumRows()+" rows and "+numVals+" values");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		archive.addModule(new CSV_Module(csv));
		assertFalse("Helper class should not have been mapped", archive.hasModule(CSV_BackedModule.class));
		
		File outputFile = new File(parentDir, "csv_test.zip");
		archive.write(outputFile);
		
		archive = new ModuleArchive<>(outputFile);
		CSV_Module module = archive.getModule(CSV_Module.class);
		assertNotNull("Failed to load CSV module", module);
		CSVFile<?> loaded = module.getCSV();
		assertEquals("Row mismatch", csv.getNumRows(), loaded.getNumRows());
		for (int row=0; row<csv.getNumRows(); row++) {
			List<String> origLine = csv.getLine(row);
			List<?> loadedLine = loaded.getLine(row);
			assertEquals("Row "+row+" has a column mismatch", origLine.size(), loadedLine.size());
			for (int col=0; col<origLine.size(); col++)
				assertEquals("Value mismatch at row="+row+", col="+col, origLine.get(col), loadedLine.get(col));
		}

		System.out.println("*** END testLoadSaveCSV() ***");
	}
	
	private static class CSV_Module implements CSV_BackedModule {
		
		private CSVFile<String> csv;
		
		private CSV_Module() {
			
		}

		public CSV_Module(CSVFile<String> csv) {
			this.csv = csv;
		}

		@Override
		public String getFileName() {
			return "file.csv";
		}

		@Override
		public String getName() {
			return "CSV Module";
		}

		@Override
		public CSVFile<?> getCSV() {
			return csv;
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			this.csv = csv;
		}
		
	}

	@Test
	public void testLoadSaveJSON() throws IOException {
		System.out.println("*** testLoadSaveJSON() ***");
		System.out.println("Testing JSON");
		
		// first try default serialization
		TestClass test = new TestClass("My simple value", "Transient text");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		JSON_Module module = new JSON_Module(test);
		archive.addModule(module);
		assertFalse("Helper class should not have been mapped", archive.hasModule(JSON_BackedModule.class));
		
		File outputFile = new File(parentDir, "json_test.zip");
		archive.write(outputFile);
		System.out.println("JSON FILE -------");
		System.out.println(module.getJSON());
		System.out.println("-----------------");
		
		archive = new ModuleArchive<>(outputFile);
		
		JSON_Module newModule = archive.getModule(JSON_Module.class);
		assertNotNull("Failed to load JSON module", newModule);
		TestClass loaded = newModule.obj;
		assertNotNull("Failed to load object from JSON", loaded);
		assertEquals("Value serialization error", test.value, loaded.value);
		assertNull("Shouldn't have deserialized transient value", loaded.transientValue);

		System.out.println("*** END testLoadSaveJSON() ***");
	}
	
	private static class TestClass {
		
		private String value;
		private transient String transientValue;
		
		private TestClass() {
			
		}
		
		public TestClass(String value, String transientValue) {
			super();
			this.value = value;
			this.transientValue = transientValue;
		}
		
	}
	
	private static class JSON_Module implements JSON_BackedModule {
		
		private TestClass obj;

		private JSON_Module() {
			
		}
		
		public JSON_Module(TestClass obj) {
			this.obj = obj;
		}

		@Override
		public String getFileName() {
			return "file.json";
		}

		@Override
		public String getName() {
			return "JSON Module";
		}

		@Override
		public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
			System.out.println("JSON_Module: writing");
			out.beginObject();
			out.name("obj");
			gson.toJson(obj, TestClass.class, out);
			out.endObject();
		}

		@Override
		public void initFromJSON(JsonReader in, Gson gson) throws IOException {
			System.out.println("JSON_Module: loading");
			in.beginObject();
			in.nextName();
			obj = gson.fromJson(in, TestClass.class);
			in.endObject();
			Preconditions.checkNotNull(obj, "Loaded object is null?");
		}
		
	}

	@Test
	public void testLoadSaveJSONAdapter() throws IOException {
		System.out.println("*** testLoadSaveJSONAdapter() ***");
		System.out.println("Testing JSON with a TypeAdpater");
		
		// first try default serialization
		TestClass test = new TestClass("My simple value", "Transient text");
		
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		JSON_AdapterModule module = new JSON_AdapterModule(test);
		archive.addModule(module);
		assertFalse("Helper class should not have been mapped", archive.hasModule(JSON_TypeAdapterBackedModule.class));
		
		File outputFile = new File(parentDir, "json_adapter_test.zip");
		assertFalse("This should be initialized to false", module.adapter.written);
		archive.write(outputFile);
		assertTrue("Adapter wasn't used in writing", module.adapter.written);
		
		System.out.println("JSON FILE -------");
		System.out.println(module.getJSON());
		System.out.println("-----------------");
		
		archive = new ModuleArchive<>(outputFile);
		
		JSON_AdapterModule newModule = archive.getModule(JSON_AdapterModule.class);
		assertNotNull("Failed to load JSON module", newModule);
		assertTrue("Adapter wasn't used in reading", newModule.adapter.read);
		TestClass loaded = newModule.obj;
		assertNotNull("Failed to load object from JSON", loaded);
		assertEquals("Value serialization error", test.value, loaded.value);
		assertEquals("Shoul hav deserialized transient value", test.transientValue, loaded.transientValue);

		System.out.println("*** END testLoadSaveJSONAdapter() ***");
	}
	
	private static class TestClassAdapber extends TypeAdapter<TestClass> {
		
		boolean read = false;
		boolean written = false;

		@Override
		public void write(JsonWriter out, TestClass value) throws IOException {
			written = true;
			out.beginObject();
			out.name("value").value(value.value);
			out.name("transientValue").value(value.transientValue);
			out.endObject();
		}

		@Override
		public TestClass read(JsonReader in) throws IOException {
			read = true;
			String value = null;
			String transientValue = null;
			in.beginObject();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "value":
					value = in.nextString();
					break;
				case "transientValue":
					transientValue = in.nextString();
					break;

				default:
					break;
				}
			}
			
			in.endObject();
			return new TestClass(value, transientValue);
		}
		
	}
	
	private static class JSON_AdapterModule implements JSON_TypeAdapterBackedModule<TestClass> {
		
		private TestClass obj;
		private TestClassAdapber adapter = new TestClassAdapber();

		private JSON_AdapterModule() {
			
		}
		
		public JSON_AdapterModule(TestClass obj) {
			this.obj = obj;
		}

		@Override
		public String getFileName() {
			return "file.json";
		}

		@Override
		public String getName() {
			return "JSON Adapter Module";
		}

		@Override
		public Type getType() {
			return TestClass.class;
		}

		@Override
		public TestClass get() {
			return obj;
		}

		@Override
		public void set(TestClass value) {
			this.obj = value;
		}

		@Override
		public void registerTypeAdapters(GsonBuilder builder) {
			builder.registerTypeAdapter(getType(), adapter);
		}
		
	}

}
