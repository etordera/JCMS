package com.gmail.etordera.imaging;

import java.awt.Dimension;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
 * @author enric
 *
 */
public class JPEGMetadata {

	/** Nom del fitxer JPEG */
	private String m_filename;
	/** Activar/Desactivar registres */
	private boolean m_logging;
	
	/** Metadata: Orientació de la imatge */
	private long m_orientation = 0;
	/** Metadata: Espai de color EXIF */
	private long m_exifColorSpace = 0;
	/** <code>true</code> only if Adobe APP14 JPEG segment was found */
	private boolean m_adobeApp14Found = false;
	/** Metadata: Adobe color transform code */
	private int m_adobeTransform = ADOBE_TRANSFORM_UNKNOWN;
	/** Metadata: offset del thumbnail dins del fitxer JPEG */ 
	private long m_thumbPos = 0;
	/** Metadata: Mida del thumbnail */
	private long m_thumbSize = 0;
	/** Metadata: offsets dels fragments del perfil ICC dins del fitxer JPEG */ 
	private Vector<Long> m_iccPositions = null;
	/** Metadata: mides dels fragments del perfil ICC dins del fitxer JPEG */ 
	private Vector<Long> m_iccSizes = null;

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
	
	/* Constants JPEG */
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
	//private static final byte JFIF_TAG[] = {'J','F','I','F',0};
	//private static final byte JFXX_TAG[] = {'J','F','X','X',0};
		
	/**
	 * Construeix un objecte JPEGMetadata amb la informació del fitxer JPEG.
	 * 
	 * @param filename Path al fitxer JPEG
	 */
	public JPEGMetadata(String filename) {
		this(filename, false);
	}

	/**
	 * Construeix un objecte JPEGMetadata amb la informació del fitxer JPEG.
	 * 
	 * @param filename Path al fitxer JPEG
	 * @param logging Activar registre de missatges de depuració
	 */
	public JPEGMetadata(String filename, boolean logging) {
		m_logging = logging;
		m_filename = filename;
		readMetadata();
	}

	
	/**
	 * Guarda la miniatura del JPEG en un fitxer
	 * @param thumbnail Path al fitxer a generar amb la miniatura
	 * @return true si tenim èxit, false en cas contrari
	 */
	public boolean saveThumbnail(String thumbnail) {
		if (m_thumbPos!=0 && m_thumbSize!=0) {
			try {
				FileInputStream fis = new FileInputStream(m_filename);
				FileOutputStream fos = new FileOutputStream(thumbnail);
				fis.getChannel().transferTo(m_thumbPos, m_thumbSize, fos.getChannel());
				fis.close();
				fos.close();
				log("Thumbnail saved");				
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
	 * Genera un InputStream per a accedir a la miniatura.
	 * @return un ByteArrayInputStream amb les dades de la miniatura, o null si no és possible carregar-la
	 */
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
	 * Indica si s'ha pogut llegir informació de la miniatura
	 * 
	 * @return true si tenim miniatura disponible, false en cas contrari
	 */
	public boolean hasThumbnail() {
		return (m_thumbPos!=0 && m_thumbSize!=0);
	}
	
	
	/**
	 * Reads metadata from JPEG file and stores it inside this object.
	 */
	private void readMetadata() {
		// Read buffer
		byte[] buffer = new byte[256];

		// Check file is readable
		File file = new File(m_filename);
		if (!file.canRead()) {
			log("* Can't read file");
			return;
		}

		try {
			FileInputStream fis = new FileInputStream(m_filename);
			
			// Llegim marcador d'inici
			if (fis.read(buffer,0,2) != 2) {
				log("* First read error");
				fis.close();
				return;
			}
			if (memcmp(buffer,SOI,2) != 0) {
				log("* Not a valid JPEG image.");
				fis.close();
				return;
			}

			// Detectem marcadors
			long markerLength;
			long markerStart;
			int appid;
			boolean finished = false;
			m_iccPositions = new Vector<Long>();
			m_iccSizes = new Vector<Long>();
			while (!finished) {
				if (fis.read(buffer,0,2) != 2) {
					log("* Marker read error");
					fis.close();
					return;
				}
				if (buffer[0] != (byte)0xFF) {
					log("* Invalid marker");
					fis.close();
					return;
				}
				switch (buffer[1]) {
					case (byte)0xFF:
						log("- Padding");
						break;

					case JPEG_MARKER_APP1:
						markerStart = fis.getChannel().position();
						if (fis.read(buffer,0,2) != 2) {
							log("APP1 Marker read error");
							fis.close();
							return;
						}
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						log("- APP1 Marker ("+markerLength+" bytes)");
						if (fis.read(buffer,0,6) != 6) {
							log("APP1 Marker data read error");
							fis.close();
							return;
						}
						if (memcmp(buffer,EXIF_TAG,6) == 0) {
							long exifStart;
							exifStart = fis.getChannel().position();
							log("- Exif data found");
							// Llegim capçalera TIFF
							if (fis.read(buffer,0,8) != 8) {
								log("EXIF TIFF Header read error");
								fis.close();
								return;
							}
							boolean littleEndian = false;
							if (buffer[0] == (byte) 0x49) {
								log("Little Endian");
								littleEndian = true;
							} else {
								log("Big Endian");
								littleEndian = false;
							}
							long tag42 = exifReadWord(subArray(buffer,2,4), littleEndian);
							long offsetIFD0 =  exifReadLong(subArray(buffer,4,8),littleEndian);
							log("Tag 42: "+tag42+" - Offset to IFD0: "+offsetIFD0);

							// Llegim paràmetres TIFF (IFD0)
							fis.getChannel().position(exifStart+offsetIFD0);
							if (fis.read(buffer,0,2) != 2) {
								log("EXIF IFD0 read error");
								fis.close();
								return;
							}
							long IFD0count = exifReadWord(buffer,littleEndian);
							log("- "+IFD0count+" parameters in IFD0");
							long exifIFDoffset = 0;
							long whitePointOffset = 0;
							long primariesOffset = 0;
							for (int i=0; i<IFD0count; i++) {
								if (fis.read(buffer,0,12) != 12) {
									log("EXIF IFD0 read error");
									fis.close();
									return;
								}
								long exifTag = exifReadWord(subArray(buffer,0,2),littleEndian);
								long exifType = exifReadWord(subArray(buffer,2,4),littleEndian);
								long exifCount = exifReadLong(subArray(buffer,4,8),littleEndian);
								long exifValueOffset = exifReadValue(subArray(buffer,8,12),littleEndian,exifType,exifCount);
								log("- "+i+": Tag "+exifTag+", Type "+exifType+", Count "+exifCount+", Value-Offset "+exifValueOffset);
								if (exifTag == 0x0112) {
									m_orientation = exifValueOffset;
								} else if (exifTag == 0x8769) {
									exifIFDoffset = exifValueOffset;
								} else if (exifTag == 0x13e) {
									whitePointOffset = exifValueOffset;
								} else if (exifTag == 0x13f) {
									primariesOffset = exifValueOffset;
								}
							}
							// Llegim offset a IFD1
							if (fis.read(buffer,0,4) != 4) {
								log("EXIF IFD1 offset read error");
								fis.close();
								return;
							}
							long IFD1offset = 0;
							IFD1offset = exifReadLong(buffer,littleEndian);
							log("- Offset to IFD1: "+IFD1offset);

							// Llegim paràmetres EXIF
							if (exifIFDoffset != 0) {
								fis.getChannel().position(exifStart+exifIFDoffset);
								if (fis.read(buffer,0,2) != 2) {
									log("EXIF IFD read error");
									fis.close();
									return;
								}
								long ExifIFDcount = exifReadWord(buffer,littleEndian);
								log("- "+ExifIFDcount+" parameters in Exif IFD");
								for (int i=0; i<ExifIFDcount; i++) {
									if (fis.read(buffer,0,12) != 12) {
										log("EXIF IFD read error");
										fis.close();
										return;
									}
									long exifTag = exifReadWord(buffer,littleEndian);
									long exifType = exifReadWord(subArray(buffer,2,4),littleEndian);
									long exifCount = exifReadLong(subArray(buffer,4,8),littleEndian);
									long exifValueOffset = exifReadValue(subArray(buffer,8,12),littleEndian,exifType,exifCount);
									log("- "+i+": Tag "+exifTag+", Type "+exifType+", Count "+exifCount+", Value-Offset "+exifValueOffset);
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
    								if (fis.read(buffer,0,16) != 16) {
    									log("EXIF white point read error");
    									fis.close();
    									return;
    								}
                                    for (int wp=0; wp<4; wp++) {
                                    	rationals[wp] = exifReadLong(subArray(buffer,wp*4,wp*4+4),littleEndian);
                                    }
                                }       

                                if (primariesOffset != 0) {
                                	fis.getChannel().position(exifStart+primariesOffset);
    								if (fis.read(buffer,0,48) != 48) {
    									log("EXIF primaries read error");
    									fis.close();
    									return;
    								}
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
							
							// Llegim informació del thumbnail (IFD1)
							if (IFD1offset != 0) {
								fis.getChannel().position(exifStart+IFD1offset);
								if (fis.read(buffer,0,2) != 2) {
									log("EXIF IFD1 read error");
									fis.close();
									return;
								}
								long ExifIFD1count = exifReadWord(buffer,littleEndian);
								log("- "+ExifIFD1count+" parameters in Exif IFD1 (Thumbnail)");
								for (int i=0; i<ExifIFD1count; i++) {
									if (fis.read(buffer,0,12) != 12) {
										log("EXIF IFD1 read error");
										fis.close();
										return;
									}
									long exifTag = exifReadWord(buffer,littleEndian);
									long exifType = exifReadWord(subArray(buffer,2,4),littleEndian);
									long exifCount = exifReadLong(subArray(buffer,4,8),littleEndian);
									long exifValueOffset = exifReadValue(subArray(buffer,8,12),littleEndian,exifType,exifCount);
									log("- "+i+": Tag "+exifTag+", Type "+exifType+", Count "+exifCount+", Value-Offset "+exifValueOffset);
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
						if (fis.read(buffer,0,2) != 2) {
							log("APP2 Marker read error");
							fis.close();
							return;
						}
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						log("- APP2 Marker ("+markerLength+" bytes)");
						if (fis.read(buffer,0,12) != 12) {
							log("APP2 Marker data read error");
							fis.close();
							return;
						}
						if (memcmp(buffer,ICC_TAG,12) == 0) {
							if (fis.read(buffer,0,2) != 2) {
								log("ICC chunk read error");
								fis.close();
								return;
							}
							long chunkSize = markerLength-2-12-2;
							log("- ICC Chunk ("+chunkSize+" bytes)");
							m_iccPositions.add(fis.getChannel().position());
							m_iccSizes.add(chunkSize);
						}
						fis.getChannel().position(markerStart+markerLength);
						break;

					case JPEG_MARKER_APP14:
						markerStart = fis.getChannel().position();
						if (fis.read(buffer,0,2) != 2) {
							log("APP14 Marker read error");
							fis.close();
							return;
						}
						markerLength = ((0xFF & buffer[0]) << 8) + (0xFF & buffer[1]);
						log("- APP14 Marker ("+markerLength+" bytes)");
						if (fis.read(buffer,0,6) != 6) {
							log("APP14 Marker data read error");
							fis.close();
							return;
						}
						if (memcmp(buffer,ADOBE_TAG,6) == 0) {
							if (fis.read(buffer,0,6) != 6) {
								log("Adobe Marker read error");
								fis.close();
								return;
							}
							m_adobeApp14Found = true;
							m_adobeTransform = buffer[5];
						}
						fis.getChannel().position(markerStart+markerLength);						
						break;
												
					case JPEG_MARKER_APP0:
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
						appid = (0xFF & buffer[1]) - 0x000000E0;
						if (fis.read(buffer,0,2) != 2) {
							log("APP"+appid+" Marker read error");
							fis.close();
							return;
						}
						markerLength = (0xFF & buffer[0])*256 + (0xFF & buffer[1]);
						log("- APP"+appid+" Marker ("+markerLength+" bytes)");
						fis.skip(markerLength-2);
						break;

					default:
						finished = true;
						break;
				}
			}
			
			// Tanquem el fitxer
			fis.close();
						
		} catch (Exception e) {
			return;
		}
		
		return;
	}
	
	
	/**
	 * Devuelve el perfil de color incrustado en los metadatos de la imagen JPEG.
	 * 
	 * @return Perfil de color incrustado, o <code>null</code> si no se ha detectado.
	 */
	public ICC_Profile getIccProfile() {
		
		// Comprobamos inicializaciones correctas
		if ((m_iccPositions == null) || (m_iccSizes == null)) {
			System.err.println("Metadatos no cargados");
			return null;
		}
		if ((m_iccPositions.size() != m_iccSizes.size()) || (m_iccPositions.size() == 0)) {
			return null;
		}
		
		// Preparamos buffer para el perfil de color
		long profileSize = 0;
		for (long chunkSize : m_iccSizes) {
			profileSize += chunkSize;
		}
		byte[] profileData = new byte[(int)profileSize];

		// Cargamos segmentos del perfil en el buffer
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
					System.err.println("No se pueden leer los bytes del segmento "+i+" del perfil ICC.");
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
		
		// Cargamos perfil a partir del buffer
		ICC_Profile profile = null;
		try {
			profile = ICC_Profile.getInstance(profileData);
		} catch (IllegalArgumentException e) {
			System.err.println("Perfil de color incrustado no válido.");
			return null;
		}
		
		return profile;
	}
	
	/**
	 * Compara dos arrays de bytes.
	 * 
	 * @param src Array 1
	 * @param dst Array 2
	 * @param length Longitud de bytes a comparar
	 * @return 0 si són iguals, 1 si són diferents
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

		// Calculem dimensions de les dades en bytes
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

		// Obtenim les dades sobre els bytes que correspon
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
	 * Genera un subarray a partir d'un array de bytes
	 * 
	 * @param buffer Array de bytes original
	 * @param start Índex a partir del qual s'extreuen les dades
	 * @param end Índex fins al qual s'extreuen les dades
	 * @return Nou array de bytes
	 */
	private byte[] subArray(byte[] buffer,int start, int end) {
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
	 * Registre de missatges per a depuració
	 * 
	 * @param msg Missatge
	 */
	private void log(String msg) {
		if (m_logging) {
			System.out.println(msg);
		}
	}
	
	/**
	 * Get image orientation.
	 * 
	 * @return Image orientation Exif value
	 */
	public long getOrientation() {
		return m_orientation;
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
	 * Gets Adobe Color Transform ID. It tells if channel data is direct RGB/CMYK, YCbCr or YCCK.
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
	
	public boolean isLogging() {
		return m_logging;
	}

	public void setLogging(boolean logging) {
		m_logging = logging;
	}
	
	
	
	/**
	 * Devuelve las dimensiones en píxeles de un archivo de imagen, sin necesidad de cargar
	 * la imagen completamente en memoria.
	 * 
	 * @param jpegFile Fichero de imagen a analizar
	 * @return Dimensiones de la imagen. Si no es posible extraer la información del fichero, devuelve (0,0)
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
		JPEGMetadata md = new JPEGMetadata(imagePath);
		return md.getIccProfile();
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
			File tempFile = File.createTempFile("temp", "jpg");
			FileOutputStream os = new FileOutputStream(tempFile);
			
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
			oldImage.delete();
			tempFile.renameTo(oldImage);
			
		} catch (Exception e) {
			e.printStackTrace();
			try {fis.close();} catch (Exception ex) {/*Ignore*/}
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

		try {
			// Get metadata from file
			ImageInputStream iis = ImageIO.createImageInputStream(new File(filename));
			ImageReader reader = ImageIO.getImageReaders(iis).next();
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
            
        } catch (Exception e) {
        	System.err.println("Unable to read DPI from "+filename+": "+e.getMessage());
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
	
}
