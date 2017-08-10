package com.jenkov.nioserver.memory;

import java.nio.ByteBuffer;
import java.util.BitSet;

/*import java.awt.EventQueue;
import javax.swing.JFrame;*/

/**
 * @author nagalun
 * @date 07-08-2017
 */
public class MemoryManager {
	//private final MemoryView memView;
	private final static int DEFAULT_BLOCK_SIZE = 512;

	private final int minBlocksAllocated;
	private final int blockSize;
	private ByteBuffer memoryBuffer;
	private BitSet allocationMap; /* Blocks allocated */
	private int allocMapSize;
	private int[] sizeSamples = new int[32];
	private int sizeSamplesIdx = 0;

	/*
	 * Unfortunately we can't know the direct buffer memory limit, until we reach
	 * it. Will get set once a OutOfMemoryError occurs.
	 **/
	private int maxBufferSize = -1;

	public MemoryManager(final int minBlocksAllocated) {
		this(minBlocksAllocated, true, DEFAULT_BLOCK_SIZE);
	}

	public MemoryManager(final int minBlocksAllocated, final boolean useDirect, final int blockSize) {
		this.blockSize = blockSize;
		this.memoryBuffer = useDirect ? ByteBuffer.allocateDirect(minBlocksAllocated * blockSize)
				: ByteBuffer.allocate(minBlocksAllocated * blockSize);
		this.allocationMap = new BitSet(minBlocksAllocated);
		this.allocationMap.set(minBlocksAllocated);
		this.allocMapSize = minBlocksAllocated;
		this.minBlocksAllocated = minBlocksAllocated;

		/*this.memView = new MemoryView(allocationMap);
		EventQueue.invokeLater(() -> {
			JFrame frame = new JFrame("MemoryView");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.add(this.memView);
			frame.setLocationByPlatform(true);
			frame.pack();
			frame.setVisible(true);
		});*/
	}

	/*
	 * Returns the offset in the memoryBuffer for the empty block, or -1 if it
	 * wasn't found.
	 */
	private int findFreeBlock(int blocks) {
		int totalBlocks = allocMapSize;
		if (blocks <= totalBlocks) {
			for (int i = allocationMap.nextClearBit(0); i != -1
					&& i <= totalBlocks; i = allocationMap.nextClearBit(i)) {
				int e = allocationMap.nextSetBit(i);
				e = e == -1 ? totalBlocks : e;
				e -= i;
				if (e >= blocks) {
					return i;
				}
				i += e;
			}
		}
		return -1;
	}

	/*
	 * This should only be called when it's known that 'size' won't fit in the
	 * memory buffer. Also it will increase the buffer 128 blocks minimum.
	 **/
	private int resizeToFit(int blocks) {
		/* No small resizes */
		blocks = blocks <= 256 ? 256 : blocks;
		int size = allocMapSize;
		int freeBlock = allocationMap.previousSetBit(size - 1) + 1;
		if (freeBlock == 0) {
			freeBlock = allocationMap.get(0) ? size : 0;
		}
		int newBlockSize = freeBlock + blocks;
		return resize(newBlockSize) ? freeBlock : -1;
	}

	private boolean resize(int blocks) {
		int currentSize = allocMapSize;
		if (maxBufferSize != -1 && blocks * blockSize > maxBufferSize) {
			return false;
		}
		for (int i = currentSize; i-- > blocks;) {
			if (allocationMap.get(i)) {
				/*
				 * Some memory is being used on the part we want to take away, and moving memory
				 * would be a complex operation. Since allocating direct memory is also
				 * expensive, instead of resizing to the closest value to 'blocks' as we can
				 * get, wait until all the memory higher than 'blocks' is free.
				 **/
				return false;
			}
		}
		System.out.println("Resizing to: " + blocks * blockSize + ", from: " + currentSize * blockSize);
		ByteBuffer newBuf = null;
		try {
			newBuf = memoryBuffer.isDirect() ? ByteBuffer.allocateDirect(blocks * blockSize)
					: ByteBuffer.allocate(blocks * blockSize);
		} catch (OutOfMemoryError e) {
			/*
			 * Minor annoyance may happen here, when the memory resize difference is big
			 * (limit could be set lower than it should be). We can't just ignore this
			 * because trying to allocate, and throwing the exception is expensive.
			 **/
			maxBufferSize = currentSize * blockSize;
			System.out.println("Out of direct memory, maximum set: " + maxBufferSize);
			return false;
		}
		memoryBuffer.position(0);
		if (currentSize > blocks) {
			memoryBuffer.limit(blocks * blockSize);
		}
		allocationMap.clear(allocMapSize);
		allocationMap.set(blocks);
		allocMapSize = blocks;
		newBuf.put(memoryBuffer);
		memoryBuffer = newBuf;
		return true;
	}

	private void adjustSize() {
		int currentSize = allocMapSize;
		int max = minBlocksAllocated;
		for (int i = 0; i < sizeSamples.length; i++) {
			max = sizeSamples[i] > max ? sizeSamples[i] : max;
		}
		/*
		 * This function should only shrink the buffer, not expand it. Only try to
		 * shrink when the size difference is >= 4 MB
		 **/
		if (max != currentSize && currentSize - max >= 1024 * 1024 * 4 / blockSize) {
			resize(max);
		}
	}

	private int allocateMemory(int blocks) {
		int offset = findFreeBlock(blocks);
		if (offset == -1 && (offset = resizeToFit(blocks)) == -1) {
			/* Couldn't allocate any memory :( */
			return -1;
		}
		allocationMap.set(offset, offset + blocks, true);
		//memView.render();
		return offset * blockSize;
	}

	public MappedMemory getMemory(int size) {
		int blocksTaken = (size + blockSize - 1) / blockSize;
		int offset = allocateMemory(blocksTaken);
		sizeSamples[sizeSamplesIdx++] = blocksTaken;
		sizeSamplesIdx %= sizeSamples.length;
		if (offset != -1) {
			return new MappedMemory(this, offset, size);
		} else {
			/* Something has gone wrong if we get here */
			/*
			 * System.out.println("Error while allocating MemoryManager memory of size: " +
			 * size); // Debug only
			 */
			return new MappedMemory(ByteBuffer.allocate(size));
		}
	}

	public void freeMemory(MappedMemory mem, int offset) {
		if (!mem.hasOwnBuffer()) {
			int blocksTaken = (mem.length + blockSize - 1) / blockSize;
			int startingBlock = offset / blockSize;
			allocationMap.set(startingBlock, startingBlock + blocksTaken, false);
			//memView.render();
			adjustSize();
		}
	}

	public boolean resizeMappedMemory(MappedMemory mem, int newSize, int coffset) {
		int blocksTaken = (mem.length + blockSize - 1) / blockSize;
		int newBlocksTaken = (newSize + blockSize - 1) / blockSize;
		int startingBlock = coffset / blockSize;
		if (newBlocksTaken < blocksTaken) {
			allocationMap.set(startingBlock + newBlocksTaken, startingBlock + blocksTaken, false);
		} else if (newBlocksTaken > blocksTaken) {
			allocationMap.set(startingBlock, startingBlock + blocksTaken, false);
			int offset = allocateMemory(newBlocksTaken);
			if (offset == -1) {
				return false;
			}
			if (offset == coffset) {
				mem.length = newSize;
				return true;
			}
			ByteBuffer tmpBuf = memoryBuffer.duplicate(); /* Hmm... */
			memoryBuffer.position(offset);
			tmpBuf.position(coffset);
			tmpBuf.limit(coffset + mem.length);
			memoryBuffer.put(tmpBuf);
			mem.setBuffer(null, offset);
			// memoryBuffer.position(0);
		}
		mem.length = newSize;
		return true;
	}

	public ByteBuffer getMemoryBuffer() {
		return memoryBuffer;
	}
}
