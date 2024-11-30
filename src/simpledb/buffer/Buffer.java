package simpledb.buffer;

import simpledb.file.*;
import simpledb.log.LogMgr;
import java.util.ArrayList;
import java.util.List;

/**
 * An individual buffer. A databuffer wraps a page 
 * and stores information about its status,
 * such as the associated disk block,
 * the number of times the buffer has been pinned,
 * whether its contents have been modified,
 * and if so, the id and lsn of the modifying transaction.
 * @author Edward Sciore
 */
public class Buffer {
   private FileMgr fm;
   private LogMgr lm;
   private Page contents;
   private BlockId blk = null;
   private int pins = 0;
   private int txnum = -1;
   private int lsn = -1;
   private List<Long> accessTimes = new ArrayList<>(); // Tracks last K unpin times

   public Buffer(FileMgr fm, LogMgr lm) {
      this.fm = fm;
      this.lm = lm;
      contents = new Page(fm.blockSize());
   }
   
   public Page contents() {
      return contents;
   }

   public BlockId block() {
      return blk;
   }

   public void setModified(int txnum, int lsn) {
      this.txnum = txnum;
      if (lsn >= 0)
         this.lsn = lsn;
   }

   public boolean isPinned() {
      return pins > 0;
   }
   
   public int modifyingTx() {
      return txnum;
   }

   void assignToBlock(BlockId b) {
      flush();
      blk = b;
      fm.read(blk, contents);
      pins = 0;
   }
   
   void flush() {
      if (txnum >= 0) {
         lm.flush(lsn);
         fm.write(blk, contents);
         txnum = -1;
      }
   }

   void pin() {
      pins++;
   }

   void unpin() {
      pins--;
      if (pins == 0) {
         recordAccessTime();
      }
   }

   private void recordAccessTime() {
      accessTimes.add(System.currentTimeMillis());
      if (accessTimes.size() > 3) {
         accessTimes.remove(0); // Maintain only the last 3 access times
      }
   }

   public List<Long> getAccessTimes() {
      return new ArrayList<>(accessTimes);
   }
}
