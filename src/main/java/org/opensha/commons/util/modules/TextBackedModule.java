package org.opensha.commons.util.modules;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

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
		BufferedWriter bWrite = new BufferedWriter(new OutputStreamWriter(out));
		bWrite.write(getText());
		bWrite.flush();
	}

	@Override
	default void initFromStream(BufferedInputStream in) throws IOException {
		BufferedReader bRead = new BufferedReader(new InputStreamReader(in));
		StringBuilder text = null;
		
		String line;
		while ((line = bRead.readLine()) != null) {
			if (text == null)
				text = new StringBuilder();
			else
				text.append("\n");
			text.append(line);
		}
		if (text == null)
			text = new StringBuilder("");
		
		setText(text.toString());
	}

}
