package com.gmail.etordera.jcms;

/**
 * Custom Exception class for errors detected in JCMS classes.
 * 
 * @author enric
 *
 */
public class JCMSException extends Exception {

	/** Generated serial version UID */
	private static final long serialVersionUID = 7029834840954137184L;

	/** Constructs a new JCSM Exception with null as its detail message. */
	public JCMSException() {
		super();
	}

	/**
	 * Constructs a new JCMS Exception with the specified detail message.
	 * @param message Detail message for the exception
	 */
	public JCMSException(String message) {
		super(message);
	}
	
	/**
	 * Constructs a new JCMS Exception with the specified cause and a detail message
	 * of <code>(cause==null ? null : cause.toString())</code> (which typically contains
	 * the class and detail message of cause).
	 * @param cause Cause of the exception
	 */
	public JCMSException(Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs a new JCMS Exception with the specified detail message and cause.
	 * @param message Detail message for the exception
	 * @param cause Cause of the exception
	 */
	public JCMSException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new JCMS Exception with the specified detail message, cause,
	 * suppression enabled or disabled, and writable stack trace enabled or disabled.
	 * @param message Detail message for the exception
	 * @param cause Cause of the exception
	 * @param enableSuppression Suppression enabled or disabled
	 * @param writableStackTrace Stack trace enabled or disabled
	 */
	public JCMSException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
