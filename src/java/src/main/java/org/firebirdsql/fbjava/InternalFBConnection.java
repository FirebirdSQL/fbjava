package org.firebirdsql.fbjava;

import java.sql.SQLException;

import org.firebirdsql.gds.ng.TransactionState;
import org.firebirdsql.gds.ng.jna.JnaDatabase;
import org.firebirdsql.gds.ng.jna.JnaTransaction;
import org.firebirdsql.jca.FBManagedConnection;
import org.firebirdsql.jdbc.FBConnection;
import org.firebirdsql.jdbc.InternalTransactionCoordinator;
import org.firebirdsql.jna.fbclient.ISC_STATUS;

import com.sun.jna.ptr.IntByReference;


public final class InternalFBConnection extends FBConnection
{
	public InternalFBConnection(FBManagedConnection mc) throws SQLException
	{
		super(mc);

		// for autocommit off
		txCoordinator.setCoordinator(new InternalTransactionCoordinator.LocalTransactionCoordinator(
			this, getLocalTransaction()));

		InternalContext internalContext = InternalContext.get();
		IntByReference transactionHandle = new IntByReference();
		ISC_STATUS[] statusVector = new ISC_STATUS[20];

		Main.library.fb_get_transaction_handle(statusVector, transactionHandle, internalContext.getTransaction());

		JnaTransaction jnaTransaction = new JnaTransaction((JnaDatabase) getFbDatabase(), transactionHandle,
			TransactionState.ACTIVE)
		{
			@Override
			protected void finalize()
			{
			}
		};

		getGDSHelper().setCurrentTransaction(jnaTransaction);
	}

	@Override
	public void setManagedEnvironment(boolean managedConnection)
	{
		// for autocommit off
	}

	@Override
	public synchronized void close() throws SQLException
	{
		freeStatements();
	}
}
