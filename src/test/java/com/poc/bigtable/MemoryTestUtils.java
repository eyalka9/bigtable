package com.poc.bigtable;

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
        System.out.println("===============================================");
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
}