package org.firebirdsql.fbjava;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.firebirdsql.fbjava.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


class Routine
{
	Method method;
	List<Parameter> inputParameters = new ArrayList<>();
	List<Parameter> outputParameters = new ArrayList<>();

	void setupParameters(IStatus status, List<Parameter> parameters, IMessageMetadata metadata,
		IMetadataBuilder builder) throws FbException
	{
		for (int i = 0; i < parameters.size(); ++i)
		{
			Parameter parameter = parameters.get(i);
			parameter.conversion = parameter.dataType.setupConversion(status,
				parameter.javaClass, metadata, builder, i);
		}

		IMessageMetadata outMetadata = builder.getMetadata(status);
		try
		{
			for (int i = 0; i < parameters.size(); ++i)
			{
				Parameter parameter = parameters.get(i);
				parameter.nullOffset = outMetadata.getNullOffset(status, i);
				parameter.offset = outMetadata.getOffset(status, i);
				parameter.length = outMetadata.getLength(status, i);
			}
		}
		finally
		{
			outMetadata.release();
		}
	}

	Object[] getFromMessage(IStatus status, List<Parameter> parameters, Pointer message)
	{
		Object[] values = new Object[parameters.size()];
		int i = 0;

		for (Parameter parameter : parameters)
		{
			values[i] = parameter.conversion.getFromMessage(message, parameter.nullOffset, parameter.offset);
			++i;
		}

		return values;
	}

	void putInMessage(IStatus status, List<Parameter> parameters, Object[] values, Pointer message)
	{
		assert parameters.size() == values.length;

		int i = 0;

		for (Parameter parameter : parameters)
		{
			Object value = values[i];
			parameter.conversion.putInMessage(message, parameter.nullOffset, parameter.offset, value);
			++i;
		}
	}
}
