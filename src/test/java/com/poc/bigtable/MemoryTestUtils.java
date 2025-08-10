package com.poc.bigtable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import org.apache.arrow.memory.RootAllocator;

public class MemoryTestUtils {
    
    public static void printMemoryUsage(String testName, String phase) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        System.out.println("=== MEMORY USAGE - " + testName + " (" + phase + ") ===");
        System.out.println("Used Memory:  " + formatBytes(usedMemory) + " (" + usedMemory + " bytes)");
        System.out.println("Free Memory:  " + formatBytes(freeMemory) + " (" + freeMemory + " bytes)");
        System.out.println("Total Memory: " + formatBytes(totalMemory) + " (" + totalMemory + " bytes)");
        System.out.println("Max Memory:   " + formatBytes(maxMemory) + " (" + maxMemory + " bytes)");
        System.out.println("Memory Usage: " + String.format("%.2f%%", (usedMemory * 100.0) / maxMemory));
        
        // Add direct memory monitoring
        printDirectMemoryUsage();
        System.out.println("===============================================");
    }
    
    public static void printDirectMemoryUsage() {
        try {
            // Get direct memory usage from memory pools
            List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean pool : memoryPools) {
                if (pool.getName().contains("direct") || pool.getName().contains("Direct")) {
                    MemoryUsage usage = pool.getUsage();
                    System.out.println("Direct Memory: " + formatBytes(usage.getUsed()) + " / " + 
                                     formatBytes(usage.getMax()) + " (" + pool.getName() + ")");
                }
            }
            
            // Try to get total direct memory via MXBean
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            System.out.println("Non-Heap Memory: " + formatBytes(nonHeapUsage.getUsed()) + " / " + 
                             formatBytes(nonHeapUsage.getMax()));
            
        } catch (Exception e) {
            System.out.println("Direct Memory: Unable to retrieve (" + e.getMessage() + ")");
        }
    }
    
    public static void printArrowMemoryUsage(RootAllocator allocator) {
        if (allocator != null) {
            System.out.println("=== ARROW MEMORY USAGE ===");
            System.out.println("Arrow Allocated: " + formatBytes(allocator.getAllocatedMemory()));
            System.out.println("Arrow Peak: " + formatBytes(allocator.getPeakMemoryAllocation()));
            System.out.println("Arrow Limit: " + formatBytes(allocator.getLimit()));
            System.out.println("Arrow Reservations: " + allocator.getHeadroom());
            System.out.println("==========================");
        }
    }
    
    public static MemorySnapshot takeSnapshot(String label) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return new MemorySnapshot(label, usedMemory, freeMemory, totalMemory, maxMemory);
    }
    
    public static void compareSnapshots(MemorySnapshot before, MemorySnapshot after) {
        long memoryDiff = after.usedMemory - before.usedMemory;
        System.out.println("=== MEMORY COMPARISON ===");
        System.out.println("Before: " + before.label + " - Used: " + formatBytes(before.usedMemory));
        System.out.println("After:  " + after.label + " - Used: " + formatBytes(after.usedMemory));
        System.out.println("Difference: " + (memoryDiff >= 0 ? "+" : "") + formatBytes(memoryDiff));
        System.out.println("========================");
    }
    
    public static void forceGarbageCollection() {
        System.gc();
        try {
            Thread.sleep(100); // Give GC time to run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    public static class MemorySnapshot {
        public final String label;
        public final long usedMemory;
        public final long freeMemory;
        public final long totalMemory;
        public final long maxMemory;
        
        public MemorySnapshot(String label, long usedMemory, long freeMemory, long totalMemory, long maxMemory) {
            this.label = label;
            this.usedMemory = usedMemory;
            this.freeMemory = freeMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
        }
    }
    
    public static class PerformanceTimer {
        private long startTime;
        private String operation;
        
        public PerformanceTimer(String operation) {
            this.operation = operation;
            this.startTime = System.currentTimeMillis();
        }
        
        public long stop() {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("Timer: " + operation + ": " + elapsed + " ms");
            return elapsed;
        }
        
        public long stopAndReturn() {
            return System.currentTimeMillis() - startTime;
        }
    }
    
    public static PerformanceTimer startTimer(String operation) {
        return new PerformanceTimer(operation);
    }
}