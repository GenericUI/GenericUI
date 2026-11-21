#ifndef _GENERICUI_JNI_MD_H_
#define _GENERICUI_JNI_MD_H_

#ifndef JNIEXPORT
#define JNIEXPORT __attribute__((visibility("default")))
#endif
#ifndef JNIIMPORT
#define JNIIMPORT
#endif
#ifndef JNICALL
#define JNICALL
#endif

typedef int jint;
#ifdef _LP64
typedef long jlong;
#else
typedef long long jlong;
#endif
typedef signed char jbyte;

#endif /* _GENERICUI_JNI_MD_H_ */
