package com.jenkov.nioserver;

import com.jenkov.nioserver.memory.MemoryManager;

/**
 * Created by jjenkov on 16-10-2015.
 * Modified by nagalun on 07-08-2017.
 */
public interface IMessageReaderFactory {

    public IMessageReader createMessageReader(final MemoryManager memoryManager, final IEventHandler evtHandler);

}
