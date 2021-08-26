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

import java.lang.reflect.Array;

import org.firebirdsql.fbjava.ExternalResultSet;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalResultSet;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalResultSetIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


final class ExternalResultSetWrapper implements IExternalResultSetIntf
{
	private IExternalResultSet wrapper;
	private final Routine routine;
	private final IExternalContext context;
	private final InternalContext internalContext;
	private final Pointer outMsg;
	private final ExternalResultSet extRs;
	private final int inCount;
	private final Object[] inOut;
	private final Object[] inOut2;

	private ExternalResultSetWrapper(Routine routine, IExternalContext context, InternalContext internalContext,
		Pointer outMsg, ExternalResultSet extRs, int inCount, Object[] inOut, Object[] inOut2)
	{
		this.routine = routine;
		this.context = context;
		this.internalContext = internalContext;
		this.outMsg = outMsg;
		this.extRs = extRs;
		this.inCount = inCount;
		this.inOut = inOut;
		this.inOut2 = inOut2;
	}

	public static IExternalResultSet create(Routine routine, IExternalContext context, InternalContext internalContext, Pointer outMsg,
		ExternalResultSet extRs, int inCount, Object[] inOut, Object[] inOut2)
	{
		final ExternalResultSetWrapper wrapped = new ExternalResultSetWrapper(routine, context, internalContext,
			outMsg, extRs, inCount, inOut, inOut2);
		wrapped.wrapper = JnaUtil.pin(new IExternalResultSet(wrapped));
		return wrapped.wrapper;
	}

	@Override
	public void dispose()
	{
		try
		{
			final IStatus status = Main.master.getStatus();
			try
			{
				final InternalContext oldContext = InternalContext.set(internalContext);
				try
				{
					try
					{
						routine.engine.runInClassLoader(status, context,
							extRs.getClass().getName(), "close",
							() -> {
								extRs.close();
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
		catch (final Throwable t)
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
			final InternalContext oldContext = InternalContext.set(internalContext);
			try
			{
				return routine.engine.runInClassLoader(status, context,
					extRs.getClass().getName(), "fetch",
					() -> {
						if (extRs.fetch())
						{
							for (int i = inCount; i < inOut.length; ++i)
								inOut2[i] = Array.get(inOut[i], 0);

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
		catch (final Throwable t)
		{
			FbException.rethrow(t);
			return false;
		}
	}
}
