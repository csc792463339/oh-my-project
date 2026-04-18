package com.ohmyproject.codex;

/**
 * 处理 Codex 调用期间的事件流与结束信号。
 * 实现者负责把事件翻译为 SSE / 持久化；不关心 ProcessBuilder 细节。
 */
public interface CodexEventHandler {

    void onEvent(CodexEvent event);

    /** stderr 上的一整行（已按行拆分），通常为日志，可忽略或记录。 */
    default void onStderrLine(String line) {}

    /**
     * 进程结束或调用失败时触发，只会回调一次。
     *
     * @param exitCode 进程退出码；若进程未能启动/被中断为 -1
     * @param error    非 null 表示发生异常；为 null 表示正常结束
     */
    void onComplete(int exitCode, Throwable error);
}
