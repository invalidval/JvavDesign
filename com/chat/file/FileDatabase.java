package com.chat.file;

import java.util.*;
import java.util.concurrent.*;

public class FileDatabase {
    private static final Map<String, FileInfo> files = new ConcurrentHashMap<>();
    private static final Map<String, NavigableSet<FileInfo>> groupFiles = new ConcurrentHashMap<>();
    private static final Map<String, NavigableSet<FileInfo>> privateFiles = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    // 添加超时时间常量
    private static final long TIMEOUT_SECONDS = 3;

    public static CompletableFuture<List<FileInfo>> getFileListAsync(String targetId, boolean isGroup) {
        CompletableFuture<List<FileInfo>> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                NavigableSet<FileInfo> fileSet;
                if (isGroup) {
                    fileSet = groupFiles.get(targetId);
                } else {
                    fileSet = privateFiles.get(targetId);
                }

                List<FileInfo> result;
                if (fileSet == null || fileSet.isEmpty()) {
                    result = new ArrayList<>();
                } else {
                    result = new ArrayList<>(fileSet);
                }

                future.complete(result);

            } catch (Exception e) {
                future.completeExceptionally(new CompletionException("获取文件列表失败", e));
            }
        });

        return future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionallyCompose(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        executor.shutdownNow(); // 超时时中断当前执行
                        return CompletableFuture.failedFuture(
                                new CompletionException("获取文件列表超时", throwable));
                    }
                    return CompletableFuture.failedFuture(throwable);
                });
    }

    public static void addFile(FileInfo fileInfo) {
        CompletableFuture.runAsync(() -> {
            try {
                files.put(fileInfo.getId(), fileInfo);
                if (fileInfo.isGroup()) {
                    groupFiles.computeIfAbsent(fileInfo.getTargetId(),
                            k -> new ConcurrentSkipListSet<>((a, b) ->
                                    Long.compare(b.getUploadTime(), a.getUploadTime())
                            )).add(fileInfo);
                } else {
                    String key = fileInfo.getUploaderId() + "_" + fileInfo.getTargetId();
                    privateFiles.computeIfAbsent(key,
                            k -> new ConcurrentSkipListSet<>((a, b) ->
                                    Long.compare(b.getUploadTime(), a.getUploadTime())
                            )).add(fileInfo);
                }
            } catch (Exception e) {
                throw new CompletionException("添加文件失败", e);
            }
        }, executor).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static NavigableSet<FileInfo> getGroupFiles(String groupId) {
        return groupFiles.getOrDefault(groupId,
                new ConcurrentSkipListSet<>((a, b) -> Long.compare(b.getUploadTime(), a.getUploadTime())));
    }

    public static Set<FileInfo> getPrivateFiles(String user1, String user2) {
        NavigableSet<FileInfo> result = new ConcurrentSkipListSet<>((a, b) ->
                Long.compare(b.getUploadTime(), a.getUploadTime()));
        result.addAll(privateFiles.getOrDefault(user1 + "_" + user2,
                new ConcurrentSkipListSet<>((a, b) -> Long.compare(b.getUploadTime(), a.getUploadTime()))));
        result.addAll(privateFiles.getOrDefault(user2 + "_" + user1,
                new ConcurrentSkipListSet<>((a, b) -> Long.compare(b.getUploadTime(), a.getUploadTime()))));
        return result;
    }

    public static FileInfo getFileInfo(String fileId) {
        return files.get(fileId);
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
