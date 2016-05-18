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

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTrigger;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTriggerIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


final class ExternalTrigger implements IExternalTriggerIntf
{
	private IExternalTrigger wrapper;
	private Routine routine;

	private ExternalTrigger(Routine routine)
	{
		this.routine = routine;
	}

	public static IExternalTrigger create(Routine routine)
	{
		ExternalTrigger wrapped = new ExternalTrigger(routine);
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
			List<Parameter> fieldParameters = routine.inputParameters;
			int count = fieldParameters.size();
			Object[] oldValues = oldMsg == null ? null : new Object[count];
			Object[] newValues = newMsg == null ? null : new Object[count];

			try (InternalContext internalContext = InternalContext.createTrigger(status, context, routine, action,
					(oldMsg == null ? null : new ValuesImpl(oldValues, count)),
					(newMsg == null ? null : new ValuesImpl(newValues, count))))
			{
				if (oldMsg != null)
					routine.getFromMessage(status, context, fieldParameters, oldMsg, oldValues);

				if (newMsg != null)
					routine.getFromMessage(status, context, fieldParameters, newMsg, newValues);

				routine.run(status, context, null);

				if (newMsg != null)
					routine.putInMessage(status, context, fieldParameters, newValues, 0, newMsg);
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
