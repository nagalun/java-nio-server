package com.jenkov.nioserver;

/**
 * @author nagalun
 * @date 07-08-2017
 */
public interface IEventHandler {
	public void onSocketOpen(final Socket socket);

	public void onSocketClose(final Socket socket);
	
	/*public void onSocketMessage(final Socket socket, final ByteBuffer msg);*/
}
