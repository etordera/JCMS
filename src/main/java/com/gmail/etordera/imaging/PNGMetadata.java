package com.gmail.etordera.imaging;

import java.awt.color.ICC_Profile;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * PNG files metadata.<br>
 * <br>
 * PNG docs: <code>https://www.w3.org/TR/PNG/#5DataRep</code>
 */
public class PNGMetadata extends ImageMetadata {
	
	/** Image width (px). */
	private int m_width;
	/** Image height (px). */
	private int m_height;
	/** Color bit depth. */
	private int m_bitDepth;
	/** PNG color type identifier. */
	private int m_colourType;
	/** Embedded color profile bytes. */
	private byte[] m_iccProfileData;
	/** Horizontal resolution (dpi). */
	private double m_dpiX;
	/** Vertical resolution (dpi). */
	private double m_dpiY;

	/**
	 * Loads metadata from a PNG file.
	 * @param pngFile Path to PNG file.
	 * @return <code>true</code> if metadata is correctly loaded, <code>false</code> on error.
	 */
	public boolean load(File pngFile) {
		try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(pngFile))) {
			readHeader(is);
			while (readNextChunk(is));
		} catch (Exception e) {
			System.err.println("Error while reading PNG metadata: " + e.getMessage());
			return false;
		}
		return true;
	}
	
	
	/**
	 * Reads a PNG file reader.
	 * @param is Stream for reading PNG header bytes.
	 * @throws Exception If unable to read, or file is not a valid PNG file.
	 */
	private void readHeader(InputStream is) throws Exception {
		byte[] png_header = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
		byte[] header = readBytes(is, 8);
		if (!Arrays.equals(png_header, header)) {
			throw new Exception("Not a PNG file");
		}
	}
	
	
	/**
	 * Reads the next chunk of PNG metadata and stores found data.
	 * @param is Stream for reading PNG chunks.
	 * @return <code>true</code> on successful read, <code>false</code> if stream has reached end.
	 * @throws Exception On any error.
	 */
	private boolean readNextChunk(InputStream is) throws Exception {
		// Get data length
		byte[] dataLengthBytes = new byte[4];
		int count = is.read(dataLengthBytes);
		if (count == -1) {
			return false;
		}
		if (count != dataLengthBytes.length) {
			throw new Exception("Can't read chunk data length");
		}
		int dataLength = readInteger(dataLengthBytes);
		
		// Get chunk type
		byte[] typeBytes = new byte[4];
		if (is.read(typeBytes) != typeBytes.length) {
			throw new Exception("Can't read chunk type");
		}
		String type = new String(typeBytes, Charset.forName("US-ASCII"));
		
		// Process chunk data
		if (type.equals("IHDR")) {
			parseHeader(is, dataLength);
			
		} else if (type.equals("iCCP")) {
			parseIcc(is, dataLength);
			
		} else if (type.equals("pHYs")) {
			parsePhys(is, dataLength);
			
		} else {
			int skipped = 0;
			while (skipped != dataLength) {
				skipped += is.skip(dataLength - skipped);
			}
		}
		
		// Read CRC
		byte[] crc = new byte[4];
		if (is.read(crc) != crc.length) {
			throw new Exception("Can't read chunk CRC");
		}
				
		return true;
	}
	

	/**
	 * Parses header metadata (IHDR chunk).
	 * @param is Stream for reading chunk data.
	 * @param length Data length.
	 * @throws Exception On error.
	 */
	private void parseHeader(InputStream is, int length) throws Exception {
		if (length != 13) {
			throw new Exception("Bad header data length");
		}
		m_width = readInteger(readBytes(is, 4));
		m_height = readInteger(readBytes(is, 4));
		m_bitDepth = readBytes(is, 1)[0];
		m_colourType = readBytes(is, 1)[0];
		readBytes(is, 3);
	}
	
	
	/**
	 * Parses embedded ICC profile metadata (iCCP chunk)
	 * @param is Stream for reading chunk data.
	 * @param length Data length.
	 * @throws Exception On error.
	 */
	private void parseIcc(InputStream is, int length) throws Exception {
		// Profile name (ignored)
		byte[] b = new byte[1];
		int nameLength = 0;
		do {
			if (is.read(b) == -1) {
				throw new Exception("Can't read ICC profile name");
			}
			nameLength++;
		} while (b[0] != 0);
		
		// Compression method
		if (readBytes(is, 1)[0] != 0) {
			throw new Exception("Unsupported ICC compression method.");
		}
		
		// Read ICC data
		int dataLength = length - nameLength - 1;
		m_iccProfileData = readBytes(is, dataLength);
		m_iccProfileData = decompress(m_iccProfileData);
	}
	

	/**
	 * Parses physical dimensions metadata (pHYs chunk)
	 * @param is Stream for reading chunk data.
	 * @param length Data length.
	 * @throws Exception On error.
	 */
	private void parsePhys(InputStream is, int length) throws Exception {
		if (length != 9) {
			throw new Exception("Bad Phys data length");
		}
		int resX = readInteger(readBytes(is, 4));
		int resY = readInteger(readBytes(is, 4));
		int unit = readBytes(is, 1)[0];

		double inchesPerMeter = 100.0 / 2.54;
		if (unit == 1) {
			m_dpiX = resX / inchesPerMeter;
			m_dpiY = resY / inchesPerMeter;
		}
	}
	
	
	/**
	 * Reads a 32 bit integer from 4 bytes of a PNG file.
	 * @param bytes 4 bytes from a JPEG file.
	 * @return Integer calculated from the 4 bytes.
	 */
	private static int readInteger(byte[] bytes) {
		int value = 0;
		value |= (0xFF & bytes[3]);
		value |= (0xFF & bytes[2]) << 8;
		value |= (0xFF & bytes[1]) << 16;
		value |= (0xFF & bytes[0]) << 24;
		return value;
	}

	
	/**
	 * Decompresses a ZLIB compressed data buffer.
	 * @param data Compressed data.
	 * @return Decompressed data, or <code>null</code> on error.
	 */
	private static byte[] decompress(byte[] data) {  
		Inflater inflater = new Inflater();   
		inflater.setInput(data);
		byte[] output = null;
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
			byte[] buffer = new byte[1024];  
			while (!inflater.finished()) {  
				int count = inflater.inflate(buffer);  
				outputStream.write(buffer, 0, count);  
			}  
			output = outputStream.toByteArray();	
		} catch (Exception e) {
			return null;
		}
		return output;  
	}
	
	
	/**
	 * Compresses a data buffer using the ZLIB algorithm.
	 * @param data Original data.
	 * @return Compressed data.
	 */
	private static byte[] compress(byte[] data) {
		byte[] output = new byte[data.length];
		Deflater compresser = new Deflater();
		compresser.setInput(data);
		compresser.finish();
		int compressedDataLength = compresser.deflate(output);
		compresser.end();
		return Arrays.copyOfRange(output, 0, compressedDataLength);
	}
	
	
	/**
	 * Embeds an ICC profile into an existing PNG file.
	 * @param imagePath Path to PNG file.
	 * @param iccProfile ICC profile to embed.
	 * @return <code>true</code> on success, <code>false</code> on error.
	 */
	public static boolean embedIccProfile(String imagePath, ICC_Profile iccProfile) {
		return embedMetadata(imagePath, iccProfile, 0);
	}


	/**
	 * Embeds resolution data into an existing PNG file.
	 * @param imagePath Path to PNG file.
	 * @param dpi Resolution to embed (dots per inch).
	 * @return <code>true</code> on success, <code>false</code> on error.
	 */
	public static boolean embedResolution(String imagePath, double dpi) {
		return embedMetadata(imagePath, null, dpi);
	}
	
	
	/**
	 * Embeds metadata data into an existing PNG file.
	 * @param imagePath Path to PNG file.
	 * @param iccProfile ICC profile to embed, or <code>null</code> for none.
	 * @param dpi Resolution to embed (dots per inch), or <code>0</code> for none.
	 * @return <code>true</code> on success, <code>false</code> on error.
	 */
	public static boolean embedMetadata(String imagePath, ICC_Profile iccProfile, double dpi) {
		
		// Create temp file
		File tempFile = null;
		try {
			tempFile = File.createTempFile("tmp", "png");
		} catch (Exception e) {
			System.err.println("No se puede crear archivo temporal para incrustar perfil: " + e.getMessage());
			return false;			
		}
		
		// Copy original PNG data, while adding/updating metadata chunks
		File imageFile = new File(imagePath);
		try (
				BufferedInputStream is = new BufferedInputStream(new FileInputStream(imageFile));
				BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile));
			)
		{
			// Header
			byte[] header = readBytes(is, 8);
			os.write(header);
			
			// Chunks
			boolean headerRead = false;
			boolean iccWritten = false;
			boolean physWritten = false;
			do {
				// Read chunk length
				byte[] dataLengthBytes = new byte[4];
				int count = is.read(dataLengthBytes);
				if (count == -1) {
					break;
				}
				if (count != dataLengthBytes.length) {
					throw new Exception("Can't read chunk data length");
				}
				int dataLength = readInteger(dataLengthBytes);
				
				// Read type
				byte[] type = readBytes(is, 4);
				String typeStr = new String(type, Charset.forName("US-ASCII"));
				
				// Read data
				byte[] data = null;
				if (dataLength > 0) {
					data = readBytes(is, dataLength);
				}
				
				// Read CRC
				byte[] crc = readBytes(is, 4);
				
				// Copy chunk to output file, skip chunks to be modified
				boolean skipChunk = false;
				skipChunk |= (typeStr.equals("iCCP") && iccProfile != null);
				skipChunk |= (typeStr.equals("pHYs") && dpi > 0);
				if (!skipChunk) {
					os.write(dataLengthBytes);
					os.write(type);
					if (dataLength > 0) {
						os.write(data);
					}
					os.write(crc);
				}
				
				// Register header read
				if (typeStr.equals("IHDR")) {
					headerRead = true;
				}
				
				// Add new ICC profile chunk
				if (headerRead && iccProfile != null && !iccWritten) {
					byte[] profileData = compress(iccProfile.getData());
					String profileName = "ICC Profile";
					byte[] namebytes = profileName.getBytes(Charset.forName("ISO-8859-1"));
					byte[] chunkData = new byte[profileName.length() + 1 + 1 + profileData.length];

					System.arraycopy(namebytes, 0, chunkData, 0, namebytes.length);
					chunkData[namebytes.length] = 0;
					chunkData[namebytes.length + 1] = 0;
					System.arraycopy(profileData, 0, chunkData, namebytes.length + 2, profileData.length);
					
					writeChunk(os, "iCCP", chunkData);					
					iccWritten = true;
				}
				
				// Add new resolution data chunk
				if (headerRead && dpi > 0 && !physWritten) {
					int pixelsPerMeter = (int) Math.round(dpi * 100.0 / 2.54);
					
					byte[] chunkData = new byte[9];
					chunkData[0] = (byte) ((pixelsPerMeter & 0xFF000000) >> 24);
					chunkData[1] = (byte) ((pixelsPerMeter & 0x00FF0000) >> 16);
					chunkData[2] = (byte) ((pixelsPerMeter & 0x0000FF00) >> 8);
					chunkData[3] = (byte) (pixelsPerMeter & 0x000000FF);
					System.arraycopy(chunkData, 0, chunkData, 4, 4);
					chunkData[8] = 1;

					writeChunk(os, "pHYs", chunkData);					
					physWritten = true;
				}
				
			} while (true);			
			
		} catch (Exception e) {
			System.err.println("Can't generate file with embeded metadata: " + e.getMessage());
			tempFile.delete();
			return false;
		}
		
		// Replace original file with generated temp file
		try {
			Files.delete(imageFile.toPath());
			Files.move(tempFile.toPath(), imageFile.toPath());
		} catch (IOException e) {
			System.err.println("Can't generate file with embeded metadata: " + e.getMessage());
			tempFile.delete();
			return false;
		}

		return true;
	}

	
	/**
	 * Writes a PNG chunk to a stream.
	 * @param os Stream where to write. 
	 * @param chunkType Chunk type.
	 * @param chunkData Chunk data.
	 * @throws Exception On error.
	 */
	private static void writeChunk(OutputStream os, String chunkType, byte[] chunkData) throws Exception {
		int length = chunkData.length;
		os.write((length & 0xFF000000) >> 24);
		os.write((length & 0x00FF0000) >> 16);
		os.write((length & 0x0000FF00) >> 8);
		os.write((length & 0x000000FF));
		
		if (chunkType.length() != 4) {
			throw new Exception("Invalid chunk type: " + chunkType);
		}
		byte[] iccType = chunkType.getBytes(Charset.forName("US-ASCII")); 
		os.write(iccType);
		
		os.write(chunkData);
		
		CRC32 crc32 = new CRC32();
		crc32.update(iccType);
		crc32.update(chunkData);
		long crcValue = crc32.getValue();
		os.write((int)((crcValue & 0x00000000FF000000) >> 24));
		os.write((int)((crcValue & 0x0000000000FF0000) >> 16));
		os.write((int)((crcValue & 0x000000000000FF00) >> 8));
		os.write((int)((crcValue & 0x00000000000000FF)));
	}
	
		
	@Override
	public ICC_Profile getIccProfile() {
		ICC_Profile profile = null;
		try {
			profile = ICC_Profile.getInstance(m_iccProfileData);
		} catch (Exception e) {
			/* Ignore */
		}
		return profile;
	}


	@Override
	public int getWidth() {
		return m_width;
	}

	
	@Override
	public int getHeight() {
		return m_height;
	}


	@Override
	public int getBitDepth() {
		return m_bitDepth;
	}

	
	@Override
	public boolean isGreyscale() {
		return m_colourType == 0 || m_colourType == 4;
	}
	

	@Override
	public boolean isRGB() {
		return m_colourType == 2 || m_colourType == 6;
	}

	
	@Override
	public boolean isIndexed() {
		return m_colourType == 3;
	}

	
	@Override
	public boolean isTransparent() {
		return m_colourType == 4 || m_colourType == 6;
	}

	
	@Override
	public ImageType getImageType() {
		return ImageType.PNG;
	}


	@Override
	public double getDpiX() {
		return m_dpiX;
	}


	@Override
	public double getDpiY() {
		return m_dpiY;
	}	
}
