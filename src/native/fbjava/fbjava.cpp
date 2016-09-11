/*
 *  The contents of this file are subject to the Initial
 *  Developer's Public License Version 1.0 (the "License");
 *  you may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *  http://www.ibphoenix.com/main.nfs?a=ibphoenix&page=ibp_idpl.
 *
 *  Software distributed under the License is distributed AS IS,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied.
 *  See the License for the specific language governing rights
 *  and limitations under the License.
 *
 *  The Original Code was created by Adriano dos Santos Fernandes
 *  for the Firebird Open Source RDBMS project.
 *
 *  Copyright (c) 2015 Adriano dos Santos Fernandes <adrianosf@uol.com.br>
 *  and all contributors signed below.
 *
 *  All Rights Reserved.
 *  Contributor(s): ______________________________________.
 */

#include "firebird/Interface.h"
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/stat.h>
#include <dirent.h>

#ifdef WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

#include "jni.h"

using namespace Firebird;
using std::auto_ptr;
using std::exception;
using std::runtime_error;
using std::string;
using std::vector;


//------------------------------------------------------------------------------


#ifdef WIN32
static const char DEFAULT_PATH_SEP = '\\';
static HMODULE hDllInstance = NULL;
#else
static const char DEFAULT_PATH_SEP = '/';
#endif


//------------------------------------------------------------------------------


template <typename T>
class FbAuto
{
public:
	FbAuto(T* aObj)
		: obj(aObj)
	{
	}

	~FbAuto()
	{
		release();
	}

public:
	FbAuto& operator= (T* o)
	{
		release();
		obj = o;
		return *this;
	}

	operator T*()
	{
		return obj;
	}

	operator const T*() const
	{
		return obj;
	}

	bool operator !() const
	{
		return !obj;
	}

	T* operator->()
	{
		return obj;
	}

	const T* operator->() const
	{
		return obj;
	}

private:
	void release()
	{
		if (obj)
		{
			release(obj);
			obj = NULL;
		}
	}

	void release(IReferenceCounted* o)
	{
		o->release();
	}

	void release(IDisposable* o)
	{
		o->dispose();
	}

private:
	T* obj;
};

//--------------------------------------

class DynLibrary
{
public:
	DynLibrary(const string& filename)
		: filename(filename)
	{
#ifdef WIN32
		module = LoadLibrary(filename.c_str());
#else
		module = dlopen(filename.c_str(), RTLD_NOW);
#endif
	}

	~DynLibrary()
	{
#ifdef WIN32
		FreeLibrary(module);
#else
		dlclose(module);
#endif
	}

public:
	// Lookup a library symbol. Throw exception in case of error.
	template <typename T>
	void findSymbol(const char* name, T& ptr)
	{
#ifdef WIN32
		ptr = reinterpret_cast<T>(GetProcAddress(module, name));
#else
		ptr = reinterpret_cast<T>(dlsym(module, name));
#endif

		if (!ptr)
			throw runtime_error(string("Symbol '") + name + "' not found in '" + filename + "'.");
	}

private:
	string filename;

#ifdef WIN32
	HMODULE module;
#else
	void* module;
#endif
};


//--------------------------------------


static string findJvmLibrary(const char* javaHome);
static void checkJavaException(JNIEnv* env);
static jclass findClass(JNIEnv* env, const char* name, bool createGlobalRef);
static jmethodID getStaticMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature);
static jmethodID getMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature);
static void init();


//--------------------------------------


static auto_ptr<DynLibrary> library;
static IMaster* master;


//--------------------------------------


static string findJvmLibrary(const char* javaHome)
{
#ifdef WIN32
	static const char* const DEFAULT_PLACES[] = {
		"\\jre\\bin\\server\\jvm.dll",
		"\\jre\\lib\\client\\jvm.dll"
	};
#else
#ifdef __amd64
#define ARCH_STR "amd64"
#else
#define ARCH_STR "i386"
#endif
	static const char* const DEFAULT_PLACES[] = {
		"/jre/lib/" ARCH_STR "/server/libjvm.so",
		"/jre/lib/" ARCH_STR "/client/libjvm.so"
	};
#endif

	if (javaHome)
	{
		for (unsigned i = 0; i < sizeof(DEFAULT_PLACES) / sizeof(DEFAULT_PLACES[0]); ++i)
		{
			string path(javaHome);
			path += DEFAULT_PLACES[i];

			struct stat st;
			if (stat(path.c_str(), &st) == 0)
				return path;
		}
	}

	return "";
}


// Check if a Java exception occurred in the current thread. If yes, rethrow it as a C++ exception.
static void checkJavaException(JNIEnv* env)
{
	static bool initException = false;
	static jclass throwableCls;
	static jmethodID throwableToStringId;

	if (env->ExceptionCheck())
	{
		jthrowable ex = env->ExceptionOccurred();

		if (!initException)
		{
			throwableCls = env->FindClass("java/lang/Throwable");
			throwableToStringId = env->GetMethodID(throwableCls, "toString",
				"()Ljava/lang/String;");

			initException = true;
		}

		env->ExceptionClear();

		string msg;
		jstring jMsg = (jstring) env->CallObjectMethod(ex, throwableToStringId);

		if (jMsg)
		{
			const char* msgPtr = env->GetStringUTFChars(jMsg, NULL);
			if (msgPtr)
			{
				msg = msgPtr;
				env->ReleaseStringUTFChars(jMsg, msgPtr);
			}

			env->DeleteLocalRef(jMsg);
		}
		else
			msg = "Unknown Java exception.";

		env->DeleteLocalRef(ex);

		throw runtime_error(msg);
	}
}


// Find a Java class and optionally creates a global reference to it.
// Throw exception in case of error.
static jclass findClass(JNIEnv* env, const char* name, bool createGlobalRef)
{
	jclass localCls = env->FindClass(name);
	jclass globalCls = NULL;

	if (localCls)
	{
		if (createGlobalRef)
		{
			globalCls = (jclass) env->NewGlobalRef(localCls);
			env->DeleteLocalRef(localCls);
		}
		else
			globalCls = localCls;
	}

	if (!globalCls)
		checkJavaException(env);

	return globalCls;
}


// Gets the ID of a static method. Throw exception in case of error.
static jmethodID getStaticMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature)
{
	jmethodID mid = env->GetStaticMethodID(cls, name, signature);
	if (!mid)
		checkJavaException(env);

	return mid;
}


// Gets the ID of a method. Throw exception in case of error.
static jmethodID getMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature)
{
	jmethodID mid = env->GetMethodID(cls, name, signature);
	if (!mid)
		checkJavaException(env);

	return mid;
}


static void init()
{
	const char* javaHome = NULL;

	try
	{
		FbAuto<IStatus> status(master->getStatus());
		ThrowStatusWrapper st(status);

		if (IConfigManager* configManager = master->getConfigManager())
		{
			if (FbAuto<IConfig> config = configManager->getPluginConfig("JAVA"))
			{
				if (FbAuto<IConfigEntry> entry = config->find(&st, "JavaHome"))
					javaHome = entry->getValue();
			}
		}
	}
	catch (const FbException& e)
	{
		throw runtime_error("Error looking for JavaHome in the config file.");
	}

#ifdef WIN32
	string libFile;

	{	// scope
		char buffer[MAX_PATH];
		GetModuleFileName(hDllInstance, buffer, sizeof(buffer));
		libFile = buffer;
	}
#else
	Dl_info dlInfo;
	if (dladdr((void*) init, &dlInfo) == 0)
		throw runtime_error("Cannot get the plugin library path.");

	string libFile(dlInfo.dli_fname);
#endif

	string libDir(libFile.substr(0, strrchr(libFile.c_str(), DEFAULT_PATH_SEP) - libFile.c_str()));
	libDir += DEFAULT_PATH_SEP;
	libDir += "../jar";

	if (!javaHome)
		javaHome = getenv("JAVA_HOME");

	if (!javaHome)
		throw runtime_error("JAVA_HOME environment variable is not defined.");

	string jvmLibrary = findJvmLibrary(javaHome);

	if (jvmLibrary.empty())
		throw runtime_error("No JVM library found in '" + string(javaHome) + "'.");

	library.reset(new DynLibrary(jvmLibrary));

	// JNI entrypoints
	jint (JNICALL *getCreatedJavaVMs)(JavaVM**, jsize, jsize*);
	library->findSymbol("JNI_GetCreatedJavaVMs", getCreatedJavaVMs);

	jint (JNICALL *createJavaVM)(JavaVM**, void**, void*);
	library->findSymbol("JNI_CreateJavaVM", createJavaVM);

	//// TODO: Get configuration options.
	JavaVMInitArgs vmArgs;
	memset(&vmArgs, 0, sizeof(vmArgs));
	vmArgs.version = JNI_VERSION_1_4;
	vmArgs.nOptions = 0;
	vmArgs.ignoreUnrecognized = JNI_FALSE;

	JavaVM* jvm = NULL;

	// Verify if there is already a JVM created. It happens, for example, using the embedded
	// engine in Java or if any other plugin has loaded it. If it was already loaded, we
	// don't do anything with the configured options.
	jsize jvmCount = 0;
	jint jvmStatus = getCreatedJavaVMs(&jvm, 1, &jvmCount);

	if (jvmCount == 0)
	{
		// There is no JVM. Let's create one.
		JNIEnv* dummyEnv;
		jvmStatus = createJavaVM(&jvm, (void**) &dummyEnv, &vmArgs);

		if (jvmStatus != 0)
			throw runtime_error("Error creating JVM.");
	}

	JNIEnv* env;

	if (jvm->GetEnv((void**) &env, JNI_VERSION_1_4) == JNI_EDETACHED)
	{
		if (jvm->AttachCurrentThread((void**) &env, NULL) != JNI_OK)
			throw runtime_error("Cannot attach the current thread to the JVM.");
	}

	//// TODO: Delete local refs.

	// Lets create an URL classloader for the plugin.

	jclass urlCls = findClass(env, "java/net/URL", false);
	jmethodID urlInitId = getMethodID(env, urlCls, "<init>", "(Ljava/lang/String;)V");

	jclass urlClassLoaderCls = findClass(env, "java/net/URLClassLoader", false);
	jmethodID urlClassLoaderInitId = getMethodID(env, urlClassLoaderCls, "<init>",
		"([Ljava/net/URL;)V");
	jmethodID urlClassLoaderLoadClassId = getMethodID(env, urlClassLoaderCls, "loadClass",
		"(Ljava/lang/String;Z)Ljava/lang/Class;");

	vector<string> classPathEntries;

	DIR* dir = opendir(libDir.c_str());

	if (!dir)
		throw runtime_error("Cannot set the classpath. Directory '" + libDir + "' does not exist.");

	dirent* dent;

	while ((dent = readdir(dir)))
	{
		string name(dent->d_name);

#ifdef WIN32
		if (name != "." && name != "..")
#else
		if (dent->d_type == DT_REG || dent->d_type == DT_LNK)
#endif
		{
			if (name.length() > 4 && name.substr(name.length() - 4) == ".jar")
			{
				string protocol("file://");

#ifdef WIN32
				protocol += "/";
#endif

				classPathEntries.push_back(protocol + libDir + DEFAULT_PATH_SEP + dent->d_name);
			}
		}
	}

    closedir(dir);

	jobjectArray urlArray = env->NewObjectArray(classPathEntries.size(), urlCls, NULL);
	if (!urlArray)
		checkJavaException(env);

	for (vector<string>::const_iterator i = classPathEntries.begin(); i != classPathEntries.end(); ++i)
	{
		jstring str = env->NewStringUTF(i->c_str());
		if (!str)
			checkJavaException(env);

		jobject url = env->NewObject(urlCls, urlInitId, str);
		if (!url)
			checkJavaException(env);

		env->SetObjectArrayElement(urlArray, i - classPathEntries.begin(), url);
		checkJavaException(env);
	}

	// Call org.firebirdsql.fbjava.impl.Main.initialize(String)

	jstring mainClassName = env->NewStringUTF("org.firebirdsql.fbjava.impl.Main");
	if (!mainClassName)
		checkJavaException(env);

	jobject classLoader = env->NewObject(urlClassLoaderCls, urlClassLoaderInitId, urlArray);
	if (!classLoader)
		checkJavaException(env);

	jclass mainCls = (jclass) env->CallObjectMethod(classLoader, urlClassLoaderLoadClassId,
		mainClassName, true);
	if (!mainCls)
		checkJavaException(env);

	jmethodID mainInitializeId = getStaticMethodID(env, mainCls, "initialize", "(Ljava/lang/String;)V");
	checkJavaException(env);

	jstring nativeLibrary = env->NewStringUTF(libFile.c_str());
	if (!nativeLibrary)
		checkJavaException(env);

	env->CallStaticVoidMethod(mainCls, mainInitializeId, nativeLibrary);
	checkJavaException(env);
}


extern "C" void /*FB_EXPORTED*/ FB_PLUGIN_ENTRY_POINT(IMaster* master)
{
	::master = master;

	try
	{
		init();
	}
	catch (const exception& e)
	{
		//// FIXME: how to report an error here?
		fprintf(stderr, "%s\n", e.what());
	}
}


#ifdef WIN32
BOOL WINAPI DllMain(HINSTANCE hInstance, DWORD dwReason, void*)
{
	if (dwReason == DLL_PROCESS_ATTACH)
		hDllInstance = hInstance;

	return TRUE;
}
#endif
