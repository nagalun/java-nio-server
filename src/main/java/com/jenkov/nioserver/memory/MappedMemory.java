package com.jenkov.nioserver.memory;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * @author nagalun
 * @date 07-08-2017
 */
public class MappedMemory {
	private int refs = 1; /* Only useful with real shared buffer */
	private MemoryManager memoryManager;
	private WeakReference<ByteBuffer> cachedMemManBuffer = null;
	private WeakReference<ByteBuffer> cachedSlicedBuffer = null;
	private ByteBuffer fallbackBuf;
	private int offset;
	public int length;
	
	private boolean freed = false;
	
	public int userData = 0;
	
	public MappedMemory(final ByteBuffer fallbackBuf) {
		this.fallbackBuf = fallbackBuf;
		this.length = fallbackBuf.capacity();
		this.memoryManager = null;
		this.offset = 0;
	}

	public MappedMemory(final MemoryManager mm, final int offset, final int length) {
		this.fallbackBuf = null;
		this.offset = offset;
		this.length = length;
		this.memoryManager = mm;
	}
	
	public boolean resize(final int newSize) {
		if (newSize == this.length) {
			// Do nothing
		} else if (this.memoryManager != null && this.memoryManager.resizeMappedMemory(this, newSize, offset)) {
			this.cachedMemManBuffer = null;
			// Resizing successful.
		} else {
			ByteBuffer newBuf = ByteBuffer.allocate(newSize);
			if (this.memoryManager != null) {
				ByteBuffer oldMem = getBuffer();
				if (newSize < this.length) {
					oldMem.limit(newSize);
				}
				newBuf.put(oldMem);
				this.memoryManager.freeMemory(this, offset);
				this.memoryManager = null;
			} else {
				this.fallbackBuf.position(0);
				if (newSize < this.length) {
					this.fallbackBuf.limit(newSize);
				}
				newBuf.put(this.fallbackBuf);
			}
			this.fallbackBuf = newBuf;
			this.length = newSize;
		}
		return true;
	}
	
	public void free() {
		if (!hasOwnBuffer()) {
			this.memoryManager.freeMemory(this, offset);
		} else {
			this.fallbackBuf = null;
		}
		freed = true;
	}
	
	public boolean hasOwnBuffer() {
		return this.fallbackBuf != null;
	}
	
	public ByteBuffer getBuffer() {
		if (freed) {
			throw new java.lang.IllegalArgumentException();
		}
		if (hasOwnBuffer()) {
			return this.fallbackBuf;
		}
		final ByteBuffer bbuf = this.memoryManager.getMemoryBuffer();
		if (this.cachedMemManBuffer != null && this.cachedSlicedBuffer != null
				&& bbuf == this.cachedMemManBuffer.get() && this.cachedSlicedBuffer.get() != null) {
			return this.cachedSlicedBuffer.get();
		}
		this.cachedMemManBuffer = new WeakReference<ByteBuffer>(bbuf);
		bbuf.limit(this.offset + this.length);
		bbuf.position(this.offset);
		final ByteBuffer slicedbuf = bbuf.slice();
		bbuf.limit(bbuf.capacity());
		this.cachedSlicedBuffer = new WeakReference<ByteBuffer>(slicedbuf);
		return slicedbuf;
	}
	
	public void setBuffer(ByteBuffer fallbackBuf, int offset) {
		this.fallbackBuf = fallbackBuf;
		this.offset = offset;
	}
	
	public void ref() {
		++refs;
	}
	
	public void unref() {
		if (--refs <= 0) {
			free();
		}
	}
}
