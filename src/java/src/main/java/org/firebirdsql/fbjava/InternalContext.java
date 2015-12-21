package org.firebirdsql.fbjava;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.firebirdsql.fbjava.FbClientLibrary.IAttachment;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;
import org.firebirdsql.fbjava.FbClientLibrary.ITransaction;


class InternalContext implements AutoCloseable
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
