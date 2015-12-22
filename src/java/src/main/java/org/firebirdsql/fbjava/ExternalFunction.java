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
package org.firebirdsql.fbjava;

import java.lang.reflect.InvocationTargetException;

import org.firebirdsql.fbjava.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalFunction;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalFunctionIntf;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


class ExternalFunction implements IExternalFunctionIntf
{
	private IExternalFunction wrapper;
	private Routine routine;

	private ExternalFunction(Routine routine)
	{
		this.routine = routine;
	}

	public static IExternalFunction create(Routine routine)
	{
		ExternalFunction wrapped = new ExternalFunction(routine);
		wrapped.wrapper = JnaUtil.pin(new IExternalFunction(wrapped));
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
	public void execute(IStatus status, IExternalContext context, Pointer inMsg, Pointer outMsg) throws FbException
	{
		try
		{
			try (InternalContext internalContext = InternalContext.get(status, context))
			{
				Object[] in = routine.getFromMessage(status, context, routine.inputParameters, inMsg);
				Object[] out = {routine.method.invoke(null, in)};

				routine.putInMessage(status, context, routine.outputParameters, out, outMsg);
			}
		}
		catch (InvocationTargetException e)
		{
			FbException.rethrow(e.getCause());
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
		}
	}
}
