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
package org.firebirdsql.fbjava.examples.fbjava_example;

import java.sql.SQLException;

import org.firebirdsql.fbjava.TriggerContext;
import org.firebirdsql.fbjava.Values;
import org.firebirdsql.fbjava.ValuesMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public class FbLogger
{
	private static final Logger log = LoggerFactory.getLogger(FbLogger.class);
	private static final String NEWLINE = System.getProperty("line.separator");

	public static void info() throws SQLException
	{
		TriggerContext context = TriggerContext.get();

		String msg = "Table: " + context.getTableName() +
			"; Type: " + context.getType() +
			"; Action: " + context.getAction() +
			valuesToStr(context.getFieldsMetadata(), context.getOldValues(), NEWLINE + "OLD:" + NEWLINE) +
			valuesToStr(context.getFieldsMetadata(), context.getNewValues(), NEWLINE + "NEW:" + NEWLINE);

		log.info(msg);
	}

	private static String valuesToStr(ValuesMetadata metadata, Values values, String label) throws SQLException
	{
		if (values == null)
			return "";

		StringBuilder sb = new StringBuilder(label);

		for (int i = 1, count = metadata.getParameterCount(); i <= count; ++i)
			sb.append(metadata.getName(i) + ": " + values.getObject(i) + NEWLINE);

		return sb.toString();
	}
}
