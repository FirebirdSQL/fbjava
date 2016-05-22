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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;
import org.firebirdsql.gds.ISCConstants;

import com.sun.jna.Pointer;


final class FbException extends Exception
{
	private static final long serialVersionUID = 1L;

	public FbException(Throwable t)
	{
		super(t);
	}

	public FbException(String msg)
	{
		super(msg);
	}

	public FbException(String msg, Throwable t)
	{
		super(msg, t);
	}

	public static void rethrow(Throwable t) throws FbException
	{
		throw new FbException(null, t);
	}

	public static void catchException(IStatus status, Throwable t)
	{
		while (t != null && t instanceof FbException && t.getMessage() == null)
			t = t.getCause();

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		String msg = sw.toString();

		try (CloseableMemory memory = new CloseableMemory(msg.length() + 1))
		{
			memory.setString(0, msg);

			Pointer[] vector = new Pointer[] {
				new Pointer(ISCConstants.isc_arg_gds),
				new Pointer(ISCConstants.isc_random),
				new Pointer(ISCConstants.isc_arg_cstring),
				new Pointer(msg.length()),
				memory,
				new Pointer(ISCConstants.isc_arg_end)
			};

			status.setErrors2(vector.length, vector);
		}
	}

	public static void checkException(IStatus status) throws FbException
	{
		if ((status.getState() & IStatus.STATE_ERRORS) != 0)
			throw new FbException("FIXME:");	//// FIXME:
	}
}