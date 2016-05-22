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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.cert.Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.firebirdsql.gds.ISCConstants;
import org.firebirdsql.gds.TransactionParameterBuffer;
import org.firebirdsql.jdbc.FBConnection;


/**
 * Per database ClassLoader.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
final class DbClassLoader extends URLClassLoader
{
	String databaseName;
	private FBConnection connection;
	CodeSource codeSource;
	PermissionCollection codeSourcePermission = new Permissions();

	DbClassLoader(String databaseName, URL[] urls, ClassLoader parent)
		throws SQLException
	{
		super(urls, parent);

		this.databaseName = databaseName;

		codeSource = new CodeSource(urls[0], (Certificate[]) null);

		Properties properties = new Properties();
		properties.setProperty("isc_dpb_no_db_triggers", "1");

		connection = (FBConnection) DriverManager.getConnection(
			"jdbc:firebirdsql:embedded:" + databaseName + "?charSet=UTF-8", properties);
		connection.setAutoCommit(false);
		connection.setReadOnly(true);

		TransactionParameterBuffer tpb = connection.createTransactionParameterBuffer();
		tpb.addArgument(ISCConstants.isc_tpb_read_committed);
		tpb.addArgument(ISCConstants.isc_tpb_rec_version);
		tpb.addArgument(ISCConstants.isc_tpb_read);
		connection.setTransactionParameters(Connection.TRANSACTION_READ_COMMITTED, tpb);

		DbPolicy.databaseOpened();
	}

	@Override
	public void close() throws IOException
	{
		try
		{
			DbPolicy.databaseClosed();
			connection.close();
		}
		catch (SQLException e)
		{
			throw new IOException(e);
		}
	}

	@Override
	protected PermissionCollection getPermissions(CodeSource codesource)
	{
		return codeSourcePermission;
	}

	public Connection getConnection()
	{
		return connection;
	}
}
