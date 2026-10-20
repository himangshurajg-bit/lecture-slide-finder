#include <jni.h>
#include <whisper.h>
#include <string>
#include <algorithm>
#include <thread>
#include <sstream>
#include <vector>
struct Cancellation {JavaVM *vm; jobject flag; jmethodID get;};
static bool cancelled(void *ptr) {
    auto *c=static_cast<Cancellation*>(ptr);JNIEnv *env=nullptr;bool own=false;
    if(c->vm->GetEnv(reinterpret_cast<void**>(&env),JNI_VERSION_1_6)!=JNI_OK){if(c->vm->AttachCurrentThread(&env,nullptr)!=JNI_OK)return true;own=true;}
    bool stop=env->CallBooleanMethod(c->flag,c->get);if(env->ExceptionCheck()){env->ExceptionClear();stop=true;}
    if(own)c->vm->DetachCurrentThread();return stop;
}
static void error(JNIEnv *env,const char *text){env->ThrowNew(env->FindClass("java/io/IOException"),text);}
static jstring utf8(JNIEnv *env,const std::string &s){jbyteArray bytes=env->NewByteArray(s.size());env->SetByteArrayRegion(bytes,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));auto cls=env->FindClass("java/lang/String");return static_cast<jstring>(env->NewObject(cls,env->GetMethodID(cls,"<init>","([BLjava/lang/String;)V"),bytes,env->NewStringUTF("UTF-8")));}
static std::string quote(const char *text){std::string out="\"";for(const unsigned char *p=reinterpret_cast<const unsigned char*>(text);*p;++p){if(*p=='"'||*p=='\\'){out+='\\';out+=*p;}else if(*p<32){const char *h="0123456789abcdef";out+="\\u00";out+=h[*p>>4];out+=h[*p&15];}else out+=*p;}return out+'"';}
extern "C" JNIEXPORT jlong JNICALL Java_io_github_himangshurajg_lecture_WhisperEngine_open(JNIEnv *env,jclass,jstring file){
    const char *path=env->GetStringUTFChars(file,nullptr);auto params=whisper_context_default_params();params.use_gpu=false;
    auto *ctx=whisper_init_from_file_with_params(path,params);env->ReleaseStringUTFChars(file,path);if(!ctx)error(env,"Unable to load bundled multilingual speech model");return reinterpret_cast<jlong>(ctx);
}
extern "C" JNIEXPORT void JNICALL Java_io_github_himangshurajg_lecture_WhisperEngine_close(JNIEnv*,jclass,jlong handle){whisper_free(reinterpret_cast<whisper_context*>(handle));}
extern "C" JNIEXPORT jstring JNICALL Java_io_github_himangshurajg_lecture_WhisperEngine_recognize(JNIEnv *env,jclass,jlong handle,jfloatArray audio,jint count,jstring language,jobject flag){
    auto *ctx=reinterpret_cast<whisper_context*>(handle);if(!ctx||count<=0||count>480000||count>env->GetArrayLength(audio)){error(env,"Invalid audio chunk");return nullptr;}
    // Copy, never pin a Java array across long-running native inference.
    std::vector<float> pcm(count);env->GetFloatArrayRegion(audio,0,count,pcm.data());
    Cancellation cancel{};env->GetJavaVM(&cancel.vm);cancel.flag=env->NewGlobalRef(flag);cancel.get=env->GetMethodID(env->GetObjectClass(flag),"get","()Z");
    const char *lang=env->GetStringUTFChars(language,nullptr);auto p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads=std::max(1u,std::min(4u,std::thread::hardware_concurrency()));p.translate=false;p.no_context=true;p.language=lang;
    p.print_progress=false;p.print_realtime=false;p.print_timestamps=false;p.print_special=false;p.temperature_inc=0;p.abort_callback=cancelled;p.abort_callback_user_data=&cancel;
    int result=whisper_full(ctx,p,pcm.data(),count);bool stopped=cancelled(&cancel);env->ReleaseStringUTFChars(language,lang);env->DeleteGlobalRef(cancel.flag);
    if(stopped){env->ThrowNew(env->FindClass("java/lang/InterruptedException"),"Transcription cancelled");return nullptr;}
    if(result!=0){error(env,"On-device speech recognition failed");return nullptr;}
    std::ostringstream out;out<<"[";bool first=true;for(int i=0;i<whisper_full_n_segments(ctx);i++){
        double start=whisper_full_get_segment_t0(ctx,i)/100.0,end=whisper_full_get_segment_t1(ctx,i)/100.0;end=std::min(end,count/16000.0);if(end<=start)continue;
        if(!first)out<<",";first=false;out<<"{\"start\":"<<start<<",\"end\":"<<end<<",\"text\":"<<quote(whisper_full_get_segment_text(ctx,i))<<"}";
    }out<<"]";return utf8(env,out.str());
}
