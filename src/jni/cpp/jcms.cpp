#include <jni.h>
#include <lcms2.h>
#include "com_gmail_etordera_jcms_JCMS.h"

JNIEXPORT jlong JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsOpenProfileFromFile
  (JNIEnv *env, jclass cls, jstring filename, jstring mode) {

	const char *strFilename = env->GetStringUTFChars(filename, 0);
	const char *strMode = env->GetStringUTFChars(mode, 0);
	cmsHPROFILE hProfile = cmsOpenProfileFromFile(strFilename, strMode);
	env->ReleaseStringUTFChars(filename, strFilename);
	env->ReleaseStringUTFChars(mode, strMode);

	return (jlong)hProfile;
}

JNIEXPORT jlong JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsOpenProfileFromMem
  (JNIEnv *env, jclass cls, jbyteArray dataBuffer) {
	jbyte* data = env->GetByteArrayElements(dataBuffer, NULL);
	jsize size = env->GetArrayLength(dataBuffer);
	cmsHPROFILE hProfile = cmsOpenProfileFromMem((void*) data, (cmsUInt32Number) size);
	env->ReleaseByteArrayElements(dataBuffer, data, 0);

	return (jlong)hProfile;
}

JNIEXPORT jboolean JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsCloseProfile
  (JNIEnv *env, jclass cls, jlong hprofile) {
	cmsCloseProfile((void *)hprofile);
	return true;
}

JNIEXPORT jstring JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsGetProfileInfoASCII
  (JNIEnv *env, jclass cls, jlong hprofile) {
	char textBuffer[512];
	cmsGetProfileInfoASCII((void*)hprofile, cmsInfoDescription,"en","EN",textBuffer,512);
	return env->NewStringUTF(textBuffer);
}

JNIEXPORT jbyteArray JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsSaveProfileToMem
  (JNIEnv *env, jclass cls, jlong hprofile) {

	// Get required buffer size
	cmsUInt32Number size = 0;
	if (!cmsSaveProfileToMem((cmsHPROFILE) hprofile, NULL, &size)) {
		return env->NewByteArray(0);
	}

	// Generate buffer and save data
	jbyteArray dataBuffer = env->NewByteArray((jsize) size);
	jbyte* data = env->GetByteArrayElements(dataBuffer, NULL);
	if (!cmsSaveProfileToMem((cmsHPROFILE) hprofile, (void *)data, &size)) {
		delete dataBuffer;
		env->ReleaseByteArrayElements(dataBuffer, data, 0);
		return env->NewByteArray(0);
	}
	env->ReleaseByteArrayElements(dataBuffer, data, 0);

	return dataBuffer;
}


JNIEXPORT jlong JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsCreateTransform
  (JNIEnv *env, jclass cls, jlong hInputProfile, jint inputType, jlong hOutputProfile, jint outputType, jint intent, jint flags) {
	cmsHTRANSFORM hTransform = cmsCreateTransform((void*)hInputProfile, inputType, (void*)hOutputProfile, outputType, intent, flags);
	return (jlong)hTransform;
}

JNIEXPORT void JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsDeleteTransform
  (JNIEnv *env, jclass cls, jlong hTransform) {
	cmsDeleteTransform((void*)hTransform);
	return;
}

JNIEXPORT void JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsDoTransform
  (JNIEnv *env, jclass cls, jlong hTransform, jbyteArray inputBuffer, jbyteArray outputBuffer, jint size) {
	jbyte* input = env->GetByteArrayElements(inputBuffer, NULL);
	jbyte* output = env->GetByteArrayElements(outputBuffer, NULL);
	cmsDoTransform((void*)hTransform, (const void*)input, (void*)output, size);
	env->ReleaseByteArrayElements(inputBuffer, input, 0);
	env->ReleaseByteArrayElements(outputBuffer, output, 0);
	return;
}

JNIEXPORT jlong JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsCreate_1sRGBProfile
  (JNIEnv *env, jclass cls) {
	cmsHPROFILE hProfile = cmsCreate_sRGBProfile();
	return (jlong) hProfile;
}


JNIEXPORT jlong JNICALL Java_com_gmail_etordera_jcms_JCMS_cmsCreateGrayProfile
  (JNIEnv *env, jclass cls, jdouble gamma) {
	cmsToneCurve* GammaCurve = cmsBuildGamma(0, (cmsFloat64Number)gamma);
	cmsHPROFILE hProfile = cmsCreateGrayProfile(cmsD50_xyY(), GammaCurve);
	cmsFreeToneCurve(GammaCurve);
	return (jlong) hProfile;
}
