package simpledb.buffer;

import simpledb.file.*;
import simpledb.log.LogMgr;
import java.util.*;

/**
 * Manages the pinning and unpinning of buffers to blocks.
 * Implements LRU(K=3) for replacement policy.
 * @author Edward Sciore
 */
public class BufferMgr {
   private Map<BlockId, Buffer> bufferPoolMap = new HashMap<>();
   private Buffer[] bufferPool;
   private int numAvailable;
   private static final long MAX_TIME = 10000; // 10 seconds
   
   public BufferMgr(FileMgr fm, LogMgr lm, int numbuffs) {
      bufferPool = new Buffer[numbuffs];
      numAvailable = numbuffs;
      for (int i = 0; i < numbuffs; i++)
         bufferPool[i] = new Buffer(fm, lm);
   }
   
   public synchronized int available() {
      return numAvailable;
   }
   
   public synchronized void flushAll(int txnum) {
      for (Buffer buff : bufferPool)
         if (buff.modifyingTx() == txnum)
            buff.flush();
   }
   
   public synchronized void unpin(Buffer buff) {
      buff.unpin();
      if (!buff.isPinned()) {
         numAvailable++;
         notifyAll();
      }
   }
   
   public synchronized Buffer pin(BlockId blk) {
      try {
         long timestamp = System.currentTimeMillis();
         Buffer buff = findExistingBuffer(blk);
         while (buff == null && !waitingTooLong(timestamp)) {
            wait(MAX_TIME);
            buff = tryToPin(blk);
         }
         if (buff == null)
            throw new BufferAbortException();
         return buff;
      }
      catch (InterruptedException e) {
         throw new BufferAbortException();
      }
   }  

   private boolean waitingTooLong(long startTime) {
      return System.currentTimeMillis() - startTime > MAX_TIME;
   }
   
   Buffer findExistingBuffer(BlockId blk) {
      return bufferPoolMap.get(blk);
   }
   
   private Buffer tryToPin(BlockId blk) {
      Buffer buff = findExistingBuffer(blk);
      if (buff == null) {
         buff = chooseUnpinnedBuffer();
         if (buff == null)
            return null;
         bufferPoolMap.remove(buff.block()); // Remove old mapping
         buff.assignToBlock(blk);
         bufferPoolMap.put(blk, buff); // Add new mapping
      }
      if (!buff.isPinned())
         numAvailable--;
      buff.pin();
      return buff;
   }

   private Buffer chooseUnpinnedBuffer() {
	    Buffer lruBuffer = null;
	    long maxDistance = Long.MIN_VALUE; // For Kth distance
	    long oldestAccessTime = Long.MAX_VALUE; // For traditional LRU tie-breaking

	    for (Buffer buff : bufferPool) {
	        if (!buff.isPinned()) {
	            List<Long> accessTimes = buff.getAccessTimes();

	            long kthDistance;
	            if (accessTimes.size() < 3) {
	                // For buffers with fewer than K=3 accesses, treat Kth distance as infinite
	                kthDistance = Long.MAX_VALUE;
	            } else {
	                // Calculate Kth backward distance
	                kthDistance = System.currentTimeMillis() - accessTimes.get(0);
	            }

	            if (kthDistance > maxDistance) {
	                // Update candidate based on Kth distance
	                maxDistance = kthDistance;
	                oldestAccessTime = accessTimes.isEmpty() ? 0 : accessTimes.get(accessTimes.size() - 1);
	                lruBuffer = buff;
	            } else if (kthDistance == maxDistance) {
	                // Tie-break using traditional LRU
	                long lastAccessTime = accessTimes.isEmpty() ? 0 : accessTimes.get(accessTimes.size() - 1);
	                if (lastAccessTime < oldestAccessTime) {
	                    oldestAccessTime = lastAccessTime;
	                    lruBuffer = buff;
	                }
	            }
	        }
	    }

	    return lruBuffer;
	}
}
