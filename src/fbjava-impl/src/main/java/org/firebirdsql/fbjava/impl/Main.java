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
import com.sun.jna.Pointer;
import com.sun.jna.Structure;


final class Main
{
	public static FbClientLibrary library;
	public static IMaster master;
	public static IUtil util;

	public static void initialize(final String nativeLibrary, final String fbclientLibrary, final Pointer masterPtr) throws ClassNotFoundException
	{
		library = (FbClientLibrary) Native.load(fbclientLibrary, FbClientLibrary.class);

		master = Structure.newInstance(IMaster.class, masterPtr);
		master.read();
		util = master.getUtilInterface();

		// We assume the plugin is used with a UTF-8 connection charset, so
		// set the Jaybird 3 default charset and disable logging.
		System.setProperty("org.firebirdsql.jdbc.disableLogging", "true");
		System.setProperty("org.firebirdsql.jdbc.defaultConnectionEncoding", "utf8");

		// Load Jaybird and register jdbc:default:connection url.
		Class.forName("org.firebirdsql.jdbc.FBDriver");

		master.getPluginManager().registerPluginFactory(IPluginManager.TYPE_EXTERNAL_ENGINE, "JAVA",
			PluginFactory.create());
	}
}
