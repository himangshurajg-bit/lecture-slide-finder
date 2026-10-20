#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
/* Vosk's public C ABI; the Apache-2.0 native library is linked, not JNA. */
typedef struct VoskModel VoskModel;
typedef struct VoskRecognizer VoskRecognizer;
extern VoskModel *vosk_model_new(const char *);
extern void vosk_model_free(VoskModel *);
extern VoskRecognizer *vosk_recognizer_new(VoskModel *, float);
extern void vosk_recognizer_free(VoskRecognizer *);
extern void vosk_recognizer_set_words(VoskRecognizer *, int);
extern int vosk_recognizer_accept_waveform_s(VoskRecognizer *, const short *, int);
extern const char *vosk_recognizer_result(VoskRecognizer *);
extern const char *vosk_recognizer_final_result(VoskRecognizer *);
extern void vosk_set_log_level(int);
typedef struct { VoskModel *model; VoskRecognizer *recognizer; } Session;
static void fail(JNIEnv *e,const char *s){(*e)->ThrowNew(e,(*e)->FindClass(e,"java/io/IOException"),s);}
static jstring text(JNIEnv *e,const char *s){
 jbyteArray b=(*e)->NewByteArray(e,strlen(s)); if(!b)return NULL;
 (*e)->SetByteArrayRegion(e,b,0,strlen(s),(const jbyte *)s);
 jclass c=(*e)->FindClass(e,"java/lang/String");
 return (jstring)(*e)->NewObject(e,c,(*e)->GetMethodID(e,c,"<init>","([BLjava/lang/String;)V"),b,(*e)->NewStringUTF(e,"UTF-8"));
}
JNIEXPORT jlong JNICALL Java_io_github_himangshurajg_lecture_VoskEngine_open(JNIEnv *e,jclass c,jstring path){
 const char *p=(*e)->GetStringUTFChars(e,path,NULL); if(!p)return 0;
 vosk_set_log_level(-1); Session *s=calloc(1,sizeof(Session));
 if(s)s->model=vosk_model_new(p); (*e)->ReleaseStringUTFChars(e,path,p);
 if(s&&s->model)s->recognizer=vosk_recognizer_new(s->model,16000);
 if(!s||!s->recognizer){if(s){if(s->model)vosk_model_free(s->model);free(s);}fail(e,"Cannot load offline language model");return 0;}
 vosk_recognizer_set_words(s->recognizer,1); return (jlong)(intptr_t)s;
}
JNIEXPORT void JNICALL Java_io_github_himangshurajg_lecture_VoskEngine_close(JNIEnv *e,jclass c,jlong handle){
 Session *s=(Session *)(intptr_t)handle;if(s){vosk_recognizer_free(s->recognizer);vosk_model_free(s->model);free(s);}
}
JNIEXPORT jstring JNICALL Java_io_github_himangshurajg_lecture_VoskEngine_accept(JNIEnv *e,jclass c,jlong handle,jshortArray audio,jint count,jboolean last){
 Session *s=(Session *)(intptr_t)handle;
 if(!s||count<0||count>4000||count>(*e)->GetArrayLength(e,audio)){fail(e,"Invalid speech audio chunk");return NULL;}
 if(last)return text(e,vosk_recognizer_final_result(s->recognizer));
 short pcm[4000]; (*e)->GetShortArrayRegion(e,audio,0,count,pcm); if((*e)->ExceptionCheck(e))return NULL;
 int result=vosk_recognizer_accept_waveform_s(s->recognizer,pcm,count);
 if(result<0){fail(e,"Offline speech recognition failed");return NULL;}
 return text(e,result?vosk_recognizer_result(s->recognizer):"{}");
}
