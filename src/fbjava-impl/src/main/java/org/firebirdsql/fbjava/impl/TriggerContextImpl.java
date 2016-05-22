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

import org.firebirdsql.fbjava.TriggerContext;
import org.firebirdsql.fbjava.Values;
import org.firebirdsql.fbjava.ValuesMetadata;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTrigger;


class TriggerContextImpl extends ContextImpl implements TriggerContext
{
	private final Action action;

	public TriggerContextImpl(InternalContext internalContext, int triggerAction)
	{
		super(internalContext);

		switch (triggerAction)
		{
			case IExternalTrigger.ACTION_CONNECT:
				action = Action.CONNECT;
				break;

			case IExternalTrigger.ACTION_DISCONNECT:
				action = Action.DISCONNECT;
				break;

			case IExternalTrigger.ACTION_TRANS_START:
				action = Action.TRANS_START;
				break;

			case IExternalTrigger.ACTION_TRANS_COMMIT:
				action = Action.TRANS_COMMIT;
				break;

			case IExternalTrigger.ACTION_TRANS_ROLLBACK:
				action = Action.TRANS_ROLLBACK;
				break;

			case IExternalTrigger.ACTION_DDL:
				action = Action.DDL;
				break;

			case IExternalTrigger.ACTION_DELETE:
				action = Action.DELETE;
				break;

			case IExternalTrigger.ACTION_INSERT:
				action = Action.INSERT;
				break;

			case IExternalTrigger.ACTION_UPDATE:
				action = Action.UPDATE;
				break;

			default:
				throw new AssertionError("Unrecognized trigger action");
		}
	}

	@Override
	public String getTableName()
	{
		return internalContext.getRoutine().tableName;
	}

	@Override
	public Type getType()
	{
		return internalContext.getRoutine().triggerType;
	}

	@Override
	public Action getAction()
	{
		return action;
	}

	@Override
	public ValuesMetadata getFieldsMetadata()
	{
		Routine routine = internalContext.getRoutine();
		return routine.triggerType == Type.DATABASE ? null : routine.inputMetadata;
	}

	@Override
	public Values getOldValues()
	{
		return internalContext.getInValues();
	}

	@Override
	public Values getNewValues()
	{
		return internalContext.getOutValues();
	}
}
