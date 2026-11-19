/*
 * Small JNI bridge between Kotlin (Mp3Encoder.kt) and the LAME MP3 encoder.
 * Mono 16-bit PCM in, constant-bitrate MP3 frames out.
 */
#include <jni.h>
#include <stdint.h>
#include "lame.h"

#define GF(h) ((lame_global_flags *)(intptr_t)(h))

JNIEXPORT jlong JNICALL
Java_com_tzipdvir_haklatot_recording_Mp3Encoder_nativeInit(JNIEnv *env, jclass clazz,
                                                           jint sampleRate, jint bitrateKbps) {
    lame_global_flags *gf = lame_init();
    if (gf == NULL) return 0;
    lame_set_in_samplerate(gf, sampleRate);
    lame_set_out_samplerate(gf, sampleRate);
    lame_set_num_channels(gf, 1);
    lame_set_mode(gf, MONO);
    lame_set_VBR(gf, vbr_off);
    lame_set_brate(gf, bitrateKbps);
    lame_set_quality(gf, 5);          /* good quality / speed balance */
    lame_set_bWriteVbrTag(gf, 0);     /* streaming: every frame is self-contained, no header to patch later */
    if (lame_init_params(gf) < 0) {
        lame_close(gf);
        return 0;
    }
    return (jlong)(intptr_t)gf;
}

JNIEXPORT jint JNICALL
Java_com_tzipdvir_haklatot_recording_Mp3Encoder_nativeEncode(JNIEnv *env, jclass clazz, jlong handle,
                                                             jshortArray pcm, jint samples,
                                                             jbyteArray out) {
    jsize outLen = (*env)->GetArrayLength(env, out);
    jshort *in = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    int n = lame_encode_buffer(GF(handle), in, in, samples, (unsigned char *)o, outLen);
    (*env)->ReleaseShortArrayElements(env, pcm, in, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return n;
}

JNIEXPORT jint JNICALL
Java_com_tzipdvir_haklatot_recording_Mp3Encoder_nativeFlush(JNIEnv *env, jclass clazz, jlong handle,
                                                            jbyteArray out) {
    jsize outLen = (*env)->GetArrayLength(env, out);
    jbyte *o = (*env)->GetByteArrayElements(env, out, NULL);
    int n = lame_encode_flush(GF(handle), (unsigned char *)o, outLen);
    (*env)->ReleaseByteArrayElements(env, out, o, 0);
    return n;
}

JNIEXPORT void JNICALL
Java_com_tzipdvir_haklatot_recording_Mp3Encoder_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle != 0) lame_close(GF(handle));
}
