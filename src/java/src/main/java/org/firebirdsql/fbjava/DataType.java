package org.firebirdsql.fbjava;

import org.firebirdsql.fbjava.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;

import com.sun.jna.Pointer;


abstract class DataType
{
	static final short NOT_NULL_FLAG = (short) 0;	// Should test with this constant instead of with NULL_FLAG.
	static final short NULL_FLAG = (short) -1;

	abstract class Conversion
	{
		abstract Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
			throws FbException;
		abstract void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
			throws FbException;
	}

	abstract Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
		IMetadataBuilder builder, int index) throws FbException;
}
