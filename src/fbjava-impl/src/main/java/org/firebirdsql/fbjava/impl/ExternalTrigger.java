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

import java.util.List;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTrigger;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTriggerIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


final class ExternalTrigger implements IExternalTriggerIntf
{
	private IExternalTrigger wrapper;
	private final Routine routine;

	private ExternalTrigger(final Routine routine)
	{
		this.routine = routine;
	}

	public static IExternalTrigger create(final Routine routine)
	{
		final ExternalTrigger wrapped = new ExternalTrigger(routine);
		wrapped.wrapper = JnaUtil.pin(new IExternalTrigger(wrapped));
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
	public void execute(IStatus status, IExternalContext context, int action, Pointer oldMsg, Pointer newMsg)
		throws FbException
	{
		try
		{
			final List<Parameter> fieldParameters = routine.inputParameters;
			final int count = fieldParameters.size();
			final Object[] oldValues = oldMsg == null ? null : new Object[count];
			final Object[] newValues = newMsg == null ? null : new Object[count];

			try (final InternalContext internalContext = InternalContext.createTrigger(
					status, context, routine, action,
					(oldMsg == null ? null : new ValuesImpl(oldValues, count)),
					(newMsg == null ? null : new ValuesImpl(newValues, count))))
			{
				final ThrowableRunnable preExecute = () -> {
					if (oldMsg != null)
						routine.getFromMessage(status, context, fieldParameters, oldMsg, oldValues);

					if (newMsg != null)
						routine.getFromMessage(status, context, fieldParameters, newMsg, newValues);
				};

				final ThrowableFunction<Object, Void> postExecute = out -> {
					if (newMsg != null)
						routine.putInMessage(status, context, fieldParameters, newValues, 0, newMsg);
					return null;
				};

				final InternalContext oldContext = InternalContext.set(internalContext);
				try
				{
					routine.run(status, context, null, preExecute, postExecute);
				}
				finally
				{
					InternalContext.set(oldContext);
				}
			}
		}
		catch (final Throwable t)
		{
			FbException.rethrow(t);
		}
	}
}
