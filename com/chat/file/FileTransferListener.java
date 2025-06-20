package com.chat.file;

public interface FileTransferListener {
    void onProgress(int percentage);
    void onComplete(String filePath);
    void onError(String error);
}
