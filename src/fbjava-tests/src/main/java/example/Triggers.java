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
package example;

import java.sql.SQLException;

import org.firebirdsql.fbjava.TriggerContext;
import org.firebirdsql.fbjava.Values;
import org.firebirdsql.fbjava.ValuesMetadata;


public class Triggers
{
	public static void trigger1() throws SQLException
	{
		TriggerContext context = TriggerContext.get();
		ValuesMetadata fieldsMetadata = context.getFieldsMetadata();
		Values oldValues = context.getOldValues();
		Values newValues = context.getNewValues();

		System.out.println("--> " + context.getTableName() + ", " + context.getType() + ", " +
			context.getAction() + ", " + context.getFieldsMetadata() + ", " +
			context.getOldValues() + ", " + context.getNewValues());

		if (fieldsMetadata != null)
		{
			int count = fieldsMetadata.getParameterCount();

			for (int i = 1; i <= count; ++i)
			{
				System.out.println(">>> " + i + ", " + fieldsMetadata.getName(i) + ", " +
					(oldValues == null ? null : oldValues.get(i)) + ", " +
					(newValues == null ? null : newValues.get(i)));

				/***
				if (newValues != null)
					newValues.set(2, BigDecimal.valueOf(((BigDecimal) newValues.get(2)).intValue() + 1));
				***/
			}
		}

		System.out.flush();
	}
}
