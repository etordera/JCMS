package com.gmail.etordera.jcms;

/**
 * Represents an ICC color transformation operation.<br><br>
 * This class uses native LittleCMS C library for managing ICC color transformations, and may allocate resources that
 * will not be automatically freed by JVM's garbage collection. It is mandatory to call {@link #dispose() dispose()} method
 * on any <code>IccTransform</code> object when it is no longer needed in order to free all native resources and avoid memory leaks.
 * 
 * @author Enric Tordera
 *
 */
public class IccTransform {
	
	/** Handle to native ICC transform data */
	private long m_hTransform = 0;
	
	/**
	 * Creates an <code>IccTransform</code> object that will manage ICC color conversions.<br>
	 * <br>
	 * Remember to call the {@link #dispose() dispose()} method when this object is no longer needed in order
	 * to assure proper freeing of native resources.
	 * 
	 * @param srcProfile Source ICC profile for the color transformation
	 * @param inputFormat Format of the input pixel data for the color transformation operations (JCMS.TYPE_*).
	 * @param dstProfile Destination ICC profile for the color transformation
	 * @param outputFormat Format of the output pixel data for the color transformation operations  (JCMS.TYPE_*).
	 * @param intent Rendering intent for the color transformation (JCMS.INTENT_*).
	 * @param flags Flags that modify transformation algorithm (JCMS.CMSFLAGS_*) 
	 * @throws JCMSException If not able to create native Icc Transform object
	 */
	public IccTransform(IccProfile srcProfile, int inputFormat, IccProfile dstProfile, int outputFormat, int intent, int flags) throws JCMSException {
		m_hTransform = JCMS.cmsCreateTransform(srcProfile.getHandle(), inputFormat, dstProfile.getHandle(), outputFormat, intent, flags);
		if (m_hTransform == 0) {
			throw new JCMSException("Can't create native IccTransform");
		}
	}
	
	/**
	 * Frees any native resources allocated by this object
	 */
	public void dispose() {
		if (m_hTransform != 0) {
			JCMS.cmsDeleteTransform(m_hTransform);
			m_hTransform = 0;
		}
	}
		
	/**
	 * Performs ICC color transformation on an array of pixel data.<br>
	 * <br>
	 * Input array of pixel data should be formatted according to the input format specified when constructing the
	 * <code>IccTransform</code> object. Transformed pixel data will be formatted according to the output
	 * format specified when constructing the <code>IccTransform</code> object.
	 * 
	 * @param inputData Array of input pixel data to transform.
	 * @param outputData Array of output transformed pixel data.
	 * @param size Number of pixels to be transformed
	 */
	public void transform(byte[] inputData, byte[] outputData, int size) {
		JCMS.cmsDoTransform(m_hTransform, inputData, outputData, size);
	}
	
}
