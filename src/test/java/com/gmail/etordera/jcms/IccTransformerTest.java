package com.gmail.etordera.jcms;

import static org.junit.Assert.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

/**
 * Tests for {@link com.gmail.etordera.jcms.IccTransformer} class 
 *
 */
public class IccTransformerTest {

	/**
	 * Test method for {@link com.gmail.etordera.jcms.IccTransformer#transform(java.io.File, java.io.File)}.
	 */
	@Test
	public void testTransformFileFile() {
		
		System.out.println("> IccTransformer.transform(File, File) started.");

		// Define paths
		String resourcesPath = "src/test/resources/";
		File inputFolder = new File(resourcesPath + "input");
		File outputFolder = new File(resourcesPath + "output");
		File expectedFolder = new File(resourcesPath + "expected");
		
		// Check paths
		assertTrue("Input folder missing.", inputFolder.isDirectory());
		assertTrue("Expected folder missing.", expectedFolder.isDirectory());
		assertTrue("Can't create outputfolder", outputFolder.isDirectory() || outputFolder.mkdirs());
		
		// Clear outputFolder
		File[] outputFiles = outputFolder.listFiles((dir, name)->{
			return name.toLowerCase().matches(".*\\.jpe?g");
		});
		for (File outputFile : outputFiles) {
			assertTrue("Can't delete previous output file: " + outputFile.getName(), outputFile.delete());
		}
		
		// Perform conversions
		IccProfile destProfile = null;
		IccTransformer transformer = null;
		try {
			destProfile = new IccProfile(IccProfile.PROFILE_ADOBERGB);
			transformer = new IccTransformer(destProfile.getICC_Profile(), JCMS.INTENT_RELATIVE_COLORIMETRIC, true);
			File[] inputFiles = inputFolder.listFiles((dir, name)->{
				return name.toLowerCase().matches(".*\\.jpe?g");
			});
			for (File in : inputFiles) {
				File out = new File(outputFolder, "converted-" + in.getName());
				System.out.print("Transform " + in.getName() + " to " + out.getName() + "...");
				transformer.transform(in, out);
				System.out.println("Ok");
			}
		} catch (JCMSException e) {
			fail("JCMS Exception: " + e.getMessage());
			
		} finally {
			if (transformer != null) transformer.dispose();
			if (destProfile != null) destProfile.dispose();
		}
		
		// Test results
		outputFiles = outputFolder.listFiles((dir, name)->{
			return name.toLowerCase().matches(".*\\.jpe?g");
		});
		for (File out : outputFiles) {
			File expected = new File(expectedFolder, out.getName());
			System.out.print("Checking " + out.getName() + "...");
			assertTrue(out.getName() + " did not result as expected", compareFiles(out, expected));
			System.out.println("Ok");
		}
		
		System.out.println("> IccTransformer.transform(File, File) finished.");		
	}

	
	/**
	 * Checks if two files have identical byte content.
	 * @param file1 First file to compare.
	 * @param file2 Second file to compare.
	 * @return <code>true</code> only if both are valid files and have the exact same byte content.
	 */
	private boolean compareFiles(File file1, File file2) {
		// Validate parameters
		if (file1 == null || file2 == null) {
			return false;
		}
		if (!file1.isFile() || !file2.isFile()) {
			return false;
		}
		
		// Check length
		if (file1.length() != file2.length()) {
			return false;
		}
		
		// Compare bytes
		try (
				BufferedInputStream in1 = new BufferedInputStream(new FileInputStream(file1));
				BufferedInputStream in2 = new BufferedInputStream(new FileInputStream(file2));				
			)
		{
			int byte1 = -1;
			int byte2 = -1;
			do {
				byte1 = in1.read();
				byte2 = in2.read();
				if (byte1 != byte2) {
					return false;
				}
			} while (byte1 >= 0);	
		} catch (Exception e) {
			System.out.println("Exception: " + e.getMessage());
			return false;
		}
		
		return true;
	}
	
}
