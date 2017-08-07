package com.jenkov.nioserver;

import java.io.IOException;

/**
 * Created by jjenkov on 16-10-2015.
 * Modified by nagalun on 07-08-2017.
 */
public interface IMessageReader {

	public void read(Socket socket) throws IOException;

	public void clear();

}
