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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.firebirdsql.logging.Logger;
import org.firebirdsql.logging.LoggerFactory;


/**
 * URLConnection for blobs in plugin tables.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
final class BlobConnection extends URLConnection
{
	private static final Logger log = LoggerFactory.getLogger(BlobConnection.class);
	private ByteArrayOutputStream bout;

	public BlobConnection(URL url) throws IOException
	{
		super(url);

		connect();
	}

	@Override
	public void connect() throws IOException
	{
		if (bout != null)
			return;

		try
		{
			final DbClassLoader classLoader = (DbClassLoader) Thread.currentThread().getContextClassLoader();
			final Connection conn = classLoader.getConnection();

			try (PreparedStatement stmt = conn.prepareStatement("select content from sqlj.read_jar(?)"))
			{
				String urlStr = url.toString().substring(8);	// "fbjava:/"
				stmt.setString(1, urlStr);

				try (ResultSet rs = stmt.executeQuery())
				{
					if (rs.next())
					{
						InputStream in = rs.getBinaryStream(1);
						bout = new ByteArrayOutputStream();

						byte[] buffer = new byte[8192];
						int count;

						while ((count = in.read(buffer)) != -1)
							bout.write(buffer, 0, count);
					}
					else
					{
						try (PreparedStatement stmt2 = conn.prepareStatement("select child from sqlj.list_dir(?)"))
						{
							stmt2.setString(1, urlStr);

							try (ResultSet rs2 = stmt2.executeQuery())
							{
								if (rs2.next())
								{
									bout = new ByteArrayOutputStream();
									PrintWriter writer = new PrintWriter(bout);

									do
									{
										writer.println(rs2.getString(1));
									} while (rs2.next());

									writer.flush();
								}
							}
						}

						throw new IOException(
							String.format("Resource '%s' has not been found on the database.", url));
					}
				}
			}
		}
		catch (SQLException e)
		{
			log.error("Error retrieving resource or class from the database", e);
			throw new IOException(e);
		}
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		return new ByteArrayInputStream(bout.toByteArray());
	}
}
