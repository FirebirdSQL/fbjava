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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


final class Routine
{
	static enum Type
	{
		FUNCTION,
		PROCEDURE,
		TRIGGER
	}

	ExternalEngine engine;
	Method method;
	List<Parameter> inputParameters = new ArrayList<>();
	List<Parameter> outputParameters = new ArrayList<>();

	Routine(ExternalEngine engine)
	{
		this.engine = engine;
	}

	void setupParameters(IStatus status, List<Parameter> parameters, IMessageMetadata metadata,
		IMetadataBuilder builder) throws FbException
	{
		for (int i = 0; i < parameters.size(); ++i)
		{
			Parameter parameter = parameters.get(i);
			parameter.conversion = parameter.dataType.setupConversion(status,
				parameter.javaClass, metadata, builder, i);
		}

		IMessageMetadata builtMetadata = builder.getMetadata(status);
		try
		{
			for (int i = 0; i < parameters.size(); ++i)
			{
				Parameter parameter = parameters.get(i);
				parameter.nullOffset = builtMetadata.getNullOffset(status, i);
				parameter.offset = builtMetadata.getOffset(status, i);
				parameter.length = builtMetadata.getLength(status, i);
			}
		}
		finally
		{
			builtMetadata.release();
		}
	}

	Object[] getFromMessage(IStatus status, IExternalContext context, List<Parameter> parameters, Pointer message)
		throws FbException
	{
		Object[] values = new Object[parameters.size()];
		getFromMessage(status, context, parameters, message, values);
		return values;
	}

	void getFromMessage(IStatus status, IExternalContext context, List<Parameter> parameters, Pointer message,
		Object[] values) throws FbException
	{
		assert values.length <= parameters.size();
		int i = 0;

		for (Parameter parameter : parameters)
		{
			values[i] = parameter.conversion.getFromMessage(context, message, parameter.nullOffset, parameter.offset);
			++i;
		}
	}

	void putInMessage(IStatus status, IExternalContext context, List<Parameter> parameters, Object[] values,
		int valuesStart, Pointer message) throws FbException
	{
		assert parameters.size() == values.length - valuesStart;

		int i = valuesStart;

		for (Parameter parameter : parameters)
		{
			Object value = values[i];
			parameter.conversion.putInMessage(context, message, parameter.nullOffset, parameter.offset, value);
			++i;
		}
	}

	Object run(IExternalContext context, Object[] args) throws Throwable
	{
		return engine.runInClassLoader(context.getUserName(), method.getDeclaringClass().getName(), method.getName(),
			() -> {
				try
				{
					return method.invoke(null, args);
				}
				catch (Exception | ExceptionInInitializerError t)
				{
					throw t.getCause();
				}
			});
	}
}
