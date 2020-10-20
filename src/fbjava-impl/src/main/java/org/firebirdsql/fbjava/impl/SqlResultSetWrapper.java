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

import com.sun.jna.Pointer;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalResultSet;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalResultSetIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import java.sql.ResultSet;
import java.sql.Types;


final class SqlResultSetWrapper implements IExternalResultSetIntf
{
	private IExternalResultSet wrapper;
	private Routine routine;
	private IExternalContext context;
	private InternalContext internalContext;
	private Pointer outMsg;
	private ResultSet rs;
	private int inCount;
	private Object[] inOut;
	private Object[] inOut2;

	private SqlResultSetWrapper(Routine routine, IExternalContext context, InternalContext internalContext, Pointer outMsg,
								ResultSet rs, int inCount, Object[] inOut, Object[] inOut2)
	{
		this.routine = routine;
		this.context = context;
		this.internalContext = internalContext;
		this.outMsg = outMsg;
		this.rs = rs;
		this.inCount = inCount;
		this.inOut = inOut;
		this.inOut2 = inOut2;
	}

	public static IExternalResultSet create(Routine routine, IExternalContext context, InternalContext internalContext, Pointer outMsg,
		ResultSet rs, int inCount, Object[] inOut, Object[] inOut2)
	{
		SqlResultSetWrapper wrapped = new SqlResultSetWrapper(routine, context, internalContext, outMsg, rs,
			inCount, inOut, inOut2);
		wrapped.wrapper = JnaUtil.pin(new IExternalResultSet(wrapped));
		return wrapped.wrapper;
	}

	@Override
	public void dispose()
	{
		try
		{
			IStatus status = Main.master.getStatus();
			try
			{
				InternalContext oldContext = InternalContext.set(internalContext);
				try
				{
					try
					{
						routine.engine.runInClassLoader(status, context,
							rs.getClass().getName(), "close",
							() -> {
								rs.close();
								return null;
							});
					}
					finally
					{
						internalContext.close();
					}
				}
				finally
				{
					InternalContext.set(oldContext);
				}
			}
			finally
			{
				status.dispose();
			}
		}
		catch (Throwable t)
		{
			//// TODO: ???
		}

		JnaUtil.unpin(wrapper);
	}

	@Override
	public boolean fetch(IStatus status) throws FbException
	{
		//// TODO: batch

		try
		{
			InternalContext oldContext = InternalContext.set(internalContext);
			try
			{
				return routine.engine.runInClassLoader(status, context,
					rs.getClass().getName(), "next",
					() -> {
						if (rs.next())
						{
							for (int i = inCount, j = 1; i < inOut.length; ++i, ++j) {
								switch (routine.outputParameters.get(j - 1).type.getSecond())
								{
									case Types.BLOB:
										inOut2[i] = rs.getBlob(j);
										break;
									default:
										inOut2[i] = rs.getObject(j);
								}
							}

							routine.putInMessage(status, context, routine.outputParameters,
								inOut2, inCount, outMsg);

							return true;
						}
						else
							return false;
					});
			}
			finally
			{
				InternalContext.set(oldContext);
			}
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
			return false;
		}
	}
}
