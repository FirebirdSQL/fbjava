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

import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalFunction;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalFunctionIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


final class ExternalFunction implements IExternalFunctionIntf
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
			Object[] in = new Object[routine.inputParameters.size()];

			try (InternalContext internalContext = InternalContext.create(status, context, routine,
					new ValuesImpl(in, in.length), null))
			{
				ThrowableRunnable preExecute = () ->
					routine.getFromMessage(status, context, routine.inputParameters, inMsg, in);

				ThrowableFunction<Object, Void> postExecute = out -> {
					routine.putInMessage(status, context, routine.outputParameters, new Object[] {out}, 0, outMsg);
					return null;
				};

				InternalContext oldContext = InternalContext.set(internalContext);
				try
				{
					routine.run(status, context, in, preExecute, postExecute);
				}
				finally
				{
					InternalContext.set(oldContext);
				}
			}
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
		}
	}
}
