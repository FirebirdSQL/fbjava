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
#include <algorithm>
#include <array>
#include <filesystem>
#include <fstream>
#include <functional>
#include <iostream>
#include <iterator>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstring>
#include <stdlib.h>
#include <sys/stat.h>

#ifdef WIN32
#include <windows.h>
#define FB_EXPORTED __declspec(dllexport)
#else
#include <dlfcn.h>
#define FB_EXPORTED __attribute__((visibility("default")))
#endif

#include "jni.h"

using namespace Firebird;
namespace fs = std::filesystem;
using std::array;
using std::back_inserter;
using std::cerr;
using std::endl;
using std::exception;
using std::getline;
using std::ifstream;
using std::runtime_error;
using std::string;
using std::transform;
using std::unique_ptr;
using std::vector;


//------------------------------------------------------------------------------


#ifdef WIN32
static const char DEFAULT_PATH_SEPARATOR = ';';
static HMODULE hDllInstance = nullptr;
#else
static const char DEFAULT_PATH_SEPARATOR = ':';
#endif


//------------------------------------------------------------------------------


struct FbDeleter
{
	void operator()(IDisposable* obj)
	{
		obj->dispose();
	}

	void operator()(IReferenceCounted* obj)
	{
		obj->release();
	}
};

template <typename T> using FbUniquePtr = unique_ptr<T, FbDeleter>;

template <typename T>
FbUniquePtr<T> fbUnique(T* obj)
{
	return FbUniquePtr<T>(obj);
}


template <typename T>
class JniRef
{
public:
	JniRef()
		: env(nullptr),
		  obj(nullptr),
		  global(false)
	{
	}

	JniRef(JNIEnv* env, T obj, bool global)
		: env(env),
		  obj(obj),
		  global(global)
	{
	}

	JniRef(JniRef&& o) noexcept
		: env(o.env),
		  obj(o.obj),
		  global(o.global)
	{
		o.env = nullptr;
		o.obj = nullptr;
	}

	~JniRef()
	{
		if (obj)
		{
			if (global)
				env->DeleteGlobalRef(obj);
			else
				env->DeleteLocalRef(obj);
		}
	}

	JniRef(const JniRef&) = delete;
	JniRef& operator=(const JniRef&) = delete;

public:
	bool operator!() const
	{
		return !obj;
	}

	T get() const
	{
		return obj;
	}

private:
	JNIEnv* env;
	T obj;
	bool global;
};

template <typename T>
JniRef<T> jniLocalRef(JNIEnv* env, T ref)
{
	return JniRef<T>(env, ref, false);
}


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


static string findJvmLibrary(const string& javaHome);
static void checkJavaException(JNIEnv* env);
static JniRef<jclass> findClass(JNIEnv* env, const char* name, bool createGlobalRef);
static jmethodID getStaticMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature);
static jmethodID getMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature);
static void init();
string trim(const string& s);


//--------------------------------------


static unique_ptr<DynLibrary> library;
static IMaster* master = nullptr;


//--------------------------------------


static string findJvmLibrary(const string& javaHome)
{
#ifdef WIN32
	static const array<const char*, 2> DEFAULT_PLACES = {
		"\\jre\\bin\\server\\jvm.dll",
		"\\jre\\lib\\client\\jvm.dll"
		//// FIXME: Java 11
	};
#else
#ifdef __amd64
#define ARCH_STR "amd64"
#else
#define ARCH_STR "i386"
#endif
	static const array<const char*, 4> DEFAULT_PLACES = {
		"/jre/lib/" ARCH_STR "/server/libjvm.so",
		"/lib/server/libjvm.so",
		"/jre/lib/" ARCH_STR "/client/libjvm.so",
		"/lib/client/libjvm.so"
	};
#endif

	if (!javaHome.empty())
	{
		for (const auto defaultPlace : DEFAULT_PLACES)
		{
			string path(javaHome + defaultPlace);

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
		const auto ex = env->ExceptionOccurred();

		if (!initException)
		{
			throwableCls = env->FindClass("java/lang/Throwable");
			throwableToStringId = env->GetMethodID(throwableCls, "toString",
				"()Ljava/lang/String;");

			initException = true;
		}

		env->ExceptionClear();

		string msg;
		const auto jMsg = (jstring) env->CallObjectMethod(ex, throwableToStringId);

		if (jMsg)
		{
			const auto* msgPtr = env->GetStringUTFChars(jMsg, nullptr);
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
static JniRef<jclass> findClass(JNIEnv* env, const char* name, bool createGlobalRef)
{
	const auto localCls = env->FindClass(name);
	jclass cls = nullptr;

	if (localCls)
	{
		if (createGlobalRef)
		{
			cls = (jclass) env->NewGlobalRef(localCls);
			env->DeleteLocalRef(localCls);
		}
		else
			cls = localCls;
	}

	if (!cls)
		checkJavaException(env);

	return JniRef<jclass>(env, cls, createGlobalRef);
}


// Gets the ID of a static method. Throw exception in case of error.
static jmethodID getStaticMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature)
{
	const auto mid = env->GetStaticMethodID(cls, name, signature);
	if (!mid)
		checkJavaException(env);

	return mid;
}


// Gets the ID of a method. Throw exception in case of error.
static jmethodID getMethodID(JNIEnv* env, jclass cls, const char* name, const char* signature)
{
	const auto mid = env->GetMethodID(cls, name, signature);
	if (!mid)
		checkJavaException(env);

	return mid;
}


static void init()
{
	string javaHome;
	vector<string> jvmArgs;
	const string jnaLibPathOpt("-Djna.library.path=");

	try
	{
		const auto status = fbUnique(master->getStatus());
		ThrowStatusWrapper st(status.get());

		if (const auto configManager = master->getConfigManager())
		{
			if (const auto config = fbUnique(configManager->getPluginConfig("JAVA")))
			{
				if (const auto entry = fbUnique(config->find(&st, "JavaHome")))
					javaHome = entry->getValue();

				if (const auto entry = fbUnique(config->find(&st, "JvmArgsFile")))
				{
					ifstream input(entry->getValue());
					string line;

					while (getline(input, line))
					{
						line = trim(line);

						if (!line.empty() && line[0] != '#')
							jvmArgs.push_back(line);
					}
				}
			}
		}
	}
	catch (const FbException&)
	{
		throw runtime_error("Error looking for JavaHome in the config file.");
	}

	string jnaLibPath;
#ifdef WIN32
	jnaLibPath = master->getConfigManager()->getRootDirectory();
#else
	jnaLibPath = master->getConfigManager()->getDirectory(IConfigManager::DIR_LIB);
#endif

	for (auto& arg : jvmArgs)
	{
		if (arg.length() >= jnaLibPathOpt.length() && arg.compare(0, jnaLibPathOpt.length(), jnaLibPathOpt) == 0)
		{
			arg.append(1, DEFAULT_PATH_SEPARATOR);
			arg.append(jnaLibPath);
			jnaLibPath.clear();
		}
	}

	if (!jnaLibPath.empty())
		jvmArgs.push_back(jnaLibPathOpt + jnaLibPath);

	fs::path libFile;
	string fbclientName;

#ifdef WIN32
	{	// scope
		char buffer[MAX_PATH];
		GetModuleFileName(hDllInstance, buffer, sizeof(buffer));
		libFile = buffer;

		HMODULE hMasterModule = nullptr;
		GetModuleHandleEx(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, (LPCTSTR) master, &hMasterModule);
		GetModuleFileName(hMasterModule, buffer, sizeof(buffer));
		fbclientName = buffer;
	}
#else
	{	// scope
		Dl_info dlInfo;
		if (dladdr((void*) init, &dlInfo) == 0)
			throw runtime_error("Cannot get the plugin library path.");

		libFile = dlInfo.dli_fname;

		if (dladdr((void*) master, &dlInfo) == 0)
			throw runtime_error("Cannot get the fbclient library path.");

		fbclientName = dlInfo.dli_fname;
	}
#endif

	fs::path libDir(libFile.parent_path().parent_path() / "jar");

	if (javaHome.empty())
	{
		auto javaHomeEnv = getenv("JAVA_HOME");
		if (javaHomeEnv)
			javaHome = javaHomeEnv;
	}

	if (javaHome.empty())
		throw runtime_error("JAVA_HOME environment variable is not defined.");

	const auto jvmLibrary = findJvmLibrary(javaHome);

	if (jvmLibrary.empty())
		throw runtime_error("No JVM library found in '" + javaHome + "'.");

	library.reset(new DynLibrary(jvmLibrary));

	// JNI entrypoints
	jint (JNICALL *getCreatedJavaVMs)(JavaVM**, jsize, jsize*);
	library->findSymbol("JNI_GetCreatedJavaVMs", getCreatedJavaVMs);

	jint (JNICALL *createJavaVM)(JavaVM**, void**, void*);
	library->findSymbol("JNI_CreateJavaVM", createJavaVM);

	vector<JavaVMOption> vmOptions(jvmArgs.size());
	transform(jvmArgs.begin(), jvmArgs.end(), vmOptions.begin(),
		[](auto& arg) { return JavaVMOption{&arg[0], nullptr}; }
	);

	const auto jvmVersion = JNI_VERSION_1_4;

	JavaVMInitArgs vmArgs;
	memset(&vmArgs, 0, sizeof(vmArgs));
	vmArgs.version = jvmVersion;
	vmArgs.nOptions = vmOptions.size();
	vmArgs.options = vmOptions.data();
	vmArgs.ignoreUnrecognized = JNI_FALSE;

	JavaVM* jvm = nullptr;

	// Verify if there is already a JVM created. It happens, for example, using the embedded
	// engine in Java or if any other plugin has loaded it. If it was already loaded, we
	// don't do anything with the configured options.
	jsize jvmCount = 0;
	getCreatedJavaVMs(&jvm, 1, &jvmCount);

	if (jvmCount == 0)
	{
		// There is no JVM. Let's create one.
		JNIEnv* dummyEnv;
		auto jvmStatus = createJavaVM(&jvm, (void**) &dummyEnv, &vmArgs);

		if (jvmStatus != 0)
			throw runtime_error("Error creating JVM.");
	}

	JNIEnv* env;

	if (jvm->GetEnv((void**) &env, jvmVersion) == JNI_EDETACHED)
	{
		if (jvm->AttachCurrentThread((void**) &env, nullptr) != JNI_OK)
			throw runtime_error("Cannot attach the current thread to the JVM.");
	}

	// Lets create an URL classloader for the plugin.

	const auto urlCls = findClass(env, "java/net/URL", false);
	const auto urlInitId = getMethodID(env, urlCls.get(), "<init>", "(Ljava/lang/String;)V");

	const auto urlClassLoaderCls = findClass(env, "java/net/URLClassLoader", false);
	const auto urlClassLoaderInitId = getMethodID(env, urlClassLoaderCls.get(), "<init>",
		"([Ljava/net/URL;)V");
	const auto urlClassLoaderLoadClassId = getMethodID(env, urlClassLoaderCls.get(), "loadClass",
		"(Ljava/lang/String;Z)Ljava/lang/Class;");

	vector<string> classPathEntries;

	for (const auto& dirEntry : fs::directory_iterator(libDir))
	{
		if (dirEntry.is_regular_file())
		{
			const string name = dirEntry.path().filename().string();

			if (name.length() > 4 && name.substr(name.length() - 4) == ".jar")
			{
				string protocol("file://");

#ifdef WIN32
				protocol += "/";
#endif

				classPathEntries.push_back(protocol + dirEntry.path().string());
			}
		}
	}

	if (classPathEntries.empty())
		throw runtime_error("Cannot set the classpath. Directory '" + libDir.string() + "' does not exist.");

	const auto urlArray = jniLocalRef(env, env->NewObjectArray(classPathEntries.size(), urlCls.get(), nullptr));
	if (!urlArray)
		checkJavaException(env);

	{	// scope
		unsigned i = 0;

		for (const auto& classPathEntry : classPathEntries)
		{
			const auto str = jniLocalRef(env, env->NewStringUTF(classPathEntry.c_str()));
			if (!str)
				checkJavaException(env);

			const auto url = jniLocalRef(env, env->NewObject(urlCls.get(), urlInitId, str.get()));
			if (!url)
				checkJavaException(env);

			env->SetObjectArrayElement(urlArray.get(), i++, url.get());
			checkJavaException(env);
		}
	}

	// Call org.firebirdsql.fbjava.impl.Main.initialize(String, String, Pointer)

	const auto mainClassName = jniLocalRef(env, env->NewStringUTF("org.firebirdsql.fbjava.impl.Main"));
	if (!mainClassName)
		checkJavaException(env);

	const auto classLoader = jniLocalRef(env,
		env->NewObject(urlClassLoaderCls.get(), urlClassLoaderInitId, urlArray.get()));
	if (!classLoader)
		checkJavaException(env);

	const auto mainCls = jniLocalRef(env, (jclass) env->CallObjectMethod(classLoader.get(), urlClassLoaderLoadClassId,
		mainClassName.get(), true));
	if (!mainCls)
		checkJavaException(env);

	const auto mainInitializeId = getStaticMethodID(env, mainCls.get(), "initialize", "(Ljava/lang/String;Ljava/lang/String;Lcom/sun/jna/Pointer;)V");
	checkJavaException(env);

	const auto nativeLibrary = jniLocalRef(env, env->NewStringUTF(libFile.string().c_str()));
	if (!nativeLibrary)
		checkJavaException(env);

	const auto fbclientLibrary = jniLocalRef(env, env->NewStringUTF(fbclientName.c_str()));
	if (!fbclientLibrary)
		checkJavaException(env);

	// create a pointer to the IMaster interface
	const auto jmaster = (jlong)(master);

	const auto jnaPointerName = jniLocalRef(env, env->NewStringUTF("com.sun.jna.Pointer"));
	if (!jnaPointerName)
		checkJavaException(env);
	const auto jnaPointerCls = jniLocalRef(env, (jclass) env->CallObjectMethod(classLoader.get(), urlClassLoaderLoadClassId,
		jnaPointerName.get(), true));
	if (!jnaPointerCls)
		checkJavaException(env);

	const auto jnaPointerInitId = getMethodID(env, jnaPointerCls.get(), "<init>", "(J)V");
	const auto masterPointer =  jniLocalRef(env, env->NewObject(jnaPointerCls.get(), jnaPointerInitId, jmaster));
	if (!masterPointer)
		checkJavaException(env);

	env->CallStaticVoidMethod(mainCls.get(), mainInitializeId, nativeLibrary.get(), fbclientLibrary.get(), masterPointer.get());
	checkJavaException(env);
}


string trim(const string& s)
{
	auto it = s.cbegin();
	while (it != s.end() && isspace(*it))
		++it;

	auto rit = s.crbegin();
	while (rit.base() != it && isspace(*rit))
		++rit;

	return string(it, rit.base());
}


extern "C" void FB_EXPORTED FB_PLUGIN_ENTRY_POINT(IMaster* master)
{
	::master = master;

	try
	{
		init();
	}
	catch (const exception& e)
	{
		//// FIXME: how to report an error here?
		cerr << e.what() << endl;
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
