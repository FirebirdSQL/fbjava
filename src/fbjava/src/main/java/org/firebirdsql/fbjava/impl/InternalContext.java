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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IAttachment;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;
import org.firebirdsql.fbjava.impl.FbClientLibrary.ITransaction;


final class InternalContext implements AutoCloseable
{
	private static ThreadLocal<InternalContext> tls = new ThreadLocal<InternalContext>() {
		@Override
		protected InternalContext initialValue()
		{
			return new InternalContext();
		}
	};
	private IAttachment attachment;
	private ITransaction transaction;
	private Connection connection;

	public static InternalContext get()
	{
		return tls.get();
	}

	public static InternalContext get(IStatus status, IExternalContext context) throws FbException
	{
		InternalContext internalContext = get();
		internalContext.setup(status, context);
		return internalContext;
	}

	public void setup(IStatus status, IExternalContext context) throws FbException
	{
		attachment = context.getAttachment(status);
		transaction = context.getTransaction(status);
	}

	public IAttachment getAttachment()
	{
		return attachment;
	}

	public ITransaction getTransaction()
	{
		return transaction;
	}

	public Connection getConnection() throws SQLException
	{
		if (connection == null)
			connection = DriverManager.getConnection("jdbc:default:connection");

		return connection;
	}

	@Override
	public void close() throws Exception
	{
		if (connection != null)
			connection.close();
		connection = null;

		transaction.release();
		transaction = null;

		attachment.release();
		attachment = null;
	}
}
