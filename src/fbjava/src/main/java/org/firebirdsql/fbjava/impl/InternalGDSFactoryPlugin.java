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

import org.firebirdsql.gds.GDSException;
import org.firebirdsql.gds.impl.BaseGDSFactoryPlugin;
import org.firebirdsql.gds.ng.FbDatabaseFactory;


public final class InternalGDSFactoryPlugin extends BaseGDSFactoryPlugin	// must be public
{
	public static final String INTERNAL_TYPE_NAME = "INTERNAL";
	private static final String[] TYPE_ALIASES = {};
	private static final String[] JDBC_PROTOCOLS = {"jdbc:default:connection"};

	@Override
	public String getPluginName()
	{
		return "GDS implementation for default connection.";
	}

	@Override
	public String getTypeName()
	{
		return INTERNAL_TYPE_NAME;
	}

	@Override
	public String[] getTypeAliases()
	{
		return TYPE_ALIASES;
	}

	@Override
	public Class<?> getConnectionClass()
	{
		return InternalFBConnection.class;
	}

	@Override
	public String[] getSupportedProtocols()
	{
		return JDBC_PROTOCOLS;
	}

	@Override
	public String getDatabasePath(String server, Integer port, String path) throws GDSException
	{
		return path;
	}

	@Override
	public String getDatabasePath(String jdbcUrl) throws GDSException
	{
		return "default";	//// FIXME: ???
	}

	@Override
	public FbDatabaseFactory getDatabaseFactory()
	{
		return InternalDatabaseFactory.getInstance();
	}
}
