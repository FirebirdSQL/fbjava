package org.firebirdsql.fbjava;

import java.sql.SQLException;

import org.firebirdsql.gds.DatabaseParameterBuffer;
import org.firebirdsql.gds.ng.IConnectionProperties;
import org.firebirdsql.gds.ng.jna.FbClientDatabaseFactory;
import org.firebirdsql.gds.ng.jna.JnaDatabase;
import org.firebirdsql.gds.ng.jna.JnaDatabaseConnection;
import org.firebirdsql.jna.fbclient.ISC_STATUS;

import com.sun.jna.ptr.IntByReference;


class InternalDatabaseFactory extends FbClientDatabaseFactory
{
	private static final InternalDatabaseFactory INSTANCE = new InternalDatabaseFactory();

	public static InternalDatabaseFactory getInstance()
	{
		return INSTANCE;
	}

	@Override
	public JnaDatabase connect(IConnectionProperties connectionProperties) throws SQLException
	{
		InternalContext internalContext = InternalContext.get();
		IntByReference attachmentHandle = new IntByReference();
		ISC_STATUS[] statusVector = new ISC_STATUS[20];

		Main.library.fb_get_database_handle(statusVector, attachmentHandle, internalContext.getAttachment());

		JnaDatabaseConnection jnaDatabaseConnection = new JnaDatabaseConnection(
			getClientLibrary(), connectionProperties)
		{
			@Override
			public JnaDatabase identify() throws SQLException
			{
				return new JnaDatabase(this) {
					@Override
					protected void attachOrCreate(DatabaseParameterBuffer dpb, boolean create) throws SQLException
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

		return jnaDatabaseConnection.identify();
	}
}
