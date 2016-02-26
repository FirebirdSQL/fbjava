/*
 * FB/Java plugin
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the Firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a git log command.
 *
 * All rights reserved.
 */
package org.firebirdsql.fbjava.impl;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IMaster;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IPluginManager;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IUtil;

import com.sun.jna.Native;


final class Main
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
