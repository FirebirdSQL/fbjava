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


/**
 * This interface represents a Firebird Function or Procedure Context.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public interface CallableRoutineContext extends Context
{
	/**
	 * Gets the Context instance associated with the current call.
	 */
	public static CallableRoutineContext get()
	{
		return (CallableRoutineContext) Context.get();
	}

	/**
	 * Gets the metadata package name that called the external routine.
	 * For unpackaged routines, return null.
	 */
	public String getPackageName();

	/**
	 * Gets the input values metadata.
	 */
	public ValuesMetadata getInputMetadata();

	/**
	 * Gets the output values metadata.
	 * For functions, it always returns a ValuesMetadata with a single entry.
	 */
	public ValuesMetadata getOutputMetadata();

	/**
	 * Gets the input values.
	 */
	public Values getInputValues();
}
