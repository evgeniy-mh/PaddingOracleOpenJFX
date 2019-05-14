package main.java.com.evgeniy_mh.paddingoracle;

import java.util.concurrent.atomic.AtomicInteger;

public class DecodeInfo {

  public AtomicInteger blocksCount;
  public AtomicInteger currentBlock;
  public AtomicInteger currentByte;

  DecodeInfo() {
    blocksCount = new AtomicInteger(0);
    currentBlock = new AtomicInteger(0);
    currentByte = new AtomicInteger(0);
  }
}
