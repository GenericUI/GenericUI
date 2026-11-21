#ifndef _GENERICUI_JNI_MD_H_
#define _GENERICUI_JNI_MD_H_

#ifndef JNIEXPORT
#define JNIEXPORT __declspec(dllexport)
#endif
#ifndef JNIIMPORT
#define JNIIMPORT __declspec(dllimport)
#endif
#ifndef JNICALL
#define JNICALL __stdcall
#endif

typedef int jint;
typedef long long jlong;
typedef signed char jbyte;

#endif /* _GENERICUI_JNI_MD_H_ */
