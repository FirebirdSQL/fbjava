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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IAttachment;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;
import org.firebirdsql.fbjava.impl.FbClientLibrary.ITransaction;


// This (non-public) class is accessed by org.firebirdsql.fbjava.Context by reflection.
final class InternalContext implements AutoCloseable
{
	private static ThreadLocal<InternalContext> tls = new ThreadLocal<InternalContext>() {
		@Override
		protected InternalContext initialValue()
		{
			return new InternalContext();
		}
	};

	class ContextData implements AutoCloseable
	{
		private IAttachment attachment;
		private ITransaction transaction;
		private Routine routine;
		private ValuesImpl inValues;
		private ValuesImpl outValues;
		private ContextImpl contextImpl;

		ContextData(IAttachment attachment, ITransaction transaction, Routine routine, ValuesImpl inValues, ValuesImpl outValues, ContextImpl contextImpl) {
			this.attachment = attachment;
			this.transaction = transaction;
			this.routine = routine;
			this.inValues = inValues;
			this.outValues = outValues;
			this.contextImpl = contextImpl;
		}

		@Override
		public void close() throws Exception {
			this.transaction.release();
			this.transaction = null;
			this.attachment.release();
			this.attachment = null;
			this.routine = null;
			this.contextImpl = null;
		}

		IAttachment getAttachment() {
			return attachment;
		}

		ITransaction getTransaction() {
			return transaction;
		}

		Routine getRoutine() {
			return routine;
		}

		ValuesImpl getInValues() {
			return inValues;
		}

		ValuesImpl getOutValues() {
			return outValues;
		}

		ContextImpl getContextImpl() {
			return contextImpl;
		}
	}

	private Connection connection;
	private Deque<ContextData> contextDataStack = new ArrayDeque<ContextData>();

	public static InternalContext get()
	{
		return tls.get();
	}

	public static ContextImpl getContextImpl()
	{
		return get().contextDataStack.size() > 0 ? get().contextDataStack.getFirst().getContextImpl() : null;
	}

	public static InternalContext create(IStatus status, IExternalContext context, Routine routine,
		ValuesImpl inValues, ValuesImpl outValues) throws FbException
	{
		InternalContext internalContext = get();
		internalContext.setup(status, context, routine, 0, inValues, outValues);
		return internalContext;
	}

	public static InternalContext createTrigger(IStatus status, IExternalContext context, Routine routine,
		int action, ValuesImpl oldValues, ValuesImpl newValues) throws FbException
	{
		InternalContext internalContext = get();
		internalContext.setup(status, context, routine, action, oldValues, newValues);
		return internalContext;
	}

	private void setup(IStatus status, IExternalContext context, Routine routine, int triggerAction,
		ValuesImpl inValues, ValuesImpl outValues) throws FbException
	{
		ContextImpl contextImpl = null;
		if (routine == null)
			contextImpl = null;
		else
		{
			switch (routine.type)
			{
				case FUNCTION:
					contextImpl = new FunctionContextImpl(this);
					break;

				case PROCEDURE:
					contextImpl = new ProcedureContextImpl(this);
					break;

				case TRIGGER:
					contextImpl = new TriggerContextImpl(this, triggerAction);
					break;
			}
		}
		this.contextDataStack.push(new ContextData(context.getAttachment(status), context.getTransaction(status), routine, inValues, outValues, contextImpl));
	}

	public IAttachment getAttachment()
	{
		return get().contextDataStack.size() > 0 ? contextDataStack.getFirst().getAttachment() : null;
	}

	public ITransaction getTransaction()
	{
		return get().contextDataStack.size() > 0 ? contextDataStack.getFirst().getTransaction() : null;
	}

	public Routine getRoutine()
	{
		return get().contextDataStack.size() > 0 ? contextDataStack.getFirst().getRoutine() : null;
	}

	public ValuesImpl getInValues()
	{
		return get().contextDataStack.size() > 0 ? contextDataStack.getFirst().getInValues() : null;
	}

	public ValuesImpl getOutValues()
	{
		return get().contextDataStack.size() > 0 ? contextDataStack.getFirst().getOutValues() : null;
	}

	public Connection getConnection() throws SQLException
	{
		if (connection == null)
		{
			Properties properties = new Properties();
			properties.setProperty("encoding", "utf8");

			connection = DriverManager.getConnection("jdbc:default:connection", properties);
		}

		return connection;
	}

	@Override
	public void close() throws Exception
	{
		contextDataStack.pop();
		if (contextDataStack.isEmpty()) {
			if (connection != null)
				connection.close();
			connection = null;
		}
	}
}
