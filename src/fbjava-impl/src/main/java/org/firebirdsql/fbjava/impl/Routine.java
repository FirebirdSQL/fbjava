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

import org.firebirdsql.fbjava.TriggerContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTrigger;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IRoutineMetadata;
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

	final Type type;
	final ExternalEngine engine;
	final String objectName;
	final String packageName;
	final String body;
	String tableName;
	TriggerContext.Type triggerType;
	String nameInfo;
	Method method;
	final List<Parameter> inputParameters = new ArrayList<>();
	final List<Parameter> outputParameters = new ArrayList<>();
	final ValuesMetadataImpl inputMetadata = new ValuesMetadataImpl();
	final ValuesMetadataImpl outputMetadata = new ValuesMetadataImpl();
	boolean generic;

	Routine(IStatus status, IRoutineMetadata metadata, ExternalEngine engine, Type type) throws FbException
	{
		this.engine = engine;
		this.type = type;

		objectName = metadata.getName(status);
		packageName = metadata.getPackage(status);
		body = metadata.getBody(status);

		if (type == Type.TRIGGER)
		{
			tableName = metadata.getTriggerTable(status);

			switch (metadata.getTriggerType(status))
			{
				case IExternalTrigger.TYPE_BEFORE:
					triggerType = TriggerContext.Type.BEFORE;
					break;

				case IExternalTrigger.TYPE_AFTER:
					triggerType = TriggerContext.Type.AFTER;
					break;

				case IExternalTrigger.TYPE_DATABASE:
					triggerType = TriggerContext.Type.DATABASE;
					break;

				default:
					throw new AssertionError("Unrecognized trigger type");
			}
		}
	}

	void setupParameters(IStatus status, List<Parameter> parameters, ValuesMetadataImpl valuesMetadata,
		IMessageMetadata metadata, IMetadataBuilder builder) throws FbException
	{
		final int count = metadata.getCount(status);

		if (parameters.size() == 0 && count != 0)
		{
			for (int index = 0; index < count; ++index)
			{
				Parameter parameter = new Parameter(
					ExternalEngine.dataTypesByClass.get(Object.class), Object.class);
				parameter.name = metadata.getField(status, index);
				parameter.type = ExternalEngine.fbTypeNames.get(metadata.getType(status, index));

				parameters.add(parameter);
			}

			generic = true;
		}

		for (int i = 0; i < parameters.size(); ++i)
		{
			final Parameter parameter = parameters.get(i);
			parameter.conversion = parameter.dataType.setupConversion(status,
				parameter.javaClass, metadata, builder, i);
		}

		final IMessageMetadata builtMetadata = builder.getMetadata(status);
		try
		{
			for (int i = 0; i < parameters.size(); ++i)
			{
				final Parameter parameter = parameters.get(i);
				parameter.nullOffset = builtMetadata.getNullOffset(status, i);
				parameter.offset = builtMetadata.getOffset(status, i);
				parameter.length = builtMetadata.getLength(status, i);
				parameter.scale = builtMetadata.getScale(status, i);
				parameter.isNullable = builtMetadata.isNullable(status, i);
			}

			valuesMetadata.setup(parameters);
		}
		finally
		{
			builtMetadata.release();
		}
	}

	void getFromMessage(IStatus status, IExternalContext context, List<Parameter> parameters, Pointer message,
		Object[] values) throws FbException
	{
		assert values.length <= parameters.size();

		try
		{
			ExternalEngine.doPrivileged(() -> {
				int i = 0;

				for (final Parameter parameter : parameters)
				{
					values[i] = parameter.conversion.getFromMessagePrivileged(context, message,
						parameter.nullOffset, parameter.offset);
					++i;
				}

				return null;
			});
		}
		catch (final Throwable t)
		{
			FbException.rethrow(t);
		}

		int i = 0;

		for (final Parameter parameter : parameters)
		{
			if (values[i] != null)
				values[i] = parameter.conversion.getFromMessageUnprivileged(context, values[i]);
			++i;
		}
	}

	void putInMessage(IStatus status, IExternalContext context, List<Parameter> parameters, Object[] values,
		int valuesStart, Pointer message) throws FbException
	{
		assert parameters.size() == values.length - valuesStart;

		int i = valuesStart;

		final Object[] temp = new Object[values.length - valuesStart];

		for (Parameter parameter : parameters)
		{
			Object value = values[i];
			temp[i - valuesStart] = parameter.conversion.putInMessageUnprivileged(context, value);
			++i;
		}

		try
		{
			ExternalEngine.doPrivileged(() -> {
				int j = valuesStart;

				for (final Parameter parameter : parameters)
				{
					parameter.conversion.putInMessagePrivileged(context, message, parameter.nullOffset, parameter.offset,
						values[j], temp[j - valuesStart]);
					++j;
				}

				return null;
			});
		}
		catch (final Throwable t)
		{
			FbException.rethrow(t);
		}
	}

	 <T>T run(IStatus status, IExternalContext context, Object[] args, ThrowableRunnable preExecute,
		ThrowableFunction<Object, T> postExecute) throws Throwable
	{
		return engine.runInClassLoader(status, context, method.getDeclaringClass().getName(), method.getName(),
			() -> {
				preExecute.run();

				final Object ret;

				try
				{
					ret = method.invoke(null, (generic ? null : args));
				}
				catch (final Exception | ExceptionInInitializerError t)
				{
					final Throwable cause = t.getCause();
					throw cause == null ? t : cause;
				}

				return postExecute.apply(ret);
			});
	}
}
