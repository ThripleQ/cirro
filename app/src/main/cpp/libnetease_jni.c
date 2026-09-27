/*
 * Nume JNI glue — bridges libnetease to the App's Kotlin/OkHttp network layer.
 *
 * Architecture (option A, transport injection):
 *   - libnetease is built WITHOUT libcurl (NE_USE_CURL=OFF). Its request layer
 *     (ne_call_* / services) stays intact and, when it needs to send HTTP,
 *     calls the installed transport.
 *   - Here we install that transport (via ne_http_set_transport): a C shim that
 *     jumps over JNI to the Kotlin singleton NumeTransport.httpRequest(...),
 *     which runs OkHttp synchronously and returns status/body/Set-Cookie.
 *   - Kotlin talks back into the library through the three NumeNative methods
 *     bound below (setCookieFile / setApiBase / request). The cookie jar is
 *     internally mutex-guarded (see the thread contract in netease/request.h),
 *     so multiple request threads can drive the library concurrently.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "netease/http.h"
#include "netease/request.h"
#include "netease/services.h"
#include "netease/util.h"

#define NE_NS "com/thripleq/nume/core/net/"

static JavaVM *g_vm = NULL;

/* ── cached JNI handles (resolved once in JNI_OnLoad) ── */
static jclass       g_transport_cls;
static jmethodID    g_transport_http;
static jclass       g_out_cls;
static jfieldID     g_out_status;
static jfieldID     g_out_err;
static jfieldID     g_out_body;
static jfieldID     g_out_setcookies;
static jclass       g_apiresult_cls;
static jmethodID    g_apiresult_ctor;

/* ── helpers ─────────────────────────────────────────── */
static JNIEnv *attached(void) {
    if (!g_vm) return NULL;
    JNIEnv *env = NULL;
    if ((*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) return env;
    JavaVMAttachArgs a = { JNI_VERSION_1_6, NULL, NULL };
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, &a) == JNI_OK) return env;
    return NULL;
}

static char *copy_jstring(JNIEnv *env, jstring s) {
    if (!s) return NULL;
    const char *u = (*env)->GetStringUTFChars(env, s, NULL);
    if (!u) return NULL;
    char *r = ne_xstrdup(u);
    (*env)->ReleaseStringUTFChars(env, s, u);
    return r;
}

static ne_http_resp *transport_error_resp(const char *msg) {
    ne_http_resp *r = ne_xmalloc(sizeof(ne_http_resp));
    memset(r, 0, sizeof *r);
    r->status = 0;
    r->body = ne_xstrdup("");
    r->err = ne_xstrdup(msg);
    return r;
}

/* ── installed transport: C → Kotlin (OkHttp) ─────────── */
static ne_http_resp *jni_transport_request(
    const char *url, const char *method,
    const char *body, const char *content_type,
    const char *cookie_header, const char *user_agent) {

    JNIEnv *env = attached();
    if (!env) return transport_error_resp("jni attach failed");

    /* Kotlin NumeTransport.httpRequest(method, url, body, contentType,
       cookieHeader, userAgent, realIp); the C transport is (url, method, ...),
       so reorder the first two here to match the Kotlin order. realIp is the
       X-Real-IP value from ne_http_get_real_ip() — NULL when no IP injection
       is configured, letting OkHttp skip the header entirely. */
    const char *real_ip = ne_http_get_real_ip();
    const char *vals[7] = { method, url, body, content_type,
                            cookie_header, user_agent, real_ip };
    jstring js[7];
    int ok = 1;
    for (int i = 0; i < 7; i++) {
        js[i] = vals[i] ? (*env)->NewStringUTF(env, vals[i]) : NULL;
        if (vals[i] && !js[i]) { ok = 0; }
    }
    if (!ok) {
        for (int i = 0; i < 7; i++) if (js[i]) (*env)->DeleteLocalRef(env, js[i]);
        return transport_error_resp("string creation failed");
    }

    jobject out = (*env)->CallStaticObjectMethod(env, g_transport_cls,
                     g_transport_http, js[0], js[1], js[2], js[3], js[4], js[5], js[6]);
    for (int i = 0; i < 7; i++) (*env)->DeleteLocalRef(env, js[i]);
    if (!out) {
        /* A pending JVM exception explains a null transport result; surface it
           instead of discarding it (previous builds only said "returned null"). */
        char msg[256] = "okhttp transport returned null";
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env); /* prints full stack to logcat */
            jthrowable ex = (*env)->ExceptionOccurred(env);
            (*env)->ExceptionClear(env);
            jclass excCls = (*env)->FindClass(env, "java/lang/Throwable");
            if (excCls && ex) {
                jmethodID m = (*env)->GetMethodID(env, excCls, "toString",
                                                  "()Ljava/lang/String;");
                if (m) {
                    jstring s = (*env)->CallObjectMethod(env, ex, m);
                    jboolean isCopy = JNI_FALSE;
                    const char *u = (*env)->GetStringUTFChars(env, s, &isCopy);
                    if (u) {
                        snprintf(msg, sizeof msg, "okhttp transport: %s", u);
                        (*env)->ReleaseStringUTFChars(env, s, u);
                    }
                    (*env)->DeleteLocalRef(env, s);
                }
                (*env)->DeleteLocalRef(env, excCls);
            }
            (*env)->DeleteLocalRef(env, ex);
        }
        return transport_error_resp(msg);
    }

    ne_http_resp *r = ne_xmalloc(sizeof(ne_http_resp));
    memset(r, 0, sizeof *r);
    r->status = (*env)->GetIntField(env, out, g_out_status);

    jstring jerr = (*env)->GetObjectField(env, out, g_out_err);
    r->err = copy_jstring(env, jerr);
    (*env)->DeleteLocalRef(env, jerr);

    jbyteArray jbody = (*env)->GetObjectField(env, out, g_out_body);
    jsize bn = jbody ? (*env)->GetArrayLength(env, jbody) : 0;
    if (bn > 0) {
        r->body = ne_xmalloc((size_t)bn + 1);
        (*env)->GetByteArrayRegion(env, jbody, 0, bn, (jbyte *)r->body);
        r->body[bn] = '\0';
        r->body_len = (size_t)bn;
    } else {
        r->body = ne_xstrdup("");
    }
    (*env)->DeleteLocalRef(env, jbody);

    /* Set-Cookie values arrive as an array; join with '\n' for the jar */
    jobjectArray jsets = (*env)->GetObjectField(env, out, g_out_setcookies);
    if (jsets) {
        jsize n = (*env)->GetArrayLength(env, jsets);
        size_t cap = 64, len = 0;
        char *buf = ne_xmalloc(cap);
        buf[0] = '\0';
        for (jsize i = 0; i < n; i++) {
            jstring s = (*env)->GetObjectArrayElement(env, jsets, i);
            char *line = copy_jstring(env, s);
            (*env)->DeleteLocalRef(env, s);
            if (!line) continue;
            size_t need = len + strlen(line) + 2;
            while (cap < need) cap *= 2;
            buf = ne_xrealloc(buf, cap);
            if (len) buf[len++] = '\n';
            memcpy(buf + len, line, strlen(line));
            len += strlen(line);
            buf[len] = '\0';
            free(line);
        }
        r->set_cookies = len ? buf : NULL;
        if (!r->set_cookies) free(buf);
        (*env)->DeleteLocalRef(env, jsets);
    }

    (*env)->DeleteLocalRef(env, out);
    return r;
}

static const ne_http_transport g_jni_transport = { jni_transport_request };

/* ── dispatch: op → libnetease service function ──────── */
static ne_resp *dispatch(int op, int narg, const char *const a[]) {
#define A(i) (i < narg && a[i] ? a[i] : "")
    switch (op) {
        case 1:  return ne_search(A(0), A(1), A(2), A(3));
        case 2:  return ne_check_music(A(0), A(1));
        case 3:  return ne_record_recent(A(0));
        case 4:  return ne_recommend_resource();
        case 5:  return ne_song_url_v1(A(0), A(1));
        case 6:  return ne_song_url_old(A(0), A(1));
        case 7:  return ne_song_download_url(A(0), A(1));
        case 8:  return ne_song_music_quality(A(0));
        case 9:  return ne_song_purchased(A(0), A(1));
        case 10: return ne_album_purchased(A(0), A(1));
        case 11: return ne_album_detail(A(0));
        case 12: return ne_song_detail(A(0));
        case 13: return ne_playlist_detail(A(0), A(1));
        case 14: return ne_user_playlist(A(0), A(1), A(2));
        case 15: return ne_lyric(A(0));
        case 16: return ne_toplist_detail();
        case 17: return ne_recommend_songs();
        case 18: return ne_recommend_playlists(A(0));
        case 19: return ne_user_account();
        case 20: return ne_vip_info();
        case 21: return ne_like_list(A(0));
        case 22: return ne_playlist_subscribe(A(0), A(1));
        case 23: return ne_playlist_tracks(A(0), A(1), A(2));
        case 24: return ne_playlist_create(A(0), A(1));
        case 25: return ne_playlist_delete(A(0));
        case 26: return ne_playlist_update_name(A(0), A(1));
        case 27: return ne_login_email(A(0), A(1));
        case 28: return ne_login_cellphone(A(0), A(1));
        case 29: return ne_login_refresh();
        case 30: return ne_send_captcha(A(0), A(1));
        case 31: return ne_login_cellphone_captcha(A(0), A(1), A(2));
        case 32: return ne_artist_detail(A(0));
        case 33: return ne_artist_songs(A(0), A(1), A(2), A(3));
        case 34: return ne_artist_albums(A(0), A(1), A(2));
        case 35: return ne_artist_desc(A(0));
        case 36: return ne_radio_detail(A(0));
        case 37: return ne_radio_programs(A(0), A(1), A(2), A(3));
        case 38: return ne_comments(A(0), A(1), A(2), A(3));
        case 39: return ne_comments_hot(A(0), A(1), A(2), A(3));
        default: return NULL;
    }
#undef A
}

/* ── native entry points (bound to NumeNative) ───────── */
static void native_set_cookie_file(JNIEnv *env, jobject thiz, jstring path) {
    char *p = copy_jstring(env, path);
    ne_set_cookie_file(p ? p : "");
    ne_jar_reload();
    free(p);
}

static void native_set_api_base(JNIEnv *env, jobject thiz, jstring base) {
    char *p = copy_jstring(env, base);
    if (p) { ne_set_api_base(p); free(p); }
}

/* Merge a browser-exported cookie string ("MUSIC_U=xxx; __csrf=yyy; ...")
 * into the global jar and persist it to the cookie file, so the next process
 * start reloads the login state. Uses the library's thread-safe import, so it
 * is safe to run concurrently with in-flight requests. */
static void native_import_cookies(JNIEnv *env, jobject thiz, jstring cookieStr) {
    char *s = copy_jstring(env, cookieStr);
    if (!s) return;
    ne_jar_import_cookies(s);
    free(s);
}

static jobject native_request(JNIEnv *env, jobject thiz, jint op,
                              jobjectArray jargs) {
    jsize n = jargs ? (*env)->GetArrayLength(env, jargs) : 0;
    if (n > 8) n = 8;
    const char *a[8] = { 0 };
    for (jsize i = 0; i < n; i++) {
        jstring s = (*env)->GetObjectArrayElement(env, jargs, i);
        a[i] = copy_jstring(env, s);
        (*env)->DeleteLocalRef(env, s);
    }

    ne_resp *r = dispatch((int)op, (int)n, a);
    for (jsize i = 0; i < n; i++) free((void *)a[i]);
    if (!r) {
        r = ne_xmalloc(sizeof(ne_resp));
        memset(r, 0, sizeof *r);
        r->code = 520;
        r->err = 1;
        r->body = ne_xstrdup("no service for op");
    }

    const char *body = r->body ? r->body : "";
    jsize blen = (jsize)strlen(body);
    jbyteArray jb = (*env)->NewByteArray(env, blen);
    (*env)->SetByteArrayRegion(env, jb, 0, blen, (const jbyte *)body);
    jobject res = (*env)->NewObject(env, g_apiresult_cls, g_apiresult_ctor,
                                    (jint)r->code, (jint)r->err, jb);
    (*env)->DeleteLocalRef(env, jb);
    ne_resp_free(r);
    return res;
}

static JNINativeMethod g_methods[] = {
    { "setCookieFile", "(Ljava/lang/String;)V", (void *)native_set_cookie_file },
    { "setApiBase",    "(Ljava/lang/String;)V", (void *)native_set_api_base },
    { "importCookies", "(Ljava/lang/String;)V", (void *)native_import_cookies },
    { "request",
      "(I[Ljava/lang/String;)L" NE_NS "ApiResult;", (void *)native_request },
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    JNIEnv *e = NULL;
    if ((*vm)->GetEnv(vm, (void **)&e, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    jclass nc = (*e)->FindClass(e, NE_NS "NumeNative");
    if (nc) {
        (*e)->RegisterNatives(e, nc, g_methods, 4);
        (*e)->DeleteLocalRef(e, nc);
    }

    g_transport_cls = (*e)->NewGlobalRef(e,
        (*e)->FindClass(e, NE_NS "NumeTransport"));
    g_transport_http = (*e)->GetStaticMethodID(e, g_transport_cls, "httpRequest",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)L" NE_NS "NumeTransportOut;");
    g_out_cls = (*e)->NewGlobalRef(e,
        (*e)->FindClass(e, NE_NS "NumeTransportOut"));
    g_out_status = (*e)->GetFieldID(e, g_out_cls, "status", "I");
    g_out_err = (*e)->GetFieldID(e, g_out_cls, "err", "Ljava/lang/String;");
    g_out_body = (*e)->GetFieldID(e, g_out_cls, "body", "[B");
    g_out_setcookies = (*e)->GetFieldID(e, g_out_cls, "setCookies",
                                        "[Ljava/lang/String;");
    g_apiresult_cls = (*e)->NewGlobalRef(e,
        (*e)->FindClass(e, NE_NS "ApiResult"));
    g_apiresult_ctor = (*e)->GetMethodID(e, g_apiresult_cls, "<init>", "(II[B)V");

    ne_http_set_transport(&g_jni_transport);
    ne_http_set_random_cn_ip(1);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *e = NULL;
    if ((*vm)->GetEnv(vm, (void **)&e, JNI_VERSION_1_6) != JNI_OK) return;
    (*e)->DeleteGlobalRef(e, g_transport_cls);
    (*e)->DeleteGlobalRef(e, g_out_cls);
    (*e)->DeleteGlobalRef(e, g_apiresult_cls);
}