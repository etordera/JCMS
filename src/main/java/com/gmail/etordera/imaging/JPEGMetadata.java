package com.gmail.etordera.imaging;

import java.awt.Dimension;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;

/**
 * Gets metadata from JPEG images.
 * 
 */
public class JPEGMetadata extends ImageMetadata {

	/** Top orientation EXIF identifier. */
	private static final int EXIF_ORIENTATION_UP = 1;
	/** Left orientation EXIF identifier. */
	private static final int EXIF_ORIENTATION_LEFT = 6;
	/** Right orientation EXIF identifier. */
	private static final int EXIF_ORIENTATION_RIGHT = 8;
	/** Upside down orientation EXIF identifier. */
	private static final int EXIF_ORIENTATION_DOWN = 3;
			
	/** JPEG file name */
	private String m_filename;
	
	/** Metadata: Image orientation */
	private long m_orientation = EXIF_ORIENTATION_UP;
	/** Metadata: EXIF color space tag */
	private long m_exifColorSpace = 0;
	/** <code>true</code> only if Adobe APP14 JPEG segment was found */
	private boolean m_adobeApp14Found = false;
	/** Metadata: Adobe color transform code */
	private int m_adobeTransform = ADOBE_TRANSFORM_UNKNOWN;
	/** Metadata: Thumbnail offset */ 
	private long m_thumbPos = 0;
	/** Metadata: Thumbnail size in bytes */
	private long m_thumbSize = 0;
	/** Metadata: ICC profile fragments offsets */ 
	private Vector<Long> m_iccPositions = null;
	/** Metadata: ICC profile fragments sizes */ 
	private Vector<Long> m_iccSizes = null;
	/** Tells whether image contains a JFIF marker. */
	private boolean m_isJFIF = false;
	/** Tells whether image contains an EXIF marker. */
	private boolean m_isEXIF = false;
	/** Number of color bands/channels. */
	private int m_numBands;
	/** Horizontal print resolution (dpi). */
	private double m_dpiX;
	/** Vertical print resolution (dpi). */
	private double m_dpiY;

	/* Adobe Constants */
	/**	Adobe color transform unknown (GRAY, RGB or CMYK values) */
	public static final int ADOBE_TRANSFORM_UNKNOWN = 0;
	/**	Adobe color transform YCbCr */
	public static final int ADOBE_TRANSFORM_YCbCr = 1;
	/**	Adobe color transform YCCK */
	public static final int ADOBE_TRANSFORM_YCCK = 2;
	
	/* Exif color space constants */
	/** ID for sRGB Exif color space */
	public static final int EXIF_CS_SRGB = 1;
	/** ID for AdobeRGB Exif color space */
	public static final int EXIF_CS_ADOBERGB = 2;
	/** ID for unknown Exif color space */
	public static final int EXIF_CS_UNKNOWN = 0xFFFF;
	
	/* JPEG Constants */
	private static final byte JPEG_MARKER_APP0  = (byte)0xE0;
	private static final byte JPEG_MARKER_APP1  = (byte)0xE1;
	private static final byte JPEG_MARKER_APP2  = (byte)0xE2;
	private static final byte JPEG_MARKER_APP3  = (byte)0xE3;
	private static final byte JPEG_MARKER_APP4  = (byte)0xE4;
	private static final byte JPEG_MARKER_APP5  = (byte)0xE5;
	private static final byte JPEG_MARKER_APP6  = (byte)0xE6;
	private static final byte JPEG_MARKER_APP7  = (byte)0xE7;
	private static final byte JPEG_MARKER_APP8  = (byte)0xE8;
	private static final byte JPEG_MARKER_APP9  = (byte)0xE9;
	private static final byte JPEG_MARKER_APP10 = (byte)0xEA;
	private static final byte JPEG_MARKER_APP11 = (byte)0xEB;
	private static final byte JPEG_MARKER_APP12 = (byte)0xEC;
	private static final byte JPEG_MARKER_APP13 = (byte)0xED;
	private static final byte JPEG_MARKER_APP14 = (byte)0xEE;
	private static final byte JPEG_MARKER_APP15 = (byte)0xEF;	
	private static final byte SOI[] = {(byte) 0xFF,(byte) 0xD8};
	/** Identification string for APP2 ICC Profile segments */
	private static final byte ICC_TAG[] = {'I','C','C','_','P','R','O','F','I','L','E',0};
	/** Identification string for APP1 EXIF segments */
	private static final byte EXIF_TAG[] = {'E','x','i','f',0,0};
	/** Identification string for APP14 Adobe segments */
	private static final byte ADOBE_TAG[] = {'A','d','o','b','e',0};
	/** Identification string for JFIF segments. */
	private static final byte JFIF_TAG[] = {'J','F','I','F',0};
	//private static final byte JFXX_TAG[] = {'J','F','X','X',0};
		
	/**
	 * Default constructor.
	 */
	public JPEGMetadata() {
	}

	/**
	 * Creates metadata object and loads metadata from a JPEG file.
	 * @param filepath Path to JPEG file.
	 * @throws IOException If metadata can't be read from JPEG file.
	 */
	public JPEGMetadata(String filepath) throws IOException {
		if (!load( new File(filepath) ))  {
			throw new IOException("The file '"+filepath+"' couldn't be load");
		}
	}


	/**
	 * Loads metadata from a JPEG file.
	 * @param jpegFile Path to JPEG file.
	 * @return <code>true</code> on successful metadata load, <code>false</code> on error.
	 */
	public boolean load(File jpegFile) {
		m_filename = jpegFile.getAbsolutePath();
		boolean result = readMetadata();
		if (!result) {
			System.err.println("Error while loading JPEG metadata: " + jpegFile.getAbsolutePath());
		} else {
			loadPixelSize(jpegFile);
			loadNumBands(m_filename);
			if (m_dpiX == 0 || m_dpiY == 0) {
				loadDpi(m_filename);
			}
		}
		return result;
	}

	
	/**
	 * Saves JPEG thumbnail to file.
	 *
	 * @param thumbnail Path to output thumbnail file
	 * @return <code>true</code> on succes, <code>false</code> on error
	 */
	public boolean saveThumbnail(String thumbnail) {
		if (m_thumbPos!=0 && m_thumbSize!=0) {
			try {
				FileInputStream fis = new FileInputStream(m_filename);
				FileOutputStream fos = new FileOutputStream(thumbnail);
				fis.getChannel().transferTo(m_thumbPos, m_thumbSize, fos.getChannel());
				fis.close();
				fos.close();
			} catch (FileNotFoundException e) {
				return false;
			} catch (IOException e) {
				return false;
			}
		} else {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Gets an <code>InputStream</code> from where thumbnail can be read.
	 * 
	 * @return thumbnail data, or <code>null</code> if unable to load.
	 */
	@Override
	public ByteArrayInputStream getThumbnailAsInputStream() {
		if (m_thumbPos!=0 && m_thumbSize!=0) {
			byte[] buffer = new byte[(int)m_thumbSize];
			try {
				FileInputStream fis = new FileInputStream(m_filename);
				fis.getChannel().position(m_thumbPos);
				fis.read(buffer);
				fis.close();
				return new ByteArrayInputStream(buffer);
			} catch (FileNotFoundException e) {
				return null;
			} catch (IOException e) {
				return null;
			}
		} else {
			return null;
		}
	}
	
	/**
	 * Check if embedded thumbnail could be read
	 * 
	 * @return <code>true</code> if thumbnail was found and is available, <code>false</code> if not
	 */
	@Override
	public boolean hasThumbnail() {
		return (m_thumbPos!=0 && m_thumbSize!=0);
	}
	
	
	/**
	 * Reads metadata from JPEG file and stores it.
	 */
	private boolean readMetadata() {
		// Read buffer
		byte[] buffer;

		try (FileInputStream fis = new FileInputStream(m_filename)) {
						
			// Read start marker
			if (!Arrays.equals(SOI, readBytes(fis, 2))) {
				throw new Exception("Not a JPEG image (SOI marker not found).");
			}

			// Detect markers
			long markerLength;
			long markerStart;
			boolean finished = false;
			m_iccPositions = new Vector<Long>();
			m_iccSizes = new Vector<Long>();
			while (!finished) {
				buffer = readBytes(fis, 2);
				if (buffer[0] != (byte)0xFF) {
					throw new Exception("Invalid marker");
				}
				switch (buffer[1]) {
					case (byte)0xFF:
						// Padding
						break;

					case JPEG_MARKER_APP0:
						markerStart = fis.getChannel().position();
						buffer = readBytes(fis, 2);
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						if (Arrays.equals(JFIF_TAG, readBytes(fis, 5))) {
							if (!m_isEXIF) {
								m_isJFIF = true;
							}
						}
						fis.getChannel().position(markerStart+markerLength);
						break;
										
					case JPEG_MARKER_APP1:
						markerStart = fis.getChannel().position();
						buffer = readBytes(fis, 2);
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						if (Arrays.equals(EXIF_TAG, readBytes(fis, 6))) {
							m_isEXIF = true;
							long exifStart;
							exifStart = fis.getChannel().position();
							// Read TIFF header
							buffer = readBytes(fis, 8);
							boolean littleEndian = (buffer[0] == (byte) 0x49);
							//long tag42 = exifReadWord(subArray(buffer,2,4), littleEndian);
							long offsetIFD0 =  exifReadLong(subArray(buffer,4,8),littleEndian);

							// Read TIFF parameters (IFD0)
							fis.getChannel().position(exifStart+offsetIFD0);
							long IFD0count = exifReadWord(readBytes(fis, 2), littleEndian);
							long exifIFDoffset = 0;
							long whitePointOffset = 0;
							long primariesOffset = 0;
							for (int i=0; i<IFD0count; i++) {
								buffer = readBytes(fis, 12);
								long exifTag = exifReadWord(subArray(buffer,0,2),littleEndian);
								long exifType = exifReadWord(subArray(buffer,2,4),littleEndian);
								long exifCount = exifReadLong(subArray(buffer,4,8),littleEndian);
								long exifValueOffset = exifReadValue(subArray(buffer,8,12),littleEndian,exifType,exifCount);
								if (exifTag == 0x0112) {
									m_orientation = exifValueOffset;
									
								} else if (exifTag == 0x8769) {
									exifIFDoffset = exifValueOffset;
									
								} else if (exifTag == 0x13e) {
									whitePointOffset = exifValueOffset;
									
								} else if (exifTag == 0x13f) {
									primariesOffset = exifValueOffset;
									
								} else if (exifTag == 0x011A || exifTag == 0x011B) {
									// X Resolution / Y Resolution
									long currentPos = fis.getChannel().position();
									fis.getChannel().position(exifStart + exifValueOffset);
									long numerator = exifReadLong(readBytes(fis, 4), littleEndian);
									long denominator = exifReadLong(readBytes(fis, 4), littleEndian);
									double dpi = (double)numerator/(double)denominator;
									if (exifTag == 0x011A) {
										m_dpiX = dpi;
									} else {
										m_dpiY = dpi;
									}
									fis.getChannel().position(currentPos);								
								}
							}
							// Read IFD1 offset
							long IFD1offset =  exifReadLong(readBytes(fis, 4), littleEndian);

							// Read EXIF parameters
							if (exifIFDoffset != 0) {
								fis.getChannel().position(exifStart+exifIFDoffset);
								long ExifIFDcount = exifReadWord(readBytes(fis, 2), littleEndian);
								for (int i=0; i<ExifIFDcount; i++) {
									buffer = readBytes(fis, 12);
									long exifTag = exifReadWord(buffer,littleEndian);
									long exifType = exifReadWord(subArray(buffer,2,4),littleEndian);
									long exifCount = exifReadLong(subArray(buffer,4,8),littleEndian);
									long exifValueOffset = exifReadValue(subArray(buffer,8,12),littleEndian,exifType,exifCount);
									if (exifTag == 0xA001) { // ColorSpace
										m_exifColorSpace = exifValueOffset;
									}
								}
							}

							// Check AdobeRGB white point and primaries
							if (m_exifColorSpace == EXIF_CS_UNKNOWN) {
								long rationals[] = new long[16];
                                for (int r=0; r<16; r++) rationals[r] = 0;

                                if (whitePointOffset != 0) {
                                	fis.getChannel().position(exifStart+whitePointOffset);
                                	buffer = readBytes(fis, 16);
                                    for (int wp=0; wp<4; wp++) {
                                    	rationals[wp] = exifReadLong(subArray(buffer,wp*4,wp*4+4),littleEndian);
                                    }
                                }       

                                if (primariesOffset != 0) {
                                	fis.getChannel().position(exifStart+primariesOffset);
                                	buffer = readBytes(fis, 48);
                                    for (int p=0; p<12; p++) {
                                    	rationals[4+p] = exifReadLong(subArray(buffer,p*4,p*4+4),littleEndian);
                                    }
                                }       

                                long adobeRGBrationals[] = {313,1000,329,1000,64,100,33,100,21,100,71,100,15,100,6,100};
                                boolean isAdobeRGB = true;
                                for (int cmp=0; cmp<16; cmp++) {
                                    if (rationals[cmp] != adobeRGBrationals[cmp]) {
                                            isAdobeRGB = false;
                                            break;
                                    }
                                }
                                if (isAdobeRGB) {
                                	m_exifColorSpace = EXIF_CS_ADOBERGB;
                                }					
							}
							
							// Read thumbnail data (IFD1)
							if (IFD1offset != 0) {
								fis.getChannel().position(exifStart+IFD1offset);
								long ExifIFD1count = exifReadWord(readBytes(fis, 2), littleEndian);
								for (int i=0; i<ExifIFD1count; i++) {
									buffer = readBytes(fis, 12);
									long exifTag = exifReadWord(buffer,littleEndian);
									long exifType = exifReadWord(subArray(buffer,2,4),littleEndian);
									long exifCount = exifReadLong(subArray(buffer,4,8),littleEndian);
									long exifValueOffset = exifReadValue(subArray(buffer,8,12),littleEndian,exifType,exifCount);
									if (exifTag == 0x0201) { // JPEGInterchangeFormat (thumb offset)
										m_thumbPos = exifStart+exifValueOffset;
									} else if (exifTag == 0x0202) { // JPEGInterchangeFormatLength (thumb length)
										m_thumbSize = exifValueOffset;
									}
								}
							}
						}
						fis.getChannel().position(markerStart+markerLength);
						break;

					case JPEG_MARKER_APP2:
						markerStart = fis.getChannel().position();
						buffer = readBytes(fis, 2);
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						if (Arrays.equals(ICC_TAG, readBytes(fis, 12))) {
							readBytes(fis, 2);
							long chunkSize = markerLength-2-12-2;
							m_iccPositions.add(fis.getChannel().position());
							m_iccSizes.add(chunkSize);
						}
						fis.getChannel().position(markerStart+markerLength);
						break;

					case JPEG_MARKER_APP14:
						markerStart = fis.getChannel().position();
						buffer = readBytes(fis, 2);
						markerLength = ((0xFF & buffer[0]) << 8) + (0xFF & buffer[1]);
						if (Arrays.equals(ADOBE_TAG, readBytes(fis, 6))) {
							buffer = readBytes(fis, 6);
							m_adobeApp14Found = true;
							m_adobeTransform = buffer[5];
						}
						fis.getChannel().position(markerStart+markerLength);						
						break;
												
					case JPEG_MARKER_APP3:
					case JPEG_MARKER_APP4:
					case JPEG_MARKER_APP5:
					case JPEG_MARKER_APP6:
					case JPEG_MARKER_APP7:
					case JPEG_MARKER_APP8:
					case JPEG_MARKER_APP9:
					case JPEG_MARKER_APP10:
					case JPEG_MARKER_APP11:
					case JPEG_MARKER_APP12:
					case JPEG_MARKER_APP13:
					case JPEG_MARKER_APP15:
						buffer = readBytes(fis, 2);
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						fis.skip(markerLength-2);
						break;

					default:
						finished = true;
						break;
				}
			}
			
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
	
	
	/**
	 * Gets embedded ICC color profile.
	 * @return Embedded ICC color profile, or <code>null</code> if not detected.
	 */
	@Override
	public ICC_Profile getIccProfile() {
		
		// Check initializations
		if ((m_iccPositions == null) || (m_iccSizes == null)) {
			System.err.println("Metadata still not loaded.");
			return null;
		}
		if ((m_iccPositions.size() != m_iccSizes.size()) || (m_iccPositions.size() == 0)) {
			return null;
		}
		
		// Prepare buffer for color profile
		long profileSize = 0;
		for (long chunkSize : m_iccSizes) {
			profileSize += chunkSize;
		}
		byte[] profileData = new byte[(int)profileSize];

		// Load profile segments into buffer
		FileInputStream is = null;
		int currentPosition = 0;
		try {
			is = new FileInputStream(new File(m_filename));
			for (int i=0; i<m_iccPositions.size(); i++) {
				long chunkPosition = m_iccPositions.get(i);
				long chunkSize = m_iccSizes.get(i);
				is.getChannel().position(chunkPosition);
				int readBytes = is.read(profileData,currentPosition,(int)chunkSize);
				if (readBytes != chunkSize) {
					System.err.println("Unable to read bytes for segment #"+i+" of ICC profile.");
					is.close();
					return null;
				}
				currentPosition += readBytes;
			}
			is.close();
		} catch (Exception e) {
			e.printStackTrace();
			try {is.close();} catch (Exception ex) {/*Ignore*/}
			return null;
		}
		
		// Load profile from buffer
		ICC_Profile profile = null;
		try {
			profile = ICC_Profile.getInstance(profileData);
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid embedded profile.");
			return null;
		}
		
		return profile;
	}
	
	/**
	 * Compares two byte arrays
	 * 
	 * @param src Array 1
	 * @param dst Array 2
	 * @param length Length of bytes to compare
	 * @return <code>0</code> if equal, <code>1</code> if different
	 */
	private static int memcmp(byte[] src, byte[] dst, int length) {
		boolean result = false;
		if ((src.length >= length) && (dst.length >= length)) {
			result = true;
			try {
				for (int i=0; i<length && result; i++) {
					if (src[i] != dst[i]) result = false;
				}
			} catch (IndexOutOfBoundsException e) {
				result = false;
			}			
		}
		return (result ? 0 : 1);
	}
	
	/**
	 * Helper function for reading Exif data.<br>
	 * Reads a word value from a memory buffer.
	 * 
	 * @param buffer Memory buffer to read
	 * @param littleEndian <code>true</code> if buffer is little endian encoded
	 * @return Word value
	 */
	long exifReadWord(byte[] buffer, boolean littleEndian) {
		if (littleEndian) {
			return (0xFF & buffer[1])*256 + (0xFF & buffer[0]);
		} else {
			return (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
		}
	}

	/**
	 * Helper function for reading Exif data.<br>
	 * Reads a long value from a memory buffer.
	 * 
	 * @param buffer Memory buffer to read
	 * @param littleEndian <code>true</code> if buffer is little endian encoded
	 * @return Long value
	 */
	long exifReadLong(byte[] buffer, boolean littleEndian) {
		if (littleEndian) {
			return ((0xFF & buffer[3]) << 24) | ((0xFF & buffer[2]) << 16) | ((0xFF & buffer[1]) << 8) | (0xFF & buffer[0]);
		} else {
			return ((0xFF & buffer[0]) << 24) | ((0xFF & buffer[1]) << 16) | ((0xFF & buffer[2]) << 8) | (0xFF & buffer[3]);
		}

	}

	/**
	 * Helper function for reading Exif values.
	 * 
	 * @param buffer Data buffer where Exif value is stored
	 * @param littleEndian <code>true</code> if buffer is little endian encoded
	 * @param typeL Exif data type id
	 * @param sizeL Exif data length
	 * @return Exif value
	 */
	long exifReadValue(byte[] buffer, boolean littleEndian, long typeL, long sizeL) {

		long value = 0;
		int type = (int) typeL;
		int size = (int) sizeL;

		// Get data size in bytes
		int totalsize;
		switch (type) {
			case 1:	//BYTE
			case 2: //ASCII
			case 7: //UNDEFINED
				totalsize = size;
				break;

			case 3: // SHORT
				totalsize = 2*size;
				break;

			default:
				totalsize = 4;
				break;
		}
		if (totalsize >=4) totalsize=4;

		// Get data on corresponding bytes
		switch (totalsize) {
			case 1:
				value = (0xFF & buffer[0]);
				break;

			case 2:
				value = exifReadWord(buffer,littleEndian);
				break;

			case 3:
				if (littleEndian) {
					value = ((0xFF & buffer[2]) << 16) | ((0xFF & buffer[1]) << 8) | (0xFF & buffer[0]);
				} else {
					value = ((0xFF & buffer[0]) << 16) | ((0xFF & buffer[1]) << 8) | (0xFF & buffer[2]);
				}
				break;

			case 4:
				value = exifReadLong(buffer,littleEndian);
				break;
		}

		return value;
	}

	/**
	 * Generates a sub-array from a byte array
	 * 
	 * @param buffer Original byte array
	 * @param start Start index for sub-array
	 * @param end End index for sub-array
	 * @return New sub-array, of length <code>end-start</code>
	 */
	private byte[] subArray(byte[] buffer, int start, int end) {
		int newLength = end-start;
		byte[] newBuffer = new byte[newLength];
		for (int i=0; i<newLength; i++) {
			if (start+i < buffer.length) {
				newBuffer[i] = buffer[start+i];
			} else {
				newBuffer[i] = 0;
			}
		}
		return newBuffer;
	}
	

	/**
	 * Get Exif color space.
	 * 
	 * @return Exif color space id (JPEGMetadata.EXIF_CS*)
	 */
	public long getExifColorSpace() {
		return m_exifColorSpace;
	}

	/**
	 * Gets Adobe Color Transform ID. Tells if channel data is direct RGB/CMYK, YCbCr or YCCK.
	 * 
	 * @return Adobe Color Transform ID (<code>JPEGMetadata.ADOBE_TRANSFORM_*</code>)
	 */
	public int getAdobeColorTransform() {
		return m_adobeTransform;
	}
	
	/**
	 * Tells wether an Adobe APP14 JPEG segment was found in the metadata.<br>
	 * If this marker was found, {@link #getAdobeColorTransform()} will return the detected
	 * Adobe Color Transform ID.
	 * 
	 * @return <code>true</code> if an Adobe APP14 JPEG segment was found in the metadata. 
	 */
	public boolean isAdobeApp14Found() {
		return  m_adobeApp14Found;
	}
	
	
	/**
	 * Gets pixel size of an image file, without having to load image into memory.
	 * 
	 * @param jpegFile Image file
	 * @return Image pixel dimensions, or <code>(0,0)</code> if not able to read.
	 */
	public static Dimension getPixelSize(File jpegFile) {
		Dimension dimension = null;
		ImageInputStream is = null;
		ImageReader reader = null;
		
		try {
			is = ImageIO.createImageInputStream(jpegFile);
		    Iterator<ImageReader> readers = ImageIO.getImageReaders(is);
		    if (readers.hasNext()) {
		        reader = readers.next();
	            reader.setInput(is);
		        dimension = new Dimension(reader.getWidth(0), reader.getHeight(0));
	            reader.dispose();
		    }
		    is.close();
		    
		} catch (Exception e) {
			if (reader != null) try {reader.dispose();} catch (Exception ex) {/* Ignore */}
			if (is != null) try {is.close();} catch (Exception ex) {/* Ignore */}
		}
		
		return (dimension == null ? new Dimension(0,0) : dimension);
	}
	
	/**
	 * Extract ICC Profile embedded in a JPEG file
	 * @param imagePath Path to JPEG file
	 * @return Embedded ICC Profile, or <code>null</code> if not found
	 */
	public static ICC_Profile getIccProfile(String imagePath) {
		ICC_Profile icc = null;
		JPEGMetadata md = new JPEGMetadata();
		if (md.load(new File(imagePath))) {
			icc = md.getIccProfile();
		}
		return icc;
	}
	
	/**
	 * Embeds an ICC Profile into an existing JPEG image file.
	 * @param iccProfile ICC Profile to embed
	 * @param imagePath JPEG image file to modify
	 * @return <code>true</code> on success, <code>false</code> on failure
	 */
	public static boolean embedIccProfile(ICC_Profile iccProfile, String imagePath) {
	
		// Determine location of current APP2 ICC Profile markers
		byte[] buffer = new byte[256];
		Vector<Long> APP2Positions = new Vector<Long>();
		Vector<Long> APP2Lengths = new Vector<Long>();
		FileInputStream fis = null;
		FileOutputStream os = null;
		File tempFile = null;
		try {
			fis = new FileInputStream(imagePath);
			
			// Check SOI marker
			if (fis.read(buffer,0,2) != 2) {
				fis.close();
				return false;
			}
			if (memcmp(buffer,SOI,2) != 0) {
				fis.close();
				return false;
			}

			// Parse APP markers
			long markerLength = 0;
			boolean finished = false;
			while (!finished) {
				if (fis.read(buffer,0,2) != 2) {
					fis.close();
					return false;
				}
				if (buffer[0] != (byte)0xFF) {
					fis.close();
					return false;
				}
				switch (buffer[1]) {
					case (byte)0xFF:
						break;

					case JPEG_MARKER_APP2:
						long markerStart = fis.getChannel().position();
						if (fis.read(buffer,0,2) != 2) {
							fis.close();
							return false;
						}
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						if (fis.read(buffer,0,12) != 12) {
							fis.close();
							return false;
						}
						if (memcmp(buffer,ICC_TAG,12) == 0) {
							APP2Positions.add(markerStart-2);
							APP2Lengths.add(markerLength+2);
						}
						fis.getChannel().position(markerStart+markerLength);
						break;

					case JPEG_MARKER_APP0:
					case JPEG_MARKER_APP1:
					case JPEG_MARKER_APP3:
					case JPEG_MARKER_APP4:
					case JPEG_MARKER_APP5:
					case JPEG_MARKER_APP6:
					case JPEG_MARKER_APP7:
					case JPEG_MARKER_APP8:
					case JPEG_MARKER_APP9:
					case JPEG_MARKER_APP10:
					case JPEG_MARKER_APP11:
					case JPEG_MARKER_APP12:
					case JPEG_MARKER_APP13:
					case JPEG_MARKER_APP14:
					case JPEG_MARKER_APP15:
						if (fis.read(buffer,0,2) != 2) {
							fis.close();
							return false;
						}
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						fis.skip(markerLength-2);
						break;

					default:
						finished = true;
						break;
				}
			}
			fis.getChannel().position(0);	

			// Generate new JPEG in temp file
			tempFile = File.createTempFile("temp", ".jpg");
			os = new FileOutputStream(tempFile);
			
			// Copy SOI
			fis.getChannel().transferTo(0, 2, os.getChannel());
			
			// Insert new APP2 markers
			byte[] profileData = iccProfile.getData();
			int remainingIccBytes = profileData.length;
			int iccChunks = remainingIccBytes / 65519 + ((remainingIccBytes % 65519 > 0) ? 1 : 0);
			int chunkCount = 1;
			while (remainingIccBytes > 0) {
				int bytesToWrite = Math.min(65519, remainingIccBytes);
				os.write(0xFF);
				os.write(JPEG_MARKER_APP2);
				markerLength = 2+12+2+bytesToWrite;
				os.write((byte)((markerLength & 0xFF00) >> 8));
				os.write((byte)(markerLength & 0xFF));
				os.write(ICC_TAG);
				os.write((byte)(chunkCount & 0xFF));
				os.write((byte)(iccChunks & 0xFF));
				os.write(profileData, profileData.length - remainingIccBytes, bytesToWrite);
				chunkCount++;
				remainingIccBytes -= bytesToWrite;
			}
			
			// TODO: Copy rest of original JPEG, skipping old APP2 markers
			fis.getChannel().transferTo(2, fis.getChannel().size()-2, os.getChannel());
			
			// Finish read and write
			fis.close();
			os.close();
			
			// Replace old image file with temp file
			File oldImage = new File(imagePath);
			Files.copy(tempFile.toPath(), oldImage.toPath(), StandardCopyOption.REPLACE_EXISTING);
			Files.delete(tempFile.toPath());
			
		} catch (Exception e) {
			e.printStackTrace();
			try {fis.close();} catch (Exception ex) {/*Ignore*/}
			try {os.close();} catch (Exception ex) {/*Ignore*/}
			if (tempFile != null) tempFile.delete();
			return false;
		}	
		
		return true;
	}
	
	/**
	 * Get horizontal DPI resolution from metadata of a given file.
	 * 
	 * @param filename Path to file
	 * @return DPI value, or <code>0</code> if not found in metadata.
	 */
	public static int getDpi(String filename) {
		int dpi = 0;

		ImageReader reader = null;
		try (ImageInputStream iis = ImageIO.createImageInputStream(new File(filename))) {
			// Get metadata from file
			reader = ImageIO.getImageReaders(iis).next();
			reader.setInput(iis, true);
			IIOMetadata metadata = reader.getImageMetadata(0);

			// Locate dpi			
            String[] names = metadata.getMetadataFormatNames();
            for (String name : names) {
                Node node = metadata.getAsTree(name);
                String dpiString = (String) queryXPath(node, "JPEGvariety/app0JFIF/@Xdensity", XPathConstants.STRING);
                if (dpiString != null) {
                	dpi = Integer.parseInt(dpiString);
                	break;
                }
                dpiString = (String) queryXPath(node, "Dimension/HorizontalPixelSize/@value", XPathConstants.STRING);
                if (dpiString != null) {
                	dpi = (int) Math.round(25.4 / Double.parseDouble(dpiString));
                	break;
                }
            }
            reader.dispose();
            
        } catch (Exception e) {
        	try {reader.dispose();} catch (Exception ex) {/* Ignore */}
        }
		
        return dpi;
	}
	
	/**
	 * Gets content from a DOM tree using XPath query
	 *  
	 * @param xpathQuery XPath expression to query the tree.
	 * @param type Type of content to get (<code>XPathConstants.*</code>)
	 * @return Object with found content, or <code>null</code> on error
	 */
    private static Object queryXPath(Node node, String xpathQuery, QName type) {

		// Generamos objeto XPath
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		
		// Evaluamos expresión
		Object object = null;
		try {
			XPathExpression expression = xpath.compile(xpathQuery);
			if (expression.evaluate(node, XPathConstants.NODE) != null) {
				object = expression.evaluate(node, type);
			}
		} catch (Exception e) {
			object = null;
		}
		
		return object;
	}
	

	/**
	 * Stores pixel density / resolution embedded in JPEG metadata.
	 * @param filename Path to JPEG file.
	 */
	private void loadDpi(String filename) {
		ImageReader reader = null;
		try (ImageInputStream iis = ImageIO.createImageInputStream(new File(filename))) {
			// Get metadata from file
			reader = ImageIO.getImageReaders(iis).next();
			reader.setInput(iis, true);
			IIOMetadata metadata = reader.getImageMetadata(0);

			// Locate dpi			
            String[] names = metadata.getMetadataFormatNames();
            for (String name : names) {
                Node node = metadata.getAsTree(name);
                String dpiString = (String) queryXPath(node, "JPEGvariety/app0JFIF/@Xdensity", XPathConstants.STRING);
                if (dpiString != null) {
                	m_dpiX = Double.parseDouble(dpiString);
                }
                dpiString = (String) queryXPath(node, "JPEGvariety/app0JFIF/@Ydensity", XPathConstants.STRING);
                if (dpiString != null) {
                	m_dpiY = Double.parseDouble(dpiString);
                }
                if (m_dpiX != 0 && m_dpiY != 0) {
                	break;
                }
                dpiString = (String) queryXPath(node, "Dimension/HorizontalPixelSize/@value", XPathConstants.STRING);
                if (dpiString != null) {
                	m_dpiX = Math.round(25.4 / Double.parseDouble(dpiString));
                }
                dpiString = (String) queryXPath(node, "Dimension/VerticalPixelSize/@value", XPathConstants.STRING);
                if (dpiString != null) {
                	m_dpiY = Math.round(25.4 / Double.parseDouble(dpiString));
                }
                if (m_dpiX != 0 && m_dpiY != 0) {
                	break;
                }
            }
            
            reader.dispose();
            
        } catch (Exception e) {
        	try {reader.dispose();} catch (Exception ex) {/*Ignore*/}
        }		
	}
    
    
	/**
	 * Detects number of channels in JPEG file.
	 * @param filePath Path to JPEG file.
	 */
	private void loadNumBands(String filePath) {		
		ImageInputStream in = null;
		try {
			in = ImageIO.createImageInputStream(new File(filePath));
		    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
		    boolean done = false;
		    while (readers.hasNext() && !done) {
		        ImageReader reader = readers.next();
		        try {
		            reader.setInput(in);
		            m_numBands = reader.getImageTypes(0).next().getNumBands();
		            done = true;
		        } finally {
		            reader.dispose();
		        }
		    }
		    in.close();
		} catch (Exception e) {
		    try {in.close();} catch (Exception ex) {/*Ignore*/}
		    m_numBands = 4;
		}
	}
	
	
	/** 
	 * Tells whether image metadata contains a valid JFIF marker.
	 * @return <code>true</code> if metadata contains a valid JFIF marker, <code>false</code> otherwise.
	 */
	public boolean isJFIF() {
		return m_isJFIF;
	}
	
	
	/** 
	 * Tells whether image metadata contains a valid EXIF marker.
	 * @return <code>true</code> if metadata contains a valid EXIF marker, <code>false</code> otherwise.
	 */
	public boolean isEXIF() {
		return m_isEXIF;
	}	
	
	
	@Override
	public ImageType getImageType() {
		return ImageType.JPEG;
	}

	@Override
	public boolean isGreyscale() {
		return m_numBands == 1;
	}


	@Override
	public boolean isRGB() {
		return m_numBands == 3;
	}


	@Override
	public boolean isCMYK() {
		return m_numBands == 4;
	}


	@Override
	public double getDpiX() {
		return m_dpiX;
	}


	@Override
	public double getDpiY() {
		return m_dpiY;
	}
	
	@Override
	public ImageOrientation getOrientation() {
		if (m_orientation == EXIF_ORIENTATION_LEFT) {
			return ImageOrientation.LEFT;
		} else if (m_orientation == EXIF_ORIENTATION_RIGHT) {
			return ImageOrientation.RIGHT;
		} else if (m_orientation == EXIF_ORIENTATION_DOWN) {
			return ImageOrientation.DOWN;
		}
		return ImageOrientation.TOP;
	}	
}
