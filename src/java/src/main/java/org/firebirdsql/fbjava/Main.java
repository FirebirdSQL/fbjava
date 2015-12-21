package org.firebirdsql.fbjava;

import org.firebirdsql.fbjava.FbClientLibrary.IMaster;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginManager;
import org.firebirdsql.fbjava.FbClientLibrary.IUtil;

import com.sun.jna.Native;


class Main
{
	public static FbClientLibrary library;
	public static IMaster master;
	public static IUtil util;

	public static void initialize(String nativeLibrary) throws ClassNotFoundException
	{
		//// FIXME: Receive the client library name from the plugin.
		library = (FbClientLibrary) Native.loadLibrary("fbclient", FbClientLibrary.class);

		//// FIXME: Receive the master interface from the plugin.
		master = library.fb_get_master_interface();
		util = master.getUtilInterface();

		// Load Jaybird and register jdbc:default:connection url.
		Class.forName("org.firebirdsql.jdbc.FBDriver");

		master.getPluginManager().registerPluginFactory(IPluginManager.TYPE_EXTERNAL_ENGINE, "JAVA",
			PluginFactory.create());
	}
}
