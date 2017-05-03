package com.gmail.etordera.imaging;

import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Utility class for writing images to disk.
 *
 */
public class ImageWriter {

	/**
	 * Writes an image to disk in JPEG format.
	 * 
	 * @param image Image to write.
	 * @param file Destination file.
	 * @param quality JPEG compression quality: <code>0</code> (minimum quality) to <code>1</code> maximum. 
	 * @param dpi Resolution to be included in JPEG metadata (dots per inch)
	 * @param profile ICC profile to be embedded in JPEG metadata (may be <code>null</code>)
	 * @return <code>true</code> on success, <code>false</code> on error
	 */
	public static boolean writeJpeg(BufferedImage image, File file, float quality, int dpi, ICC_Profile profile) {

		javax.imageio.ImageWriter writer = null;
		try (ImageOutputStream os = ImageIO.createImageOutputStream(file)) {

			// Create JPEG writer
			writer = ImageIO.getImageWritersByFormatName("JPEG").next();
			writer.setOutput(os);

			// Set quality
			ImageWriteParam params = writer.getDefaultWriteParam();
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(quality);

			// Set dpi
			IIOMetadata data = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), params);
			if (dpi > 0) {
				IIOMetadataNode tree = (IIOMetadataNode) data.getAsTree("javax_imageio_jpeg_image_1.0");
				IIOMetadataNode jfif = (IIOMetadataNode) tree.getElementsByTagName("app0JFIF").item(0);
				jfif.setAttribute("Xdensity", Integer.toString(dpi));
				jfif.setAttribute("Ydensity", Integer.toString(dpi));
				jfif.setAttribute("resUnits", "1");	
				data.setFromTree("javax_imageio_jpeg_image_1.0", tree);
			}
			
			// Write
			writer.write(null,new IIOImage(image,null,data),params);
			writer.dispose();
			
		} catch (Exception e) {
			try {writer.dispose();} catch (Exception ex) {/*Ignore*/}
			return false;
		}

		// Embed ICC profile
		if (profile != null)  {
			if (!JPEGMetadata.embedIccProfile(profile, file.getAbsolutePath())) {
				return false;
			}
		}
		
		return true;
	}

	
	/**
	 * Writes an image to disk in PNG format.
	 * 
	 * @param image Image to write.
	 * @param file Destination file.
	 * @param dpi Resolution to be included in PNG metadata (dots per inch), <code>0</code> for none.
	 * @param profile ICC profile to be embedded in JPEG metadata (may be <code>null</code>)
	 * @return <code>true</code> on success, <code>false</code> on error
	 */
	public static boolean writePng(BufferedImage image, File file, double dpi, ICC_Profile profile) {

		// Write PNG Image
		try  {
			if (!ImageIO.write(image, "PNG", file)) {
				throw new Exception("No suitable writer found for PNG format.");
			}
		} catch (Exception e) {
			System.err.println("PNG write error: " + e.getMessage());
			return false;
		}

		// Embed Metadata
		if (dpi < 0) dpi = 0;
		if (!PNGMetadata.embedMetadata(file.getAbsolutePath(), profile, dpi)) {
			System.err.println("Unable to embed PNG metadata.");
			return false;
		}
		
		return true;
	}	
}
