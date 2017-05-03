package com.gmail.etordera.imaging;

import java.awt.color.ICC_Profile;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Extract metadata from image files..<br>
 * <br>
 * Usage:<br>
 * <pre>
 * <code>
 *     File imageFile = new File("/path/to/image.jpg");
 *     ImageMetadata md = ImageMetadata.getInstance(imageFile);
 *     System.out.println("  > Type: " + md.getImageType());
 *     System.out.println("  > Size: " + md.getWidth() + " x " + md.getHeight());
 *     ICC_Profile profile = md.getIccProfile();
 *     if (profile != null) {
 *         System.out.println("  > ICC : " + ImageUtils.getProfileDescription(profile));
 *     }
 *     System.out.println("  > Orientation: " + md.getOrientation());
 *     System.out.println("  > Transparent: " + md.isTransparent());
 *     System.out.println("  > Greyscale  : " + md.isGreyscale());
 *     System.out.println("  > RGB        : " + md.isRGB());
 *     System.out.println("  > Indexed    : " + md.isIndexed());
 *     System.out.println("  > Thumbnail  : " + md.hasThumbnail());
 *     System.out.println();
 * </code>
 * </pre>
 */
public class ImageMetadata {

	/** PNG signature bytes. */
	private static final int[] PNG_SIGNATURE = {137, 80, 78, 71, 13, 10, 26, 10};
	/** JPEG signature bytes. */
	private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8};
	/** BMP signature bytes. */
	private static final int[] BMP_SIGNATURE = {0x42, 0x4D};
	/** TIFF (little-endian) signature bytes. */
	private static final int[] TIFF_LE_SIGNATURE = {0x49, 0x49, 0x2A, 0x00};
	/** TIFF (big-endian) signature bytes. */
	private static final int[] TIFF_BE_SIGNATURE = {0x4D, 0x4D, 0x00, 0x2A};
	

	/** List of known file signatures. */
	private static HashMap<ImageType, int[]> s_signatures = new HashMap<>();	
	static {
		s_signatures.put(ImageType.PNG, PNG_SIGNATURE);
		s_signatures.put(ImageType.JPEG, JPEG_SIGNATURE);		
		s_signatures.put(ImageType.BMP, BMP_SIGNATURE);		
		s_signatures.put(ImageType.TIFF_LE, TIFF_LE_SIGNATURE);		
		s_signatures.put(ImageType.TIFF_BE, TIFF_BE_SIGNATURE);		
	}
	
	
	/** Detected image type. */
	private ImageType m_imageType = ImageType.UNKNOWN;
	/** Image width (px). */
	private int m_width;
	/** Image height (px). */
	private int m_height;
	
	/**
	 * Generates an object of the right type for getting metadata
	 * from an image file.
	 * @param filename Path to image file.
	 * @return Metadata object.
	 */
	public static ImageMetadata getInstance(String filename) {
		return getInstance(new File(filename));
	}

	/**
	 * Generates an object of the right type for getting metadata
	 * from an image file.
	 * @param file Image file.
	 * @return Metadata object.
	 */
	public static ImageMetadata getInstance(File file) {
		ImageMetadata md = new ImageMetadata();
		md.setImageType(getImageType(file));
		switch (md.getImageType()) {
			case JPEG:
				JPEGMetadata jpeg = new JPEGMetadata();
				if (jpeg.load(file)) {
					md = jpeg;
				}
				break;
				
			case PNG:
				PNGMetadata png = new PNGMetadata();
				if (png.load(file)) {
					md = png;
				}
				break;
				
			default:
				md.loadPixelSize(file);
				break;				
		}
		
		return md;
	}
		
	
	/**
	 * Detects image type.
	 * @param file File under test.
	 * @return Detected image type, or <code>ImageType.UNKNOWN</code> if not able to detect.
	 */
	public static ImageType getImageType(File file) {
		ImageType imageType = ImageType.UNKNOWN;
		ArrayList<ImageType> discardedTypes = new ArrayList<>();
		try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(file))) {
			int pos = 0;
			int b;
			while (discardedTypes.size() < s_signatures.size() && imageType == ImageType.UNKNOWN && (b = is.read()) != -1) {
				for (ImageType type : s_signatures.keySet()) {
					if (!discardedTypes.contains(type)) {
						if (s_signatures.get(type)[pos] == b) {
							if ((pos+1) == s_signatures.get(type).length) {
								imageType = type;
							}
						} else {
							discardedTypes.add(type);
						}						
					}
				}
				pos++;
			}
		} catch (Exception e) {
			System.err.println("Unable to read file: " + file.getAbsolutePath() + ": " + e.getMessage());
		}
		return imageType;
	}
	

	/**
	 * Gets ICC profile embedded in an image file.
	 * @param imagePath Path to image file.
	 * @return Embedded ICC Profile, or <code>null</code> if not detected. 
	 */
	public static ICC_Profile getIccProfile(String imagePath) {
		return getIccProfile(new File(imagePath));
	}
	

	/**
	 * Gets ICC profile embedded in an image file.
	 * @param imageFile Image file.
	 * @return Embedded ICC Profile, or <code>null</code> if not detected. 
	 */
	public static ICC_Profile getIccProfile(File imageFile) {
		ImageMetadata md = getInstance(imageFile);
		return md.getIccProfile();
	}
	
	
	/**
	 * Gets the detected image type.
	 * @return Detected image type.
	 */
	public ImageType getImageType() {
		return m_imageType;
	}

	
	/**
	 * Sets the detected image type.
	 * @param imageType Detected image type.
	 */
	private void setImageType(ImageType imageType) {
		m_imageType = imageType;
	}

	
	/**
	 * Gets imatge width.
	 * @return Image width (px), or <code>0</code> if not detected.
	 */
	public int getWidth() {
		return m_width;
	}

	
	/**
	 * Gets imatge height.
	 * @return Image height (px), or <code>0</code> if not detected.
	 */
	public int getHeight() {
		return m_height;
	}


	/**
	 * Detects and sotres pixel dimensions of an image file. 
	 * @param imageFile Image file under analysis.
	 */
	protected void loadPixelSize(File imageFile) {
		ImageInputStream is = null;
		ImageReader reader = null;
		try {
			is = ImageIO.createImageInputStream(imageFile);
		    Iterator<ImageReader> readers = ImageIO.getImageReaders(is);
		    if (readers.hasNext()) {
		        reader = readers.next();
	            reader.setInput(is);
		        m_width = reader.getWidth(0);
		        m_height = reader.getHeight(0);
	            reader.dispose();
		    }
		    is.close();
		    
		} catch (Exception e) {
			if (reader != null) try {reader.dispose();} catch (Exception ex) {/* Ignore */}
			if (is != null) try {is.close();} catch (Exception ex) {/* Ignore */}
		}		
	}
	
	
	/**
	 * Gets color depth.
	 * @return Color depth as number of bits, or <code>0</code> if not detected.
	 */
	public int getBitDepth() {
		return 0;
	}

	
	/** 
	 * Tells whether the image is in grayscale mode.
	 * @return <code>true</code> if image is in grayscale mode, <code>false</code>otherwise.
	 */
	public boolean isGreyscale() {
		return false;
	}
	

	/** 
	 * Tells whether the image is in RGB mode.
	 * @return <code>true</code> if image is in RGB mode, <code>false</code>otherwise.
	 */
	public boolean isRGB() {
		return false;
	}

	
	/** 
	 * Tells whether the image is in indexed mode.
	 * @return <code>true</code> if image is in indexed mode, <code>false</code>otherwise.
	 */
	public boolean isIndexed() {
		return false;
	}

	
	/** 
	 * Tells whether the image is in CMYK mode.
	 * @return <code>true</code> if image is in CMYK mode, <code>false</code>otherwise.
	 */
	public boolean isCMYK() {
		return false;
	}

	
	/** 
	 * Tells whether the image has transparency data (alpha channel).
	 * @return <code>true</code> if image has transparency data, <code>false</code>otherwise.
	 */
	public boolean isTransparent() {
		return false;
	}
	
	/**
	 * Gets the embedded ICC profile.
	 * @return Embedded ICC Profile, or <code>null</code> if not detected. 
	 */
	public ICC_Profile getIccProfile() {
		return null;
	}
	
	
	/**
	 * Gets orientation of image pixels.
	 * @return Orientation of image pixels.
	 */
	public ImageOrientation getOrientation() {
		return ImageOrientation.TOP;
	}
	
	
	/**
	 * Tells wheter an embedded thumbnail was detected.
	 * @return <code>true</code> if an embedded thumbnail was detected, <code>false</code> otherwise.
	 */
	public boolean hasThumbnail() {
		return false;
	}
	
	
	/**
	 * Generates a stream for getting embedded thumbnail data.
	 * @return stream for getting embedded thumbnail data, or <code>null</code> if thumbnail was not detected.
	 */
	public ByteArrayInputStream getThumbnailAsInputStream() {
		return null;
	}

	
	/**
	 * Gets image horizontal resolution.
	 * @return Image horizontal resolution (dpi), or <code>0</code> if not detected.
	 */
	public double getDpiX() {
		return 0;
	}

	
	/**
	 * Gets image vertical resolution.
	 * @return Image vertical resolution (dpi), or <code>0</code> if not detected.
	 */
	public double getDpiY() {
		return 0;
	}
	
	
	/**
	 * Reads a number of bytes from a stream.
	 * @param is Input stream for reading.
	 * @param numBytes Number of bytes to read.
	 * @return Read bytes.
	 * @throws IOException If it is not possible to read the specified number of bytes.
	 */
	protected static byte[] readBytes(InputStream is, int numBytes) throws IOException {
		byte[] result = new byte[numBytes];
		int read = 0;
		while (read < numBytes) {
			int count = is.read(result);
			if (count == -1) {
				throw new IOException("End of stream reached before reading all requested bytes (read "+read+", requested "+numBytes+").");
			}
			read += count;
		}
		return result;
	}
	
}
