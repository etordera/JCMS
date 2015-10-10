package com.gmail.etordera.imaging;

import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Utility class for writing JPEG images.
 * 
 * @author enric
 *
 */
public class JPEGWriter {

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
	public static boolean write(BufferedImage image, File file, float quality, int dpi, ICC_Profile profile) {
		
		ImageOutputStream os = null;
		try {

			// Create JPEG writer
			ImageWriter writer = ImageIO.getImageWritersByFormatName("JPEG").next();
			os = ImageIO.createImageOutputStream(file);
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
			os.close();
			
			// Embed ICC profile
			if (profile != null)  {
				if (!JPEGMetadata.embedIccProfile(profile, file.getAbsolutePath())) {
					return false;
				}
			}
			
		} catch (Exception e) {
			try {os.close();} catch (Exception ex) {}				
			return false;
		}
		
		return true;
	}
	
}
