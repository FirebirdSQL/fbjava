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
import java.lang.reflect.InvocationTargetException;

import org.firebirdsql.fbjava.ExternalResultSet;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalProcedure;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalProcedureIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalResultSet;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalResultSetIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


final class ExternalProcedure implements IExternalProcedureIntf
{
	private IExternalProcedure wrapper;
	private Routine routine;

	private ExternalProcedure(Routine routine)
	{
		this.routine = routine;
	}

	public static IExternalProcedure create(Routine routine)
	{
		ExternalProcedure wrapped = new ExternalProcedure(routine);
		wrapped.wrapper = JnaUtil.pin(new IExternalProcedure(wrapped));
		return wrapped.wrapper;
	}

	@Override
	public void dispose()
	{
		JnaUtil.unpin(wrapper);
	}

	@Override
	public void getCharSet(IStatus status, IExternalContext context, Pointer name, int nameSize) throws FbException
	{
		name.setString(0, "UTF8");
	}

	@Override
	public IExternalResultSet open(IStatus status, IExternalContext context, Pointer inMsg, Pointer outMsg)
		throws FbException
	{
		try
		{
			try (InternalContext internalContext = InternalContext.create(status, context, routine))
			{
				int inCount = routine.inputParameters.size();
				int outCount = routine.outputParameters.size();
				Object[] inOut = new Object[inCount + outCount];
				Object[] inOut2 = new Object[inCount + outCount];

				routine.getFromMessage(status, context, routine.inputParameters, inMsg, inOut);

				for (int i = inCount; i < inOut.length; ++i)
					inOut[i] = Array.newInstance(routine.outputParameters.get(i - inCount).javaClass, 1);

				ExternalResultSet rs = (ExternalResultSet) routine.run(status, context, inOut);

				if (rs == null)
				{
					for (int i = inCount; i < inOut.length; ++i)
						inOut2[i] = Array.get(inOut[i], 0);

					routine.putInMessage(status, context, routine.outputParameters, inOut2, inCount, outMsg);
					return null;
				}
				else
				{
					//// FIXME: Stack trace filtering is not working fully correct here.

					class ExtResultSet implements IExternalResultSetIntf
					{
						private IExternalResultSet wrapper;

						@Override
						public void dispose()
						{
							try
							{
								routine.engine.runInClassLoader(status, context,
									routine.method.getDeclaringClass().getName(), routine.method.getName(),
									() -> {
										rs.close();
										return null;
									});
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
								return routine.engine.runInClassLoader(status, context,
									routine.method.getDeclaringClass().getName(), routine.method.getName(),
									() -> {
										if (rs.fetch())
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
							catch (Throwable t)
							{
								FbException.rethrow(t);
								return false;
							}
						}
					}

					ExtResultSet wrapped = new ExtResultSet();
					wrapped.wrapper = JnaUtil.pin(new IExternalResultSet(wrapped));
					return wrapped.wrapper;
				}
			}
		}
		catch (InvocationTargetException e)
		{
			FbException.rethrow(e.getCause());
			return null;
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
			return null;
		}
	}
}
