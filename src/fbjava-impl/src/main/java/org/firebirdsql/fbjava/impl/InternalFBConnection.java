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

import java.sql.SQLException;

import org.firebirdsql.gds.ng.TransactionState;
import org.firebirdsql.gds.ng.jna.JnaDatabase;
import org.firebirdsql.gds.ng.jna.JnaTransaction;
import org.firebirdsql.jca.FBManagedConnection;
import org.firebirdsql.jdbc.FBConnection;
import org.firebirdsql.jna.fbclient.ISC_STATUS;

import com.sun.jna.ptr.IntByReference;


public final class InternalFBConnection extends FBConnection	// must be public
{
	public InternalFBConnection(final FBManagedConnection mc) throws SQLException
	{
		super(mc);

		final InternalContext internalContext = InternalContext.get();
		final IntByReference transactionHandle = new IntByReference();
		final ISC_STATUS[] statusVector = new ISC_STATUS[20];

		Main.library.fb_get_transaction_handle(statusVector, transactionHandle, internalContext.getTransaction());

		final JnaTransaction jnaTransaction = new JnaTransaction((JnaDatabase) getFbDatabase(), transactionHandle,
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
	public void setManagedEnvironment(final boolean managedConnection) throws SQLException
	{
		// for autocommit off
		checkValidity();
		setTransactionCoordinator(managedConnection, false);
	}

	@Override
	public synchronized void close() throws SQLException
	{
		freeStatements();
	}
}
