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

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.SQLException;

import org.firebirdsql.gds.DatabaseParameterBuffer;
import org.firebirdsql.gds.ng.IConnectionProperties;
import org.firebirdsql.gds.ng.jna.FbEmbeddedDatabaseFactory;
import org.firebirdsql.gds.ng.jna.JnaDatabase;
import org.firebirdsql.gds.ng.jna.JnaDatabaseConnection;
import org.firebirdsql.jna.fbclient.ISC_STATUS;

import com.sun.jna.ptr.IntByReference;


final class InternalDatabaseFactory extends FbEmbeddedDatabaseFactory
{
	private static final InternalDatabaseFactory INSTANCE = new InternalDatabaseFactory();

	public static InternalDatabaseFactory getInstance()
	{
		return INSTANCE;
	}

	@Override
	public JnaDatabase connect(IConnectionProperties connectionProperties) throws SQLException
	{
		final InternalContext internalContext = InternalContext.get();
		final IntByReference attachmentHandle = new IntByReference();
		final ISC_STATUS[] statusVector = new ISC_STATUS[20];

		Main.library.fb_get_database_handle(statusVector, attachmentHandle, internalContext.getAttachment());

		try
		{
			final JnaDatabaseConnection jnaDatabaseConnection =
				AccessController.doPrivileged(new PrivilegedExceptionAction<JnaDatabaseConnection>()
			{
				@Override
				public JnaDatabaseConnection run() throws Exception
				{
					return new JnaDatabaseConnection(getClientLibrary(), connectionProperties) {
						@Override
						public JnaDatabase identify() throws SQLException
						{
							return new JnaDatabase(this) {
								@Override
								protected void attachOrCreate(DatabaseParameterBuffer dpb, boolean create)
									throws SQLException
								{
									handle.setValue(attachmentHandle.getValue());
									setAttached();
									afterAttachActions();
								}

								@Override
								protected void internalDetach()
								{
								}
							};
						}
					};
				}
			});

			return jnaDatabaseConnection.identify();
		}
		catch (final PrivilegedActionException e)
		{
			throw new SQLException(e.getCause());
		}
	}
}
