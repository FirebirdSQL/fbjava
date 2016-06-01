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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;

import org.firebirdsql.fbjava.ExternalResultSet;
import org.firebirdsql.fbjava.ProcedureContext;
import org.firebirdsql.fbjava.Values;
import org.firebirdsql.fbjava.ValuesMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public class FbJdbc
{
	private static final Logger log = LoggerFactory.getLogger(FbJdbc.class);

	static
	{
		try
		{
			Class.forName("org.postgresql.Driver");
		}
		catch (ClassNotFoundException e)
		{
			log.warn("Cannot load org.postgresql.Driver", e);
		}
	}

	public static ExternalResultSet executeQuery() throws Exception
	{
		final ProcedureContext context = ProcedureContext.get();
		return new ExternalResultSet() {
			ValuesMetadata outMetadata = context.getOutputMetadata();
			Values outValues = context.getOutputValues();
			int count = outMetadata.getParameterCount();
			Connection conn;
			PreparedStatement stmt;
			ResultSet rs;

			{
				StringTokenizer tokenizer = new StringTokenizer(context.getNameInfo(), "|");

				String uri = tokenizer.nextToken();
				String user = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
				String password = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;

				conn = DriverManager.getConnection(uri, user, password);
				try
				{
					StringBuilder sb = new StringBuilder("select ");

					for (int i = 0; i < count; ++i)
					{
						if (i != 0)
							sb.append(", ");
						sb.append("x." + outMetadata.getName(i + 1));
					}

					sb.append(" from (" + context.getBody() + ") x");

					stmt = conn.prepareStatement(sb.toString());
					rs = stmt.executeQuery();
				}
				catch (Exception e)
				{
					close();
					throw e;
				}
			}

			@Override
			public void close()
			{
				try
				{
					if (rs != null)
						rs.close();

					if (stmt != null)
						stmt.close();

					if (conn != null)
						conn.close();
				}
				catch (SQLException e)
				{
					log.error("Error closing the ExternalResultSet", e);
				}
			}

			@Override
			public boolean fetch() throws Exception
			{
				if (!rs.next())
					return false;

				for (int i = 0; i < count; ++i)
				{
					Object obj = rs.getObject(i + 1);

					if (obj instanceof Number)
						obj = rs.getBigDecimal(i + 1);

					outValues.setObject(i + 1, obj);
				}

				return true;
			}
		};
	}
}
