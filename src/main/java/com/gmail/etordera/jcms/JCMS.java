package com.gmail.etordera.jcms;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Runtime;
import java.lang.Throwable;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility class for the JavaCMS library.<br>
 * <br>
 * Defines common constants and provides JNI access to LittleCMS library.
 * 
 * @author Enric Tordera
 */
public class JCMS {

	/** Path to temp dir where native libraries are extracted to during runtime */
	private static String m_tempDir = null;

	// Ensure loading of shared object / dll for JNI access to LittleCMS library
	static {
		try {
			// Try to load library in current library path
			System.loadLibrary("jcms");
			
		} catch (UnsatisfiedLinkError e) {
			
			// Get libraries from classpath resources
			String basePath = "com/gmail/etordera/jcms/lib/"+getPlatform();
			String[] libs = getResourceSharedObjects(basePath);

			try {
				// Prepare temp directory for extracted resources
				Path tempDir = Files.createTempDirectory("jcmstmp");
				m_tempDir = tempDir.toFile().getAbsolutePath();
				tempDir.toFile().deleteOnExit();
				
				// Windows: Make sure temp dir is deleted on JVM exit
				if (getPlatform().contains("windows")) {
					Runtime.getRuntime().addShutdownHook(new Thread() {
						public void run() {
							try {
								String[] cmdArray = {
										"cmd", "/c", "ping", "-n", "5", "127.0.0.1",
										"&", "cmd", "/c", "del", m_tempDir+"\\*.dll",
										"&", "cmd", "/c", "rmdir", m_tempDir
								};
								Runtime.getRuntime().exec(cmdArray);
								
							} catch (IOException e) {
								System.out.println("IOException: "+e.getMessage());
							}
						}
					});					
				}
							
				// Try to load libraries from classpath resources
				if (!loadSharedObjects(basePath, new LinkedList<String>(Arrays.asList(libs)), tempDir)) {
					throw new UnsatisfiedLinkError("Failed to load JCMS native libraries.");
				}

			} catch (IOException ioex) {
				throw new UnsatisfiedLinkError("Failed to load JCMS native library ("+ioex.getMessage()+")");
			}			
		}
	}
		
	/**
	 * Extracts shared objects from classpath and loads them, taking care of order loading dependencies.
	 * 
	 * @param basePath Base path in classpath where shared objects are stored
	 * @param soNames Names of shared objects to load from classpath
	 * @param tempDir Temporary directory to be used for extraction
	 * @return <code>true</code> on success, <code>false</code> on error
	 */
	private static boolean loadSharedObjects(String basePath, List<String> soNames, Path tempDir) {
		Iterator<String> it = soNames.iterator();
		boolean someFailed = false;
		boolean someLoaded = false;
		while (it.hasNext()) {
			String soName = it.next();
			try {
				// Extract shared object to temp file
				InputStream in = JCMS.class.getClassLoader().getResourceAsStream(basePath+"/"+soName);
				File tempFile = new File(tempDir.toFile().getAbsolutePath()+"/"+soName);
				Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				in.close();

				// Load library from temp file
				System.load(tempFile.getAbsolutePath());

				// Register temp file deletion
				tempFile.deleteOnExit();
				
				// Delete from list
				it.remove();
				
				// Register success
				someLoaded = true;
				
			} catch (Throwable t) {
				// Register fail
				someFailed = true;
			}				
		}
		
		// Recursive call manages loading order dependencies
		if (someLoaded && someFailed) {
			return loadSharedObjects(basePath, soNames, tempDir);
		}
		
		return someLoaded;
	}
	
	/**
	 * Gets a string identifier of the runtime platform (OS name and architecture).<br>
	 * Examples: <code>windows32</code>, <code>linux64</code>
	 * 
	 * @return A string identifier for runtime platform.
	 */
	private static String getPlatform() {
		String osName = System.getProperty("os.name").toLowerCase();
		String osArch = System.getProperty("os.arch");
		
		String libOs = "";
		if (osName.contains("linux")) {
			libOs = "linux";
		} else if (osName.contains("windows")) {
			libOs = "windows";
		} else if (osName.contains("mac") || osName.contains("darwin")) {
			libOs = "mac";
		}
		
		String libArch = "32";
		if (osArch.contains("64")) {
			libArch = "64";
		}
		
		return libOs+libArch;
	}
	
	/**
	 * Gets a listing of all shared object files (.dll and .so) found in a path
	 * inside the current classpath.
	 * 
	 * @param path Absolute path inside the classpath where to look for shared object files (without leading slash)
	 * @return A list of names of all shared object files (can be empty, never <code>null</code>)
	 */
	private static String[] getResourceSharedObjects(String path) {
		String[] listing = new String[] {};
		
		try {
			URL dirURL = JCMS.class.getClassLoader().getResource(path);
			if (dirURL != null && dirURL.getProtocol().equals("file")) {
				File dir = new File(dirURL.toURI());
				listing = dir.list(new FilenameFilter() {
					public boolean accept(File dir, String name) {
						return name.toLowerCase().matches(".*\\.(so|dll)");
					}					
				});
			} 
			
			if (dirURL == null) {
				String me = JCMS.class.getName().replace(".", "/")+".class";
				dirURL = JCMS.class.getClassLoader().getResource(me);
			}
			  
			if (dirURL.getProtocol().equals("jar")) {
				String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!"));
				JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
				Enumeration<JarEntry> entries = jar.entries();
				Set<String> result = new HashSet<String>();
				while (entries.hasMoreElements()) {
				  String name = entries.nextElement().getName();
				  if (name.startsWith(path)) {
				    String entry = name.substring(path.length());
				    int checkSubdir = entry.indexOf("/");
				    if (checkSubdir == 0) {
				      entry = entry.substring(1);
				    }
				    if (entry.toLowerCase().matches(".*\\.(so|dll)")) {
				    	result.add(entry);
				    }
				  }
				}
				jar.close();
				listing = result.toArray(new String[result.size()]);
			} 
				    	  
		} catch (Exception e) {
			System.err.println("Error while listing shared object resources: "+e.getMessage());
		}
		
		return listing;
	}
	
	
	
	// ---------------------------------------------------------------
	// Constants: Rendering intents for color transformations
	// ---------------------------------------------------------------
	/** Rendering intent Perceptual */
	public static final int INTENT_PERCEPTUAL = 0;
	/** Rendering intent Relative Colorimetric */
	public static final int INTENT_RELATIVE_COLORIMETRIC = 1;
	/** Rendering intent Saturation */
	public static final int INTENT_SATURATION = 2;
	/** Rendering intent Absolute Colorimetric */
	public static final int INTENT_ABSOLUTE_COLRIMETRIC = 3;

	
	// ---------------------------------------------------------------
	// Constants: Bitmap buffers format types
	// ---------------------------------------------------------------
	public static final int TYPE_GRAY_8            = 196617;
	public static final int TYPE_GRAY_8_REV        = 204809;
	public static final int TYPE_GRAY_16           = 196618;
	public static final int TYPE_GRAY_16_REV       = 204810;
	public static final int TYPE_GRAY_16_SE        = 198666;
	public static final int TYPE_GRAYA_8           = 196745;
	public static final int TYPE_GRAYA_16          = 196746;
	public static final int TYPE_GRAYA_16_SE       = 198794;
	public static final int TYPE_GRAYA_8_PLANAR    = 200841;
	public static final int TYPE_GRAYA_16_PLANAR   = 200842;
	public static final int TYPE_RGB_8             = 262169;
	public static final int TYPE_RGB_8_PLANAR      = 266265;
	public static final int TYPE_BGR_8             = 263193;
	public static final int TYPE_BGR_8_PLANAR      = 267289;
	public static final int TYPE_RGB_16            = 262170;
	public static final int TYPE_RGB_16_PLANAR     = 266266;
	public static final int TYPE_RGB_16_SE         = 264218;
	public static final int TYPE_BGR_16            = 263194;
	public static final int TYPE_BGR_16_PLANAR     = 267290;
	public static final int TYPE_BGR_16_SE         = 265242;
	public static final int TYPE_RGBA_8            = 262297;
	public static final int TYPE_RGBA_8_PLANAR     = 266393;
	public static final int TYPE_RGBA_16           = 262298;
	public static final int TYPE_RGBA_16_PLANAR    = 266394;
	public static final int TYPE_RGBA_16_SE        = 264346;
	public static final int TYPE_ARGB_8            = 278681;
	public static final int TYPE_ARGB_8_PLANAR     = 282777;
	public static final int TYPE_ARGB_16           = 278682;
	public static final int TYPE_ABGR_8            = 263321;
	public static final int TYPE_ABGR_8_PLANAR     = 267417;
	public static final int TYPE_ABGR_16           = 263322;
	public static final int TYPE_ABGR_16_PLANAR    = 267418;
	public static final int TYPE_ABGR_16_SE        = 265370;
	public static final int TYPE_BGRA_8            = 279705;
	public static final int TYPE_BGRA_8_PLANAR     = 283801;
	public static final int TYPE_BGRA_16           = 279706;
	public static final int TYPE_BGRA_16_SE        = 281754;
	public static final int TYPE_CMY_8             = 327705;
	public static final int TYPE_CMY_8_PLANAR      = 331801;
	public static final int TYPE_CMY_16            = 327706;
	public static final int TYPE_CMY_16_PLANAR     = 331802;
	public static final int TYPE_CMY_16_SE         = 329754;
	public static final int TYPE_CMYK_8            = 393249;
	public static final int TYPE_CMYKA_8           = 393377;
	public static final int TYPE_CMYK_8_REV        = 401441;
	public static final int TYPE_YUVK_8            = 401441;
	public static final int TYPE_CMYK_8_PLANAR     = 397345;
	public static final int TYPE_CMYK_16           = 393250;
	public static final int TYPE_CMYK_16_REV       = 401442;
	public static final int TYPE_YUVK_16           = 401442;
	public static final int TYPE_CMYK_16_PLANAR    = 397346;
	public static final int TYPE_CMYK_16_SE        = 395298;
	public static final int TYPE_KYMC_8            = 394273;
	public static final int TYPE_KYMC_16           = 394274;
	public static final int TYPE_KYMC_16_SE        = 396322;
	public static final int TYPE_KCMY_8            = 409633;
	public static final int TYPE_KCMY_8_REV        = 417825;
	public static final int TYPE_KCMY_16           = 409634;
	public static final int TYPE_KCMY_16_REV       = 417826;
	public static final int TYPE_KCMY_16_SE        = 411682;
	public static final int TYPE_CMYK5_8           = 1245225;
	public static final int TYPE_CMYK5_16          = 1245226;
	public static final int TYPE_CMYK5_16_SE       = 1247274;
	public static final int TYPE_KYMC5_8           = 1246249;
	public static final int TYPE_KYMC5_16          = 1246250;
	public static final int TYPE_KYMC5_16_SE       = 1248298;
	public static final int TYPE_CMYK6_8           = 1310769;
	public static final int TYPE_CMYK6_8_PLANAR    = 1314865;
	public static final int TYPE_CMYK6_16          = 1310770;
	public static final int TYPE_CMYK6_16_PLANAR   = 1314866;
	public static final int TYPE_CMYK6_16_SE       = 1312818;
	public static final int TYPE_CMYK7_8           = 1376313;
	public static final int TYPE_CMYK7_16          = 1376314;
	public static final int TYPE_CMYK7_16_SE       = 1378362;
	public static final int TYPE_KYMC7_8           = 1377337;
	public static final int TYPE_KYMC7_16          = 1377338;
	public static final int TYPE_KYMC7_16_SE       = 1379386;
	public static final int TYPE_CMYK8_8           = 1441857;
	public static final int TYPE_CMYK8_16          = 1441858;
	public static final int TYPE_CMYK8_16_SE       = 1443906;
	public static final int TYPE_KYMC8_8           = 1442881;
	public static final int TYPE_KYMC8_16          = 1442882;
	public static final int TYPE_KYMC8_16_SE       = 1444930;
	public static final int TYPE_CMYK9_8           = 1507401;
	public static final int TYPE_CMYK9_16          = 1507402;
	public static final int TYPE_CMYK9_16_SE       = 1509450;
	public static final int TYPE_KYMC9_8           = 1508425;
	public static final int TYPE_KYMC9_16          = 1508426;
	public static final int TYPE_KYMC9_16_SE       = 1510474;
	public static final int TYPE_CMYK10_8          = 1572945;
	public static final int TYPE_CMYK10_16         = 1572946;
	public static final int TYPE_CMYK10_16_SE      = 1574994;
	public static final int TYPE_KYMC10_8          = 1573969;
	public static final int TYPE_KYMC10_16         = 1573970;
	public static final int TYPE_KYMC10_16_SE      = 1576018;
	public static final int TYPE_CMYK11_8          = 1638489;
	public static final int TYPE_CMYK11_16         = 1638490;
	public static final int TYPE_CMYK11_16_SE      = 1640538;
	public static final int TYPE_KYMC11_8          = 1639513;
	public static final int TYPE_KYMC11_16         = 1639514;
	public static final int TYPE_KYMC11_16_SE      = 1641562;
	public static final int TYPE_CMYK12_8          = 1704033;
	public static final int TYPE_CMYK12_16         = 1704034;
	public static final int TYPE_CMYK12_16_SE      = 1706082;
	public static final int TYPE_KYMC12_8          = 1705057;
	public static final int TYPE_KYMC12_16         = 1705058;
	public static final int TYPE_KYMC12_16_SE      = 1707106;
	public static final int TYPE_XYZ_16            = 589850;
	public static final int TYPE_Lab_8             = 655385;
	public static final int TYPE_LabV2_8           = 1966105;
	public static final int TYPE_ALab_8            = 671897;
	public static final int TYPE_ALabV2_8          = 1982617;
	public static final int TYPE_Lab_16            = 655386;
	public static final int TYPE_LabV2_16          = 1966106;
	public static final int TYPE_Yxy_16            = 917530;
	public static final int TYPE_YCbCr_8           = 458777;
	public static final int TYPE_YCbCr_8_PLANAR    = 462873;
	public static final int TYPE_YCbCr_16          = 458778;
	public static final int TYPE_YCbCr_16_PLANAR   = 462874;
	public static final int TYPE_YCbCr_16_SE       = 460826;
	public static final int TYPE_YUV_8             = 524313;
	public static final int TYPE_YUV_8_PLANAR      = 528409;
	public static final int TYPE_YUV_16            = 524314;
	public static final int TYPE_YUV_16_PLANAR     = 528410;
	public static final int TYPE_YUV_16_SE         = 526362;
	public static final int TYPE_HLS_8             = 851993;
	public static final int TYPE_HLS_8_PLANAR      = 856089;
	public static final int TYPE_HLS_16            = 851994;
	public static final int TYPE_HLS_16_PLANAR     = 856090;
	public static final int TYPE_HLS_16_SE         = 854042;
	public static final int TYPE_HSV_8             = 786457;
	public static final int TYPE_HSV_8_PLANAR      = 790553;
	public static final int TYPE_HSV_16            = 786458;
	public static final int TYPE_HSV_16_PLANAR     = 790554;
	public static final int TYPE_HSV_16_SE         = 788506;
	public static final int TYPE_NAMED_COLOR_INDEX = 10;
	public static final int TYPE_XYZ_FLT          = 4784156;
	public static final int TYPE_Lab_FLT          = 4849692;
	public static final int TYPE_LabA_FLT         = 4849820;
	public static final int TYPE_GRAY_FLT         = 4390924;
	public static final int TYPE_RGB_FLT          = 4456476;
	public static final int TYPE_RGBA_FLT         = 4456604;
	public static final int TYPE_ARGB_FLT         = 4472988;
	public static final int TYPE_BGR_FLT          = 4457500;
	public static final int TYPE_BGRA_FLT         = 4474012;
	public static final int TYPE_ABGR_FLT         = 4457500;
	public static final int TYPE_CMYK_FLT         = 4587556;
	public static final int TYPE_XYZ_DBL          = 4784152;
	public static final int TYPE_Lab_DBL          = 4849688;
	public static final int TYPE_GRAY_DBL         = 4390920;
	public static final int TYPE_RGB_DBL          = 4456472;
	public static final int TYPE_BGR_DBL          = 4457496;
	public static final int TYPE_CMYK_DBL         = 4587552;
	public static final int TYPE_GRAY_HALF_FLT    = 4390922;
	public static final int TYPE_RGB_HALF_FLT     = 4456474;
	public static final int TYPE_RGBA_HALF_FLT    = 4456602;
	public static final int TYPE_CMYK_HALF_FLT    = 4587554;
	public static final int TYPE_ARGB_HALF_FLT    = 4472986;
	public static final int TYPE_BGR_HALF_FLT     = 4457498;
	public static final int TYPE_BGRA_HALF_FLT    = 4474010;
	public static final int TYPE_ABGR_HALF_FLT    = 4457498;	
	
	
	// ---------------------------------------------------------------
	// Constants: Flags for color transforms
	// ---------------------------------------------------------------
	/** Inhibit 1-pixel cache */
	public static final int CMSFLAGS_NOCACHE = 0x0040;     
	/** Inhibit optimizations */
	public static final int CMSFLAGS_NOOPTIMIZE = 0x0100;     
	/** Don't transform anyway */
	public static final int CMSFLAGS_NULLTRANSFORM = 0x0200;     
	/** Out of Gamut alarm */
	public static final int CMSFLAGS_GAMUTCHECK = 0x1000;     
	/** Do softproofing */
	public static final int CMSFLAGS_SOFTPROOFING = 0x4000;     
	public static final int CMSFLAGS_BLACKPOINTCOMPENSATION = 0x2000; 
	/** Don't fix scum dot */
	public static final int CMSFLAGS_NOWHITEONWHITEFIXUP = 0x0004;     
	/** Use more memory to give better accurancy */
	public static final int CMSFLAGS_HIGHRESPRECALC = 0x0400;     
	/** Use less memory to minimize resouces */
	public static final int CMSFLAGS_LOWRESPRECALC = 0x0800;     
	/** Create 8 bits devicelinks */
	public static final int CMSFLAGS_8BITS_DEVICELINK = 0x0008;    
	/** Guess device class (for transform2devicelink) */
	public static final int CMSFLAGS_GUESSDEVICECLASS = 0x0020;    
	/** Keep profile sequence for devicelink creation */
	public static final int CMSFLAGS_KEEP_SEQUENCE = 0x0080;    
	/** Force CLUT optimization */
	public static final int CMSFLAGS_FORCE_CLUT = 0x0002;     
	/** create postlinearization tables if possible */
	public static final int CMSFLAGS_CLUT_POST_LINEARIZATION = 0x0001;     
	/** create prelinearization tables if possible */
	public static final int CMSFLAGS_CLUT_PRE_LINEARIZATION = 0x0010;     
	public static final int CMSFLAGS_NODEFAULTRESOURCEDEF = 0x01000000; 	
		
	
	// ---------------------------------------------------------------
	// LittleCMS library native access functions
	// ---------------------------------------------------------------
	
	/**
	 * Opens a file-based ICC profile returning a handle to it.
	 * 
	 * @param filename File name with full path
	 * @param mode "r" for normal operation, "w" for profile creation
	 * @return A handle to an ICC profile object on success, <code>0</code> on error.
	 */
	public static native long cmsOpenProfileFromFile(String filename, String mode);
	
	/**
	 * Opens an ICC profile which is entirely contained in a memory block, returning
	 * a handle to it.
	 * 
	 * @param buffer Data buffer containing the profile
	 * @return A handle to an ICC profile object on success, <code>0</code> on error.
	 */
	public static native long cmsOpenProfileFromMem(byte[] buffer);
	
	/**
	 * Closes a profile handle and frees any associated resource. Can return error when creating disk 
	 * profiles, as this function flushes the data to disk.
	 * 
	 * @param hProfile Handle to a profile object.
	 * @return <code>true</code> on success, <code>false</code> on error
	 */
	public static native boolean cmsCloseProfile(long hProfile);
	
	
	/**
	 * Gets profile description information string stored inside the ICC Profile.
	 * 
	 * @param hProfile Handle to a profile object
	 * @return String description of the ICC Profile
	 */
	public static native String cmsGetProfileInfoASCII(long hProfile);
	
	/**
	 * Saves contents of the profile to a memory buffer.
	 * 
	 * @param hProfile Handle to a profile object
	 * @return Memory buffer with profile contents. It will be empty on error.
	 */
	public static native byte[] cmsSaveProfileToMem(long hProfile);
	
	/**
	 * Creates a color transform for translating bitmaps.
	 * 
	 * @param hInputProfile Handle to a profile object capable to work in input direction
	 * @param inputFormat Input bitmap buffer type identifier (TYPE_*)
	 * @param hOutputProfile Handle to a profile object capable to work in output direction
	 * @param outputFormat Output bitmap buffer type identifier (TYPE_*)
	 * @param intent Intent identifier (INTENT_*)
	 * @param flags Bit-field constants for the conversion (CMSFLAGS_*)
	 * @return A handle to a transform object on success, <code>0</code> on error.
	 */
	public static native long cmsCreateTransform(long hInputProfile, int inputFormat, long hOutputProfile, int outputFormat, int intent, int flags);
	
	/**
	 * Closes a transform handle and frees any associated memory. This function does NOT free the
	 * profiles used to create the transform.
	 * @param hTransform Handle to the transform object to be freed.
	 */
	public static native void cmsDeleteTransform(long hTransform);
	
	/**
	 * Translates bitmaps according parameters of a predefined color transform.
	 * @param hTransform Handle to transform
	 * @param inputBuffer Input bitmap data buffer
	 * @param outputBuffer Output bitmap data buffer
	 * @param size Number of PIXELS to be transformed
	 */
	public static native void cmsDoTransform(long hTransform, byte[] inputBuffer, byte[] outputBuffer, int size);
	
	/**
	 * Create an ICC virtual profile for sRGB space.
	 * @return A handle to an ICC profile object on success, <code>0</code> on error
	 */
	public static native long cmsCreate_sRGBProfile();
	
	/**
	 * Creates a gray profile based on D50 white point and custom gamma.
	 * 
	 * @param gamma Gamma value that defines the transfer function of the gray profile.
	 * @return A handle to an ICC profile object on success, <code>0</code> on error
	 */
	public static native long cmsCreateGrayProfile(double gamma);
}
