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
package org.firebirdsql.fbjava;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.SQLException;


/**
 * This interface represents a Firebird External Context.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public interface Context
{
	/**
	 * Gets the Context instance associated with the current call.
	 */
	public static Context get()
	{
		// For security purposes, InternalContextImpl is not public, so get it by reflection.

		return AccessController.doPrivileged(new PrivilegedAction<Context>() {
			@Override
			public Context run()
			{
				try
				{
					Class<?> clazz = Class.forName("org.firebirdsql.fbjava.impl.InternalContext");
					Method method = clazz.getMethod("getContextImpl");
					method.setAccessible(true);
					return (Context) method.invoke(null);
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		});
	}

	/**
	 * Gets the Connection object.
	 * It's also possible to get a Connection object with
	 * DriverManager.getConnection("jdbc:default:connection")
	 */
	public Connection getConnection() throws SQLException;

	/**
	 * Gets the metadata object name that called the external routine.
	 */
	public String getObjectName();

	/**
	 * Gets info stored at entry point metadata.
	 */
	public String getNameInfo();

	/**
	 * Gets metadata body.
	 */
	public String getBody();
}
