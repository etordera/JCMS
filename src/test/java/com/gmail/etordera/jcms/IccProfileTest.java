package com.gmail.etordera.jcms;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for {@link com.gmail.etordera.jcms.IccProfile} class 
 *
 */
public class IccProfileTest {

	/**
	 * Test method for {@link com.gmail.etordera.jcms.IccProfile#getProfileInfo()}.
	 */
	@Test
	public void testGetProfileInfo() {
		try {
			IccProfile profile = new IccProfile(IccProfile.PROFILE_ADOBERGB);
			String profileInfo = profile.getProfileInfo();
			assertEquals("Adobe RGB (1998)", profileInfo);
		} catch (JCMSException e) {
			fail("Exception loading AdobeRGB Icc Profile: "+e.getMessage());
			return;
		}
	}

}
