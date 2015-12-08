package org.firebirdsql.fbjava;

import org.firebirdsql.fbjava.FbClientLibrary.IPluginBase;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginConfig;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginFactory;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginFactoryIntf;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;


class PluginFactory implements IPluginFactoryIntf
{
	private PluginFactory()
	{
	}

	public static IPluginFactory create()
	{
		return JnaUtil.pin(new IPluginFactory(new PluginFactory()));
	}

	@Override
	public IPluginBase createPlugin(IStatus status, IPluginConfig factoryParameter) throws FbException
	{
		return ExternalEngine.create();
	}
}
