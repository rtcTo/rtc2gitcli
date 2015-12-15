/**
 * File Name: Files.java
 * 
 * Copyright (c) 2015 BISON Schweiz AG, All Rights Reserved.
 */

package to.rtc.cli.migrate.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes all lines of a file using the default platform line separator.
 *
 * @author Patrick Reinhart
 */
public class Files {

	/**
	 * Reads all lines of the given <code>file</code> using the given character
	 * set <code>cs</code> and returns them as a list without any of the line
	 * separators.
	 * 
	 * @param file
	 *            the file being read
	 * @param cs
	 *            the character used for reading
	 * @return a list of all read lines (empty lines as empty string)
	 * @throws IOException
	 *             if the read operation fails
	 */
	public static List<String> readLines(File file, Charset cs)
			throws IOException {
		if (file.exists()) {
			FileInputStream in = new FileInputStream(file);
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(
						in, cs));
				List<String> lines = new ArrayList<String>();
				String line = null;
				while ((line = br.readLine()) != null) {
					lines.add(line);
				}
				return lines;
			} finally {
				in.close();
			}
		} else {
			return new ArrayList<String>();
		}
	}

	/**
	 * Writes all <code>lines</code> given to the <code>file</code> using the
	 * given character set <code>cs</code> and optionally appends them to an
	 * existing file, if <code>append</code> is set to <code>true</code>. The
	 * default platform line separator will be used.
	 * 
	 * @param file
	 *            the file being written/appended
	 * @param lines
	 *            the lines to be written without any line separators
	 * @param cs
	 *            the character set used for writing
	 * @param append
	 *            <code>true</code> if a existing file should be appended,
	 *            <code>false</code> otherwise
	 * @throws IOException
	 *             if the write operation fails
	 */
	public static void writeLines(File file, List<String> lines, Charset cs,
			boolean append) throws IOException {
		FileOutputStream out = new FileOutputStream(file, append);
		try {
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, cs));
			try {
				for (String line : lines) {
					pw.append(line).println();
				}
			} finally {
				pw.close();
			}
		} finally {
			out.close();
		}
	}
}
