package org.firebirdsql.fbjava;

import org.firebirdsql.gds.GDSException;
import org.firebirdsql.gds.impl.BaseGDSFactoryPlugin;
import org.firebirdsql.gds.ng.FbDatabaseFactory;


public final class InternalGDSFactoryPlugin extends BaseGDSFactoryPlugin
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
