package org.opensha.commons.util.modules.helpers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleHelper;

import com.google.common.base.Preconditions;

/**
 * Helper interface for {@link ArchivableModule}'s that are backed by a simple text file. Implementations need only
 * implement {@link #getFileName()}, {@link #getTest()}, and {@link #setText(String)}.
 * 
 * @author kevin
 *
 */
@ModuleHelper // don't map this class to any implementation in ModuleContainer
public interface TextBackedModule extends FileBackedModule {
	
	/**
	 * @return text that represents this module
	 */
	public String getText();
	
	/**
	 * Set text that represents this module
	 * @param text
	 */
	public void setText(String text);

	@Override
	default void writeToStream(BufferedOutputStream out) throws IOException {
		writeToStream(out, getText());
	}
	
	public static void writeToStream(OutputStream out, String text) throws IOException {
		Preconditions.checkNotNull(text, "Text is null, cannot write to stream");
		BufferedWriter bWrite = new BufferedWriter(new OutputStreamWriter(out));
		bWrite.write(text);
		bWrite.flush();
	}

	@Override
	default void initFromStream(BufferedInputStream in) throws IOException {
		setText(readFromStream(in));
	}
	
	public static String readFromStream(InputStream in) throws IOException {
		InputStreamReader read = new InputStreamReader(in);
		StringBuilder text = new StringBuilder();
		
		char[] buff = new char[1000];
		for (int charsRead; (charsRead = read.read(buff)) != -1;)
			text.append(buff, 0, charsRead);
		
		return text.toString();
	}

}
