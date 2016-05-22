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
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;

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
