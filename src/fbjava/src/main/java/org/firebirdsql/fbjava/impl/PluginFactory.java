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

import org.firebirdsql.fbjava.impl.FbClientLibrary.IConfig;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IConfigEntry;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IPluginBase;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IPluginConfig;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IPluginFactory;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IPluginFactoryIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;


final class PluginFactory implements IPluginFactoryIntf
{
	private PluginFactory()
	{
	}

	public static IPluginFactory create()
	{
		return JnaUtil.pin(new IPluginFactory(new PluginFactory()));
	}

	@Override
	public IPluginBase createPlugin(IStatus status, IPluginConfig pluginConfig) throws FbException
	{
		String securityDatabase;

		IConfig config = pluginConfig.getDefaultConfig(status);
		try
		{
			IConfigEntry entry = config.find(status, "SecurityDatabase");
			try
			{
				securityDatabase = entry.getValue();
			}
			finally
			{
				entry.release();
			}
		}
		finally
		{
			config.release();
		}

		return ExternalEngine.create(securityDatabase);
	}
}
