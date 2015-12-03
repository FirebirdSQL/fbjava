package org.firebirdsql.fbjava;

import org.firebirdsql.fbjava.FbClientLibrary.IMaster;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginFactory;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginManager;

import com.sun.jna.Native;


class Main
{
	public static FbClientLibrary library;
	public static IMaster master;

	public static void initialize(String nativeLibrary)
	{
		//// FIXME: Receive the client library name from the plugin.
		library = (FbClientLibrary) Native.loadLibrary("fbclient", FbClientLibrary.class);

		//// FIXME: Receive the master interface from the plugin.
		master = library.fb_get_master_interface();

		IPluginFactory pluginFactory = new IPluginFactory(new PluginFactory());
		master.getPluginManager().registerPluginFactory(IPluginManager.TYPE_EXTERNAL_ENGINE, "JAVA", pluginFactory);
	}
}
