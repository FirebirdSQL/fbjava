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
 * This interface represents a Firebird Trigger Context.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public interface TriggerContext extends Context
{
	/**
	 * Trigger Type.
	 */
	public static enum Type
	{
		BEFORE,
		AFTER,
		DATABASE
	}

	/**
	 * Trigger Action.
	 */
	public static enum Action
	{
		INSERT,
		UPDATE,
		DELETE,
		CONNECT,
		DISCONNECT,
		TRANS_START,
		TRANS_COMMIT,
		TRANS_ROLLBACK,
		DDL
	}

	/**
	 * Gets the Context instance associated with the current call.
	 */
	public static TriggerContext get()
	{
		return (TriggerContext) Context.get();
	}

	/**
	 * Gets the table that fired the trigger.
	 * Returns null for database and DDL triggers.
	 */
	public String getTableName();

	/**
	 * Gets the type of the trigger.
	 */
	public Type getType();

	/**
	 * Gets the action that fired the trigger.
	 */
	public Action getAction();

	/**
	 * Gets the fields metadata.
	 * Returns null for database/ddl triggers.
	 */
	public ValuesMetadata getFieldsMetadata();

	/**
	 * Gets the fields old values.
	 * Returns null for database/ddl and insert triggers.
	 */
	public Values getOldValues();

	/**
	 * Gets the fields new values.
	 * Returns null for database/ddl and delete triggers.
	 */
	public Values getNewValues();
}
